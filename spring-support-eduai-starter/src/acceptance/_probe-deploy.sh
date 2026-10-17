#!/bin/sh
echo "== deployed jar sha =="
sha256sum /home/kali/eduai/backend/eduai-backend.jar | cut -c1-24
echo "== class contains fix? =="
cd /tmp && rm -rf cc2 && mkdir cc2 && cd cc2
unzip -o -q /home/kali/eduai/backend/eduai-backend.jar 'BOOT-INF/classes/com/chua/starter/eduai/service/impl/EduMarkingServiceImpl.class'
grep -a -c 'isUnidentifiedStudent' BOOT-INF/classes/com/chua/starter/eduai/service/impl/EduMarkingServiceImpl.class
echo "== wait for api =="
i=0
while [ $i -lt 30 ]; do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 4 'http://127.0.0.1:19095/v2/edu/wrongbook/page?pageNum=1&pageSize=1' 2>/dev/null)
  if [ "$code" != "000" ]; then echo "api=$code after $((i*10))s"; break; fi
  i=$((i+1)); sleep 10
done
