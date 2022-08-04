ps -ef | grep "TRAN SEND" | awk "{print \"\"\$2 }" | xargs kill -9
ps -ef | grep "TRAN RECV" | awk "{print \"\"\$2 }" | xargs kill -9
screen -wipe

