package SmsGrid;
import java.util.Random;
import java.util.Scanner;
import com.logica.smpp.*;
import com.logica.smpp.pdu.*;
import com.logica.smpp.util.ByteBuffer;
import org.smpp.charset.Gsm7BitCharset;

import java.io.UnsupportedEncodingException;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.sql.*;

public class testsms_mb extends Thread
{
	Statement st=null,st1=null;
	static int nExeCount=1;
	static int nExeModSrNo=1;
	static int nCircleCode;
	int linkStatus=0;
	boolean prgContinue = true;
	//INSTANCE VARIABLES
	Session session=null;
	EnquireLink requestEnq=null;
	EnquireLinkResp responseEnq=null;
	SubmitSM requestSub=null;
	SubmitSMResp responseSub=null;
	BindRequest requestBind=null;
	TCPIPConnection connection=null;
	BindResponse responseBind=null;
	
	public static void main(String[] args) 
	{
		try
		{
			testsms_mb daemon = new testsms_mb();
			daemon.start();
		}
		catch(Exception e)
		{
			System.out.println("Caught New "+e);
		}
	}
	
	//CONSTRUCTOR FOR INITIALISATION
	public void consms()
	{
		try
		{
			requestBind = new BindTransmitter();
			String ipAddress = ""; //enter smpp server IP
			int port = 1234; //enter smpp server port
			System.out.println("Trying To Connect To Network");
			TCPIPConnection connection = new TCPIPConnection(ipAddress, port);
			System.out.println("Connected To Network");
			connection.setReceiveTimeout(20*10);
			session = new Session(connection);
			System.out.println("Making Session");
			String systemId = ""; // enter username
			String password = ""; // enter password
			String systemType= "SMPP";
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
			
			System.out.println("Before Bind");
			responseBind = session.bind(requestBind);
			System.out.println("bind response = " + responseBind.debugString() + ", status = " + responseBind.getCommandStatus());
		}
		catch(Exception e)
		{
			e.printStackTrace();
			System.exit(0);
		}
	}

	//THE THREAD RUN METHOD WHICH WILL RUN IN A INFINITE LOOP
	public void run()
	{
		try
		{
			checkBulk();
			disconnectSmsc();
			System.gc();	
		}
		catch(Exception e)
		{
			System.exit(0);
		}
	}
	
	private String replaceWordChars(String text_in) {
	    String s = text_in;
	    final Charset windowsCharset = Charset.forName("UTF-8");
	    final Charset utfCharset     = Charset.forName("UTF-16BE");
	    byte[] incomingBytes = s.getBytes();
	    final CharBuffer windowsEncoded = windowsCharset.decode(java.nio.ByteBuffer.wrap(incomingBytes)); 
	    final byte[] utfEncoded	= utfCharset.encode(windowsEncoded).array();
	    s = new String(utfEncoded);

	    return s;
	}

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
	
	//THIS METHOD IS USED TO SEND AN SMS
	// (for sending Flag Message, Change 'protocolId to 65' and 'dataCoding to 16')
	public String sendMessage(String sender,String mobileno,String message,String smsType,String peId)
	{
		String res="";
		boolean unicode = false;
		try
		{
			mobileno=mobileno.trim();
			message=message.trim();
			requestSub = new SubmitSM();
			String serviceType = "";
			String shortMessage="";
			String validityPeriod = "";
			byte esmClass = 0;
			byte dataCoding =0; 
			byte registeredDelivery=1; 
			String[] splittedMsg;
			
			byte smDefaultMsgId = 0;
			
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
			requestSub.setValidityPeriod(validityPeriod);
			requestSub.setEsmClass(esmClass);
			requestSub.setRegisteredDelivery(registeredDelivery);
			requestSub.setDataCoding(dataCoding);
			requestSub.setSmDefaultMsgId(smDefaultMsgId);
			requestSub.assignSequenceNumber(true);
			/* Add PE_ID information in TLV for DLT Scrubbing */
			requestSub.setExtraOptional((short) 0x1400, new ByteBuffer(peId.getBytes()));
			
			if ((unicode==true && message.length() <= 70) || (unicode == false && message.length() <= 160)) 
			{
				//Short SMS
				if (unicode)
					requestSub.setShortMessage(message,"UTF-16BE");
				else
					requestSub.setShortMessage(message,"X-Gsm7Bit"); //ed.appendString(shortMessage, "X-Gsm7Bit");

				responseSub = session.submit(requestSub);
			}
			else
			{
				System.out.println("Long mesage length = " + message.length());
				//Long SMS (to be sent with UDH headers)
				requestSub.setEsmClass((byte)Data.SM_UDH_GSM);
				if (unicode) {
					splittedMsg= splitByLength(message, 67);
				}
				else {
					splittedMsg= splitByLength(message, 153);
				}
				int totalSegments = splittedMsg.length;
				System.out.println("total segments = " + totalSegments);
				for (int i = 0; i < totalSegments; i++) 
				{
					shortMessage=splittedMsg[i];
					ByteBuffer ed = new ByteBuffer();
					ed.appendByte((byte) 5); // UDH Length
					ed.appendByte((byte) 0); // IE Identifier
					ed.appendByte((byte) 3); // IE Data Length
					ed.appendByte((byte) 0x0A) ; // Reference Number  0x6e 
					ed.appendByte((byte) totalSegments) ; //Number of pieces
					ed.appendByte((byte) (i+1)) ; //Sequence number
					if (unicode) {
						System.out.println("unicode message part "+ (i+1) + " of " + totalSegments);
						requestSub.setUDHMessage(shortMessage,"UTF-16BE",new String(ed.getBuffer()));
					}
					else {
						//shortMessage = RemoveEscapeChars(shortMessage);
						ed.appendString(shortMessage, "X-Gsm7Bit");
						//ed.appendString(shortMessage, "ISO8859_1");
						System.out.println("message part "+ (i+1) + " of " + totalSegments + " sms = " + shortMessage);
						requestSub.setShortMessage(new String(ed.getBuffer()));
						//requestSub.setShortMessage(msg);
					}
					responseSub = session.submit(requestSub);
					
				}			
			}
			//code for checking whether the PDU Header and the Overall PDU is Valid Or Not
			if(requestSub.isValid() && requestSub.isHeaderValid())
			{	
				if (responseSub.getCommandStatus() == Data.ESME_ROK) 
					res="SUCCESS,"+responseSub.getMessageId();
				else if (responseSub.getCommandStatus() == Data.ESME_RTHROTTLED) 
					res="FAIL,THROTTLING ERROR";
				else 
					res="FAIL,"+responseSub.debugString();
				System.out.println("Message sent to "+mobileno+" , Status=" + res);
			}
			else
				res="FAIL,PDU_ERROR";
		}
	
		catch(Exception e)
		{
			System.out.println("Caught in sendsms_smpp "+e);
			e.printStackTrace();
		}
	
		return res;	
	}
	
	//THIS METHOD IS USED TO DISCONNECT FROM THE SMSC
	public void disconnectSmsc()
	{
		try
		{
			System.out.println("Going to unbind.");
			if (session.getReceiver().isReceiver()) 
			{
				System.out.println("It can take a while to stop the receiver.");
			}
			UnbindResp response = session.unbind();
			System.out.println("Unbind response " + response.debugString());
			//System.exit(0);
		}
		catch(Exception e)
		{
			System.exit(0);
		}	
	}

	public int enquireLink()
	{
        try
		{
	        requestEnq = new EnquireLink();
      	    responseEnq = session.enquireLink(requestEnq);
			String res=responseEnq.debugString();
			int in=res.indexOf("80000015");
			char status=res.charAt(in+2);
			if(status=='0')
			{
				System.out.println("-------------------Received Positive Response From SMSC------------------------");
				linkStatus=0;
			}
			if(status!='0')
			{
				System.out.println("Problem With SMSC Link");
				linkStatus= Character.getNumericValue(status);
			}
		}
        catch(Exception e)
		{            
            System.out.println("Enquire Link operation failed. " + e);
			linkStatus=100;
			//System.exit(0);
		}
		return linkStatus;
    	}
	
	//THIS METHOD IS USED TO INVOKE A SECOND/10 SLEEP
	//To invoke a second sleep write sleep(1000)
	public void invokeSleep(int millsec)
	{
		try
		{
			sleep(millsec);
		}
		catch(Exception e)
		{
			System.out.println("Caught "+e);
		}
	}
	//THIS METHOD CHECKS UPON QUEUED UP SMS TAGS IN XML FILE
 
	 public void checkBulk()
	 {
		try
		{
			System.out.println("----Checking smu_trans for SMS-----At "+new java.util.Date());	
			String mobileno=""; //Deep
			String message ="Be Aware!! Be Healthy!!Opt for SwasthFit Health check @Rs1299 from Dr Lal PathLabs covering Lipid, Thyroid, Kidney, Liver, CBC & HbA1c.Call 07608000950";
			String sender="INGRID"; // keep sender Id as 6 digit alphabetic for transactional sms
			String sResult="";
			
			consms();		// connect to smsc
			linkStatus=enquireLink();  // check sms link
				
			Scanner s = new Scanner(System.in);
			int cnt=0;
			while (true) {
				System.out.println("Enter 10 digit mobile number to send sms on and press enter (press 0 to exit) :");
			    String input = s.nextLine();
			    if (input.equals("0"))
			        break;
			    else
			    {
			    	linkStatus=enquireLink();  // check sms link
			    	mobileno="91"+input;
			    	sResult=sendMessage(sender,mobileno,message,"1","1001630002667855981");  //3 for unicode
			    	System.out.println("result = " + sResult);
			    }
			}	
		}catch(Exception e)
		{
			disconnectSmsc();
			e.printStackTrace();
			System.exit(0);
		}
	}
}