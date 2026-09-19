-- ============================================
-- Spring Support EduAI Starter 建表脚本
-- 版本: V1.0
-- 说明: 教育AI模块数据库表
-- ============================================

-- ----------------------------
-- 知识点表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_knowledge_point (
    knowledge_point_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_knowledge_point_owner (knowledge_point_owner_user_id),
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
    KEY edu_knowledge_point_parent_id (knowledge_point_parent_id),
    KEY edu_knowledge_point_subject (knowledge_point_subject),
    KEY edu_knowledge_point_grade_level (knowledge_point_grade_level),
    KEY edu_knowledge_point_grade (knowledge_point_grade)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点表';

-- ----------------------------
-- 题目库表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_question (
    question_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_question_owner (question_owner_user_id),
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
    KEY edu_question_type (question_type),
    KEY edu_question_subject (question_subject),
    KEY edu_question_grade_level (question_grade_level),
    KEY edu_question_grade (question_grade),
    KEY edu_question_year (question_year),
    KEY edu_question_difficulty (question_difficulty),
    KEY edu_question_audit_status (question_audit_status),
    KEY edu_question_status (question_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目库表';

-- ----------------------------
-- 试卷表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_paper (
    paper_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_paper_owner (paper_owner_user_id),
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
    KEY edu_paper_subject (paper_subject),
    KEY edu_paper_grade_level (paper_grade_level),
    KEY edu_paper_status (paper_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷表';

-- ----------------------------
-- 试卷-题目关联表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_paper_question (
    paper_question_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_paper_question_owner (paper_question_owner_user_id),
    paper_question_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关联ID',
    paper_question_paper_id BIGINT NOT NULL COMMENT '试卷ID',
    paper_question_question_id BIGINT NOT NULL COMMENT '题目ID',
    paper_question_order INT DEFAULT 0 COMMENT '题目顺序',
    paper_question_score INT DEFAULT 0 COMMENT '该题分值',
    paper_question_create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY edu_paper_question_paper_question (paper_question_paper_id, paper_question_question_id),
    KEY edu_paper_question_paper_id (paper_question_paper_id),
    KEY edu_paper_question_question_id (paper_question_question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷-题目关联表';

-- ----------------------------
-- 考试表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_exam (
    exam_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_exam_owner (exam_owner_user_id),
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
    KEY edu_exam_paper_id (exam_paper_id),
    KEY edu_exam_status (exam_status),
    KEY edu_exam_start_time (exam_start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试表';

-- ----------------------------
-- 答题卡表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_answer_sheet (
    answer_sheet_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_answer_sheet_owner (answer_sheet_owner_user_id),
    answer_sheet_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '答题卡ID',
    answer_sheet_exam_id BIGINT NOT NULL COMMENT '考试ID',
    answer_sheet_student_id VARCHAR(64) NOT NULL COMMENT '学生ID',
    answer_sheet_content JSON COMMENT '答题内容（JSON）',
    answer_sheet_submit_time DATETIME COMMENT '提交时间',
    answer_sheet_status TINYINT DEFAULT 0 COMMENT '状态: 0-未提交 1-已提交 2-已批改',
    answer_sheet_create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    answer_sheet_update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY edu_answer_sheet_exam_student (answer_sheet_exam_id, answer_sheet_student_id),
    KEY edu_answer_sheet_exam_id (answer_sheet_exam_id),
    KEY edu_answer_sheet_student_id (answer_sheet_student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='答题卡表';

-- ----------------------------
-- 阅卷结果表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_marking_result (
    marking_result_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_marking_result_owner (marking_result_owner_user_id),
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
    KEY edu_marking_result_answer_sheet (marking_result_answer_sheet_id),
    KEY edu_marking_result_question_id (marking_result_question_id),
    KEY edu_marking_result_status (marking_result_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='阅卷结果表';

-- ----------------------------
-- 错题记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_wrong_question (
    wrong_question_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_wrong_question_owner (wrong_question_owner_user_id),
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
    UNIQUE KEY edu_wrong_question_student_question (wrong_question_student_id, wrong_question_question_id),
    KEY edu_wrong_question_student_id (wrong_question_student_id),
    KEY edu_wrong_question_question_id (wrong_question_question_id),
    KEY edu_wrong_question_mastery (wrong_question_mastery_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='错题记录表';

-- ----------------------------
-- 知识库文档表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_knowledge_doc (
    knowledge_doc_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_knowledge_doc_owner (knowledge_doc_owner_user_id),
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
    KEY edu_knowledge_doc_subject (knowledge_doc_subject),
    KEY edu_knowledge_doc_status (knowledge_doc_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表';
-- ============================================
-- 教育 AI 维度表增量脚本
-- 版本：V1.1
-- 说明：新增 学科/年级/班级/学生/教师 五张维度表
-- ============================================

-- ----------------------------
-- 学科表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_subject (
    subject_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_subject_owner (subject_owner_user_id),
    subject_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '学科ID',
    subject_code VARCHAR(64) NOT NULL COMMENT '学科编码',
    subject_name VARCHAR(100) NOT NULL COMMENT '学科名',
    subject_grade_levels VARCHAR(100) COMMENT '适用学段(逗号分隔)',
    subject_sort_order INT DEFAULT 0 COMMENT '排序',
    subject_status TINYINT DEFAULT 1 COMMENT '状态: 0-停用 1-启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY edu_subject_subject_code (subject_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学科维度';

-- ----------------------------
-- 年级表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_grade (
    grade_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_grade_owner (grade_owner_user_id),
    grade_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '年级ID',
    grade_level VARCHAR(20) NOT NULL COMMENT '学段 PRIMARY/JUNIOR/SENIOR/UNIVERSITY',
    grade_name VARCHAR(20) NOT NULL COMMENT '年级名 1-12 或 大一-大四',
    grade_order INT DEFAULT 0 COMMENT '学段内顺序',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY edu_grade_grade (grade_level, grade_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='年级维度';

-- ----------------------------
-- 班级表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_class (
    class_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_class_owner (class_owner_user_id),
    class_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '班级ID',
    class_code VARCHAR(64) NOT NULL COMMENT '班级编码',
    class_name VARCHAR(100) NOT NULL COMMENT '班级名',
    class_grade_level VARCHAR(20) COMMENT '学段',
    class_grade VARCHAR(20) COMMENT '年级',
    class_teacher_id BIGINT COMMENT '班主任 userId',
    class_status TINYINT DEFAULT 1 COMMENT '状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY edu_class_class_code (class_code),
    KEY edu_class_teacher (class_teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级维度';

-- ----------------------------
-- 学生表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_student (
    student_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_student_owner (student_owner_user_id),
    student_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '学生ID',
    student_user_id BIGINT COMMENT '关联系统 userId',
    student_no VARCHAR(64) NOT NULL COMMENT '学号',
    student_name VARCHAR(100) COMMENT '姓名',
    student_class_id BIGINT COMMENT '班级ID',
    student_grade_level VARCHAR(20) COMMENT '学段',
    student_grade VARCHAR(20) COMMENT '年级',
    student_enroll_year INT COMMENT '入学年份',
    student_status TINYINT DEFAULT 1 COMMENT '状态: 0-离校 1-在读',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY edu_student_student_no (student_no),
    KEY edu_student_user (student_user_id),
    KEY edu_student_class (student_class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生档案';

-- ----------------------------
-- 教师表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_teacher (
    teacher_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_teacher_owner (teacher_owner_user_id),
    teacher_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '教师ID',
    teacher_user_id BIGINT COMMENT '关联系统 userId',
    teacher_no VARCHAR(64) NOT NULL COMMENT '工号',
    teacher_name VARCHAR(100) COMMENT '姓名',
    teacher_subject_code VARCHAR(64) COMMENT '任教学科编码',
    teacher_status TINYINT DEFAULT 1 COMMENT '状态: 0-离职 1-在职',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY edu_teacher_teacher_no (teacher_no),
    KEY edu_teacher_user (teacher_user_id),
    KEY edu_teacher_subject (teacher_subject_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师档案';

-- ----------------------------
-- 趣味模块配置表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_fun_config (
    fun_config_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_fun_config_owner (fun_config_owner_user_id),
    config_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '配置ID',
    module_code VARCHAR(64) NOT NULL COMMENT '模块编码: INSECT/IDIOM/IDIOM_GAME',
    module_name VARCHAR(100) COMMENT '模块名称',
    module_desc VARCHAR(500) COMMENT '模块描述',
    module_icon VARCHAR(255) COMMENT '模块图标',
    module_path VARCHAR(255) COMMENT '模块路径',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用: 0-禁用 1-启用',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    config_json TEXT COMMENT '扩展配置JSON',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY edu_fun_config_module_code (module_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='趣味模块配置表';

-- ----------------------------
-- 成语表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_idiom (
    idiom_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_idiom_owner (idiom_owner_user_id),
    idiom_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '成语ID',
    idiom_content VARCHAR(64) NOT NULL COMMENT '成语内容',
    idiom_pinyin VARCHAR(255) COMMENT '拼音',
    idiom_meaning TEXT COMMENT '释义',
    idiom_source VARCHAR(500) COMMENT '出处',
    idiom_example TEXT COMMENT '例句',
    idiom_synonyms VARCHAR(500) COMMENT '近义词，逗号分隔',
    idiom_antonyms VARCHAR(500) COMMENT '反义词，逗号分隔',
    idiom_difficulty INT DEFAULT 1 COMMENT '难度: 1-5',
    idiom_tags VARCHAR(255) COMMENT '标签，逗号分隔',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY edu_idiom_content (idiom_content),
    KEY edu_idiom_difficulty (idiom_difficulty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成语表';

-- ----------------------------
-- 成语学段映射表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_idiom_grade (
    idiom_grade_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_idiom_grade_owner (idiom_grade_owner_user_id),
    map_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '映射ID',
    idiom_id BIGINT NOT NULL COMMENT '成语ID',
    grade_level VARCHAR(20) COMMENT '学段: PRIMARY/JUNIOR/SENIOR',
    `grade` VARCHAR(20) COMMENT '年级',
    semester VARCHAR(10) COMMENT '学期: 上/下',
    textbook_version VARCHAR(50) COMMENT '教材版本',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY edu_idiom_grade_idiom (idiom_id),
    KEY edu_idiom_grade_level (grade_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成语学段映射表';

-- ----------------------------
-- 题库采集任务表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_collect_task (
    collect_task_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_collect_task_owner (collect_task_owner_user_id),
    collect_task_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '采集任务ID',
    collect_task_name VARCHAR(100) NOT NULL COMMENT '任务名称',
    collect_task_source_type VARCHAR(50) NOT NULL COMMENT '来源类型(即SPI Provider type): TEXT/URL/API/FILE 或第三方扩展',
    collect_task_source_config TEXT COMMENT '来源配置（JSON: {text}/{url}/{apiUrl,method}/{path}，含义由Provider解析）',
    collect_task_subject VARCHAR(50) COMMENT '学科（落题默认值）',
    collect_task_grade_level VARCHAR(20) COMMENT '学段: PRIMARY/JUNIOR/SENIOR/UNIVERSITY',
    collect_task_grade VARCHAR(20) COMMENT '年级',
    collect_task_difficulty TINYINT DEFAULT 3 COMMENT '难度: 1-5',
    collect_task_cron VARCHAR(50) COMMENT '定时Cron（Spring 6，留空使用 plugin.eduai.collect.default-cron）',
    collect_task_next_run_time DATETIME COMMENT '下次执行时间（调度器扫描依据）',
    collect_task_status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    collect_task_remark VARCHAR(500) COMMENT '备注',
    collect_task_total_count INT DEFAULT 0 COMMENT '累计采集题数',
    collect_task_last_run_time DATETIME COMMENT '最近执行时间',
    collect_task_last_run_status TINYINT COMMENT '最近执行结果: 0-失败 1-成功',
    collect_task_last_run_count INT COMMENT '最近执行采集题数',
    collect_task_create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    collect_task_update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    collect_task_create_by VARCHAR(64) COMMENT '创建人',
    collect_task_update_by VARCHAR(64) COMMENT '更新人',
    KEY edu_collect_task_status (collect_task_status),
    KEY edu_collect_task_next_run_time (collect_task_next_run_time),
    KEY edu_collect_task_source_type (collect_task_source_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题库采集任务表';

-- ----------------------------
-- 题库采集执行日志表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_collect_log (
    collect_log_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_collect_log_owner (collect_log_owner_user_id),
    collect_log_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
    collect_log_task_id BIGINT NOT NULL COMMENT '采集任务ID',
    collect_log_task_name VARCHAR(100) COMMENT '任务名称快照',
    collect_log_trigger_type VARCHAR(10) DEFAULT 'AUTO' COMMENT '触发方式: AUTO-定时 MANUAL-手动',
    collect_log_source_type VARCHAR(50) COMMENT '来源类型快照',
    collect_log_status TINYINT DEFAULT 0 COMMENT '执行状态: 0-执行中 1-成功 2-失败',
    collect_log_question_count INT DEFAULT 0 COMMENT '本次采集入库题数',
    collect_log_error_msg VARCHAR(2000) COMMENT '失败原因',
    collect_log_start_time DATETIME COMMENT '开始时间',
    collect_log_end_time DATETIME COMMENT '结束时间',
    collect_log_create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY edu_collect_log_task_id (collect_log_task_id),
    KEY edu_collect_log_status (collect_log_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题库采集执行日志表';

-- ----------------------------
-- 初始数据 (种子)
-- ----------------------------
INSERT IGNORE INTO edu_subject (subject_code, subject_name, subject_grade_levels, subject_sort_order, subject_status) VALUES
    ('chinese', '语文', 'PRIMARY,JUNIOR,SENIOR', 1, 1),
    ('math',    '数学', 'PRIMARY,JUNIOR,SENIOR,UNIVERSITY', 2, 1),
    ('english', '英语', 'PRIMARY,JUNIOR,SENIOR,UNIVERSITY', 3, 1),
    ('physics', '物理', 'JUNIOR,SENIOR,UNIVERSITY', 4, 1),
    ('chemistry', '化学', 'JUNIOR,SENIOR,UNIVERSITY', 5, 1),
    ('biology', '生物', 'JUNIOR,SENIOR', 6, 1),
    ('history', '历史', 'JUNIOR,SENIOR', 7, 1),
    ('geography', '地理', 'JUNIOR,SENIOR', 8, 1),
    ('politics', '政治', 'JUNIOR,SENIOR,UNIVERSITY', 9, 1);

INSERT IGNORE INTO edu_grade (grade_level, grade_name, grade_order) VALUES
    ('PRIMARY', '1', 1),('PRIMARY', '2', 2),('PRIMARY', '3', 3),
    ('PRIMARY', '4', 4),('PRIMARY', '5', 5),('PRIMARY', '6', 6),
    ('JUNIOR', '初一', 1),('JUNIOR', '初二', 2),('JUNIOR', '初三', 3),
    ('SENIOR', '高一', 1),('SENIOR', '高二', 2),('SENIOR', '高三', 3),
    ('UNIVERSITY', '大一', 1),('UNIVERSITY', '大二', 2),('UNIVERSITY', '大三', 3),('UNIVERSITY', '大四', 4);
