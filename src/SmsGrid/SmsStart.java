package SmsGrid;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.Properties;

import SmsGrid.MessageQueue;
import SmsGrid.SendQueue;
//import SmsGrid.RecvQueue_Smpp;
import SmsGrid.SmsSend_Smpp;

public class SmsStart extends Thread
{
	public static int nQueueno;
	public static int nQueueCount;
	public static String sAccType="";
	public static String sBindType="";
	public static int nAccId;
	public static int nTpsCount;  
	
	static String dburl;
	static String dbName;
	static String driver;
	static String dbuserName;
	static String dbpassword;
	static java.sql.Connection conn= null;
	
	//private static Logger logger = Logger.getLogger(SmsStart.class);
	//private static Logger logger;
	
	public static void main(String[] args) 
	{
		//PropertyConfigurator.configure("/home/ec2-user/SmsGrid/bin/log4j.properties");    
	     //logger = Logger.getRootLogger();
		// Check how many arguments were passed in
	    if(args.length != 2)
	    {
	    	stdout("Proper Usage is: SmsStart PROMO SEND/RECV  or TRAN SEND/RECV or OTP SEND/RECV");
	        System.exit(0);
	    }
	    else
	    {
	    	sAccType=args[0];
	    	sBindType = args[1];
	    	stdout("Account Type :" + sAccType + ", Bind Type :" + sBindType);
	    }
		
	    try
		{
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
			
			try {
				Class.forName(driver).newInstance();
				conn = DriverManager.getConnection(dburl+dbName,dbuserName,dbpassword);
				} catch (Exception e) {
				e.printStackTrace();
				System.exit(0);
				}
			
			Statement st=null;
			ResultSet rs=null;
			
			st=conn.createStatement();
			
			if (sBindType.toUpperCase().equals("SEND")) 
			{
				rs = st.executeQuery("select acc_id,acc_maxsession from tblsms_account where upper(acc_type) like '%SEND%' and upper(acc_status)='Y' and upper(acc_accountype)='" + sAccType + "'");
				nQueueno=0;
				while(rs.next())
				{	
					nAccId=rs.getInt("acc_id");
					MessageQueue [] mq;
					mq = new MessageQueue [rs.getInt("acc_maxsession")];
					
					for (int i=0;i<rs.getInt("acc_maxsession");i++)
						mq[nQueueno++] = new MessageQueue(50000,nAccId,i+1);
					
					stdout("Starting SEND SMS QUEUE Thread for Account Type :" + sAccType);
					Thread thq1 = new Thread(new SendQueue(sAccType,mq,nAccId));
					thq1.start();
					
					for (int i=0;i<rs.getInt("acc_maxsession");i++)
					{
						stdout("Starting SENDSMS SMPP Thread-" + (i+1)); 
						Thread ths = new Thread(new SmsSend_Smpp(i,mq,sAccType));
						ths.start();
					}
				}
				rs.close();
			}
			else if (sBindType.toUpperCase().equals("RECV"))
			{
				//////////////////START RECV SMS THREADS
				rs = st.executeQuery("select acc_id,acc_maxsession from tblsms_account where upper(acc_type) like '%RECV%' and upper(acc_status)='Y' and upper(acc_accountype)='" + sAccType + "'");
				nQueueno=0;
				while(rs.next())
				{	
					nAccId=rs.getInt("acc_id");
					MessageQueue [] rq;
					rq = new MessageQueue [rs.getInt("acc_maxsession")];
					
					for (int i=0;i<rs.getInt("acc_maxsession");i++)
						rq[nQueueno++] = new MessageQueue(50000,nAccId,i+1);
					
					for (int i=0;i<rs.getInt("acc_maxsession");i++) 
					{
						stdout("Starting RECVSMS SMPP Thread-" + (i+1));
						Thread thr = new Thread(new SmsRecv(i,rq,sAccType));
						thr.start();
					}
				}
				rs.close();
			}
			else
				stdout("Invalid command line argument");
			/////////////////////////////////////////////////////
			conn.close();
			System.out.println("SmsStart Program finishes here.");
		}
		catch(Exception e)
		{
			System.out.println("Exception Caught in SmsStart: "+e);
		}
	}

	private static void stdout(String str)
	{
		System.out.println("SMSSTART: " + str);
		//logger.info("SMSSTART: " + str);
	}
}