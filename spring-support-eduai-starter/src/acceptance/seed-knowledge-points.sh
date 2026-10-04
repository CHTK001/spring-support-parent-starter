#!/bin/sh
# 补知识点数据并关联到 9003 卷题目（幂等：按 code 去重，重复执行不产生重复行）
DB="mysql -h127.0.0.1 -P3306 -ueduai -peduai123 --default-character-set=utf8mb4 -B eduai"

echo "== 1. 插入知识点（存在则跳过）=="
$DB <<'SQL'
INSERT INTO edu_knowledge_point
  (knowledge_point_name, knowledge_point_code, knowledge_point_subject, knowledge_point_grade_level,
   knowledge_point_grade, knowledge_point_description, knowledge_point_sort_order, knowledge_point_status,
   knowledge_point_create_time, knowledge_point_update_time)
SELECT * FROM (
  SELECT '古诗默写' AS n, 'KP-GS-MX' AS c, '语文' AS s, '初中' AS gl, '初二' AS g,
         '按原文准确默写诗句，注意易错字与标点。' AS d, 1 AS o, 1 AS st, NOW() AS ct, NOW() AS ut
  UNION ALL SELECT '修辞手法','KP-XC-SF','语文','初中','初二','比喻、拟人、夸张等修辞的判定，以及其在表情达意上的作用。',2,1,NOW(),NOW()
  UNION ALL SELECT '文学常识','KP-WX-CS','语文','初中','初二','作家、作品、朝代等基础文学常识。',3,1,NOW(),NOW()
  UNION ALL SELECT '词语理解','KP-CY-LJ','语文','初中','初二','结合语境理解词语含义与用法。',4,1,NOW(),NOW()
  UNION ALL SELECT '内容概括','KP-NR-GK','语文','初中','初二','概括文章主要内容的完整性与准确性。',5,1,NOW(),NOW()
) t WHERE NOT EXISTS (SELECT 1 FROM edu_knowledge_point k WHERE k.knowledge_point_code = t.c);
SQL

echo "== 2. 知识点行数与 id =="
$DB -N -e "select concat('  ',knowledge_point_id,'  ',knowledge_point_code,'  ',knowledge_point_name) from edu_knowledge_point order by knowledge_point_id;" 2>&1

echo "== 3. 关联到 9003 卷题目（按题序分组）=="
$DB <<'SQL'
-- 1-6 选择题：文学常识 + 词语理解
UPDATE edu_question q JOIN edu_paper_question pq ON pq.paper_question_question_id = q.question_id
SET q.question_knowledge_point_ids = (
  SELECT GROUP_CONCAT(k.knowledge_point_id ORDER BY k.knowledge_point_id)
  FROM edu_knowledge_point k WHERE k.knowledge_point_code IN ('KP-WX-CS','KP-CY-LJ'))
WHERE pq.paper_question_paper_id = 9003 AND pq.paper_question_order BETWEEN 1 AND 6;

-- 7-10 填空：古诗默写 + 词语理解
UPDATE edu_question q JOIN edu_paper_question pq ON pq.paper_question_question_id = q.question_id
SET q.question_knowledge_point_ids = (
  SELECT GROUP_CONCAT(k.knowledge_point_id ORDER BY k.knowledge_point_id)
  FROM edu_knowledge_point k WHERE k.knowledge_point_code IN ('KP-GS-MX','KP-CY-LJ'))
WHERE pq.paper_question_paper_id = 9003 AND pq.paper_question_order BETWEEN 7 AND 10;

-- 11-13 判断：内容概括
UPDATE edu_question q JOIN edu_paper_question pq ON pq.paper_question_question_id = q.question_id
SET q.question_knowledge_point_ids = (
  SELECT GROUP_CONCAT(k.knowledge_point_id ORDER BY k.knowledge_point_id)
  FROM edu_knowledge_point k WHERE k.knowledge_point_code = 'KP-NR-GK')
WHERE pq.paper_question_paper_id = 9003 AND pq.paper_question_order BETWEEN 11 AND 13;

-- 14-15 填空：词语理解
UPDATE edu_question q JOIN edu_paper_question pq ON pq.paper_question_question_id = q.question_id
SET q.question_knowledge_point_ids = (
  SELECT GROUP_CONCAT(k.knowledge_point_id ORDER BY k.knowledge_point_id)
  FROM edu_knowledge_point k WHERE k.knowledge_point_code = 'KP-CY-LJ')
WHERE pq.paper_question_paper_id = 9003 AND pq.paper_question_order BETWEEN 14 AND 15;

-- 16-18 简答：内容概括
UPDATE edu_question q JOIN edu_paper_question pq ON pq.paper_question_question_id = q.question_id
SET q.question_knowledge_point_ids = (
  SELECT GROUP_CONCAT(k.knowledge_point_id ORDER BY k.knowledge_point_id)
  FROM edu_knowledge_point k WHERE k.knowledge_point_code = 'KP-NR-GK')
WHERE pq.paper_question_paper_id = 9003 AND pq.paper_question_order BETWEEN 16 AND 18;

-- 19-21 简答：修辞手法 + 内容概括
UPDATE edu_question q JOIN edu_paper_question pq ON pq.paper_question_question_id = q.question_id
SET q.question_knowledge_point_ids = (
  SELECT GROUP_CONCAT(k.knowledge_point_id ORDER BY k.knowledge_point_id)
  FROM edu_knowledge_point k WHERE k.knowledge_point_code IN ('KP-XC-SF','KP-NR-GK'))
WHERE pq.paper_question_paper_id = 9003 AND pq.paper_question_order BETWEEN 19 AND 21;
SQL

echo "== 4. 复核：9003 卷题目的知识点关联 =="
$DB -N -e "select concat('  order=',pq.paper_question_order,' q=',q.question_id,' kp=[',ifnull(q.question_knowledge_point_ids,'NULL'),']') from edu_paper_question pq join edu_question q on q.question_id=pq.paper_question_question_id where pq.paper_question_paper_id=9003 order by pq.paper_question_order;" 2>&1 | head -22