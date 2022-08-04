package SmsGrid;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import org.apache.log4j.Logger;

public class SendQueue extends Thread
{
	private MessageQueue [] objArrQueue; 

	String dburl;
	String dbName;
	String driver;
	String dbuserName;
	String dbpassword;
	String sAccType;
	int nAccId;
	String sSql;
	java.sql.Connection conn= null;
	boolean prgContinue = true;
	private static Logger logger;
	SimpleDateFormat dtSql =new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	SimpleDateFormat dtLog =new SimpleDateFormat("HH:mm:ss");
	
	public SendQueue(String sAccType, MessageQueue[] smsQueue, int nAccId) throws IOException
	{
		objArrQueue=smsQueue;
		this.sAccType = sAccType;
		this.nAccId = nAccId;
		// set log file name
		System.setProperty("logfile.name", sAccType + "_send");
		logger= Logger.getLogger(SendQueue.class);
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
		conn.setAutoCommit(true);
		} catch (Exception e) {
		stdout("Db Connection Failed  " + e);
		e.printStackTrace();
		System.exit(0);
		}
		stdout("Connection Opened successfully for "+dburl);
	}
	
	public void run()
	{		
		String sTableName="";
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
			
			/* Update queued records to inprocess */
			
			//if (sAccType.toUpperCase().equals("PROMO") || sAccType.toUpperCase().equals("TPROMO") || sAccType.toUpperCase().equals("VPROMO"))
			if (sAccType.toUpperCase().equals("PROMO"))
			{
				sTableName="hd_pro_sms_queue";
				sSqlUpd="update " + sTableName + " set status='inprocess' where status = 'queued' and f_get_accId(userid,'PROMO')="+nAccId;
			}
			else if (sAccType.toUpperCase().equals("TRAN"))
			{
				sTableName="hd_trn_sms_queue";
				sSqlUpd="update " + sTableName + " set status='inprocess' where status = 'queued' and f_get_accId(userid,'TRAN')="+nAccId;
			}
			else if (sAccType.toUpperCase().equals("OTP"))
			{
				sTableName="hd_trn_sms_queue";
				sSqlUpd="update " + sTableName + " set status='inprocess' where status = 'queued' and f_get_accId(userid,'OTP')="+nAccId;
			}
			
			stdout(sSqlUpd);
			st1.executeUpdate(sSqlUpd);
			
			/*//////////////////////////////////////*/
			
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
				try{
					if(sAccType.toUpperCase().equals("PROMO")) {
						sSql="select smsid,campaignid,sentcli,mobileno,message,smstypeid,ifnull(pe_id,'0') pe_id,ifnull(template_id,'0') template_id,f_get_dnd(mobileno) dnd_flag from " + sTableName + "  where schdate <=now() and status='inprocess' and f_get_accId(userid,'PROMO')="+nAccId;
					}else if(sAccType.toUpperCase().equals("TRAN")){
						sSql="select smsid,campaignid,sentcli,mobileno,message,smstypeid,ifnull(pe_id,'0') pe_id,ifnull(template_id,'0') template_id,'N' dnd_flag from " + sTableName + "  where schdate <=now() and status='inprocess' and is_otp=0 and f_get_accId(userid,'TRAN')="+nAccId;
					}
					else if(sAccType.toUpperCase().contains("OTP")){
						sSql="select smsid,campaignid,sentcli,mobileno,message,smstypeid,ifnull(pe_id,'0') pe_id,ifnull(template_id,'0') template_id,'N' dnd_flag from " + sTableName + "  where schdate <=now() and status='inprocess' and is_otp=1 and f_get_accId(userid,'OTP')="+nAccId;
					}

					rs = st.executeQuery(sSql);
					nQueueNo=0;
					while(rs.next())
					{	
						nQemCount=0;
						Message m = new Message();
						m.setSeqNo(rs.getInt("smsid"));
						m.setSourceAddress(rs.getString("sentcli"));
						m.setDestAddress(rs.getString("mobileno"));
						m.setShortMessage(rs.getString("message"));
						m.setSmsType(rs.getString("smstypeid"));
						m.setPeId(rs.getString("pe_id"));
						m.setTemplateId(rs.getString("template_id"));
	
						try 
						{
							if (rs.getString("pe_id").equals("0")){
								sSqlUpd="update " + sTableName + " set status='pe_failed',sentdate=now(),sentremark='PEID NOTAVL',delivery_id='0',delivery_respcode='101' where smsid = "+rs.getInt("smsid");
								st1.executeUpdate(sSqlUpd);
							}
							else if (rs.getString("dnd_flag").equals("Y")){
								sSqlUpd="update " + sTableName + " set status='dnd_failed',sentdate=now(),sentremark='DND',delivery_id='0',delivery_respcode='102' where smsid = "+rs.getInt("smsid");
								st1.executeUpdate(sSqlUpd);
							}
							else if (rs.getString("template_id").equals("0")){
								sSqlUpd="update " + sTableName + " set status='template_failed',sentdate=now(),sentremark='DND',delivery_id='0',delivery_respcode='102' where smsid = "+rs.getInt("smsid");
								st1.executeUpdate(sSqlUpd);
							}
							else {
								objArrQueue[nQueueNo++].requestSms.putFirst(m);
								
								stdout("SmsReq :" + rs.getInt("smsid") + " - queued for processing -" + nQueueNo + " of " + objArrQueue.length);
								if(nQueueNo >= objArrQueue.length) // insert elements in queue based on round robin algorithm
									nQueueNo = 0;
								
								sSqlUpd="update " + sTableName + " set status='queued' where smsid="+rs.getInt("smsid");
								st1.executeUpdate(sSqlUpd);		
							}
							
							if (campId !=rs.getLong("campaignid"))
							{
								sSqlUpd="update hm_users_campaigns set ucp_status=1 where ucp_id ="+rs.getLong("campaignid");
								st1.executeUpdate(sSqlUpd);
								campId = rs.getLong("campaignid");
							}
						}
						catch(IndexOutOfBoundsException e)
						{
							stdout("Queue Full");
							break;
						}
					}//while close
				}finally {
			        try { rs.close(); } catch (Exception ignore) { }
			    }
				nQemCount++;
				if (nQemCount>5)  // if queue is empty for continuous 5 times
				{
					nQemCount=0;
					stdout("No Records Found for queue , Sleeping 5 seconds");
					Thread.sleep(5000);//No New Records for Queue, 10 seconds Sleep
					//if (conn.isClosed() == true || checkDBConn() == false) ConnectDb();
					//conn.close();  // close and reopen connection to free up locking resources
					//ConnectDb();
				}
				
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
		System.out.println("SMSQUEUE-" + sAccType + ": " + str);
		logger.info("SMSQUEUE-" + sAccType + ": " + str);
	}
}