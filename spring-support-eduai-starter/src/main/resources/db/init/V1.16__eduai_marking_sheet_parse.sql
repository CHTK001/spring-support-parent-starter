-- V1.16__eduai_marking_sheet_parse.sql
-- 试卷解析结果表（结构化落库）
--
-- 背景：原先阅卷的「解析结果」是散的——
--   * 整卷版面/文字/锚点塞在 edu_answer_sheet.answer_sheet_content 的一个大 JSON；
--   * 逐题识别字段（题号/题干/学生作答/满分）塞在 edu_marking_result.marking_result_detail 的 JSON 里；
--   * 姓名/学号只在 content.recognizedStudent 里，任务级再冗余一份到 edu_marking_task。
-- 没法直接按字段查、没法做「某生某题识别成什么」的统计与回溯。
--
-- 本表把解析口径结构化落库：一张试卷一份，每行一个解析项（STUDENT / TOTAL / QUESTION）。
--   STUDENT  行：姓名 + 学号
--   TOTAL    行：卷面总分（满分口径）
--   QUESTION 行：题号 + 题干 + 学生作答 + 该题满分 + AI 得分 + 对错
-- 唯一键 (task_id, kind, question_no) 保证重跑幂等；非逐题行用空串占位，
-- 这样「一卷一条 STUDENT/TOTAL」也能被唯一键真正约束住。
CREATE TABLE IF NOT EXISTS edu_marking_sheet_parse (
    parse_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '解析结果ID',
    parse_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    parse_task_id VARCHAR(64) NOT NULL COMMENT '阅卷任务业务标识',
    parse_answer_sheet_id BIGINT COMMENT '答题卡ID',
    parse_kind VARCHAR(16) NOT NULL COMMENT '解析项类型: STUDENT 姓名学号 / TOTAL 满分 / QUESTION 逐题',
    parse_student_name VARCHAR(64) COMMENT '卷面识别姓名',
    parse_student_no VARCHAR(64) COMMENT '卷面识别学号',
    parse_total_score INT COMMENT '卷面总分（满分口径）',
    parse_question_no VARCHAR(16) NOT NULL DEFAULT '' COMMENT '题号（非逐题行为空串）',
    parse_question_stem TEXT COMMENT '题干',
    parse_student_answer TEXT COMMENT '学生作答',
    parse_full_score INT COMMENT '该题满分',
    parse_earned_score INT COMMENT '该题得分（AI/老师分）',
    parse_correct TINYINT COMMENT '对错: 1 对 0 错 NULL 未判定',
    parse_source VARCHAR(16) COMMENT '识别来源: MODEL 模型 / LAYOUT 版面 / RULE 规则',
    parse_page INT COMMENT '所在页码',
    parse_region_label VARCHAR(32) COMMENT '来源版面区域标签',
    parse_region_x DOUBLE COMMENT '区域归一化坐标 x(0~1)',
    parse_region_y DOUBLE COMMENT '区域归一化坐标 y(0~1)',
    parse_region_w DOUBLE COMMENT '区域归一化宽度(0~1)',
    parse_region_h DOUBLE COMMENT '区域归一化高度(0~1)',
    parse_create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    parse_update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    update_by INT DEFAULT NULL,
    UNIQUE KEY uk_parse_task_kind_qno (parse_task_id, parse_kind, parse_question_no),
    KEY idx_parse_task (parse_task_id),
    KEY idx_parse_student_no (parse_student_no)
) COMMENT '试卷解析结果（结构化：姓名/学号/总分/逐题）';