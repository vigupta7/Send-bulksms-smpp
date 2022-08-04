package SmsGrid;
import com.logica.smpp.*;
import com.logica.smpp.pdu.*;
import com.logica.smpp.util.ByteBuffer;
import com.logica.smpp.Data;
import com.logica.smpp.Session;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Properties;
import java.util.Random;
import java.util.TimeZone;
import java.util.HashMap;

import org.apache.log4j.Logger;

public class SmsSend_Smpp extends Thread
{
	private MessageQueue objQueue; 
	private MessageQueue [] objArrQueue;
	private int nQueueIndex;
	private int nAccId;
	private int nSeqNo;
	private String sSenderId = "";
	private String sAccName = "";
	Statement st=null,st1=null,st2=null;
	int linkStatus=0;
	public static boolean prgContinue = true;
	private boolean asynchronous=false;
	private static Logger logger;
	

	public HashMap<Integer,String> hashMap = new HashMap<Integer,String>();
	String sSql;
	
	//INSTANCE VARIABLES
	Session session=null;
	EnquireLink requestEnq=null;
	EnquireLinkResp responseEnq=null;
	SubmitSM requestSub=null;
	SubmitSMResp responseSub;
	BindRequest requestBind=null;
	TCPIPConnection connection=null;
	BindResponse responseBind=null;
	boolean blnSmpConSend = false;
	SimpleDateFormat dtSql =new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
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
	String sTableName;
	String accountType;
	
	SMPPTestPDUEventListener pduListener = null;
	
    //********************//
	
	public SmsSend_Smpp(int arrindex,MessageQueue [] arrSmsQueue, String accType) throws IOException
	{
		accountType = accType;
		// Complete Array Queue is also passed for implementing work steal
		nQueueIndex=arrindex;
		objQueue=arrSmsQueue[arrindex];
		objArrQueue=arrSmsQueue;
		nAccId=objQueue.nAccId;
		nSeqNo = objQueue.nSeqNum;	
		
		dtSql.setTimeZone(TimeZone.getTimeZone("Asia/Calcutta"));
		dtLog.setTimeZone(TimeZone.getTimeZone("Asia/Calcutta"));
		// set log file name
		System.setProperty("logfile.name", accType + "_send");
		logger = Logger.getLogger(SmsSend_Smpp.class);
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
	
	public void run()
	{
		try
		{
			SendSms();
			System.gc();
		}
		catch(Exception e)
		{
			stdout("Caught in run "+e);
			StringWriter stack = new StringWriter();
			e.printStackTrace(new PrintWriter(stack));
			logger.info("Caught in run : : " + stack.toString());
			try {
				conn.close();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
			System.exit(0);
		}
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
		//System.out.println("Caught "+e);
		stdout("Db Connection Failed  " + e);
		e.printStackTrace();
		StringWriter stack = new StringWriter();
		e.printStackTrace(new PrintWriter(stack));
		logger.info("Db Connection Failed : : " + stack.toString());
		System.exit(0);
		}
		stdout("Connection Opened successfully for "+dburl);
	}
	
	public void SendSms()
	{
		stdout("Send Sms Started at " + dtLog.format(new java.util.Date()));
		// Connect to database to get Sms details
		try
		{
			ConnectDb();
			st=conn.createStatement();
			st1=conn.createStatement();
			st2=conn.createStatement();
			
			ResultSet rs=null;
			//ResultSet rs1=null;
			stdout("Getting Sms Account Details for Account Id :" + nAccId);
			rs = st.executeQuery("select * from tblsms_account where acc_id = "+ nAccId +" and upper(acc_status)='Y' and upper(acc_type)='SEND'");
			rs.next();

			sAccName=rs.getString("acc_name");
			sSenderId=rs.getString("acc_dflt_sender");
			ipAddress=rs.getString("acc_domain");
			port=rs.getInt("acc_port");
			systemId=rs.getString("acc_user");
			password=rs.getString("acc_passwd");
			systemType=rs.getString("acc_systemtype");
			
			if (rs.getString("acc_accountype").toUpperCase().equals("PROMO"))	
				sTableName="hd_pro_sms_queue";
			else
				sTableName = "hd_trn_sms_queue";
			
			//Connecting to SMSC as transmitter
			consms(ipAddress,port,systemId,password,systemType,"T");
			rs.close();
			
			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
				try
				{
					conn.close();
					stdout("Running Shutdown Hook at " + dtLog.format(new java.util.Date()));
					disconnectSmsc();
					prgContinue=false;
				}
				catch (Exception e)
				{
					stdout("Caught at Shutdown : "+e);
					StringWriter stack = new StringWriter();
					e.printStackTrace(new PrintWriter(stack));
					logger.info("Caught at Shutdown : : " + stack.toString());
					prgContinue=false;
				}}
			});
			
			String mobileno="";
			String message="";
			String recno="";

			String sender="";
			String sResult="";
			String sRemark = "";
			String delivery_respcode="";
			String[] sMessage;
			String sMessageId="";
			String smsType="";
			String peId="";
			String templateId="";
			int WaitCount=0;
			
			//long startTime = System.currentTimeMillis();
			//long endTime;
			//double passSeconds;
			//int sendSmsCount=0;

			while (prgContinue==true)
			{
				try {
				
					// checking current queue
					stdout("queue size = "+objQueue.requestSms.size());
					Object msgobject = null;
					if (objQueue.requestSms.size() >0)
						msgobject= objQueue.requestSms.pollLast();  //msgobject= objQueue.requestSms.take();
					else
					{
						msgobject = stealWork(nQueueIndex); //current queue empty, steal work from other
						if (null != msgobject)
						stdout("Queue empty, stealing work.. ");
					}
					
					if (null != msgobject) //  
					{   
						if (msgobject != null && msgobject instanceof Message) 
						{
							Message SmsMsg = (Message) msgobject;
							mobileno=SmsMsg.getDestAddress();
							message=SmsMsg.getShortMessage();
							recno=String.valueOf(SmsMsg.getSeqNo());
							sender = SmsMsg.getSourceAddress();
							peId=SmsMsg.getPEId();
							templateId = SmsMsg.getTemplateId();
									
							if (sender.isEmpty())
							{
								sender=sSenderId;
								//stdout("here");
							}
							if(systemId.equals("Grid_Promo")) {
								sender = "Alerts";
							}

							stdout ("sender = " + sender + " sSenderId = "+sSenderId);
							smsType=SmsMsg.getSmsType();  // 1- normal Sms, 2- Flash Sms, 3- Unicode, 4 - Unicode + Flash
	
							// Send Message
							if (mobileno.length() ==10)
							{
								mobileno = "91" + mobileno; //mobileno = "91" + mobileno.substring(mobileno.length() - 10);
								sRemark=sendMessage_smpp(sender,mobileno,message,smsType,peId,templateId);
							}
							else
								sRemark="FAIL,Invalid Length of Mobile Number";
							
							//stdout("remark = " + sRemark);
							if (sRemark.contains("ASYNC"))
							{
								sResult="sent"; // code for success		
								sMessage = sRemark.split(",");
								sMessageId=sMessage[1];
								delivery_respcode="0";
								// put sequence number and recno in hashmap
								hashMap.put(Integer.parseInt(sMessageId), recno);
							}
							else 
							{
								if (sRemark.contains("SUCCESS"))
								{
									sResult="fullprocess"; // code for success		
									sMessage = sRemark.split(",");
									sMessageId=sMessage[1];
									delivery_respcode="0";
								}
								else if (sRemark.contains("FAIL"))
								{
									sResult="error"; // code for FAIL		
									//sMessage = sRemark.split(",");
									sMessageId="0";
									delivery_respcode="100";
								}
								
								else if (sRemark.contains("EXCEPTION"))
								{
									sResult="error"; // code for FAIL	
									sMessageId="0";
									delivery_respcode="100";
									stdout("Enquire Link....");
									linkStatus = enquireLink();
									if (linkStatus!=0)
									consms(ipAddress,port,systemId,password,systemType,"T"); // Reconnect to SMSC
								}
								else 
									sResult=sRemark;
									//conn.commit();
							}
	
							sSql="update "+ sTableName + " set sentCli = '" + sender + "',status='" + sResult + "',sentdate='" + dtSql.format(new java.util.Date()) + "',sentremark='" + sRemark + "',account_id = "+ nAccId +",delivery_id='" + sMessageId + "',delivery_respcode='"+delivery_respcode + "' where smsid = "+recno;
							st.executeUpdate(sSql);
						}// end Message Object If
						
						/*
						////////// CONTROL TPS SPEED by ensuring that in 1 Second, only given tpscount is achieved //////////////
						
						endTime = System.currentTimeMillis();
						passSeconds = (endTime - startTime)/1000;
						if (passSeconds >1)  // if 1 second is already elapsed
							startTime = System.currentTimeMillis();
						else
						{
							if (sendSmsCount >= nTpsCount) // smscount is reached to given tps
							{
								int sleepSeconds = (int) (1000-(endTime - startTime));
								if (sleepSeconds >0) 
									Thread.sleep(1000-(endTime - startTime)); // sleep for rest of the milliseconds
								
								stdout(nTpsCount + " TPS throtelled, Sleeping for " + sleepSeconds + " milliseconds",false);
								sendSmsCount=0;
								startTime = System.currentTimeMillis();
							}
						}
						*/
						//////////////////////////////////////////////////////////////////////////////////////////////////////////
					}
					else // queue is empty
					{
						stdout("Queue Empty, Sleeping for 1 second");
						WaitCount++;
						if (WaitCount >=9)
						{
							WaitCount=0;
							stdout("EnquireLink in 10 seconds");
							linkStatus = enquireLink();
							if (linkStatus!=0)
								consms(ipAddress,port,systemId,password,systemType,"T"); // Reconnect to SMSC
							if (conn.isClosed() == true || checkDBConn() == false) ConnectDb(); // check DB Connection status, if false then reconnect to database
						}
						Thread.sleep(1000);
						//startTime = System.currentTimeMillis();
					}// End Queue IF
				}// end try
				catch(Exception e)
				{
					//stdout("Caught in sendsms loop : " + e + ", line no " + Thread.currentThread().getStackTrace()[2].getLineNumber());
					e.printStackTrace();
					StringWriter stack = new StringWriter();
					e.printStackTrace(new PrintWriter(stack));
					stdout("Caught in sendsms loop : : " + stack.toString());
					
					// reconnect Database
					if (conn.isClosed() == true || checkDBConn() == false) ConnectDb();
					// reconnect SMSC
					linkStatus = enquireLink();
					if (linkStatus!=0)
					consms(ipAddress,port,systemId,password,systemType,"T"); // Reconnect to SMSC
				}
			}// End Main while
			
			// close all ojects
			if (st != null) st.close();
			if (st1 != null) st1.close();
			conn.close();
			if (blnSmpConSend==true) disconnectSmsc();
			System.gc();
			stdout("System Exit");
			//System.exit(0);
		}
		catch(Exception e)
		{
			stdout("Caught in SENDSMS: "+e);
			StringWriter stack = new StringWriter();
			e.printStackTrace(new PrintWriter(stack));
			logger.info("Caught in SENDSMS : : " + stack.toString());
			disconnectSmsc();
			blnSmpConSend=false;
		}
		finally {
			if (blnSmpConSend==true) disconnectSmsc();
			System.gc();
			stdout("Finally System Exit");
		}
	}
	
	public Object stealWork(int index) throws InterruptedException {
		int queIndex;
		for (int i = 1 ; i < objArrQueue.length ; i++) 
		{
			queIndex=  (index + i) % objArrQueue.length; 
			Object msgobject = objArrQueue[queIndex].requestSms.pollFirst();
			if(msgobject!=null && msgobject instanceof Message)
				return msgobject;
			/*
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
			*/
		}
    	return null;
    }
	
	//CONSTRUCTOR FOR INITIALISATION
	public void consms(String ipAddress,int port,String systemId,String password,String systemType, String conType)
	{
		try
		{
			stdout("ip=" +ipAddress+", port= "+port+", systemId="+ systemId+", paswd=" +password+", systemType="+ systemType);
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
            connection.setReceiveTimeout(1000); // 1sec   
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
			
			
			blnSmpConSend=false;
			//stdout("Trying to Bind");
			while (blnSmpConSend==false)
			{
				if (asynchronous) {
	                pduListener = new SMPPTestPDUEventListener(session);   
	                responseBind = session.bind(requestBind, pduListener);   
	            } else {   
	            	responseBind = session.bind(requestBind); 
	            	stdout("System Bind.....!!!");
	            }   
				
				//responseBind = session.bind(requestBind);
				stdout("bind response = " + responseBind.debugString() + ", status = " + responseBind.getCommandStatus());
				if(responseBind.getCommandStatus() == Data.ESME_ROK)
				{
					stdout("Bind Successfull to "+ ipAddress);
					blnSmpConSend = true;
				}
				else
				{
					stdout("Bind Failed to "+ ipAddress + ", Trying to Re-Bind.");
					blnSmpConSend = false;
				}
			}
		}
		catch(Exception e)
		{
			//System.out.println("Caught "+e);
			stdout("Caught in consms: "+e);
			blnSmpConSend = false;
			StringWriter stack = new StringWriter();
			e.printStackTrace(new PrintWriter(stack));
			logger.info("Caught in consms : : " + stack.toString());
			//debug.exit(this);
			//System.exit(0);
		}
	}

	private static byte[][] splitMessage(byte[] aMessage, Integer maximumMultipartMessageSegmentSize) {
		final byte UDHIE_HEADER_LENGTH = 0x05;
		final byte UDHIE_IDENTIFIER_SAR = 0x00;
		final byte UDHIE_SAR_LENGTH = 0x03;

		// determine how many messages have to be sent
		int numberOfSegments = aMessage.length / maximumMultipartMessageSegmentSize;
		int messageLength = aMessage.length;
		if (numberOfSegments > 255) {
			numberOfSegments = 255;
			messageLength = numberOfSegments * maximumMultipartMessageSegmentSize;
		}
		if ((messageLength % maximumMultipartMessageSegmentSize) > 0) {
			numberOfSegments++;
		}

		// prepare array for all of the msg segments
		byte[][] segments = new byte[numberOfSegments][];

		int lengthOfData;

		// generate new reference number
		byte[] referenceNumber = new byte[1];
		new Random().nextBytes(referenceNumber);

		// split the message adding required headers
		for (int i = 0; i < numberOfSegments; i++) {
			if (numberOfSegments - i == 1) {
				lengthOfData = messageLength - i * maximumMultipartMessageSegmentSize;
			} else {
				lengthOfData = maximumMultipartMessageSegmentSize;
			}
			// new array to store the header
			segments[i] = new byte[6 + lengthOfData];

			// UDH header
			// doesn't include itself, its header length
			segments[i][0] = UDHIE_HEADER_LENGTH;
			// SAR identifier
			segments[i][1] = UDHIE_IDENTIFIER_SAR;
			// SAR length
			segments[i][2] = UDHIE_SAR_LENGTH;
			// reference number (same for all messages)
			segments[i][3] = referenceNumber[0];
			// total number of segments
			segments[i][4] = (byte) numberOfSegments;
			// segment number
			segments[i][5] = (byte) (i + 1);
			// copy the data into the array
			System.arraycopy(aMessage, (i * maximumMultipartMessageSegmentSize), segments[i], 6, lengthOfData);
		}
		return segments;
	}
	
	/*
	// This method converts utf-8 text to utf-16be for sending unicode/hindi sms
	private String replaceWordChars(String text_in) throws UnsupportedEncodingException{
		String s = text_in;
	    final Charset windowsCharset = Charset.forName("UTF-8");
	    final Charset utfCharset     = Charset.forName("UTF-16BE");

	    byte[] incomingBytes = s.getBytes();
	    final CharBuffer windowsEncoded = windowsCharset.decode(java.nio.ByteBuffer.wrap(incomingBytes)); 
	    final byte[] utfEncoded         = utfCharset.encode(windowsEncoded).array();
	    s = new String(utfEncoded);
	    return s;
	}
	
	private String converttoUTF16(String sInput) throws UnsupportedEncodingException {
		String sOutput = "";
		
		for (final byte b : sInput.getBytes("UTF-16BE")) {
			sOutput=sOutput+String.format("%1$02X", (b & 0xFF));
	    }
		return sOutput;
	}
	*/
	
	public static String[] splitByLength(String s, int chunkSize)  
	{  
	    int arraySize = (int) Math.ceil(s.length() / (double)chunkSize);  
	  
	    String[] returnArray = new String[arraySize];  
	  
	    int index = 0;  
	    for(int i = 0; i < s.length(); i = i+chunkSize)  
	    {  
	        if(s.length() - i < chunkSize)  
	        {  
	            returnArray[index++] = s.substring(i);  
	        }   
	        else  
	        {  
	            returnArray[index++] = s.substring(i, i+chunkSize);  
	        }  
	    }  
	  
	    return returnArray;  
	}

	// COUNT \n in String
	private int countLines(String str){
		String[] lines = str.split("\r\n|\r|\n");
		return  lines.length;
	}
	
	// for sending sms through smpp
	public String sendMessage_smpp(String sender,String mobileno,String message,String smsType,String peId,String templateId)
	{
		String res="";
		boolean unicode = false;
		try
		{
			mobileno=mobileno.trim();
			message=message.trim();
			requestSub = new SubmitSM();
			String serviceType = "";
			byte replaceIfPresentFlag = 0;
			String shortMessage="";
			String validityPeriod = "";
			byte esmClass = 0;
			byte dataCoding =0; 
			byte protocolId = 0; 
			byte priorityFlag = 0;
			byte registeredDelivery=1; 
			String[] splittedMsg;
			byte smDefaultMsgId = 0;
			//stdout("sender = " + sender + ", mobileno = " + mobileno + ", message = " + message + "type = " + smsType);
			
			if (smsType.equals("2")) //Flash Sms
			{
				dataCoding=(byte) 240;
				serviceType="4";
			}
			else if (smsType.equals("3")) //Unicode Sms
			{
				dataCoding=8;
				unicode = true;
			}
			else if (smsType.equals("4")) //Unicode +Flash Sms
			{

				dataCoding=24;
				unicode = true;
			}
			else // normal sms
				dataCoding=0;

			Address sourceAdd=new Address();
			sourceAdd.setTon((byte)0x05); //0x05 will ensure that sender number is send as is and not suffixed with 0 or 00 i.e Alphanumeric Sender Ox01 is for numeric sender
			sourceAdd.setNpi((byte)0x01);
			sourceAdd.setAddress(sender,Data.SM_ADDR_LEN);
									
			Address destAdd=new Address();
			destAdd.setTon((byte)0x01);
			destAdd.setNpi((byte)0x01);
			destAdd.setAddress(mobileno,Data.SM_ADDR_LEN);
			
			requestSub.setServiceType(serviceType);
			requestSub.setSourceAddr(sourceAdd);
			requestSub.setDestAddr(destAdd);
			//requestSub.setReplaceIfPresentFlag(replaceIfPresentFlag);
			
			requestSub.setValidityPeriod(validityPeriod);
			requestSub.setEsmClass(esmClass);
			//requestSub.setProtocolId(protocolId);
			//requestSub.setPriorityFlag(priorityFlag);
			requestSub.setRegisteredDelivery(registeredDelivery);
			requestSub.setDataCoding(dataCoding);
				
			requestSub.setSmDefaultMsgId(smDefaultMsgId);
			//requestSub.setAlertOnMsgDelivery(true);
			requestSub.assignSequenceNumber(true);
			//requestSub.setSequenceNumber(seqno);
			/* Add PE_ID information in TLV for DLT Scrubbing */
			requestSub.setExtraOptional((short) 0x1400, new ByteBuffer(peId.getBytes()));
			/* Add TEMPLATE_ID information in TLV for DLT Scrubbing */
			requestSub.setExtraOptional((short) 0x1401, new ByteBuffer(templateId.getBytes()));
			stdout("PEID = " + peId + " , TEMPLATEID = "+templateId);
			
			if ((unicode==true && message.length() <= 70) || (unicode == false && message.length() <= 160)) 
			{
				
				shortMessage=new String(message.getBytes());
				//Short SMS
				if (unicode) {
					//stdout("Unicode SMS");
					requestSub.setShortMessage(message,"UTF-16BE");
				}
				else {
					//stdout("Normal SMS");
					requestSub.setShortMessage(message); //ed.appendString(shortMessage, "X-Gsm7Bit");
					//requestSub.setShortMessage(message, "X-Gsm7Bit"); //ed.appendString(shortMessage, "X-Gsm7Bit");
				}

				if (asynchronous) {
					session.submit(requestSub);
				}
				else {
					responseSub = session.submit(requestSub);
				}
			}
			else
			{
				//Long SMS (to be sent with UDH headers)
				requestSub.setEsmClass((byte)Data.SM_UDH_GSM);

				if (unicode) {
					splittedMsg= splitByLength(message, 67);
				}
				else {
					splittedMsg= splitByLength(message, 153);
				}

				int totalSegments = splittedMsg.length;

				for (int i = 0; i < totalSegments; i++)
				{
					shortMessage=splittedMsg[i];
					ByteBuffer ed = new ByteBuffer();
					ed.appendByte((byte) 5); // UDH Length
					ed.appendByte((byte) 0); // IE Identifier
					ed.appendByte((byte) 3); // IE Data Length
					ed.appendByte((byte) 0x0A) ; // Reference Number
					ed.appendByte((byte) totalSegments) ; //Number of pieces
					ed.appendByte((byte) (i+1)) ; //Sequence number

					if (unicode) {
						//ed.appendString(shortMessage, Data.ENC_UTF16_BE);
						requestSub.setUDHMessage(shortMessage,"UTF-16BE",new String(ed.getBuffer()));
					}
					else {
						ed.appendString(shortMessage, "X-Gsm7Bit");
						requestSub.setShortMessage(new String(ed.getBuffer()));
						//ed.appendString(shortMessage,Data.ENC_ISO8859_1);
					}

					if (asynchronous) {
						session.submit(requestSub);
					}
					else {
						responseSub = session.submit(requestSub);
					}
					
					stdout("message part "+ (i+1) + " of " + totalSegments);
					//stdout("part #" + (i+1) + " response = " + responseSub.debugString());
				}			
			}
			
			//code for checking whether the PDU Header and the Overall PDU is Valid Or Not
			if(requestSub.isValid() && requestSub.isHeaderValid())
			{	

				if (asynchronous)
				{
					res="ASYNC,"+requestSub.getSequenceNumber();
					stdout("Message sent to "+mobileno);
				}
				else
				{
					if (responseSub.getCommandStatus() == Data.ESME_ROK) 
						res="SUCCESS,"+responseSub.getMessageId();
					else if (responseSub.getCommandStatus() == Data.ESME_RTHROTTLED) 
						res="FAIL,THROTTLING ERROR";
					else 
						res="FAIL,"+responseSub.debugString();
					
					stdout("Message sent to "+mobileno+" , Status=" + res);
					
				}
			}
			else {
				res="FAIL,PDU_ERROR";
			}
		}
	
		catch(Exception e)
		{
			//System.out.println("Caught "+e);
			stdout("Caught in sendsms_smpp "+e);
			e.printStackTrace();
			/*
			StringWriter stack = new StringWriter();
			e.printStackTrace(new PrintWriter(stack));
			res="EXCEPTION,"+ stack.toString();
			logger.info("Caught in sendsms_smpp : : " + stack.toString());
			*/
		}
	
		return res;	
	}

	//THIS METHOD IS USED TO DISCONNECT FROM THE SMSC
	public void disconnectSmsc()
	{
		try
		{
			stdout("Going to unbind.");
			if (session.getReceiver().isReceiver()) 
			{
				stdout("It can take a while to stop the receiver.");
			}
			UnbindResp response = session.unbind();
			stdout("Unbind response " + response.debugString());
			blnSmpConSend=false;
			//System.exit(0);
		}
		catch(Exception e)
		{
			blnSmpConSend=false;
			stdout("Caught in disconnectSmsc : "+e);
			StringWriter stack = new StringWriter();
			e.printStackTrace(new PrintWriter(stack));
			logger.info("Caught in disconnectSmsc : : " + stack.toString());
			prgContinue=false;
			System.exit(0);
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
			blnSmpConSend = false;
			//System.exit(0);
		}
		return linkStatus;
    }
	
	private void stdout(String str)
	{
		System.out.println("SEND-"+sAccName+"-"+nSeqNo +" : " + str);
		logger.info("SEND-"+sAccName+"-"+nSeqNo +" : " + str);
	}

	private class SMPPTestPDUEventListener extends SmppObject implements ServerPDUEventListener 
	{   
	     Session session;   
	     //ResultSet rs=null;
	     String qry="";
	     int rSeq;
	     String nRecId="";
	     String sResult="";
		 String sRemark = "";
		 String delivery_respcode="";
		 String sMessageId="";
	     		
	     public SMPPTestPDUEventListener(Session session) 
	     {   
	         this.session = session;   
	     }   
	
	     public void handleEvent(ServerPDUEvent event) 
	     {   
	         PDU pdu = event.getPDU();   
	         if (pdu.isResponse()) 
	         {   
		        	 try 
		        	 {
			        	 if (pdu instanceof EnquireLinkResp) 
	                 {
			        		 responseEnq = (EnquireLinkResp) pdu;
						 if (responseEnq.debugString().contains("80000015"))
						 {
							stdout("-------------------Received Positive Response From SMSC------------------------");
						 }
						 else
						 {
							stdout("Problem With SMSC Link :" + responseEnq.debugString());
							consms(ipAddress,port,systemId,password,systemType,"T"); // Reconnect to SMSC
						 }
	                 }
			        	 else if (pdu instanceof SubmitSMResp) 
			        	 {
			        		stdout("async response received " + pdu.debugString());
			        	   	SubmitSMResp s = (SubmitSMResp) pdu;
			        	   	sMessageId=s.getMessageId();
			        	   	delivery_respcode=String.valueOf(s.getCommandStatus());
			        	   	rSeq = s.getSequenceNumber();
			        	   	nRecId=hashMap.get(rSeq);
			        	   	hashMap.remove(rSeq);
			        	   	
	                    	//stdout("hashmap recid :" + nRecId + ", seqno = " + rSeq);
		                    
	                    if (s.getCommandStatus() == Data.ESME_ROK) 
	                    {
		                    sResult="fullprocess"; // code for success		
		                    sRemark= "SUCCESS";
	                    }
						else if (s.getCommandStatus() == Data.ESME_RTHROTTLED) 
						{
							sResult="error"; // code for Fail		
		                    	sRemark= "THROTTLING ERROR";
						}
						else 
						{
							sResult="error"; // code for Fail		
							sRemark= pdu.debugString();
						}
	
	                    if (nRecId !=null || nRecId !="")
	                    {
	                    	stdout("Status = " + s.getCommandStatus());
							qry="update " + sTableName +" set status='" + sResult + "',sentremark='"+sRemark+"',delivery_id ='" + sMessageId + "', delivery_respcode='"+ delivery_respcode +"' where smsid="+nRecId;
							stdout(qry);
							st2.executeUpdate(qry);
	                    }
			        	 }// END IF PDU instance of SubmitSm
		        	 }// end try 
		        	 catch (Exception e) 
		        	 {
						stdout("async Response Error " + e);
						e.printStackTrace();
		        	 } 
	         } // END IF pdu.isResponse()
	     }  // End Handle Event Function 
	}
}

