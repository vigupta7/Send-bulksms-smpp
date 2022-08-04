package SmsGrid;

import com.logica.smpp.*;
import com.logica.smpp.pdu.*;
import com.logica.smpp.util.Queue;
import com.logica.smpp.Data;
import com.logica.smpp.Session;
import com.logica.smpp.TCPIPConnection;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Properties;
import java.util.TimeZone;

import org.apache.log4j.Logger;

/*
 * Read Sms from Smpp Account in asychronous fashion and stores them in a queue
 */

public class SmsRecv extends Thread
{
	private MessageQueue objQueue; 
	private MessageQueue [] objArrQueue;
	private int nQueueIndex;
	int linkStatus=0;
	
	String sAccName;
	int nAccId;
	int nSeqNo;
	
	boolean prgContinue = true;
	private static Logger logger ;
	SimpleDateFormat dtLog =new SimpleDateFormat("HH:mm:ss");
	
	String dburl;
	String dbName;
	String driver;
	String dbuserName;
	String dbpassword;
	java.sql.Connection conn= null;
	
	String ipAddress;
	int port;
	String systemId;
	String password;
	String systemType;

	//SMPP INSTANCE VARIABLES
	SMPPTestPDUEventListener pduListener = null;
	Queue requestEvents = new Queue();
	//long receiveTimeout = Data.RECEIVE_BLOCKING;  // values of -1 means wait infinite until a pdu is recieved
	long receiveTimeout = 10000; // wait 10 seconds
	private boolean asynchronous=true;
	Session session=null;
	EnquireLink requestEnq=null;
	EnquireLinkResp responseEnq=null;
	SubmitSM requestSub=null;
	SubmitSMResp responseSub=null;
	BindRequest requestBind=null;
	TCPIPConnection connection=null;
	BindResponse responseBind=null;
	boolean blnSmpConRecv = false;
		
	public SmsRecv(int arrindex,MessageQueue [] arrSmsQueue, String accType) throws IOException
	{		
		nQueueIndex=arrindex;
		objQueue=arrSmsQueue[arrindex];
		objArrQueue=arrSmsQueue;
		nAccId=objQueue.nAccId;
		nSeqNo = objQueue.nSeqNum;	
		
		dtLog.setTimeZone(TimeZone.getTimeZone("Asia/Calcutta"));
		// set log file name
		System.setProperty("logfile.name", accType + "_recv");
		logger= Logger.getLogger(SmsRecv.class);
				
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
		String sSqlUpd="";
		String[] sMessage;
		String sMessageId = "";
		String sErrCode, sDelvStat;
		String sTableName="";
		String sender="";

		try{	
			Statement st=null;
			ResultSet rs=null;
			
			ConnectDb();
			st=conn.createStatement();
			stdout("Getting Sms Account Details for Account Id :" + nAccId);
			rs = st.executeQuery("select * from tblsms_account where acc_id = "+ nAccId +" and upper(acc_status)='Y' and upper(acc_type)='RECV'");
			rs.next();
			ipAddress=rs.getString("acc_domain");
			port=rs.getInt("acc_port");
			systemId=rs.getString("acc_user");
			password=rs.getString("acc_passwd");
			systemType=rs.getString("acc_systemtype");
			sAccName=rs.getString("acc_name");

			if (rs.getString("acc_accountype").toUpperCase().equals("PROMO") || rs.getString("acc_accountype").toUpperCase().equals("TPROMO") || rs.getString("acc_accountype").toUpperCase().equals("VPROMO"))
                sTableName="hd_pro_sms_queue";
			else if (rs.getString("acc_accountype").toUpperCase().equals("TRAN"))
                sTableName = "hd_trn_sms_queue";
			
			rs.close();
			
			stdout("SmsReceiver Program STARTS for Account " + sAccName+" - "+ nSeqNo);
			
			//Connecting to Smsc as Reciever
			consms(ipAddress,port,systemId,password,systemType,"R");
			
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
				try {
					
					/* Videocon SMSC does not support querySM
					// checking QuerySM queue
					Object msgobject;
					if (objQueue.requestSms.size() >0)
						msgobject= objQueue.requestSms.pollLast();
					else
					{
						msgobject = stealWork(nQueueIndex); //current queue empty, steal work from other
						if (null != msgobject)
						stdout("QuerySM Queue empty, stealing work.. ");
					}
					
					if (msgobject != null && msgobject instanceof Message) 
					{
						Message SmsMsg = (Message) msgobject;
						
						QuerySM request = new QuerySM();
						QuerySMResp response;
						
						Address sourceAdd = new Address();
						sourceAdd.setTon((byte) 0x01);
						sourceAdd.setNpi((byte) 0x01);
						sourceAdd.setAddress(SmsMsg.getSourceAddress(), Data.SM_ADDR_LEN);
						
						request.setSourceAddr(sourceAdd);
						request.setMessageId(SmsMsg.getMessageId());

						response = session.query(request);
						if (response != null && response.getCommandStatus() == Data.ESME_ROK) 
						{
							stdout("QuerySM response " + response.debugString());
							
							sMessageId=response.getMessageId();
							sDelvStat = new Byte(response.getMessageState()).toString();
							sErrCode = new Byte(response.getErrorCode()).toString();
							
							sSqlUpd="update " + sTableName +" set status='" + sDelvStat + "',delivery_respcode='"+ sErrCode +"',updated_at=now() where delivery_id='"+sMessageId + "'";
							st.executeUpdate(sSqlUpd);
						}
						else 
						{
							stdout("QuerySM failed ");
						}
					}
					*/
					// Recieve SM
					PDU pdu = null;
					if (asynchronous) {
						ServerPDUEvent pduEvent = pduListener.getRequestEvent(receiveTimeout);
						if (pduEvent != null) {
							pdu = pduEvent.getPDU();
						}
					} else {
						pdu = session.receive(receiveTimeout);
						stdout("Trying to receive delivery report from smsc.");
					}
					
					if (pdu != null && pdu.isRequest()) {
						stdout("Received PDU " + pdu.debugString());	
						if  (pdu instanceof DeliverSM) 
						{ // Delivery Report - DLR
							stdout("delivery report received " + pdu.getCommandStatus());
							DeliverSM deliverySM = (DeliverSM) pdu;
							
							if (deliverySM.isOk()) 
							{
								Response response = ((Request) pdu).getResponse();
								stdout("delivery report: " + response.debugString());
								session.respond(response);

								String messageReport = deliverySM.getShortMessage();
								sMessage = messageReport.split(" ");

								try {
									sMessageId = deliverySM.getReceiptedMessageId();
								} catch (Exception e) {
									sMessageId = (sMessage[0].split(":"))[1];
								}

								// Extract DeliveryStat and ErrorCode using loop
								/*
								for (String s: sMessage) {
								    if (s.contains("stat:"))
								    		sDelvStat = (s.split(":"))[1];
								    else if (s.contains("err:"))
								    		sErrCode = (s.split(":"))[1];
								}
								*/
								
								//sDelvStat = (sMessage[7].split(":"))[1];
								//sErrCode = (sMessage[8].split(":"))[1];
								
								sSqlUpd="update " + sTableName +" set status=f_dlv_report_str('" + messageReport + "'),delivery_respstatus=f_dlv_report_str('" + messageReport + "'),sentremark='"+messageReport+"',delivery_respcode=f_dlv_report_err('"+ messageReport +"'),updated_at=now() where delivery_id='"+sMessageId + "'";
								//stdout("query = " + sSqlUpd);
								st.executeUpdate(sSqlUpd);
								/*
								sSqlUpd="delete from " + sTableName +"  where delivery_id='"+sMessageId + "'";
								st.executeUpdate(sSqlUpd);
								*/
		                    	}
						}
						 else if (pdu instanceof EnquireLink) {
							EnquireLinkResp response = new EnquireLinkResp ();
							response.setOriginalRequest ((EnquireLink) pdu);
							response.setSequenceNumber (pdu.getSequenceNumber ());
							response.setCommandStatus (Data.ESME_ROK);
				            stdout("Response sent to enquire_link request.");
				            session.respond (response);
					        }
						else
							stdout("Unknown PDU " + pdu.debugString());
					} 
					else {
						stdout("No PDU received this time.");
						stdout("EnquireLink");
						linkStatus = enquireLink();
						if (linkStatus!=0)
							consms(ipAddress,port,systemId,password,systemType,"R"); // Reconnect to SMSC
						
						if (conn.isClosed() == true || checkDBConn() == false) ConnectDb(); // check DB Connection status, if false then reconnect to database
					}
					
					
				} catch (Exception e) {
					e.printStackTrace();
					StringWriter stack = new StringWriter();
					e.printStackTrace(new PrintWriter(stack));
					logger.info("Caught at Receiving : : " + stack.toString());
					if (conn.isClosed() == true || checkDBConn() == false) ConnectDb(); // check DB Connection status, if false then reconnect to database
				} 
						
			}// end while

			if (st != null) st.close();
			conn.close();
			System.gc();
			stdout("RECV SMS SMPP Program finishes here.");
		}
		catch(Exception e)
		{
			stdout("Caught New "+e);
		}
	}
	
	/*
	public Object stealWork(int index) throws InterruptedException {
		int queIndex;
		for (int i = 1 ; i < objArrQueue.length ; i++) 
		{
			queIndex=  (index + i) % objArrQueue.length; 
			Object msgobject = objArrQueue[queIndex].requestSms.pollFirst();
			   if(msgobject!=null && msgobject instanceof Message)
				{
					Message SmsMsg = (Message) msgobject;
					if (SmsMsg.getAccontId() == 0 || SmsMsg.getAccontId()==nAccId)  // if work stealed from other queue belongs to same account
						return msgobject;
					else  // inserts back to queue
					{
						objArrQueue[queIndex].requestSms.putFirst(SmsMsg);
						return null;
					}
				}
		}
    	return null;
    }
    */
	
	public void consms(String ipAddress,int port,String systemId,String password,String systemType, String conType)
	{
		try
		{
			//stdout("ip=" +ipAddress+", port= "+port+", systemId="+ systemId+", paswd=" +password+", systemType="+ systemType);
			if (conType=="T")
			{
				requestBind = new BindTransmitter();
				stdout("Trying To Connect with SMSC as Transmitter");
			}
			else if (conType=="R")
			{
				requestBind = new BindReceiver();
				stdout("Trying To Connect with SMSC as Receiver");
			}
			else
			{
				stdout("Invalid Connection Type (T-Transmitter, R - receiver) Mode specified");
				//debug.exit(this);
				System.exit(0);
			}
			
			TCPIPConnection connection = new TCPIPConnection(ipAddress, port);   
            connection.setReceiveTimeout(20 * 1000);   
            session = new Session(connection);  
			//stdout("Making Session");
			String addr = "40*";
		
			AddressRange addressRange = new AddressRange();
			addressRange.setTon((byte)0x01);
			addressRange.setNpi((byte)0x01);
			addressRange.setAddressRange(addr);
			
			requestBind.setSystemId(systemId);
			requestBind.setPassword(password);
			requestBind.setSystemType(systemType);
			requestBind.setInterfaceVersion((byte)0x34);
			requestBind.setAddressRange(addressRange);
			
			blnSmpConRecv=false;
			//stdout("Trying to Bind");
			while (blnSmpConRecv==false)
			{
				if (asynchronous) {   
	                pduListener = new SMPPTestPDUEventListener(session);   
	                responseBind = session.bind(requestBind, pduListener);   
	            } else {   
	            	responseBind = session.bind(requestBind);   
	            }   
				
				//responseBind = session.bind(requestBind);
				stdout("bind response = " + responseBind.debugString() + ", status = " + responseBind.getCommandStatus());
				if(responseBind.getCommandStatus() == Data.ESME_ROK)
				{
					stdout("Bind Successfull to "+ ipAddress);
					blnSmpConRecv = true;
				}
				else
				{
					stdout("Bind Failed to "+ ipAddress + ", Trying to Re-Bind.");
					blnSmpConRecv = false;
				}
			}
		}
		catch(Exception e)
		{
			//System.out.println("Caught "+e);
			stdout("Caught in consms: "+e);
			blnSmpConRecv = false;
			StringWriter stack = new StringWriter();
			e.printStackTrace(new PrintWriter(stack));
			logger.info("Caught in consms : : " + stack.toString());
			//debug.exit(this);
			//System.exit(0);
		}
	}
	
	public int enquireLink()
	{
		//String res="";
		try
		{
	        if (asynchronous)
	        {
	        	requestEnq = new EnquireLink();
	        	session.enquireLink(requestEnq);
	        	linkStatus=0;
	        }
	        else
	        {
				requestEnq = new EnquireLink();
	      	    responseEnq = session.enquireLink(requestEnq);
				if (responseEnq.debugString().contains("80000015"))
				{
					stdout("-------------------Received Positive Response From SMSC------------------------");
					linkStatus=0;
				}
				else
				{
					stdout("Problem With SMSC Link :" + responseEnq.debugString());
					linkStatus= 100;
				}	
	        }
		}
        catch(Exception e)
		{            
        	//event.write(e,"");
        	stdout("Enquire Link operation failed. " + e + ", Response = " + responseEnq.debugString());
			linkStatus=100;
			blnSmpConRecv = false;
			//System.exit(0);
		}
		return linkStatus;
    	}
	
	private void stdout(String str)
	{
		System.out.println("RECV-"+sAccName+"-"+nSeqNo +" : " + str);
		logger.info("RECV-"+sAccName+"-"+nSeqNo +" : " + str);
	}
	
	/**
	 * Implements simple PDU listener which handles PDUs received from SMSC.
	 * It puts the received requests into a queue and discards all received
	 * responses. Requests then can be fetched (should be) from the queue by
	 * calling to the method <code>getRequestEvent</code>.
	 */
	private class SMPPTestPDUEventListener extends SmppObject implements ServerPDUEventListener {
		@SuppressWarnings("unused")
		Session session;
		
		public SMPPTestPDUEventListener(Session session) {
			this.session = session;
		}

		public void handleEvent(ServerPDUEvent event) {
			PDU pdu = event.getPDU();
			if (pdu.isRequest()) {
				//System.out.println("async request received, enqueuing " + pdu.debugString());
				synchronized (requestEvents) {
					requestEvents.enqueue(event);
					requestEvents.notify();
				}
			} else if (pdu.isResponse()) {
				stdout("async response received " + pdu.debugString());
			} else {
				stdout(
					"pdu of unknown class (not request nor " + "response) received, discarding " + pdu.debugString());
			}
		}

		/**
		 * Returns received pdu from the queue. If the queue is empty,
		 * the method blocks for the specified timeout.
		 */
		public ServerPDUEvent getRequestEvent(long timeout) {
			ServerPDUEvent pduEvent = null;
			synchronized (requestEvents) {
				if (requestEvents.isEmpty()) {
					try {
						requestEvents.wait(timeout);
					} catch (InterruptedException e) {
						// ignoring, actually this is what we're waiting for
					}
				}
				if (!requestEvents.isEmpty()) {
					pduEvent = (ServerPDUEvent) requestEvents.dequeue();
				}
			}
			return pduEvent;
		}
	}
}