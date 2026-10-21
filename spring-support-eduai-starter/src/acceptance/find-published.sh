#!/usr/bin/env bash
# 查询已发布试卷（paper_status=1）及题量，用于端到端扫码验收挑一张内置卷
mariadb -h 127.0.0.1 -P 3306 -u eduai -peduai123 -N -e "
select p.paper_id, p.paper_name, p.paper_status, count(q.paper_question_id) as qty
from eduai.edu_paper p
left join eduai.edu_paper_question q on q.paper_question_paper_id = p.paper_id
where p.paper_status = 1
group by p.paper_id, p.paper_name, p.paper_status
order by p.paper_id desc
limit 30;
" 2>&1