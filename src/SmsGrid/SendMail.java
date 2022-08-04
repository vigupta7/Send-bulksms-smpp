package SmsGrid;

import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class SendMail {

   public static void sendHtmlEmail(String subject, String htmlBody)
   {
	   final String username = "no-reply@alcodes.com";
       final String password = "No@reply@12";
       String[] toEmails = { "vishal@maaruji.com" };
       Properties prop = new Properties();
	   prop.put("mail.smtp.host", "smtp.gmail.com");
       prop.put("mail.smtp.port", "587");
       prop.put("mail.smtp.auth", "true");
       prop.put("mail.smtp.starttls.enable", "true"); //TLS
       
       Session session = Session.getInstance(prop,
               new javax.mail.Authenticator() {
                   protected PasswordAuthentication getPasswordAuthentication() {
                       return new PasswordAuthentication(username, password);
                   }
               });

       try {

           Message message = new MimeMessage(session);
           message.setFrom(new InternetAddress(username));
           
           for (int i = 0; i < toEmails.length; i++) {
				message.addRecipient(Message.RecipientType.TO, new InternetAddress(toEmails[i]));
			 }
           
           message.setSubject(subject);
           message.setContent(htmlBody, "text/html");

           Transport.send(message);

           System.out.println("Done");

       } catch (MessagingException e) {
           e.printStackTrace();
       }	
   }
}