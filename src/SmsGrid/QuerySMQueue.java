package SmsGrid;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import org.apache.log4j.Logger;


/*
 * Fetch the Received Sms Inprocess requests from queue table (appsms_recvlog)
 * and calls thread of Request Processor Class for processing. 
 */

public class QuerySMQueue extends Thread
{
	private MessageQueue [] objArrQueue; 

	String dburl;
	String dbName;
	String driver;
	String dbuserName;
	String dbpassword;
	String sAccType;
	String sAssgnStatus;
	String sSql;
	java.sql.Connection conn= null;
	boolean prgContinue = true;
	private static Logger logger = Logger.getLogger(QuerySMQueue.class);
	SimpleDateFormat dtSql =new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	SimpleDateFormat dtLog =new SimpleDateFormat("HH:mm:ss");

	public QuerySMQueue(String sAccType, MessageQueue[] smsQueue) throws IOException
	{
		objArrQueue=smsQueue;
		this.sAccType = sAccType;
		//this.sAssgnStatus = sAssgnStatus;
		Properties prop=new Properties();
		try {   
			FileInputStream in = new FileInputStream("dbConnection.properties");   
			prop.load(in);
			in.close();
		} catch (Exception e) {   
			throw new IOException("Could not read dbConnection properties file");   
		}  

		dburl = prop.getProperty("dburl");
		dbName =prop.getProperty("dbName");
		driver = prop.getProperty("driver");
		dbuserName = prop.getProperty("dbuserName");
		dbpassword = prop.getProperty("dbpassword");
	}
	
	public boolean checkDBConn() throws SQLException
	{
		Statement stmt = null;
		ResultSet rs =null;
		try {
		   stmt = conn.createStatement();
		   rs = stmt.executeQuery("SELECT 1 from dual");
		   if (rs.next()) 
		   		{
			   stmt.close();
			   rs.close();	
			   return true; // connection is valid
		   		}
		   	}
		catch (Exception e) {
			return false;
		}
		finally {
			try { rs.close(); } catch (Exception ignore) { }
			try { stmt.close(); } catch (Exception ignore) { }
		}
		return false;
	}
	
	public void ConnectDb()
	{
		stdout("Connecting to Database " +dburl);
		try {
		Class.forName(driver).newInstance();
		conn = DriverManager.getConnection(dburl+dbName,dbuserName,dbpassword);
		} catch (Exception e) {
		stdout("Db Connection Failed  " + e);
		e.printStackTrace();
		System.exit(0);
		}
		stdout("Connection Opened successfully for "+dburl);
	}
	
	public void run()
	{		
		String sSql="";
		String sSqlUpd="";
		int nQueueNo=0;
		int nQemCount=0;
		long campId=0;
		
		stdout("SmsQueue Program STARTS for " + sAccType);
		
		try{	
			Statement st=null;
			Statement st1=null;
			ResultSet rs=null;
			
			ConnectDb();
			st=conn.createStatement();
			st1=conn.createStatement();
			
			/* Update dlr_queue records to query */
			
			if (sAccType.toUpperCase().equals("PROMO"))
			{				
				sSqlUpd="update hd_pro_sms_queue set status='fullprocess' where status in ('query','dlr_queue') and sentdate < DATE_ADD(now(),INTERVAL -1 DAY)";
				st1.executeUpdate(sSqlUpd);
				sSqlUpd="update hd_pro_sms_queue set status='query' where status='dlr_queue'";
				st1.executeUpdate(sSqlUpd);
			}	
			else if (sAccType.toUpperCase().equals("TRAN"))
			{
				sSqlUpd="update hd_trn_sms_queue set status='fullprocess' where status in ('query','dlr_queue') and sentdate < DATE_ADD(now(),INTERVAL -1 DAY)";
				st1.executeUpdate(sSqlUpd);
				sSqlUpd="update hd_trn_sms_queue set status='query' where status='dlr_queue'";
				st1.executeUpdate(sSqlUpd);
			}
			
			
			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
				try
				{
					stdout("Running Shutdown Hook at " + dtLog.format(new java.util.Date()));
					prgContinue=false;
				}
				catch (Exception e)
				{
					stdout("Caught at Shutdown : "+e);
					prgContinue=false;
				}}
			});
			
			while (prgContinue==true)
			{
				if (sAccType.toUpperCase().equals("PROMO"))
				{
					sSql="select smsid,sentcli,delivery_id from hd_pro_sms_queue " + 
						"where ((status = 'fullprocess' and (delivery_respcode ='' or delivery_respcode is null)) or status='query') " + 
						"and sentdate >= DATE_ADD(now(),INTERVAL -1 DAY) and sentdate < DATE_ADD(now(),INTERVAL -20 MINUTE)";
				}
				else if (sAccType.toUpperCase().equals("TRAN"))
				{
					sSql="select smsid,sentcli,delivery_id from hd_trn_sms_queue " + 
						"where ((status = 'fullprocess' and (delivery_respcode ='' or delivery_respcode is null)) or status='query') " + 
						"and sentdate >= DATE_ADD(now(),INTERVAL -1 DAY) and sentdate < DATE_ADD(now(),INTERVAL -20 MINUTE)";
				}
				//stdout("Acc type = " + sAccType + ", query = " + sSql);
				
				if (conn.isClosed() == true || checkDBConn() == false) ConnectDb();
				try
				{
					rs = st.executeQuery(sSql);
					nQueueNo=0;
					while(rs.next())
					{	
						nQemCount=0;
						Message m = new Message();
						m.setSourceAddress(rs.getString("sentcli"));
						m.setMessageId(rs.getString("delivery_id"));
						
						try {
							objArrQueue[nQueueNo++].requestSms.put(m);  // put in any queue as per round robin
							if(nQueueNo >= objArrQueue.length-1) // insert elements in queue based on round robin algorithm
								nQueueNo = 0;
						}catch(IndexOutOfBoundsException e)
						{
							stdout("Query SM Queue Full");
							//Thread.sleep(1000);//1 seconds Sleep
							break;
						}
						stdout("SmsReq :" + rs.getInt("smsid") + " - queued for delivery report.");
						
						if (sAccType.toUpperCase().equals("PROMO"))
							sSqlUpd="update hd_pro_sms_queue set status='dlr_queue' where smsid="+rs.getInt("smsid") + " and status in ('query','fullprocess')";
						else if (sAccType.toUpperCase().equals("TRAN"))
							sSqlUpd="update hd_trn_sms_queue set status='dlr_queue' where smsid="+rs.getInt("smsid") + " and status in ('query','fullprocess')";
						
						st1.executeUpdate(sSqlUpd);
						//conn.commit();
					}//while close
				}finally {
			        try { rs.close(); } catch (Exception ignore) { }
			    }
				nQemCount++;
				if (nQemCount>5)  // if queue is empty for continuous 5 times
				{
					nQemCount=0;
					stdout("No Records Found for Query SM queue , Sleeping 10 seconds");
					Thread.sleep(10000);//No New Records for Queue, 10 seconds Sleep
				}
				//conn.close();  // close and reopen connection to free up locking resources
				//ConnectDb();
			}// while prgcontine close

			if (st != null) st.close();
			if (st1 != null) st1.close();
			conn.close();
			System.gc();
			stdout("SMS QUEUE Program finishes here.");
		}
		catch(Exception e)
		{
			stdout("Caught New "+e);
		}
	}
	
	private void stdout(String str)
	{
		System.out.println("QUERYSM-" + sAccType + ": " + str);
		logger.info("QUERYSM-" + sAccType + ": " + str);
	}
}