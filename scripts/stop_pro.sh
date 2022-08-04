ps -ef | grep "PROMO SEND" | awk "{print \"\"\$2 }" | xargs kill -9
ps -ef | grep "PROMO RECV" | awk "{print \"\"\$2 }" | xargs kill -9
screen -wipe

