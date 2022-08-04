cd /root/SmsGrid/src

screen -S promo_send -d -m java -classpath "/root/SmsGrid/bin:/root/SmsGrid/lib/mysql-connector-java-5.1.24-bin.jar:/root/SmsGrid/lib/apache-logging-log4j.jar:/root/SmsGrid/lib/opensmpp-charset-3.0.2.jar" SmsGrid.SmsStart TPROMO SEND

screen -S promo_recv -d -m java -classpath "/root/SmsGrid/bin:/root/SmsGrid/lib/mysql-connector-java-5.1.24-bin.jar:/root/SmsGrid/lib/apache-logging-log4j.jar" SmsGrid.SmsStart TPROMO RECV

screen -S promo_send -d -m java -classpath "/root/SmsGrid/bin:/root/SmsGrid/lib/mysql-connector-java-5.1.24-bin.jar:/root/SmsGrid/lib/apache-logging-log4j.jar:/root/SmsGrid/lib/opensmpp-charset-3.0.2.jar" SmsGrid.SmsStart VPROMO SEND

screen -S promo_recv -d -m java -classpath "/root/SmsGrid/bin:/root/SmsGrid/lib/mysql-connector-java-5.1.24-bin.jar:/root/SmsGrid/lib/apache-logging-log4j.jar" SmsGrid.SmsStart VPROMO RECV
