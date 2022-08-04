package SmsGrid;

//import com.logica.smpp.pdu.DeliverSM;

public class Message {
	// mandatory parameters
	private String sourceAddress;
	private String destAddress;
	private String shortMessage;
	private String messageId;
	private String SmsType;
	private int seqNo;
	private int accId;
	private String peId;
	private String templateId;
	
	public Message() {

	}
	
	public String debugString() {
		String dbgs = "(Message ::>> ";
		dbgs += "Source Address = " + sourceAddress;
		dbgs += " ";
		dbgs += "Destination Address = " + destAddress;
		dbgs += " ";
		dbgs += "Message = " + shortMessage;
		dbgs += " ";
		dbgs += ") ";
		return dbgs;
	}

	/**
	 * @return Returns the destAddress.
	 */
	public String getDestAddress() {
		return destAddress;
	}

	/**
	 * @return Returns the Sequence No.
	 */
	public int getSeqNo() {
		return seqNo;
	}

	/**
	 * @return Returns the shortMessage.
	 */
	public String getShortMessage() {
		return shortMessage;
	}


	/**
	 * @return Returns the sourceAddress.
	 */
	public String getSourceAddress() {
		return sourceAddress;
	}

	public String getMessageId() {
		return messageId;
	}
	
	public String getSmsType() {
		return SmsType;
	}
	
	/**
	 * @return Returns the Account Id.
	 */
	public int getAccontId() {
		return accId;
	}
	
	public String getPEId() {
		return peId;
	}
	
	public String getTemplateId() {
		return templateId;
	}
	
	/**
	 * @param destAddress
	 *            The destAddress to set.
	 */
	public void setDestAddress(String destAddress) {
		this.destAddress = destAddress;
	}

	public void setMessageId(String msgId) {
		this.messageId = msgId;
	}
	
	/**
	 * @param shortMessage
	 *            The shortMessage to set.
	 */
	public void setShortMessage(String shortMessage) {
		this.shortMessage = shortMessage;
	}

	/**
	 * @param sourceAddress
	 *            The sourceAddress to set.
	 */
	public void setSourceAddress(String sourceAddress) {
		this.sourceAddress = sourceAddress;
	}
	
	public void setSmsType(String smsType) {
		this.SmsType = smsType;
	}

	public void setSeqNo(int seqNo) {
		this.seqNo = seqNo;
	}

	public void setAccontId(int accId) {
		this.accId = accId;
	}
	
	public void setPeId(String peId) {
		this.peId = peId;
	}
	
	public void setTemplateId(String templateId) {
		this.templateId = templateId;
	}
}