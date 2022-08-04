cd /root/SmsGrid/src

screen -S tran_send -d -m java -classpath "/root/SmsGrid/bin:/root/SmsGrid/lib/mysql-connector-java-5.1.24-bin.jar:/root/SmsGrid/lib/apache-logging-log4j.jar" SmsGrid.SmsStart TRAN SEND

screen -S tran_recv -d -m java -classpath "/root/SmsGrid/bin:/root/SmsGrid/lib/mysql-connector-java-5.1.24-bin.jar:/root/SmsGrid/lib/apache-logging-log4j.jar" SmsGrid.SmsStart TRAN RECV
