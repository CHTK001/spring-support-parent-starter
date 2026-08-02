-- ============================================
-- Spring Support EduAI Starter 建表脚本
-- 版本: V1.0
-- 说明: 教育AI模块数据库表
-- ============================================

-- ----------------------------
-- 知识点表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_knowledge_point (
    knowledge_point_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '知识点ID',
    knowledge_point_name VARCHAR(200) NOT NULL COMMENT '知识点名称',
    knowledge_point_code VARCHAR(100) COMMENT '知识点编码',
    knowledge_point_parent_id BIGINT DEFAULT 0 COMMENT '父知识点ID，0表示根节点',
    knowledge_point_subject VARCHAR(50) COMMENT '所属学科',
    knowledge_point_grade_level VARCHAR(20) COMMENT '学段: PRIMARY/JUNIOR/SENIOR/UNIVERSITY',
    knowledge_point_grade VARCHAR(20) COMMENT '年级: 1-12 或 大一-大四',
    knowledge_point_description VARCHAR(500) COMMENT '知识点描述',
    knowledge_point_embedding TEXT COMMENT '向量嵌入（JSON数组）',
    knowledge_point_sort_order INT DEFAULT 0 COMMENT '排序号',
    knowledge_point_status TINYINT DEFAULT 1 COMMENT '状态: 0-停用 1-启用',
    knowledge_point_create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    knowledge_point_update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    knowledge_point_create_by VARCHAR(64) COMMENT '创建人',
    knowledge_point_update_by VARCHAR(64) COMMENT '更新人',
    INDEX idx_parent_id (knowledge_point_parent_id),
    INDEX idx_subject (knowledge_point_subject),
    INDEX idx_grade_level (knowledge_point_grade_level),
    INDEX idx_grade (knowledge_point_grade)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点表';

-- ----------------------------
-- 题目库表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_question (
    question_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '题目ID',
    question_type VARCHAR(30) NOT NULL COMMENT '题型: SINGLE_CHOICE/MULTIPLE_CHOICE/JUDGE/FILL/BLANK/SHORT_ANSWER/ESSAY',
    question_subject VARCHAR(50) NOT NULL COMMENT '学科',
    question_grade_level VARCHAR(20) NOT NULL COMMENT '学段: PRIMARY/JUNIOR/SENIOR/UNIVERSITY',
    question_grade VARCHAR(20) NOT NULL COMMENT '年级: 1-12 或 大一-大四',
    question_year VARCHAR(10) COMMENT '年份',
    question_difficulty TINYINT DEFAULT 3 COMMENT '难度: 1-5，1最简单',
    question_knowledge_point_ids VARCHAR(500) COMMENT '关联知识点ID列表，逗号分隔',
    question_stem TEXT NOT NULL COMMENT '题干',
    question_stem_images VARCHAR(1000) COMMENT '题干图片URL，逗号分隔',
    question_options JSON COMMENT '选项（JSON数组）',
    question_answer TEXT NOT NULL COMMENT '标准答案',
    question_analysis TEXT COMMENT '答案解析',
    question_source VARCHAR(200) COMMENT '来源（采集URL/手动录入）',
    question_source_type VARCHAR(30) DEFAULT 'MANUAL' COMMENT '来源类型: MANUAL/IMPORT/CRAWL',
    question_audit_status TINYINT DEFAULT 0 COMMENT '审核状态: 0-待审核 1-通过 2-驳回',
    question_use_count INT DEFAULT 0 COMMENT '使用次数',
    question_correct_rate DECIMAL(5,2) DEFAULT 0.00 COMMENT '正确率',
    question_status TINYINT DEFAULT 1 COMMENT '状态: 0-停用 1-启用',
    question_create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    question_update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    question_create_by VARCHAR(64) COMMENT '创建人',
    question_update_by VARCHAR(64) COMMENT '更新人',
    INDEX idx_type (question_type),
    INDEX idx_subject (question_subject),
    INDEX idx_grade_level (question_grade_level),
    INDEX idx_grade (question_grade),
    INDEX idx_year (question_year),
    INDEX idx_difficulty (question_difficulty),
    INDEX idx_audit_status (question_audit_status),
    INDEX idx_status (question_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目库表';

-- ----------------------------
-- 试卷表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_paper (
    paper_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '试卷ID',
    paper_name VARCHAR(200) NOT NULL COMMENT '试卷名称',
    paper_subject VARCHAR(50) NOT NULL COMMENT '学科',
    paper_grade_level VARCHAR(20) NOT NULL COMMENT '学段',
    paper_grade VARCHAR(20) NOT NULL COMMENT '年级',
    paper_total_score INT NOT NULL COMMENT '总分',
    paper_duration INT COMMENT '考试时长（分钟）',
    paper_question_count INT DEFAULT 0 COMMENT '题目总数',
    paper_config JSON COMMENT '组卷配置（JSON）',
    paper_status TINYINT DEFAULT 1 COMMENT '状态: 0-草稿 1-已发布 2-已归档',
    paper_source VARCHAR(30) DEFAULT 'AI' COMMENT '来源: AI/MANUAL',
    paper_create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    paper_update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    paper_create_by VARCHAR(64) COMMENT '创建人',
    paper_update_by VARCHAR(64) COMMENT '更新人',
    INDEX idx_subject (paper_subject),
    INDEX idx_grade_level (paper_grade_level),
    INDEX idx_status (paper_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷表';

-- ----------------------------
-- 试卷-题目关联表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_paper_question (
    paper_question_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关联ID',
    paper_question_paper_id BIGINT NOT NULL COMMENT '试卷ID',
    paper_question_question_id BIGINT NOT NULL COMMENT '题目ID',
    paper_question_order INT DEFAULT 0 COMMENT '题目顺序',
    paper_question_score INT DEFAULT 0 COMMENT '该题分值',
    paper_question_create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_paper_question (paper_question_paper_id, paper_question_question_id),
    INDEX idx_paper_id (paper_question_paper_id),
    INDEX idx_question_id (paper_question_question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷-题目关联表';

-- ----------------------------
-- 考试表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_exam (
    exam_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '考试ID',
    exam_paper_id BIGINT NOT NULL COMMENT '试卷ID',
    exam_name VARCHAR(200) NOT NULL COMMENT '考试名称',
    exam_class_id VARCHAR(100) COMMENT '班级ID',
    exam_start_time DATETIME COMMENT '开始时间',
    exam_end_time DATETIME COMMENT '结束时间',
    exam_duration INT COMMENT '考试时长（分钟）',
    exam_status TINYINT DEFAULT 1 COMMENT '状态: 0-未开始 1-进行中 2-已结束',
    exam_create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    exam_update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    exam_create_by VARCHAR(64) COMMENT '创建人',
    exam_update_by VARCHAR(64) COMMENT '更新人',
    INDEX idx_paper_id (exam_paper_id),
    INDEX idx_status (exam_status),
    INDEX idx_start_time (exam_start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试表';

-- ----------------------------
-- 答题卡表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_answer_sheet (
    answer_sheet_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '答题卡ID',
    answer_sheet_exam_id BIGINT NOT NULL COMMENT '考试ID',
    answer_sheet_student_id VARCHAR(64) NOT NULL COMMENT '学生ID',
    answer_sheet_content JSON COMMENT '答题内容（JSON）',
    answer_sheet_submit_time DATETIME COMMENT '提交时间',
    answer_sheet_status TINYINT DEFAULT 0 COMMENT '状态: 0-未提交 1-已提交 2-已批改',
    answer_sheet_create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    answer_sheet_update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_exam_student (answer_sheet_exam_id, answer_sheet_student_id),
    INDEX idx_exam_id (answer_sheet_exam_id),
    INDEX idx_student_id (answer_sheet_student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='答题卡表';

-- ----------------------------
-- 阅卷结果表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_marking_result (
    marking_result_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '阅卷结果ID',
    marking_result_answer_sheet_id BIGINT NOT NULL COMMENT '答题卡ID',
    marking_result_question_id BIGINT NOT NULL COMMENT '题目ID',
    marking_result_score INT COMMENT '得分',
    marking_result_comment VARCHAR(500) COMMENT '评语',
    marking_result_detail JSON COMMENT '批改详情（JSON）',
    marking_result_ai_score INT COMMENT 'AI评分',
    marking_result_ai_comment VARCHAR(500) COMMENT 'AI评语',
    marking_result_teacher_score INT COMMENT '教师修正后评分',
    marking_result_teacher_comment VARCHAR(500) COMMENT '教师评语',
    marking_result_status TINYINT DEFAULT 0 COMMENT '状态: 0-待批改 1-AI已批改 2-教师已确认',
    marking_result_task_id VARCHAR(64) COMMENT '异步任务ID',
    marking_result_create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    marking_result_update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_answer_sheet (marking_result_answer_sheet_id),
    INDEX idx_question_id (marking_result_question_id),
    INDEX idx_status (marking_result_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='阅卷结果表';

-- ----------------------------
-- 错题记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_wrong_question (
    wrong_question_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '错题记录ID',
    wrong_question_student_id VARCHAR(64) NOT NULL COMMENT '学生ID',
    wrong_question_question_id BIGINT NOT NULL COMMENT '题目ID',
    wrong_question_exam_id BIGINT COMMENT '考试ID',
    wrong_question_wrong_count INT DEFAULT 1 COMMENT '错误次数',
    wrong_question_mastery_level TINYINT DEFAULT 0 COMMENT '掌握度: 0-未掌握 1-部分掌握 2-已掌握',
    wrong_question_last_wrong_time DATETIME COMMENT '最近错误时间',
    wrong_question_last_review_time DATETIME COMMENT '最近复习时间',
    wrong_question_review_count INT DEFAULT 0 COMMENT '复习次数',
    wrong_question_status TINYINT DEFAULT 1 COMMENT '状态: 0-已删除 1-有效',
    wrong_question_create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    wrong_question_update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_student_question (wrong_question_student_id, wrong_question_question_id),
    INDEX idx_student_id (wrong_question_student_id),
    INDEX idx_question_id (wrong_question_question_id),
    INDEX idx_mastery (wrong_question_mastery_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='错题记录表';

-- ----------------------------
-- 知识库文档表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_knowledge_doc (
    knowledge_doc_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文档ID',
    knowledge_doc_name VARCHAR(200) NOT NULL COMMENT '文档名称',
    knowledge_doc_type VARCHAR(50) NOT NULL COMMENT '文档类型: PDF/WORD/TXT/MARKDOWN',
    knowledge_doc_file_path VARCHAR(500) COMMENT '文件存储路径',
    knowledge_doc_file_size BIGINT COMMENT '文件大小（字节）',
    knowledge_doc_vector_store_id VARCHAR(200) COMMENT '向量存储ID',
    knowledge_doc_subject VARCHAR(50) COMMENT '学科',
    knowledge_doc_grade_level VARCHAR(20) COMMENT '学段',
    knowledge_doc_status TINYINT DEFAULT 1 COMMENT '状态: 0-处理中 1-已索引 2-失败',
    knowledge_doc_create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    knowledge_doc_update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    knowledge_doc_create_by VARCHAR(64) COMMENT '创建人',
    knowledge_doc_update_by VARCHAR(64) COMMENT '更新人',
    INDEX idx_subject (knowledge_doc_subject),
    INDEX idx_status (knowledge_doc_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表';