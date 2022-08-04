ps -ef | grep "TPROMO SEND" | awk "{print \"\"\$2 }" | xargs kill -9
ps -ef | grep "TPROMO RECV" | awk "{print \"\"\$2 }" | xargs kill -9
ps -ef | grep "VPROMO SEND" | awk "{print \"\"\$2 }" | xargs kill -9
ps -ef | grep "VPROMO RECV" | awk "{print \"\"\$2 }" | xargs kill -9
screen -wipe