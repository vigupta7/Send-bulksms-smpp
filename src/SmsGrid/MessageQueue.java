package SmsGrid;

//import com.logica.smpp.util.Queue;
//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.LinkedBlockingQueue;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;


public class MessageQueue {
	//public BlockingQueue<Message> requestSms;
	public BlockingDeque<Message> requestSms;
	public int nAccId;
	public int nQueueSize;
	public int nSeqNum;
	
	public MessageQueue(int size,int accId,int seqNo) {
		nQueueSize=size;
		nAccId=accId;
		nSeqNum = seqNo;
		requestSms = new LinkedBlockingDeque();
		//requestSms = new LinkedBlockingQueue();
	}
}
