#!/usr/bin/env bash
mariadb -h 127.0.0.1 -P 3306 -u eduai -peduai123 -e "show columns from eduai.edu_wrong_question;" 2>&1 | head -40
echo '--- recent wrong questions ---'
mariadb -h 127.0.0.1 -P 3306 -u eduai -peduai123 -e "select * from eduai.edu_wrong_question order by 1 desc limit 5;" 2>&1