A Java program that uses OpenLogica SMPP libary to send multiple sms to parties in bulk using SMPP protocol.

# Features 

1. Uses Mysql database to read all sms records and mobile numbers.
2. Connect/Bind to SMSC via SMPP in both Transmitter and Receiver Mode to send sms and read sms delivery reports.
3. Create multiple Transmitter and Reciever threads to send and read sms.
4. Each Thread is assigned a seperate queue to remove queue locking up.
5. Sms records are fed to different queues using round robin algorithm.
6. Implements Work-Steal algorithm to balance load among workers in which If one queue or wroker is empty, it steals work from other queue.
7. Database connections breaking, SMPP connection fluctuations are taken care in the code.
8. Sending Long sms in parts via UDH.
9. Sending multilangauge sms via unicode.
10. Implements both synchronous and asychronous mode for bind.
11. Supports Throttling errors to maintain TPS rate among all threads.