#!/bin/bash
MYSQLHOST="localhost"
MYSQLDB="crmgrid"
MYSQLUSER="root"
MYSQLPASS="Grid#123Crm"
 
#File name with day-1 date
yest=$(date --date="yesterday" +"%d%m%Y")

#File location
FILE="/var/www/html/91grid/reports/BulkSms_Report_$yest.csv"
FILENAME="BulkSms_Report_$yest.csv"
FILELINK="http://13.127.251.57/91grid/reports/BulkSms_Report_$yest.csv" 

MYSQLOPTS="--batch --user=${MYSQLUSER} --password=${MYSQLPASS} --host=${MYSQLHOST} ${MYSQLDB}"
 
#testing purposes, give echo output
rm -f $FILE

echo "Report Begin: $(date)"
 
mysql ${MYSQLOPTS} << EOFMYSQL

select sentCli,concat(userid,'_',DATE_FORMAT(enterdate,'%Y%m%d%h%i%S')),
userid,mobileno,length(message),IFNULL(delivery_respcode,1),
case when status in ('inprocess','queued') then 'Pending' 
	 when status in ('sent','fullprocess') then 'Submitted' 
	 when status ='DELIVRD' then 'Delivered' 
	 when status ='error' then 'Failed' 
	 else status end,IFNULL(sentdate,""),
IFNULL(updated_at,""), IFNULL(delivery_id,""),
TIMESTAMPDIFF(SECOND,updated_at,sentdate),IFNULL(Channel,'WEB'),
campaignid,'Promotional',IFNULL(Concat(user_fname,user_lname),""),
OPR.Circle,OPR.Operator,COALESCE(sms_count,1),Message,ucp_campaign_name  
from hd_pro_sms_queue SMS join hm_users_kycs USERS on SMS.userid = USERS.user_id
left outer join hm_users_campaigns CAMP on SMS.campaignid=CAMP.ucp_id 
left outer join hm_mdn_series OPR on substr(SMS.mobileno,1,ifnull(OPR.match_len,4)) = OPR.mdn_code INTO OUTFILE '$FILE' 
FIELDS TERMINATED BY '\t' LINES TERMINATED BY '\n';

delete from hd_report_logs where report_name = 'SMS_DETAIL_ALL' and file_name='$FILE' and report_date = '$yest';

insert into hd_report_logs(report_name,file_name,file_link,report_date,generated_at) values 
('SMS_DETAIL_ALL','$FILENAME','$FILELINK','$yest',now());

EOFMYSQL
 
#add column title to the report
sed -i '1i SenderId\tSessionId\tClientId\tMsisdn\tMessageLength\tErrorCode\tErrorDesc\tSubmitDateTime\tDeliveryDate\tMessageID\tDeliveryTime\tChannelType\tCampaignId\tMessageCategory\tUsername\tCircle\tOperator\tSmsCount\tMessage\tCampaignName' $FILE

echo "Report End: $(date)"

find /var/www/html/91grid/reports/BulkSms_Report* -mtime +90 -exec rm {} \;
