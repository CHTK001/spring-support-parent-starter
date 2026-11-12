-- =====================================================================
-- V2.0__init_all_eduai.sql - spring-support-eduai-starter 全量表结构
-- 通道: INIT 同步建表（表结构先于种子数据就位）
-- 说明: 本文件是该模块唯一的建表入口。此前分散的
--   V1.2 / V1.3 / V1.4 / V1.5 / V1.6 / V1.9 / V1.13 的 init_all
--   与 V1.14 ~ V1.22 的增量脚本
--   已在本次收敛中并入本文件（结构）与 V2.0__initdata_all_eduai.sql（种子）并删除。
--
-- 合并方式:
--   1. 原来靠 ALTER 补的列，一律折进建表语句 —— 新库一次建全；
--   2. 存量库补列的 ALTER 仍保留在文件末尾（information_schema 判存 + 动态 DDL），
--      否则老库缺列；新建库上这些语句会因列已存在而跳过。
--
-- 约束: 含 MySQL 专属语法（SET @var + PREPARE 动态 DDL、COMMENT 内联注释）。
--   MariaDB 11.8 实测不接受 ADD COLUMN IF NOT EXISTS（语句报错、列不会建成），
--   MySQL 虽支持但会告警，故补列一律用「查列 → 不存在才 ALTER」两步走。
-- =====================================================================

-- ----------------------------
-- 知识点表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_knowledge_point (
    knowledge_point_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
  `knowledge_point_content_type` VARCHAR(20) DEFAULT 'TEXT' COMMENT '内容类型',
  `knowledge_point_content` TEXT COMMENT '文本内容',
  `knowledge_point_file_name` VARCHAR(255) COMMENT '原始文件名',
  `knowledge_point_file_path` VARCHAR(500) COMMENT '存储路径',
  `knowledge_point_file_type` VARCHAR(50) COMMENT '文件扩展名',
  `knowledge_point_file_size` BIGINT COMMENT '文件大小字节',

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
    KEY edu_knowledge_point_grade (knowledge_point_grade),
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    update_by INT DEFAULT NULL
);

-- ----------------------------
-- 题目库表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_question (
    question_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
  `question_score` INT DEFAULT 5 COMMENT '分值',

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
    KEY edu_question_status (question_status),
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    update_by INT DEFAULT NULL,
    UNIQUE KEY uk_edu_question_stem (question_stem(255)),
      -- 题库：被加入试卷的次数,
      -- 说明：与「使用次数」区分 —— 使用次数是答题/引用统计，,
      --      本列只统计「被新加入某张试卷」的次数，改分不重复计。,
    question_audit_remark VARCHAR(500) COMMENT '驳回理由（驳回必填，通过时清空）',
    question_audit_count INT DEFAULT 0 COMMENT '累计审核次数（每次通过/驳回+1）',
    question_add_count INT DEFAULT 0 COMMENT '被加入试卷的次数'
);

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
    KEY edu_paper_status (paper_status),
      -- 手动组卷：试卷表扩展（唯一编号 / 纸张 / 模板 / 页头页脚 / 版本）,
    paper_no VARCHAR(64) DEFAULT NULL COMMENT '试卷唯一编号（业务码，对外标识）',
    paper_page_size VARCHAR(30) DEFAULT 'A4' COMMENT '纸张规格: A4/A3/A5/B5/LETTER',
    paper_page_orientation VARCHAR(20) DEFAULT 'PORTRAIT' COMMENT '纸张方向: PORTRAIT/LANDSCAPE',
    paper_template VARCHAR(64) DEFAULT 'default' COMMENT '首页模板标识',
    paper_header TEXT DEFAULT NULL COMMENT '页头（富文本）',
    paper_footer TEXT DEFAULT NULL COMMENT '页脚（富文本）',
    paper_version INT DEFAULT 1 COMMENT '当前版本号（每次改动 +1）',
    paper_subject_locked TINYINT DEFAULT 0 COMMENT '学科/学段是否锁定: 0-否 1-是',
    UNIQUE INDEX uk_edu_paper_no (paper_no),
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    update_by INT DEFAULT NULL,
      -- 手动组卷：试卷难度与模板引用,
      -- 背景：手动组卷建卷时前端可指定整卷难度与引用的组卷模板，,
      --       两个字段随建卷/更新接口写入 edu_paper，详情接口回读。,
    paper_difficulty TINYINT DEFAULT 3 COMMENT '试卷整体难度 1-5',
    paper_template_id BIGINT DEFAULT NULL COMMENT '引用的模板ID',
      -- 手动组卷：首页大标题风格,
      -- 背景：大标题风格原先只在前端设置弹框里可选，edu_paper 无对应列，,
      --       保存时被静默丢弃，重新进入组卷页又回到默认值。,
    paper_title_style VARCHAR(20) DEFAULT 'default' COMMENT '首页大标题风格: default/line/double/calligraphy'
);

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
    KEY edu_paper_question_question_id (paper_question_question_id),
      -- 手动组卷：题项表扩展（唯一编号 = 二维码内容 / 版本）,
    paper_question_no VARCHAR(64) DEFAULT NULL COMMENT '试卷题唯一编号（二维码内容，AI 凭此定位题目）',
    paper_question_version INT DEFAULT 1 COMMENT '所属试卷版本号',
    paper_question_section VARCHAR(64) DEFAULT NULL COMMENT '题组/大题标识（可选）',
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    update_by INT DEFAULT NULL
);



-- ----------------------------
-- 试卷历史表（防篡改留痕：每次改动落一条不可变快照）
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_paper_history (
    paper_history_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_paper_history_owner (paper_history_owner_user_id),
    paper_history_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '历史ID',
    paper_history_paper_id BIGINT NOT NULL COMMENT '试卷ID',
    paper_history_paper_no VARCHAR(64) DEFAULT NULL COMMENT '试卷唯一编号',
    paper_history_version INT NOT NULL COMMENT '版本号（与试卷 paper_version 对应）',
    paper_history_action VARCHAR(30) DEFAULT NULL COMMENT '动作: CREATE/UPDATE/ADD_QUESTION/REMOVE_QUESTION/REORDER/PUBLISH',
    paper_history_snapshot LONGTEXT COMMENT '试卷快照（JSON：卷头 + 题项列表，含顺序与分值）',
    paper_history_hash VARCHAR(64) DEFAULT NULL COMMENT '快照 SHA-256 摘要（用于比对历史是否被改写）',
    paper_history_create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '留痕时间',
    paper_history_create_by VARCHAR(64) COMMENT '操作人',
    KEY edu_paper_history_paper_id (paper_history_paper_id),
    KEY edu_paper_history_paper_no (paper_history_paper_no),
    KEY edu_paper_history_version (paper_history_paper_id, paper_history_version)
);

-- ----------------------------
-- 考试表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_exam (
    exam_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
  `exam_del_flag` INT DEFAULT 0 COMMENT '逻辑删除标记',

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
    KEY edu_exam_start_time (exam_start_time),
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    update_by INT DEFAULT NULL
);

-- ----------------------------
-- 答题卡表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_answer_sheet (
    answer_sheet_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_answer_sheet_owner (answer_sheet_owner_user_id),
    answer_sheet_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '答题卡ID',
    answer_sheet_exam_id BIGINT NULL COMMENT '考试ID（扫描阅卷未绑定考试时为空）',
    answer_sheet_student_id VARCHAR(64) NOT NULL COMMENT '学生ID',
    answer_sheet_content JSON COMMENT '答题内容（JSON）',
    answer_sheet_submit_time DATETIME COMMENT '提交时间',
    answer_sheet_status TINYINT DEFAULT 0 COMMENT '状态: 0-未提交 1-已提交 2-已批改',
    answer_sheet_create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    answer_sheet_update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY edu_answer_sheet_exam_student (answer_sheet_exam_id, answer_sheet_student_id),
    KEY edu_answer_sheet_exam_id (answer_sheet_exam_id),
    KEY edu_answer_sheet_student_id (answer_sheet_student_id),
      -- V1.8 的成语唯一约束已规范化为本脚本中的唯一索引，避免重复 CREATE/ALTER。,
      -- 兼容 ALTER：补齐 SysBase 审计列,
      -- 背景：eduai 下 17 个实体继承 com.chua.starter.mybatis.pojo.SysBase，,
      --       该基类提供无前缀的 6 个审计字段：create_name / create_by / create_time /,
      --       update_time / update_name / update_by。MyBatis-Plus 生成 SELECT/INSERT 时,
      --       必然带上这些列，因此对应表**必须**存在这 6 列，否则全新安装首启即失败：,
      --         Column "create_name" not found; SQL statement: SELECT ... FROM edu_collect_task,
      --       （EduCollectTaskServiceImpl#run 启动时做调度对账会立即查 edu_collect_task）,
      -- 说明：,
      --   1) 既有库历史上已被手工/早期脚本补过这些列，ADD COLUMN IF NOT EXISTS 对它们是空操作；,
      --      全新库建表后由本段补齐，两条路径统一。,
      --   2) 列类型与既有库实测一致（create_name/update_name VARCHAR(255)，create_by/update_by INT，,
      --      create_time/update_time DATETIME；create_time 带 CURRENT_TIMESTAMP 默认值）。,
      --   3) 不含 SysBase 的表（edu_subject / edu_grade / edu_class / edu_student / edu_teacher）不加。,
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    update_by INT DEFAULT NULL
);

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
    KEY edu_marking_result_status (marking_result_status),
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    update_by INT DEFAULT NULL
);

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
    KEY edu_wrong_question_mastery (wrong_question_mastery_level),
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    wrong_question_paper_id BIGINT COMMENT '试卷ID（错题来自哪张卷）',
    wrong_question_source_task_id VARCHAR(64) NULL COMMENT '来源阅卷任务标识（哪次完成登记的，用于撤销）',
    wrong_question_corrected TINYINT NOT NULL DEFAULT 0 COMMENT '订正状态：0 未订正 / 1 已订正',
    wrong_question_correct_time DATETIME NULL COMMENT '最近订正时间',
    update_by INT DEFAULT NULL
);

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
    KEY edu_knowledge_doc_status (knowledge_doc_status),
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    update_by INT DEFAULT NULL
);
-- ----------------------------
-- 知识卡片附件表
-- 说明：/v2/edu/knowledge-point/cards 的 file_count 子查询与附件绑定/列表功能
--       都依赖本表；此前只有实体 EduKnowledgeFile 与 Mapper，缺建表语句，
--       导致知识库页面 500（Table 'eduai.edu_knowledge_file' doesn't exist）。
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_knowledge_file (
    knowledge_file_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_knowledge_file_owner (knowledge_file_owner_user_id),
    knowledge_file_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '附件条目标识',
    knowledge_point_id BIGINT NOT NULL COMMENT '所属知识点（卡片）标识',
    knowledge_file_name VARCHAR(255) COMMENT '附件原始文件名',
    knowledge_file_path VARCHAR(500) COMMENT '附件存储相对路径',
    knowledge_file_type VARCHAR(20) COMMENT '附件扩展名: png/jpg/pdf/docx 等',
    knowledge_file_size BIGINT COMMENT '附件字节数',
    knowledge_file_sort_order INT DEFAULT 0 COMMENT '排序号',
    knowledge_file_status TINYINT DEFAULT 1 COMMENT '状态: 0-停用 1-启用',
    KEY edu_knowledge_file_point (knowledge_point_id),
    KEY edu_knowledge_file_status (knowledge_file_status),
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    update_by INT DEFAULT NULL
);
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
  `subject_icon` VARCHAR(64) NULL COMMENT '图标key',
  `subject_color` VARCHAR(32) NULL COMMENT '主题色hex',

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
);

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
);

-- ----------------------------
-- 班级表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_class (
    class_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_class_owner (class_owner_user_id),
    class_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '班级ID',
    class_code VARCHAR(64) NOT NULL COMMENT '班级编码',
    class_name VARCHAR(100) NOT NULL COMMENT '班级名（业务唯一，全库不允许重名）',
    class_grade_level VARCHAR(20) COMMENT '学段',
    class_grade VARCHAR(20) COMMENT '年级',
    class_teacher_id BIGINT COMMENT '班主任 userId',
    class_status TINYINT DEFAULT 1 COMMENT '状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY edu_class_class_code (class_code),
    UNIQUE KEY uk_edu_class_name (class_name),
    KEY edu_class_teacher (class_teacher_id)
);

-- ----------------------------
-- 学生表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_student (
    student_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_student_owner (student_owner_user_id),
    student_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '学生ID',
    student_user_id BIGINT COMMENT '关联系统 userId',
    student_account VARCHAR(64) COMMENT '绑定的登录账号（登录名为字符串，故与 student_user_id 分开存）',
    student_no VARCHAR(64) NOT NULL COMMENT '学号',
    student_name VARCHAR(100) COMMENT '姓名',
    student_class_id BIGINT COMMENT '班级ID',
    student_grade_level VARCHAR(20) COMMENT '学段',
    student_grade VARCHAR(20) COMMENT '年级',
    student_enroll_year INT COMMENT '入学年份',
    student_parent_phone VARCHAR(20) COMMENT '家长手机号（学生端可用它登录；非唯一，同号可带多个孩子）',
    student_avatar VARCHAR(500) COMMENT '学生头像（URL 或 Base64，与系统用户头像同口径）',
    student_id_card VARCHAR(32) COMMENT '身份证号（仅管理端维护，学生端不可见）',
    student_password VARCHAR(64) COMMENT '登录口令摘要 hmacSha256(明文,盐)，非明文',
    student_pwd_salt VARCHAR(32) COMMENT '口令盐（随机十六进制，与 student_password 一一对应）',
    student_pwd_must_change TINYINT DEFAULT 0 COMMENT '是否需引导改密: 0-否 1-是（管理员设/重置的初始口令置 1，学生自助改密后置 0）',
    student_status TINYINT DEFAULT 1 COMMENT '状态: 0-离校 1-在读',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY edu_student_student_no (student_no),
    UNIQUE KEY uk_edu_student_account (student_account),
    KEY edu_student_user (student_user_id),
    KEY edu_student_class (student_class_id),
    KEY edu_student_parent_phone (student_parent_phone)
);

-- ----------------------------
-- 教师表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_teacher (
    teacher_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_teacher_owner (teacher_owner_user_id),
    teacher_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '教师ID',
    teacher_user_id BIGINT COMMENT '关联系统 userId',
    teacher_account VARCHAR(64) COMMENT '绑定的登录账号（与 student_account 同理，登录名是字符串）',
    teacher_no VARCHAR(64) NOT NULL COMMENT '工号',
    teacher_name VARCHAR(100) COMMENT '姓名',
    teacher_subject_code VARCHAR(64) COMMENT '任教学科编码',
    teacher_status TINYINT DEFAULT 1 COMMENT '状态: 0-离职 1-在职',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    teacher_password VARCHAR(64) COMMENT '登录口令摘要 hmacSha256(明文,盐)，非明文',
    teacher_pwd_salt VARCHAR(32) COMMENT '口令盐（随机十六进制，与 teacher_password 一一对应）',
    UNIQUE KEY edu_teacher_teacher_no (teacher_no),
    UNIQUE KEY uk_edu_teacher_account (teacher_account),
    KEY edu_teacher_user (teacher_user_id),
    KEY edu_teacher_subject (teacher_subject_code)
);

-- ----------------------------
-- 趣味模块配置表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_fun_config (
    fun_config_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_fun_config_owner (fun_config_owner_user_id),
    config_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '配置ID',
    module_code VARCHAR(64) NOT NULL COMMENT '模块编码: INSECT/IDIOM_GAME 或学生端模块如 idiom-game',
    fun_config_grade_level VARCHAR(20) COMMENT '适用学段: PRIMARY/JUNIOR/SENIOR；NULL 表示各学段通用',
    module_name VARCHAR(100) COMMENT '模块名称',
    module_desc VARCHAR(500) COMMENT '模块描述',
    fun_config_subtitle VARCHAR(100) COMMENT '学生端卡片副标题（一句话说明）',
    module_icon VARCHAR(255) COMMENT '模块图标（emoji 或图片地址）',
    module_path VARCHAR(255) COMMENT '模块路径，如 /edu/fun/insect',
    fun_config_color VARCHAR(16) COMMENT '学生端卡片图标渐变起始色',
    fun_config_color_to VARCHAR(16) COMMENT '学生端卡片图标渐变结束色',
    fun_config_show_badge TINYINT DEFAULT 0 COMMENT '是否显示未完成角标: 0-否 1-是（小学端用）',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用: 0-禁用 1-启用',
    sort_order INT DEFAULT 0 COMMENT '排序',
    config_json TEXT COMMENT '扩展配置JSON',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY edu_fun_config_module_level (module_code, fun_config_grade_level),
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    update_by INT DEFAULT NULL
);

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
    KEY edu_idiom_difficulty (idiom_difficulty),
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    update_by INT DEFAULT NULL
);

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
    KEY edu_idiom_grade_level (grade_level),
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    update_by INT DEFAULT NULL
);

-- ----------------------------
-- 古诗表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_poem (
    poem_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_poem_owner (poem_owner_user_id),
    poem_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '古诗ID',
    poem_title VARCHAR(255) NOT NULL COMMENT '标题',
    poem_author VARCHAR(64) COMMENT '作者',
    poem_dynasty VARCHAR(32) COMMENT '朝代，如 唐代/宋代',
    poem_content TEXT NOT NULL COMMENT '正文（整首，句间用换行或标点分隔）',
    poem_translation TEXT COMMENT '白话译文',
    poem_appreciation TEXT COMMENT '赏析',
    poem_notes TEXT COMMENT '注释',
    poem_tags VARCHAR(500) COMMENT '标签，逗号分隔',
    poem_difficulty INT DEFAULT 1 COMMENT '难度: 1-5',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY edu_poem_title (poem_title),
    KEY edu_poem_author (poem_author),
    KEY edu_poem_difficulty (poem_difficulty),
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    update_by INT DEFAULT NULL,
      -- 古诗：同一「标题 + 作者」视为同一首（与采集入库处理器的唯一键一致），保证种子脚本幂等。
      -- 必须是全列、不得用 191 前缀：采集器按「全等匹配」查重（WHERE title=? AND author=?），
      -- 而前缀唯一索引会让共享前 191 字符的长标题变体（如《…（酒令）》组诗）既查不到、又插不进，
      -- 触发 DuplicateKeyException 令整批采集中止（2026-10-08 补采实案：6 变体×同一前缀）。
      -- 255 字符 × 4 字节 = 1020B，Dynamic 行格式上限 3072B，可全列索引。
    UNIQUE KEY uk_edu_poem_title_author (poem_title, poem_author)
);

-- ----------------------------
-- 古诗学段映射表
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_poem_grade (
    poem_grade_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_poem_grade_owner (poem_grade_owner_user_id),
    map_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '映射ID',
    poem_id BIGINT NOT NULL COMMENT '古诗ID',
    grade_level VARCHAR(20) COMMENT '学段: 小学/初中/高中',
    `grade` VARCHAR(20) COMMENT '年级',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY edu_poem_grade_poem (poem_id),
    KEY edu_poem_grade_level (grade_level),
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    update_by INT DEFAULT NULL,
      -- 古诗学段映射：采集入库处理器判定「已存在」用的就是 (poem_id, grade_level),
      -- （见 ImportProcessor#bindPoemGrade），这里补同款唯一索引，让 initdata 的映射行也能幂等。,
      -- 注意 grade 允许为 NULL（表示该学段通用），而 MySQL/MariaDB 中 NULL 互不相等，,
      -- 因此唯一键不能包含 grade，否则通用年级行重复插入拦不住。,
    UNIQUE KEY uk_edu_poem_grade_poem_level (poem_id, grade_level)
);

-- ----------------------------
-- 昆虫图鉴表
-- 数据来源：开源项目 insect-world（https://github.com/xr843/insect-world）中文数据包，
-- 共 63 个物种 / 14 个目；结构化字段拆列存放，facts/hotspots/lifecycle/relatives 以 JSON 文本保存。
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_insect (
    insect_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_insect_owner (insect_owner_user_id),
    insect_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '昆虫ID',
    insect_code VARCHAR(64) NOT NULL COMMENT '物种英文标识(slug)，如 rhinoceros-beetle',
    insect_name VARCHAR(64) NOT NULL COMMENT '中文名',
    insect_latin VARCHAR(128) COMMENT '学名',
    insect_epithet VARCHAR(255) COMMENT '一句话雅称',
    insect_order VARCHAR(32) NOT NULL COMMENT '所属目(slug)：coleoptera/lepidoptera 等',
    insect_summary TEXT COMMENT '简介',
    insect_facts MEDIUMTEXT COMMENT '形态特征(JSON 数组: [{key,value,icon}])',
    insect_hotspots MEDIUMTEXT COMMENT '3D 标注点(JSON 数组: [{id,label,note,anchor,tone}])',
    insect_ecology TEXT COMMENT '生态角色',
    insect_trivia TEXT COMMENT '冷知识',
    insect_metamorphosis VARCHAR(16) COMMENT '变态类型: complete-完全变态 incomplete-不完全变态',
    insect_lifecycle MEDIUMTEXT COMMENT '生活史阶段(JSON 数组)',
    insect_range VARCHAR(500) COMMENT '分布',
    insect_status VARCHAR(64) COMMENT '常见度 / 保护状态',
    insect_relatives MEDIUMTEXT COMMENT '近缘种(JSON 数组)',
    insect_accent VARCHAR(16) COMMENT '主题色(hex)，用于前端卡片配色',
    insect_model_code VARCHAR(64) COMMENT '复用的 3D 建模标识(slug)；为空时用 insect_code 自己的建模，指定后可复用已有物种的模型',
    insect_difficulty INT DEFAULT 1 COMMENT '难度: 1-5',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY edu_insect_code (insect_code),
    KEY edu_insect_order (insect_order),
    KEY edu_insect_metamorphosis (insect_metamorphosis),
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    update_by INT DEFAULT NULL
);

-- ----------------------------
-- 昆虫深度讲解与测验表
-- 数据来源：开源项目 insect-world 的 guides.zh.ts（63 个物种）。
-- 与 edu_insect 按 insect_code 关联（一对一）；结构化内容以 JSON 文本保存，
-- 由服务层解析后下发，前端按讲解步骤逐步驱动 3D 镜头。
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_insect_guide (
    guide_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_insect_guide_owner (guide_owner_user_id),
    guide_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '讲解ID',
    insect_code VARCHAR(64) NOT NULL COMMENT '物种英文标识(slug)，关联 edu_insect.insect_code',
    guide_lesson MEDIUMTEXT COMMENT '分步讲解(JSON 数组: [{title,body,anchor?}])',
    guide_motion_title VARCHAR(255) COMMENT '动态演示标题',
    guide_motion_body TEXT COMMENT '动态演示正文',
    guide_quiz MEDIUMTEXT COMMENT '单选题(JSON 数组: [{question,options,answer,explain}])',
    guide_habitat_title VARCHAR(255) COMMENT '栖境标题',
    guide_habitat_body TEXT COMMENT '栖境正文',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY edu_insect_guide_code (insect_code),
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    update_by INT DEFAULT NULL
);

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
    KEY edu_collect_task_source_type (collect_task_source_type),
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    update_by INT DEFAULT NULL
);

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
    KEY edu_collect_log_status (collect_log_status),
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    update_by INT DEFAULT NULL
);

-- ----------------------------
-- 初始数据 (种子)
-- ----------------------------

CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_idiom_owner_content ON edu_idiom (idiom_owner_user_id, idiom_content);

CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_insect_owner_code ON edu_insect (insect_owner_user_id, insect_code);

CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_insect_guide_owner_code ON edu_insect_guide (guide_owner_user_id, insect_code);

-- ----------------------------
-- 学生端：模块学习进度
-- 学生端首页每张功能卡片上的「已学 xx%」与进度条都读这里。
-- 一个学生一个模块一行，唯一键保证 upsert 语义。
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_student_progress (
    progress_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_student_progress_owner (progress_owner_user_id),
    progress_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '进度ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    module_code VARCHAR(64) NOT NULL COMMENT '模块编码，如 idiom-game / fun-math',
    progress INT DEFAULT 0 COMMENT '已学进度: 0-100',
    study_minutes INT DEFAULT 0 COMMENT '累计学习分钟',
    question_count INT DEFAULT 0 COMMENT '累计练习题数',
    correct_count INT DEFAULT 0 COMMENT '累计答对题数',
    last_study_time DATETIME COMMENT '最近学习时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_edu_student_module (student_id, module_code),
    KEY edu_student_progress_student (student_id)
);

-- ----------------------------
-- 学生端：每日学习汇总
-- 打卡连续天数、今日学习分钟、本周正确率、学习积分/星星都由这张表按日期聚合得出。
-- 一个学生一天一行。
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_student_daily (
    daily_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_student_daily_owner (daily_owner_user_id),
    daily_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日汇总ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    stat_date DATE NOT NULL COMMENT '统计日期',
    study_count INT DEFAULT 0 COMMENT '学习次数（每完成一次学习累加）',
    study_minutes INT DEFAULT 0 COMMENT '学习分钟',
    question_count INT DEFAULT 0 COMMENT '完成练习题数',
    correct_count INT DEFAULT 0 COMMENT '答对题数',
    stars INT DEFAULT 0 COMMENT '获得星星',
    points INT DEFAULT 0 COMMENT '获得学习积分',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_edu_student_stat_date (student_id, stat_date),
    KEY edu_student_daily_student (student_id),
    KEY edu_student_daily_date (stat_date)
);






















-- ----------------------------
-- 兼容 ALTER：为种子数据补唯一索引（保证 initdata 幂等）
-- ----------------------------
-- 背景：initdata 用 INSERT IGNORE 保证幂等，但 IGNORE 只在**存在唯一约束**时才跳过重复：
--   1) edu_idiom 原有唯一索引 uk_edu_idiom_owner_content 含可空的 idiom_owner_user_id，
--      而 MySQL/MariaDB 中 NULL 互不相等，该索引对系统内置数据（owner 为 NULL）完全失效；
--   2) edu_question 原本没有任何唯一约束。
-- 结果：在 MySQL/MariaDB 上重复执行 initdata 会不断插入重复的成语与题目
--      （实测：edu_question 从 317 涨到 329，多出 12 条重复）。
-- 这里按采集模块自身使用的去重键（idiom_content / question_stem）补唯一索引。
--
-- 说明：edu_question.question_stem 是 TEXT，MySQL/MariaDB 建唯一索引必须指定前缀长度，
--      故统一使用 question_stem(255) 前缀索引。
-- ----------------------------

CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_idiom_content ON edu_idiom (idiom_content);










-- ----------------------------
-- 组卷模板表
-- 说明：保存大标题风格、页眉页脚（富文本）与纸张规格/方向；
--      模板归属列为 template_owner_user_id，审计列沿用 SysBase 的无前缀六列。
-- ----------------------------
CREATE TABLE IF NOT EXISTS edu_paper_template (
    template_owner_user_id VARCHAR(64) COMMENT '归属用户ID',
    KEY edu_paper_template_owner (template_owner_user_id),
    template_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '模板ID',
    template_name VARCHAR(100) NOT NULL COMMENT '模板名称',
    template_style VARCHAR(40) DEFAULT 'classic' COMMENT '大标题风格: classic/modern/simple/exam',
    template_header TEXT COMMENT '页眉（富文本）',
    template_footer TEXT COMMENT '页脚（富文本）',
    template_page_size VARCHAR(30) DEFAULT 'A4' COMMENT '纸张规格',
    template_orientation VARCHAR(20) DEFAULT 'PORTRAIT' COMMENT '纸张方向',
    template_status TINYINT DEFAULT 1 COMMENT '状态 0-停用 1-启用',
    create_name VARCHAR(255) COMMENT '创建人姓名',
    create_by INT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    update_name VARCHAR(255) COMMENT '更新人姓名',
    update_by INT COMMENT '更新人',
    KEY edu_paper_template_status (template_status)
) COMMENT '组卷模板';

-- ----------------------------
-- 兼容既有库：学生登录口令列
-- ----------------------------
-- 说明：
--   1) 全新安装走上面的 CREATE TABLE，这一段是空操作；
--      既有库（本次加列之前建的）才会真正补列，因此两条路径统一。
--   2) 刻意不加 NOT NULL：老学生没有口令，列必须允许 NULL，
--      由「没有口令 → 首次登录要求设置」的业务规则兜底，而不是让迁移失败。
--   3) student_account 的历史数据回填属于 DML，按仓库规范放在 initdata，不在本文件。


-- ----------------------------
-- 班级试卷布置（老师给班级组卷 -> 班内学生可见）
-- ----------------------------
-- 说明：
--   1) 一张试卷布置给一个班级 = 一行；(paper_id, class_id) 唯一，重复布置走更新而不是插新行。
--   2) 老师端只允许布置到「自己的班级」（class_owner_user_id = 登录账号），
--      学生端按自己的 student_class_id 反查本班已布置且已发布的试卷。
--   3) teacher_account 存登录账号（与 edu_class.class_owner_user_id 同口径），
--      这样「我布置的」不需要再关联班级表即可直接过滤。
CREATE TABLE IF NOT EXISTS edu_paper_assign (
    paper_assign_owner_user_id VARCHAR(64) COMMENT '归属用户 ID（布置人账号）',
    KEY edu_paper_assign_owner (paper_assign_owner_user_id),
    assign_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '布置ID',
    paper_id BIGINT NOT NULL COMMENT '试卷ID',
    class_id BIGINT NOT NULL COMMENT '班级ID',
    teacher_account VARCHAR(64) COMMENT '布置人账号（老师登录名）',
    assign_status TINYINT DEFAULT 1 COMMENT '状态: 0-已撤回 1-已布置',
    assign_remark VARCHAR(200) COMMENT '备注（如截止时间/说明）',
    assign_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '布置时间',
    -- 审计列必须与 SysBase（create_time/update_time/create_name/create_by/update_name/update_by）
    -- 逐列对齐：实体走 MyBatis-Plus 的自动映射，少一列就是 500。
    -- 首次发布时这里漏了 create_time，实测「布置/撤回」全 500（Unknown column 'create_time'
    -- in 'SELECT'），而按列名手写的 XML 查询（/mine、/my-class）却正常 —— 正是这个差异暴露了它。
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    update_by INT DEFAULT NULL,
    UNIQUE KEY uk_edu_paper_assign (paper_id, class_id),
    KEY edu_paper_assign_class (class_id),
    KEY edu_paper_assign_paper (paper_id),
    KEY edu_paper_assign_teacher (teacher_account)
) COMMENT '班级试卷布置';

-- 兼容既有库：首次发布（2026-10-04 06:14）建出的 edu_paper_assign 缺 create_time。
-- 上面的 CREATE TABLE 只对全新库生效，既有库必须靠这条 ALTER 补齐，
-- 否则「布置/撤回」会一直 500（这正是本次验收抓到的失败）。


-- ---------------------------------------------------------------------
-- 原 V1.5__init_all_eduai.sql （已并入本文件）
-- ---------------------------------------------------------------------
-- ===========================================
-- 教育 AI 模块增量结构变更（V1.5）
-- ===========================================
-- 变更内容：新增「阅卷任务表」edu_marking_task。
--
-- 为什么需要（对应「阅卷是异步任务：创建任务 → 有状态 → 后台慢慢处理 → 重启继续处理」）：
--   原实现把扫描阅卷放在请求线程里做（同步逐题调大模型），有两个真实问题：
--     1) 请求耗时数十秒，超过前端 HTTP 客户端 10 秒超时 → 前端 abort，
--        表现为「点了没反应」（实测：+10s 提示 timeout of 10000ms exceeded）；
--     2) 线程内执行，进程重启即丢，任务永久停在中间态。
--   因此任务必须先落库（状态 + 输入留痕），接口立即返回 taskId，由后台处理并可续跑。
--
-- 状态语义：
--   0=PENDING（待处理）  1=PROCESSING（处理中）  2=DONE（已完成）  3=FAILED（失败）
--   重启后把 0/1 一并重新入队，因此 1 是「可能被中断」的状态，必须可重入。
--
-- 为什么把图片落盘而不是存库：
--   图片是任务输入，续跑时必须还能拿到；base64 存 JSON 列会让行体积膨胀数十倍，
--   且 MySQL JSON 列对超大值不友好。故存文件路径，文件放在可配置目录下。
--
-- 为什么独立版本：V1.2 已执行过（checksum 固化），改它不会对既有库生效。
-- 幂等：CREATE TABLE IF NOT EXISTS；SysBase 审计列按 V1.2 的既有约定一并建出。
-- ===========================================

CREATE TABLE IF NOT EXISTS edu_marking_task (
    marking_task_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_marking_task_owner (marking_task_owner_user_id),
    marking_task_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务自增ID',
    marking_task_task_id VARCHAR(64) NOT NULL COMMENT '任务业务标识（对外返回，结果按它查询）',
    marking_task_status TINYINT DEFAULT 0 COMMENT '状态: 0-待处理 1-处理中 2-已完成 3-失败',
    marking_task_mode VARCHAR(16) COMMENT '试卷类型: SELF 自有卷 / ANY 任意卷',
    marking_task_paper_id BIGINT COMMENT '自负卷对应的试卷ID',
    marking_task_exam_id BIGINT COMMENT '考试ID（用于取卷面分值）',
    marking_task_student_id VARCHAR(64) COMMENT '学生ID（留空时由服务端兜底）',
    marking_task_max_score INT COMMENT '任意卷每题满分',
    marking_task_image_path VARCHAR(512) COMMENT '上传图片的落盘路径（续跑时读取）',
    marking_task_answer_sheet_id BIGINT COMMENT '生成的答题卡ID',
    marking_task_stage VARCHAR(64) COMMENT '当前阶段（识别中 / 判分中 2/3 等，供前端展示进度）',
    marking_task_total INT DEFAULT 0 COMMENT '识别出的题目数',
    marking_task_done_count INT DEFAULT 0 COMMENT '已判分题目数',
    marking_task_error VARCHAR(1000) COMMENT '失败原因',
    marking_task_start_time DATETIME COMMENT '开始处理时间',
    marking_task_finish_time DATETIME COMMENT '处理完成时间',
    create_name VARCHAR(255) DEFAULT NULL,
    create_by INT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT NULL,
    update_name VARCHAR(255) DEFAULT NULL,
    update_by INT DEFAULT NULL,
    marking_task_teacher_finished TINYINT DEFAULT 0 COMMENT '老师是否已点「阅卷完成」: 0-未完成 1-已完成',
    marking_task_teacher_finish_time DATETIME COMMENT '老师点「阅卷完成」的时间',
    marking_task_satisfaction INT COMMENT 'AI评分满意度',
    UNIQUE KEY uk_edu_marking_task_task_id (marking_task_task_id),
    KEY edu_marking_task_status (marking_task_status)
) COMMENT '阅卷任务（异步：创建任务→后台处理→可重启续跑）';

-- ---------------------------------------------------------------------
-- 原 V1.9__init_all_eduai.sql （已并入本文件）
-- ---------------------------------------------------------------------
-- ===========================================
-- 汉字基础数据表（字 / 读音 / 笔画数 / 部首 / 结构 / 笔顺 / 释义 / 字画 / 适用学段）
--
-- 用于「基础数据维护」：老师维护每个汉字的读音（拼音）、笔顺（顺序）、字画（图片或字形图）
-- 与释义，供识字/写字类模块消费。
-- ===========================================

CREATE TABLE IF NOT EXISTS edu_hanzi (
    hanzi_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_hanzi_owner (hanzi_owner_user_id),
    hanzi_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '汉字ID',
    hanzi_char VARCHAR(8) NOT NULL COMMENT '汉字（单个字）',
    hanzi_pinyin VARCHAR(64) COMMENT '读音（拼音，可带声调）',
    hanzi_strokes INT COMMENT '笔画数',
    hanzi_radical VARCHAR(8) COMMENT '部首',
    hanzi_structure VARCHAR(16) COMMENT '结构：独体/上下/左右/半包围/全包围',
    hanzi_stroke_order VARCHAR(255) COMMENT '笔顺（按书写顺序排列的笔画名，如 一丨丿丶）',
    hanzi_meaning TEXT COMMENT '释义 / 常用组词',
    hanzi_image VARCHAR(255) COMMENT '字画（图片路径或字形图，可空）',
    hanzi_grade_level VARCHAR(20) COMMENT '适用学段（中文名）：小学/初中/高中；NULL 表示不限',
    hanzi_status TINYINT DEFAULT 1 COMMENT '状态：0 停用 / 1 启用',
    create_name VARCHAR(255) DEFAULT NULL COMMENT '创建人姓名',
    create_by INT DEFAULT NULL COMMENT '创建人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT NULL COMMENT '更新时间',
    update_name VARCHAR(255) DEFAULT NULL COMMENT '更新人姓名',
    update_by INT DEFAULT NULL COMMENT '更新人ID',
    UNIQUE KEY uk_edu_hanzi_char (hanzi_char),
    KEY edu_hanzi_level (hanzi_grade_level)
);

-- ---------------------------------------------------------------------
-- 原 V1.14__eduai_teacher_class_and_config.sql （已并入本文件）
-- ---------------------------------------------------------------------
-- V1.14 老师端数据范围收敛：教师班级配置 + 应用配置表
--
-- 背景：老师端定位为「布置等」教学功能端，数据范围 = 后台配置的班级；
--       老师上传题目需管理员审批（管理员可配「自动通过」）。
-- 此前老师自建班级（edu_class.class_owner_user_id = 教师账号），
-- 本版起班级由管理员统一维护，老师的班级列表完全来自 edu_teacher_class。

CREATE TABLE IF NOT EXISTS edu_teacher_class (
    teacher_class_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关联ID',
    teacher_class_teacher_id BIGINT NOT NULL COMMENT '教师ID（edu_teacher.teacher_id）',
    teacher_class_class_id BIGINT NOT NULL COMMENT '班级ID（edu_class.class_id）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '配置时间',
    UNIQUE KEY uk_edu_teacher_class (teacher_class_teacher_id, teacher_class_class_id),
    KEY idx_edu_teacher_class_class (teacher_class_class_id)
) COMMENT='教师班级配置（老师端数据范围）';

-- 迁移：教师自建班级自动转为配置关系，保证现有老师端数据不丢；
-- 管理员后续可在「学生管理 → 教师 → 配置班级」增删。
INSERT IGNORE INTO edu_teacher_class (teacher_class_teacher_id, teacher_class_class_id)
SELECT t.teacher_id, c.class_id
FROM edu_class c
JOIN edu_teacher t ON t.teacher_account = c.class_owner_user_id
WHERE c.class_owner_user_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS edu_app_config (
    config_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '配置ID',
    config_key VARCHAR(100) NOT NULL COMMENT '配置键（如 teacher-question-auto-approve）',
    config_value VARCHAR(500) COMMENT '配置值',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_edu_app_config_key (config_key)
) COMMENT='应用配置（老师上传自动通过等）';

-- ---------------------------------------------------------------------
-- 原 V1.16__eduai_marking_sheet_parse.sql （已并入本文件）
-- ---------------------------------------------------------------------
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
-- =====================================================================
-- 存量库补列（老库已有旧表形时补列；新库上列已存在，下面的语句会跳过）
--
-- 为何不用 ADD COLUMN IF NOT EXISTS：MariaDB 11.8 实测不接受该语法（语句报错、
-- 列不会建成），MySQL 虽支持但会告警。用「查列 → 不存在才 ALTER」两步走，
-- 在 MariaDB 与 MySQL 上都成立。
--
-- 原来这些补列分散在 V1.3 / V1.4 / V1.6 / V1.13 / V1.17 / V1.18 / V1.21 / V1.22
-- 八个脚本里，本次收敛全部并入本文件末尾。
-- =====================================================================

-- ---------- edu_student：登录口令三件套 + 头像 + 身份证 ----------
SET @has_stu_pwd := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_student' AND COLUMN_NAME = 'student_password'
);
SET @sql_stu_pwd := IF(@has_stu_pwd > 0, 'SELECT 1',
    'ALTER TABLE edu_student ADD COLUMN student_password VARCHAR(64) DEFAULT NULL COMMENT ''登录口令摘要 hmacSha256(明文,盐)，非明文''');
PREPARE stmt_stu_pwd FROM @sql_stu_pwd;
EXECUTE stmt_stu_pwd;
DEALLOCATE PREPARE stmt_stu_pwd;

SET @has_stu_salt := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_student' AND COLUMN_NAME = 'student_pwd_salt'
);
SET @sql_stu_salt := IF(@has_stu_salt > 0, 'SELECT 1',
    'ALTER TABLE edu_student ADD COLUMN student_pwd_salt VARCHAR(32) DEFAULT NULL COMMENT ''口令盐（随机十六进制，与 student_password 一一对应）''');
PREPARE stmt_stu_salt FROM @sql_stu_salt;
EXECUTE stmt_stu_salt;
DEALLOCATE PREPARE stmt_stu_salt;

SET @has_stu_must := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_student' AND COLUMN_NAME = 'student_pwd_must_change'
);
SET @sql_stu_must := IF(@has_stu_must > 0, 'SELECT 1',
    'ALTER TABLE edu_student ADD COLUMN student_pwd_must_change TINYINT DEFAULT 0 COMMENT ''是否需引导改密: 0-否 1-是''');
PREPARE stmt_stu_must FROM @sql_stu_must;
EXECUTE stmt_stu_must;
DEALLOCATE PREPARE stmt_stu_must;

SET @has_stu_avatar := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_student' AND COLUMN_NAME = 'student_avatar'
);
SET @sql_stu_avatar := IF(@has_stu_avatar > 0, 'SELECT 1',
    'ALTER TABLE edu_student ADD COLUMN student_avatar VARCHAR(500) COMMENT ''学生头像（URL 或 Base64，与系统用户头像同口径）'' AFTER student_parent_phone');
PREPARE stmt_stu_avatar FROM @sql_stu_avatar;
EXECUTE stmt_stu_avatar;
DEALLOCATE PREPARE stmt_stu_avatar;

SET @has_stu_idcard := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_student' AND COLUMN_NAME = 'student_id_card'
);
SET @sql_stu_idcard := IF(@has_stu_idcard > 0, 'SELECT 1',
    'ALTER TABLE edu_student ADD COLUMN student_id_card VARCHAR(32) COMMENT ''身份证号（仅管理端维护，学生端不可见）'' AFTER student_avatar');
PREPARE stmt_stu_idcard FROM @sql_stu_idcard;
EXECUTE stmt_stu_idcard;
DEALLOCATE PREPARE stmt_stu_idcard;

-- ---------- edu_teacher：登录口令两件套 ----------
SET @has_tea_pwd := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_teacher' AND COLUMN_NAME = 'teacher_password'
);
SET @sql_tea_pwd := IF(@has_tea_pwd > 0, 'SELECT 1',
    'ALTER TABLE edu_teacher ADD COLUMN teacher_password VARCHAR(64) COMMENT ''登录口令摘要 hmacSha256(明文,盐)，非明文''');
PREPARE stmt_tea_pwd FROM @sql_tea_pwd;
EXECUTE stmt_tea_pwd;
DEALLOCATE PREPARE stmt_tea_pwd;

SET @has_tea_salt := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_teacher' AND COLUMN_NAME = 'teacher_pwd_salt'
);
SET @sql_tea_salt := IF(@has_tea_salt > 0, 'SELECT 1',
    'ALTER TABLE edu_teacher ADD COLUMN teacher_pwd_salt VARCHAR(32) COMMENT ''口令盐（随机十六进制，与 teacher_password 一一对应）''');
PREPARE stmt_tea_salt FROM @sql_tea_salt;
EXECUTE stmt_tea_salt;
DEALLOCATE PREPARE stmt_tea_salt;

-- ---------- edu_answer_sheet：考试ID 改可空（扫描阅卷未绑定考试时为空） ----------
SET @has_sheet_exam := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_answer_sheet' AND COLUMN_NAME = 'answer_sheet_exam_id'
      AND IS_NULLABLE = 'NO'
);
SET @sql_sheet_exam := IF(@has_sheet_exam > 0,
    'ALTER TABLE edu_answer_sheet MODIFY COLUMN answer_sheet_exam_id BIGINT NULL COMMENT ''考试ID（扫描阅卷未绑定考试时为空）''',
    'SELECT 1');
PREPARE stmt_sheet_exam FROM @sql_sheet_exam;
EXECUTE stmt_sheet_exam;
DEALLOCATE PREPARE stmt_sheet_exam;

-- ---------- edu_marking_task：图片路径加长 + 老师阅卷完成标记 ----------
SET @has_mark_img := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_marking_task' AND COLUMN_NAME = 'marking_task_image_path'
      AND CHARACTER_MAXIMUM_LENGTH < 2000
);
SET @sql_mark_img := IF(@has_mark_img > 0,
    'ALTER TABLE edu_marking_task MODIFY COLUMN marking_task_image_path VARCHAR(2000) COMMENT ''上传图片的落盘路径（多页以逗号按页序连接，续跑时读取）''',
    'SELECT 1');
PREPARE stmt_mark_img FROM @sql_mark_img;
EXECUTE stmt_mark_img;
DEALLOCATE PREPARE stmt_mark_img;

SET @has_mark_fin := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_marking_task' AND COLUMN_NAME = 'marking_task_teacher_finished'
);
SET @sql_mark_fin := IF(@has_mark_fin > 0, 'SELECT 1',
    'ALTER TABLE edu_marking_task ADD COLUMN marking_task_teacher_finished TINYINT DEFAULT 0 COMMENT ''老师是否已点「阅卷完成」: 0-未完成 1-已完成''');
PREPARE stmt_mark_fin FROM @sql_mark_fin;
EXECUTE stmt_mark_fin;
DEALLOCATE PREPARE stmt_mark_fin;

SET @has_mark_fin_time := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_marking_task' AND COLUMN_NAME = 'marking_task_teacher_finish_time'
);
SET @sql_mark_fin_time := IF(@has_mark_fin_time > 0, 'SELECT 1',
    'ALTER TABLE edu_marking_task ADD COLUMN marking_task_teacher_finish_time DATETIME COMMENT ''老师点「阅卷完成」的时间''');
PREPARE stmt_mark_fin_time FROM @sql_mark_fin_time;
EXECUTE stmt_mark_fin_time;
DEALLOCATE PREPARE stmt_mark_fin_time;

-- ---------- edu_marking_task：完成度 AI 评分（实体 + Mapper 均已引用） ----------
SET @has_mark_satisfaction := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_marking_task' AND COLUMN_NAME = 'marking_task_satisfaction'
);
SET @sql_mark_satisfaction := IF(@has_mark_satisfaction > 0, 'SELECT 1',
    'ALTER TABLE edu_marking_task ADD COLUMN marking_task_satisfaction INT COMMENT ''AI评分满意度''');
PREPARE stmt_mark_satisfaction FROM @sql_mark_satisfaction;
EXECUTE stmt_mark_satisfaction;
DEALLOCATE PREPARE stmt_mark_satisfaction;

-- ---------- edu_wrong_question：试卷ID / 来源任务 / 订正状态 ----------
SET @has_wq_paper := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_wrong_question' AND COLUMN_NAME = 'wrong_question_paper_id'
);
SET @sql_wq_paper := IF(@has_wq_paper > 0, 'SELECT 1',
    'ALTER TABLE edu_wrong_question ADD COLUMN wrong_question_paper_id BIGINT COMMENT ''试卷ID（错题来自哪张卷）''');
PREPARE stmt_wq_paper FROM @sql_wq_paper;
EXECUTE stmt_wq_paper;
DEALLOCATE PREPARE stmt_wq_paper;

SET @has_wq_task := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_wrong_question' AND COLUMN_NAME = 'wrong_question_source_task_id'
);
SET @sql_wq_task := IF(@has_wq_task > 0, 'SELECT 1',
    'ALTER TABLE edu_wrong_question ADD COLUMN wrong_question_source_task_id VARCHAR(64) NULL COMMENT ''来源阅卷任务标识（哪次完成登记的，用于撤销）'' AFTER wrong_question_paper_id');
PREPARE stmt_wq_task FROM @sql_wq_task;
EXECUTE stmt_wq_task;
DEALLOCATE PREPARE stmt_wq_task;

SET @has_wq_corr := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_wrong_question' AND COLUMN_NAME = 'wrong_question_corrected'
);
SET @sql_wq_corr := IF(@has_wq_corr > 0, 'SELECT 1',
    'ALTER TABLE edu_wrong_question ADD COLUMN wrong_question_corrected TINYINT NOT NULL DEFAULT 0 COMMENT ''订正状态：0 未订正 / 1 已订正'' AFTER wrong_question_source_task_id');
PREPARE stmt_wq_corr FROM @sql_wq_corr;
EXECUTE stmt_wq_corr;
DEALLOCATE PREPARE stmt_wq_corr;

SET @has_wq_corr_time := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_wrong_question' AND COLUMN_NAME = 'wrong_question_correct_time'
);
SET @sql_wq_corr_time := IF(@has_wq_corr_time > 0, 'SELECT 1',
    'ALTER TABLE edu_wrong_question ADD COLUMN wrong_question_correct_time DATETIME NULL COMMENT ''最近订正时间'' AFTER wrong_question_corrected');
PREPARE stmt_wq_corr_time FROM @sql_wq_corr_time;
EXECUTE stmt_wq_corr_time;
DEALLOCATE PREPARE stmt_wq_corr_time;

-- ---------- edu_question：审核驳回理由与累计次数 ----------
SET @has_q_audit_remark := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_question' AND COLUMN_NAME = 'question_audit_remark'
);
SET @sql_q_audit_remark := IF(@has_q_audit_remark > 0, 'SELECT 1',
    'ALTER TABLE edu_question ADD COLUMN question_audit_remark VARCHAR(500) COMMENT ''驳回理由（驳回必填，通过时清空）'' AFTER question_audit_status');
PREPARE stmt_q_audit_remark FROM @sql_q_audit_remark;
EXECUTE stmt_q_audit_remark;
DEALLOCATE PREPARE stmt_q_audit_remark;

SET @has_q_audit_count := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_question' AND COLUMN_NAME = 'question_audit_count'
);
SET @sql_q_audit_count := IF(@has_q_audit_count > 0, 'SELECT 1',
    'ALTER TABLE edu_question ADD COLUMN question_audit_count INT DEFAULT 0 COMMENT ''累计审核次数（每次通过/驳回+1）'' AFTER question_audit_remark');
PREPARE stmt_q_audit_count FROM @sql_q_audit_count;
EXECUTE stmt_q_audit_count;
DEALLOCATE PREPARE stmt_q_audit_count;

-- ---------- edu_paper_assign：创建时间 ----------
SET @has_pa_create := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_paper_assign' AND COLUMN_NAME = 'create_time'
);
SET @sql_pa_create := IF(@has_pa_create > 0, 'SELECT 1',
    'ALTER TABLE edu_paper_assign ADD COLUMN create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''');
PREPARE stmt_pa_create FROM @sql_pa_create;
EXECUTE stmt_pa_create;
DEALLOCATE PREPARE stmt_pa_create;

-- ---------- edu_hanzi：释义加宽为 TEXT ----------
-- 报错：写入 edu_hanzi（跳过已存在）失败 → Data too long for column 'hanzi_meaning'。
-- 原因：hanzi_meaning 原为 VARCHAR(500)，采集/录入的释义 + 组词可能超过 500 字符，
-- MySQL 严格模式直接拒绝写入；把列加宽为 TEXT 后不再受 500 字符上限约束。
SET @has_hanzi_meaning_text := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_hanzi' AND COLUMN_NAME = 'hanzi_meaning'
      AND LOWER(DATA_TYPE) <> 'text'
);
SET @sql_hanzi_meaning_text := IF(@has_hanzi_meaning_text > 0,
    'ALTER TABLE edu_hanzi MODIFY COLUMN hanzi_meaning TEXT NULL COMMENT ''释义 / 常用组词''',
    'SELECT 1');
PREPARE stmt_hanzi_meaning_text FROM @sql_hanzi_meaning_text;
EXECUTE stmt_hanzi_meaning_text;
DEALLOCATE PREPARE stmt_hanzi_meaning_text;

-- =====================================================================
-- V2.3__init_edu_collect_flows.sql - 教育语料采集流程扩充
-- 通道: NORMAL（V版本号__任意.sql，同步执行，按「文件名 + 版本号」记账只跑一次）
--
-- ⚠️ 跨库事实（与 V2.1 末尾同一口径，务必先读）：
--   本文件里 6 条 INSERT 操作的是 `collect_flow` —— 采集平台的表。
--   生产部署时 eduai 与采集平台**不共用数据库**（eduai -> eduai 库，采集平台 -> collect 库），
--   框架执行前会查元数据判断目标表是否存在，表不存在则**跳过该语句**
--   （不记失败、不刷错误日志），脚本仍标记为执行成功。
--   本地开发环境是单库（采集 starter 与 eduai 同进程、同库），collect_flow 存在，
--   所以这 6 条流程会在本地库里真正落库、在「采集管理」页可见。
--   若部署到分库环境，把这 6 条拿到 collect 库执行一次即可。
--   与 V2.1 里成语/古诗/汉字三条流程的处置方式完全一致。
--
-- 为什么这批流程要做：
--   采集管理里原来只有「成语 / 古诗 / 汉字」三条教育语料流程，
--   而本轮新增的「词语库」页没有对应采集入口，题库也只有手工录入。
--   本文件补齐：
--     1. 词语采集·汉语词典（ci.json，26MB，约 26 万条）→ edu_word
--     2. 歇后语采集·chinese-xinhua（xiehouyu.json，约 1.4 万条）→ edu_word(kind=XIEHOUYU)
--     3. 题库采集·成语典故（idiom.json，约 3 万条）→ edu_question
--     4. 题库采集·唐诗名句（poet.tang.0.json）→ edu_question
--     5. 题库采集·宋诗名句（poet.song.0.json）→ edu_question
--     6. 成语库补全·近义候选（读库分组回写 edu_idiom.idiom_synonyms）
--
-- 幂等：每条 INSERT 都带 WHERE NOT EXISTS（按 collect_flow_code 判重），
--   collect_flow.collect_flow_code 上有唯一索引，重复执行不会产生第二条。
--   6 条流程一律 collect_flow_status=0（停用）、cron 留空：
--   装完不会自动去抓外网，由用户按需点「立即执行」或设置 cron。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 一、词语采集·汉语词典
--
-- 数据源：chinese-xinhua 的 ci.json（根节点是 JSON 数组，约 26 万条）。
-- 节点链：开始 → 抓取 → 抽取 → 查已存在 → 过滤 → 写入 edu_word → 结束
--
-- 几处必须说清的口径：
--   1) 只用 raw.githubusercontent，不用 jsDelivr：该文件 26MB，
--      jsDelivr 对 >20MB 直接返回 403「File size exceeded the configured limit of 20 MB」。
--   2) 行选择器留空。CollectJson.path 只支持 JSONPath 的子集，**不支持通配 [*]**，
--      根节点是数组时留空即按数组元素逐行拆分（V2.1 里成语流程同一处理）。
--   3) 解析器写 JSONPATH 而不是 JSON：DefaultScriptConverter 对 h2 方言做整篇 SQL 的
--      replaceAll("(?i)\bJSON\b","CLOB")，解析器类型被改写成 CLOB 会让
--      CollectExtractExecutor 直接报错。JSONPATH 匹配不到 \bJSON\b，是安全的。
--   4) 「查已存在 + 过滤」是必需项：output 节点写业务表走纯 INSERT
--      （CollectJdbcSupport.insert，不是 upsert），而 edu_word 上有唯一键
--      uk_edu_word_content，没有这一步第二次执行必然 DuplicateKeyException 整批中止。
--   5) ci.json 不带拼音，故本流程先只写词与释义；拼音由页面手工补齐
--      （没有拼音的词照样能按整词与释义检索）。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '词语采集·汉语词典',
    0,
    'edu_word_ci_xinhua',
    '{"name":"词语采集·汉语词典","threadCount":4,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/ci.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取汉语词典","x":260,"y":160,"params":[{"name":"url","value":"${url[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"600000"},{"name":"retry","value":"3"},{"name":"retryInterval","value":"3000"},{"name":"outputVariable","value":"respCi"}],"fields":[]},{"id":"ext","shape":"extract","name":"抽取词语与释义","x":480,"y":160,"params":[{"name":"source","value":"${respCiJson}"},{"name":"mode","value":"list"},{"name":"outputVariable","value":"rows"}],"fields":[{"name":"ci","parserType":"JSONPATH","expression":"ci"},{"name":"explanation","parserType":"JSONPATH","expression":"explanation"}]},{"id":"sql","shape":"sql","name":"查已存在词语","x":700,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT word_content AS k FROM edu_word"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr","shape":"script","name":"过滤已存在","x":920,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; rows.unique{ it.ci }.findAll{ it.ci && !seen.contains(it.ci) }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_word","x":1140,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${ci}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_word"},{"name":"cell:word_content","value":"${ci}"},{"name":"cell:word_meaning","value":"${explanation}"},{"name":"cell:word_kind","value":"WORD"},{"name":"cell:word_grade_level","value":"小学"},{"name":"cell:word_status","value":"1"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1360,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"ext"},{"id":"e3","from":"ext","to":"sql"},{"id":"e4","from":"sql","to":"scr"},{"id":"e5","from":"scr","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    4,
    '词语采集：公开数据集 chinese-xinhua 的 ci.json（约 26 万条词语），按 word_content 去重后写入 edu_word。源里没有拼音与近义/反义/同音词，这几列留空由页面维护；词与释义采集完即可按整词、释义检索。该文件 26MB，只能走 raw.githubusercontent（jsDelivr 对 >20MB 直接 403），一次执行约数分钟。默认停用：请在采集平台设置 cron 后启用，或点「立即执行」取数。注意「试运行」不落库，去重判断不生效。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_word_ci_xinhua'
);

-- ---------------------------------------------------------------------
-- 二、歇后语采集·chinese-xinhua
--
-- 数据源：xiehouyu.json（根节点 JSON 数组，约 1.4 万条），结构是
--   { "riddle": "做砖的坯子、插刀的鞘子", "answer": "框框套套" }
-- 落库口径：整条歇后语拼成「引子——后语」存 word_content（唯一键就是它），
--   word_kind = XIEHOUYU 让词语库页面能按类型过滤；
--   后语单独存进 word_meaning（释义位），引子存 word_structure（结构位）。
--   这样既不改表结构，也不丢原始两段信息。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '歇后语采集·chinese-xinhua',
    0,
    'edu_xiehouyu_xinhua',
    '{"name":"歇后语采集·chinese-xinhua","threadCount":4,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/xiehouyu.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取歇后语数据集","x":260,"y":160,"params":[{"name":"url","value":"${url[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"120000"},{"name":"outputVariable","value":"respXhy"}],"fields":[]},{"id":"ext","shape":"extract","name":"抽取引子与后语","x":480,"y":160,"params":[{"name":"source","value":"${respXhyJson}"},{"name":"mode","value":"list"},{"name":"outputVariable","value":"rows"}],"fields":[{"name":"riddle","parserType":"JSONPATH","expression":"riddle"},{"name":"answer","parserType":"JSONPATH","expression":"answer"}]},{"id":"sql","shape":"sql","name":"查已存在歇后语","x":700,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT word_content AS k FROM edu_word WHERE word_kind = ''XIEHOUYU''"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr","shape":"script","name":"拼整句并过滤","x":920,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; def rows2=rows.findAll{ it.riddle && it.answer }; rows2.collect{ [content: it.riddle + ''——'' + it.answer, riddle: it.riddle, answer: it.answer] }.unique{ it.content }.findAll{ !seen.contains(it.content) }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_word（歇后语）","x":1140,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${content}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_word"},{"name":"cell:word_content","value":"${content}"},{"name":"cell:word_meaning","value":"${answer}"},{"name":"cell:word_structure","value":"${riddle}"},{"name":"cell:word_kind","value":"XIEHOUYU"},{"name":"cell:word_grade_level","value":"小学"},{"name":"cell:word_status","value":"1"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1360,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"ext"},{"id":"e3","from":"ext","to":"sql"},{"id":"e4","from":"sql","to":"scr"},{"id":"e5","from":"scr","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    4,
    '歇后语采集：公开数据集 chinese-xinhua 的 xiehouyu.json（约 1.4 万条），把「引子——后语」拼成整句写入 edu_word，word_kind=XIEHOUYU；引子存结构位、后语存释义位，原始两段信息不丢。按整句去重，可反复执行续采。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_xiehouyu_xinhua'
);

-- ---------------------------------------------------------------------
-- 三、题库采集·成语典故
--
-- 数据源：idiom.json（3.6MB，约 3 万条），结构含 word / pinyin / explanation /
--   derivation（出处）/ example（例句）。
-- 落库到 edu_question：每条形成为一道「给释义猜成语」的填空题。
--
-- 为什么用「成语」而不是直接抓某题库网站：
--   公开题库站点多数有反爬与版权限制，抓下来也不保证题目结构完整；
--   成语典故是**结构性完整**的公开语料（释义/出处/例句齐全），
--   转成题后可直接用于组卷与练习，且与既有成语库同一数据源、口径一致。
--
-- 题干与答案：
--   question_stem     = 「下列释义所指的成语是什么？<释义>（出处：<出处>）」
--   question_answer   = 成语本体
--   question_analysis = 例句（有则填）
--   题型 FILL、学科语文、学段小学。
--   注意 question_stem 有唯一键 uk_edu_question_stem，故必须先去重（同题干只留一条）。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '题库采集·成语典故',
    0,
    'edu_question_idiom',
    '{"name":"题库采集·成语典故","threadCount":4,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/idiom.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取成语数据集","x":260,"y":160,"params":[{"name":"url","value":"${url[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"300000"},{"name":"outputVariable","value":"respIdiom"}],"fields":[]},{"id":"ext","shape":"extract","name":"抽取成语字段","x":480,"y":160,"params":[{"name":"source","value":"${respIdiomJson}"},{"name":"mode","value":"list"},{"name":"outputVariable","value":"rows"}],"fields":[{"name":"word","parserType":"JSONPATH","expression":"word"},{"name":"explanation","parserType":"JSONPATH","expression":"explanation"},{"name":"derivation","parserType":"JSONPATH","expression":"derivation"},{"name":"example","parserType":"JSONPATH","expression":"example"}]},{"id":"sql","shape":"sql","name":"查已存在题干","x":700,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT question_stem AS k FROM edu_question WHERE question_source_type = ''CRAWL''"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr","shape":"script","name":"成语转题并过滤","x":920,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; def rows2=rows.findAll{ it.word && it.explanation }; rows2.collect{ def stem=''下列释义所指的成语是什么？'' + it.explanation.toString().take(600) + (it.derivation ? ''（出处：'' + it.derivation.toString().take(200) + ''）'' : ''''); [stem: stem, word: it.word, explanation: it.explanation, example: it.example] }.unique{ it.stem }.findAll{ !seen.contains(it.stem) }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_question","x":1140,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${stem}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_question"},{"name":"cell:question_type","value":"FILL"},{"name":"cell:question_subject","value":"语文"},{"name":"cell:question_grade_level","value":"小学"},{"name":"cell:question_grade","value":"一年级"},{"name":"cell:question_difficulty","value":"3"},{"name":"cell:question_score","value":"5"},{"name":"cell:question_stem","value":"${stem}"},{"name":"cell:question_answer","value":"${word}"},{"name":"cell:question_analysis","value":"${example}"},{"name":"cell:question_source","value":"chinese-xinhua/idiom.json"},{"name":"cell:question_source_type","value":"CRAWL"},{"name":"cell:question_audit_status","value":"0"},{"name":"cell:question_status","value":"1"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1360,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"ext"},{"id":"e3","from":"ext","to":"sql"},{"id":"e4","from":"sql","to":"scr"},{"id":"e5","from":"scr","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    4,
    '题库采集：公开数据集 chinese-xinhua 的 idiom.json（约 3 万条成语典故），逐条转成「给释义猜成语」的填空题写入 edu_question（题型 FILL、学科语文、来源类型 CRAWL、审核状态待审核）。题干带出处、解析带例句，老师可在题库管理里逐条审核通过后直接用于组卷。按题干去重（edu_question 的题干有唯一键），可反复执行续采。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_question_idiom'
);

-- ---------------------------------------------------------------------
-- 四、题库采集·唐诗名句
--
-- 数据源：chinese-poetry-zhCN 的 poet.tang.0.json（分片，约 1000 首）。
-- 每首转成一道「补全下句」的填空题：
--   question_stem     = 「补全下句：<第一句> ____（<标题> · <作者>）」
--   question_answer   = 第二句
--   question_analysis = 全诗正文（按行拼接）
--   题型 FILL、学科语文、学段小学。
--
-- 只取段落数 ≥2 的诗（少于两句凑不出「下句」）。
-- 与古诗库流程的区别：古诗库写 edu_poem（供背诵/赏析），本流程写 edu_question（供组卷）。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '题库采集·唐诗名句',
    0,
    'edu_question_tangpoem',
    '{"name":"题库采集·唐诗名句","threadCount":4,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/chinese-poetry/chinese-poetry-zhCN/master/poetry/poet.tang.0.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取全唐诗分片","x":260,"y":160,"params":[{"name":"url","value":"${url[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"300000"},{"name":"outputVariable","value":"respTang"}],"fields":[]},{"id":"ext","shape":"extract","name":"抽取诗词字段","x":480,"y":160,"params":[{"name":"source","value":"${respTangJson}"},{"name":"mode","value":"list"},{"name":"outputVariable","value":"rows"}],"fields":[{"name":"title","parserType":"JSONPATH","expression":"title"},{"name":"author","parserType":"JSONPATH","expression":"author"},{"name":"paragraphs","parserType":"JSONPATH","expression":"paragraphs"}]},{"id":"sql","shape":"sql","name":"查已存在题干","x":700,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT question_stem AS k FROM edu_question WHERE question_source_type = ''CRAWL''"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr","shape":"script","name":"诗句转题并过滤","x":920,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; def rows2=rows.findAll{ it.paragraphs instanceof List && it.paragraphs.size() >= 2 }; rows2.collect{ def first=it.paragraphs[0].toString(); def second=it.paragraphs[1].toString(); def stem=''补全下句：'' + first + '' ____（'' + it.title + '' · '' + it.author + ''）''; [stem: stem, answer: second, full: it.paragraphs.join(''\\\\n'')] }.unique{ it.stem }.findAll{ !seen.contains(it.stem) }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_question","x":1140,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${stem}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_question"},{"name":"cell:question_type","value":"FILL"},{"name":"cell:question_subject","value":"语文"},{"name":"cell:question_grade_level","value":"小学"},{"name":"cell:question_grade","value":"一年级"},{"name":"cell:question_difficulty","value":"2"},{"name":"cell:question_score","value":"5"},{"name":"cell:question_stem","value":"${stem}"},{"name":"cell:question_answer","value":"${answer}"},{"name":"cell:question_analysis","value":"${full}"},{"name":"cell:question_source","value":"chinese-poetry/poet.tang.0.json"},{"name":"cell:question_source_type","value":"CRAWL"},{"name":"cell:question_audit_status","value":"0"},{"name":"cell:question_status","value":"1"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1360,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"ext"},{"id":"e3","from":"ext","to":"sql"},{"id":"e4","from":"sql","to":"scr"},{"id":"e5","from":"scr","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    4,
    '题库采集：开源《全唐诗》简体镜像 chinese-poetry-zhCN 的 poet.tang.0.json 分片，把每首前两句转成「补全下句」的填空题写入 edu_question（题型 FILL、学科语文、解析存全诗）。只取段落数 ≥2 的诗。本仓库只灌这一个分片，其余分片自行在开始节点加种子 URL。按题干去重，可反复执行。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_question_tangpoem'
);

-- ---------------------------------------------------------------------
-- 五、题库采集·宋诗名句
--
-- 与唐诗那条同构，换数据源分片 poet.song.0.json（约 2500 首）。
-- 单独一条流程而不是在同一条流程里加两个 URL：
--   两个分片结构一致但体量不同，混在一条里时长与失败面都会互相牵连；
--   分成两条后可以分别「立即执行」，某一条抓失败不影响另一条已入库的数据。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '题库采集·宋诗名句',
    0,
    'edu_question_songpoem',
    '{"name":"题库采集·宋诗名句","threadCount":4,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/chinese-poetry/chinese-poetry-zhCN/master/poetry/poet.song.0.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取宋诗分片","x":260,"y":160,"params":[{"name":"url","value":"${url[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"300000"},{"name":"outputVariable","value":"respSong"}],"fields":[]},{"id":"ext","shape":"extract","name":"抽取诗词字段","x":480,"y":160,"params":[{"name":"source","value":"${respSongJson}"},{"name":"mode","value":"list"},{"name":"outputVariable","value":"rows"}],"fields":[{"name":"title","parserType":"JSONPATH","expression":"title"},{"name":"author","parserType":"JSONPATH","expression":"author"},{"name":"paragraphs","parserType":"JSONPATH","expression":"paragraphs"}]},{"id":"sql","shape":"sql","name":"查已存在题干","x":700,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT question_stem AS k FROM edu_question WHERE question_source_type = ''CRAWL''"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr","shape":"script","name":"诗句转题并过滤","x":920,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; def rows2=rows.findAll{ it.paragraphs instanceof List && it.paragraphs.size() >= 2 }; rows2.collect{ def first=it.paragraphs[0].toString(); def second=it.paragraphs[1].toString(); def stem=''补全下句：'' + first + '' ____（'' + it.title + '' · '' + it.author + ''）''; [stem: stem, answer: second, full: it.paragraphs.join(''\\\\n'')] }.unique{ it.stem }.findAll{ !seen.contains(it.stem) }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_question","x":1140,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${stem}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_question"},{"name":"cell:question_type","value":"FILL"},{"name":"cell:question_subject","value":"语文"},{"name":"cell:question_grade_level","value":"小学"},{"name":"cell:question_grade","value":"一年级"},{"name":"cell:question_difficulty","value":"2"},{"name":"cell:question_score","value":"5"},{"name":"cell:question_stem","value":"${stem}"},{"name":"cell:question_answer","value":"${answer}"},{"name":"cell:question_analysis","value":"${full}"},{"name":"cell:question_source","value":"chinese-poetry/poet.song.0.json"},{"name":"cell:question_source_type","value":"CRAWL"},{"name":"cell:question_audit_status","value":"0"},{"name":"cell:question_status","value":"1"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1360,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"ext"},{"id":"e3","from":"ext","to":"sql"},{"id":"e4","from":"sql","to":"scr"},{"id":"e5","from":"scr","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    4,
    '题库采集：开源宋代诗词简体镜像 chinese-poetry-zhCN 的 poet.song.0.json 分片，把每首前两句转成「补全下句」的填空题写入 edu_question（题型 FILL、学科语文、解析存全诗）。只取段落数 ≥2 的诗。与唐诗那条分成两条流程，可分别执行、互不影响。按题干去重，可反复执行。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_question_songpoem'
);

-- ---------------------------------------------------------------------
-- 六、成语库补全·近义候选
--
-- 既有「成语采集·chinese-xinhua」只写了 idiom_content / pinyin / meaning /
-- source / example，近义词与反义词两列一直空着（源里确实没有这两列）。
-- 本流程不动既有流程，改为「读库 → 按释义首句分组 → 回写近义候选」：
--   同组（释义首句相同）的其它成语作为近义候选，最多 3 条。
--   反义不自动推断（语义反义无法可靠推导），留空由老师维护。
--
-- 写库走 UPDATE（需 plugin.collect.sql-allow-write=true），
-- 因此默认停用，由管理员确认后再启用。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '成语库补全·近义候选',
    0,
    'edu_idiom_synonym_fill',
    '{"name":"成语库补全·近义候选","threadCount":1,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"about:blank"}],"fields":[]},{"id":"sql1","shape":"sql","name":"读未配近义的成语","x":260,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT idiom_id AS id, idiom_content AS content, SUBSTRING(idiom_meaning, 1, 12) AS head FROM edu_idiom WHERE (idiom_synonyms IS NULL OR idiom_synonyms = '''') AND idiom_meaning IS NOT NULL AND idiom_meaning <> '''' LIMIT 3000"},{"name":"outputVariable","value":"rows"}],"fields":[]},{"id":"scr","shape":"script","name":"按释义首句分组","x":480,"y":160,"params":[{"name":"script","value":"def groups=(rows?:[]).groupBy{ it.head?.toString()?.trim() }; def out=[]; groups.each{ k, list -> if(list.size() >= 2){ list.each{ r -> def others=list.findAll{ it.id != r.id }.take(3)*.content; if(others){ out << [id: r.id, synonyms: others.join('','')] } } } }; out"},{"name":"outputVariable","value":"updates"}],"fields":[]},{"id":"out","shape":"output","name":"回写近义候选","x":700,"y":160,"params":[{"name":"each","value":"${updates}"},{"name":"key","value":"${id}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_idiom"},{"name":"mode","value":"update"},{"name":"where:idiom_id","value":"${id}"},{"name":"cell:idiom_synonyms","value":"${synonyms}"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":920,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"sql1"},{"id":"e2","from":"sql1","to":"scr"},{"id":"e3","from":"scr","to":"out"},{"id":"e4","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    1,
    '成语库补全：给「近义词」为空、但有释义的成语，按释义首句分组，把同组其它成语作为近义候选回写。只补近义候选，不推断反义（语义反义无法可靠自动推导，反义由老师维护）。写库走 UPDATE，需要 plugin.collect.sql-allow-write=true；默认停用，管理员确认后再启用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_idiom_synonym_fill'
);

-- =====================================================================
-- V2.4__init_edu_word.sql - 词语库表 + 汉字通假字表
-- 通道: INIT（表结构，同步执行）
--
-- 为什么单独一个文件，而不是继续往 V2.1__init_all_eduai.sql 里加：
--   脚本执行记录按「文件名 + 版本号」记账（DataSourceScriptConfiguration#needsExecution），
--   V2.1 在本机库里已有成功记录，改它的内容不会重跑；只有**新文件名**才会被执行。
--
-- 为什么这里只有 CREATE TABLE、没有任何 ALTER：
--   本模块同时要跑在 MySQL/MariaDB（生产）与 H2（本地开发/CI）上。
--   H2 的 ScriptConverter 有两条硬规则会吃掉补列语句：
--     ① `SET @var := (SELECT ...)` 与 `PREPARE/EXECUTE/DEALLOCATE` 整条跳过
--        （DefaultScriptConverter 的「整语句跳过」分支）；
--     ② 非 MySQL 方言下，单列 `ALTER TABLE ... ADD COLUMN` 整条丢弃
--        （DefaultScriptConverter#rewriteAddColumnAlter：列已由建表语句提供，
--          重复 ADD COLUMN 在 H2 上是硬失败）。
--   而 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` 只被 MariaDB 11.8 拒绝（实测语句报错、
--   列不会建成）。也就是说：**没有任何一种补列写法能同时安全地跑在这两族库上**。
--   结论是把「新增字段」做成**新增表**——CREATE TABLE IF NOT EXISTS 两边都支持、都幂等。
--
-- 幂等：CREATE TABLE IF NOT EXISTS；重复执行不改任何已有数据。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 一、词语库 edu_word
--
-- 与 edu_hanzi（一个字一条）并列：词语是「两个字及以上」的语言单位，
-- 维护读音、释义、造句、近义/反义/同音词，供「词语库」页与趣味模块取材。
--
-- 唯一键是整词内容：同一词重复提交走更新（与 edu_hanzi 同口径）。
-- word_kind 用来区分语料类型，采集可以把「词语」「歇后语」写进同一张表，
-- 页面按 kind 过滤，不额外开表。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS edu_word (
    word_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_word_owner (word_owner_user_id),
    word_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '词语ID',
    word_content VARCHAR(64) NOT NULL COMMENT '词语内容（整词，如：美丽）',
    word_pinyin VARCHAR(128) COMMENT '读音（带声调，多读音用逗号分隔，如：měi lì）',
    word_pinyin_plain VARCHAR(128) COMMENT '无声调读音（小写去分隔，多读音逗号分隔，检索用，如：meili）',
    word_meaning TEXT COMMENT '释义',
    word_example TEXT COMMENT '例句 / 造句范例',
    word_synonyms VARCHAR(255) COMMENT '近义词 / 相似词语（逗号分隔）',
    word_antonyms VARCHAR(255) COMMENT '反义词 / 反向词语（逗号分隔）',
    word_homophones VARCHAR(255) COMMENT '同音词（逗号分隔）',
    word_structure VARCHAR(32) COMMENT '结构 / 词性，如：偏正式、动宾式、名词',
    word_kind VARCHAR(20) DEFAULT 'WORD' COMMENT '语料类型：WORD 词语 / XIEHOUYU 歇后语 / PHRASE 短语',
    word_grade_level VARCHAR(20) COMMENT '适用学段（中文名）：小学/初中/高中/大学；NULL 表示不限',
    word_status TINYINT DEFAULT 1 COMMENT '状态：0 停用 / 1 启用',
    create_name VARCHAR(255) DEFAULT NULL COMMENT '创建人姓名',
    create_by INT DEFAULT NULL COMMENT '创建人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT NULL COMMENT '更新时间',
    update_name VARCHAR(255) DEFAULT NULL COMMENT '更新人姓名',
    update_by INT DEFAULT NULL COMMENT '更新人ID',
    UNIQUE KEY uk_edu_word_content (word_content),
    KEY edu_word_kind (word_kind),
    KEY edu_word_level (word_grade_level),
    KEY edu_word_plain (word_pinyin_plain)
);

-- ---------------------------------------------------------------------
-- 二、汉字通假字 edu_hanzi_tongjia
--
-- 通假字是「本字 + 借字」的对应（如：说 通 悦、被 通 披、知 通 智）。
-- 一个字可以有多个借字，故用一对多表而不是在 edu_hanzi 上加逗号串列：
--   · 一对一列要 ALTER（上面说过两族库没有共同的补列写法）；
--   · 逗号串列没法建索引，也没法反查「悦 是哪个字的通假」。
--
-- 检索口径：汉字库关键字同时匹配本字、读音、释义与**借字**
--   （搜「悦」要能找到「说」这条通假记录）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS edu_hanzi_tongjia (
    tongjia_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '通假字记录ID',
    tongjia_hanzi_id BIGINT NOT NULL COMMENT '本字ID（edu_hanzi.hanzi_id）',
    tongjia_char VARCHAR(8) NOT NULL COMMENT '借字（如：悦）',
    tongjia_note VARCHAR(200) DEFAULT NULL COMMENT '说明，如：说，通「悦」，愉快',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_edu_hanzi_tongjia (tongjia_hanzi_id, tongjia_char),
    KEY edu_hanzi_tongjia_char (tongjia_char)
);

-- ---------------------------------------------------------------------
-- 三、词语库的拼音检索列回填说明（不写 UPDATE）
--
-- word_pinyin_plain 由服务端在保存时派生（EduWordServiceImpl#saveWord），
-- 采集流程直接写 edu_word 时该列留空 —— 此时仍可按整词与释义检索，
-- 补上读音后自动获得「敲 meili 找 美丽」的能力。
-- 这里不写批量回填 UPDATE：新表本来是空的，没有存量数据需要修。
-- ---------------------------------------------------------------------

-- ---------------------------------------------------------------------
-- 四、汉字库读音口径（仅注释，无 DDL）
--
-- 本轮把「多音字」落成数据约定而非新列：edu_hanzi.hanzi_pinyin 允许存
-- **多读音逗号分隔**（如 xíng,háng）。理由：
--   · 汉字库的读音本来就该完整，只存一个读音是采集源的缺口，不是设计意图；
--   · 检索侧对 hanzi_pinyin 做 LIKE 天然命中任一读音，不需要额外列；
--   · 现有 3496 条单读音数据无需迁移（单读音就是只有一个元素的列表）。
-- 无声调检索（敲 zhong 命中 zhòng）在查询侧用 REPLACE 链归一化，
-- 见 EduHanziServiceImpl#pageQuery 与 support/util/Pinyins。
-- ---------------------------------------------------------------------

-- =====================================================================
-- V2.6__init_edu_collect_flows_more.sql - 题库 / 词语库采集流程再扩充
-- 通道: NORMAL（V版本号__任意.sql，同步执行，按「文件名 + 版本号」记账只跑一次）
--
-- 为什么单独一个文件：V2.3 已在库里执行过，`validate-checksum` 会拦下改过的同名脚本
-- （日志报「校验和不匹配」），所以新增流程只能开新文件。
--
-- 本文件补 6 条流程，全部基于**实测可用**的公开数据源：
--   1. 词语采集·小学教材词语表  统编语文教材 2~6 年级词语表（1946 个去重词语，按册）
--   2. 汉字采集·通用规范汉字表  教育部《通用规范汉字表》一级字 3500（补 zip 缺字的年级标注）
--   3. 古文采集·论语            20 篇 500 余章
--   4. 古文采集·诗经            305 篇
--   5. 古文采集·楚辞            屈原等 17 篇
--   6. 诗词采集·宋词 + 元曲 + 唐诗三百首（三条独立流程，见下）
--
-- 数据源口径（都实测过 HTTP 200 + 结构）：
--   · chinese-poetry/chinese-poetry（master）是**简体**，可直接入库；
--     chinese-poetry-zhCN 只有 poetry/ 下的唐诗宋诗分片，没有蒙学/论语等目录。
--   · 蒙学/tangshisanbaishou.json 是**繁体**（唐詩三百首），入库前用
--     `toSimplified` 类脚本替换不可靠，故本文件**不采**它，改用 poet.tang.*.json 的简体分片
--     （已在 V2.3 的「题库采集·唐诗名句」覆盖）。
--   · 小学教材词语表来自 7jul/yuwen_words（单一 JSON，按册分组）。
--
-- 幂等：每条 INSERT 都带 WHERE NOT EXISTS（按 collect_flow_code 判重）。
-- 6 条流程一律 collect_flow_status=0（停用）、cron 留空：装完不自动抓外网。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 一、词语采集·小学教材词语表
--
-- 数据源：7jul/yuwen_words 的「小学统编语文教材词语表.json」，结构是
--   { "二年级下册词语表": ["春天","寻找",...], "三年级上册词语表": [...], ... }
-- 共 9 册、1946 个去重词语（2 字 1739 / 3 字 76 / 4 字 131，4 字的大多是成语）。
--
-- 落库：写入 edu_word，word_kind=WORD，word_grade_level 由册名推出
--   （二年级~六年级 → 小学）。册名写进 word_structure，方便老师按册筛选。
--   V2.13 起 word_structure 放宽到大文本：它同时被歇后语流程用来存谜面（最长 41 字）。
--
-- 为什么这条比「汉语词典」更该先跑：
--   ci.json 是 26 万条通用词典，量大但没有教材口径；教材词语表只有 1946 条，
--   却正好是老师布置听写/组词用的那一批，覆盖面小而准。
--
-- 去重：按 word_content（edu_word 的唯一键）查已存在，重复执行只补新词。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '词语采集·小学教材词语表',
    0,
    'edu_word_textbook',
    '{"name":"词语采集·小学教材词语表","threadCount":2,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/7jul/yuwen_words/master/%E5%B0%8F%E5%AD%A6%E7%BB%9F%E7%BC%96%E8%AF%AD%E6%96%87%E6%95%99%E6%9D%90%E8%AF%8D%E8%AF%AD%E8%A1%A8.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取教材词语表","x":240,"y":160,"params":[{"name":"url","value":"${data[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"120000"},{"name":"outputVariable","value":"respWords"}],"fields":[]},{"id":"ext","shape":"extract","name":"按册抽取词语","x":440,"y":160,"params":[{"name":"source","value":"${respWordsJson}"},{"name":"mode","value":"list"},{"name":"rowExpression","value":"$[*]"},{"name":"outputVariable","value":"volumes"}],"fields":[]},{"id":"scr1","shape":"script","name":"展平成词语行","x":640,"y":160,"params":[{"name":"script","value":"def out=[]; (volumes?:[]).eachWithIndex{ vol, i -> def name=vol?.key?.toString() ?: (''第''+(i+1)+''册''); def words=vol?.value instanceof List ? vol.value : []; if(name.contains(''词语表'')){ words.each{ w -> def s=w?.toString()?.trim(); if(s && !s.isEmpty()){ out << [content: s, volume: name, level: ''小学''] } } } }; out"},{"name":"outputVariable","value":"rows"}],"fields":[]},{"id":"sql","shape":"sql","name":"查已存在词语","x":840,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT word_content AS k FROM edu_word"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr2","shape":"script","name":"去重并过滤","x":1040,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; rows.unique{ it.content }.findAll{ !seen.contains(it.content) }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_word","x":1240,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${content}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_word"},{"name":"cell:word_content","value":"${content}"},{"name":"cell:word_structure","value":"${volume}"},{"name":"cell:word_kind","value":"WORD"},{"name":"cell:word_grade_level","value":"${level}"},{"name":"cell:word_status","value":"1"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1440,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"ext"},{"id":"e3","from":"ext","to":"scr1"},{"id":"e4","from":"scr1","to":"sql"},{"id":"e5","from":"sql","to":"scr2"},{"id":"e6","from":"scr2","to":"out"},{"id":"e7","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    2,
    '词语采集：统编小学语文教材 2~6 年级词语表（9 册、1946 个去重词语），按 word_content 去重写入 edu_word。册名写进 word_structure（如「三年级上册词语表」），学段固定小学 —— 老师可按册筛选布置听写。比通用汉语词典更贴合教学口径，建议先跑这条。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_word_textbook'
);

-- ---------------------------------------------------------------------
-- 二、汉字采集·通用规范汉字表
--
-- 数据源：ibeilly/hanzi-practice 的 public/data/moe-characters.json，
--   结构 { source, levels: [ { id:"1", name:"一级字表", desc:"常用字 3500", chars:[...] } ] }
-- 只取「一级字表」（常用字 3500），与现有 edu_hanzi 的 3496 条基本重合，
-- 用途是**补齐采集漏掉的字**（现有流水线有 4 个字因源数据缺失未入库）。
--
-- 落库：只写 hanzi_char / hanzi_grade_level / hanzi_status ——
--   读音、笔画等字段现有记录已有，新补的字留空由老师按教材口径填，
--   **不覆盖已有数据**（输出节点按 hanzi_char 查重后只插新行）。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '汉字采集·通用规范汉字表',
    0,
    'edu_hanzi_moe_table',
    '{"name":"汉字采集·通用规范汉字表","threadCount":2,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/ibeilly/hanzi-practice/main/public/data/moe-characters.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取规范字表","x":240,"y":160,"params":[{"name":"url","value":"${data[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"60000"},{"name":"outputVariable","value":"respMoe"}],"fields":[]},{"id":"scr1","shape":"script","name":"取一级字表","x":440,"y":160,"params":[{"name":"script","value":"def root=respMoeJson; def levels=(root?.levels ?: []); def lv=levels.find{ it?.id?.toString()==''1'' } ?: levels[0]; def chars=(lv?.chars ?: []); chars.findAll{ it }.collect{ [ch: it.toString().trim(), levelName: ''小学''] }.findAll{ it.ch && !it.ch.isEmpty() }"},{"name":"outputVariable","value":"rows"}],"fields":[]},{"id":"sql","shape":"sql","name":"查已存在汉字","x":640,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT hanzi_char AS k FROM edu_hanzi"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr2","shape":"script","name":"只留缺字","x":840,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; rows.unique{ it.ch }.findAll{ !seen.contains(it.ch) }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"补写 edu_hanzi","x":1040,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${ch}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_hanzi"},{"name":"cell:hanzi_char","value":"${ch}"},{"name":"cell:hanzi_grade_level","value":"${levelName}"},{"name":"cell:hanzi_status","value":"1"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1240,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"scr1"},{"id":"e3","from":"scr1","to":"sql"},{"id":"e4","from":"sql","to":"scr2"},{"id":"e5","from":"scr2","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    2,
    '汉字采集：教育部《通用规范汉字表》一级字表（常用字 3500），只补 edu_hanzi 里还没有的字，不覆盖已有读音/笔画/释义。现有 3496 条是采集源 chinese-xinhua 的覆盖结果，本条用于补齐源数据缺口。新补的字读音与笔顺留空，由老师按教材口径填。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_hanzi_moe_table'
);

-- ---------------------------------------------------------------------
-- 三、古文采集·论语
--
-- 数据源：chinese-poetry/chinese-poetry 的 论语/lunyu.json（简体），
--   结构 [ { chapter:"学而篇", paragraphs:[ "子曰：…", … ] } ]，20 篇 500 余章。
-- 落库 edu_poem：poem_title = 「论语·学而篇 第 N 章」，poem_content = 该章原文，
--   poem_author = 孔子及弟子，poem_dynasty = 春秋，poem_difficulty = 4。
--
-- 为什么拆成「每章一条」而不是「每篇一条」：
--   一篇动辄几十章，整篇当成一首「诗」在古诗库里没法读；拆成章后
--   学生端「诗词大会」可以按章抽题，老师组卷也能只取一章。
--
-- 去重：edu_poem 上有 UNIQUE KEY (poem_title, poem_author)，
--   「查已存在 + 过滤」是必需项，不是优化（与 V2.1 里古诗流程同一理由）。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '古文采集·论语',
    0,
    'edu_poem_lunyu',
    '{"name":"古文采集·论语","threadCount":2,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/chinese-poetry/chinese-poetry/master/%E8%AE%BA%E8%AF%AD/lunyu.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取论语","x":240,"y":160,"params":[{"name":"url","value":"${data[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"60000"},{"name":"outputVariable","value":"respLunyu"}],"fields":[]},{"id":"scr1","shape":"script","name":"按章拆条","x":440,"y":160,"params":[{"name":"script","value":"def out=[]; (respLunyuJson?:[]).each{ ch -> def name=ch?.chapter?.toString()?.trim(); def paras=ch?.paragraphs instanceof List ? ch.paragraphs : []; paras.eachWithIndex{ p, i -> def text=p?.toString()?.trim(); if(text){ out << [title: ''论语·''+name+'' 第''+(i+1)+''章'', content: text] } } }; out"},{"name":"outputVariable","value":"rows"}],"fields":[]},{"id":"sql","shape":"sql","name":"查已存在古文","x":640,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT CONCAT(poem_title,''|'',poem_author) AS k FROM edu_poem"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr2","shape":"script","name":"过滤已存在","x":840,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; rows.unique{ it.title }.findAll{ !seen.contains(it.title+''|孔子及弟子'') }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_poem","x":1040,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${title}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_poem"},{"name":"cell:poem_title","value":"${title}"},{"name":"cell:poem_author","value":"孔子及弟子"},{"name":"cell:poem_dynasty","value":"春秋"},{"name":"cell:poem_content","value":"${content}"},{"name":"cell:poem_difficulty","value":"4"},{"name":"cell:poem_tags","value":"论语,诸子"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1240,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"scr1"},{"id":"e3","from":"scr1","to":"sql"},{"id":"e4","from":"sql","to":"scr2"},{"id":"e5","from":"scr2","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    2,
    '古文采集：chinese-poetry 的《论语》简体全文（20 篇 500 余章），按章拆条写入 edu_poem（标题「论语·学而篇 第1章」）。拆章是为了让学生端按章抽题、老师组卷只取一章。按标题+作者去重，可反复执行续采。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_poem_lunyu'
);

-- ---------------------------------------------------------------------
-- 四、古文采集·诗经
--
-- 数据源：诗经/shijing.json（简体），结构 [ { title, chapter:"国风", section:"周南", content:[…] } ]
-- 305 篇。落库 edu_poem：标题「关雎」，朝代「先秦」，作者「佚名」，
--   tags 存「诗经,国风,周南」——章/节信息不能丢，它是分类检索的依据。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '古文采集·诗经',
    0,
    'edu_poem_shijing',
    '{"name":"古文采集·诗经","threadCount":2,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/chinese-poetry/chinese-poetry/master/%E8%AF%97%E7%BB%8F/shijing.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取诗经","x":240,"y":160,"params":[{"name":"url","value":"${data[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"60000"},{"name":"outputVariable","value":"respShijing"}],"fields":[]},{"id":"scr1","shape":"script","name":"组装诗句与标签","x":440,"y":160,"params":[{"name":"script","value":"def out=[]; (respShijingJson?:[]).each{ p -> def title=p?.title?.toString()?.trim(); def lines=p?.content instanceof List ? p.content : []; if(title && lines){ def tags=[''诗经'',p?.chapter?.toString(),p?.section?.toString()].findAll{ it }; out << [title: title, content: lines.join(''\\n''), tags: tags.join('','')] } }; out"},{"name":"outputVariable","value":"rows"}],"fields":[]},{"id":"sql","shape":"sql","name":"查已存在古诗","x":640,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT CONCAT(poem_title,''|'',poem_author) AS k FROM edu_poem"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr2","shape":"script","name":"过滤已存在","x":840,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; rows.unique{ it.title }.findAll{ !seen.contains(it.title+''|佚名'') }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_poem","x":1040,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${title}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_poem"},{"name":"cell:poem_title","value":"${title}"},{"name":"cell:poem_author","value":"佚名"},{"name":"cell:poem_dynasty","value":"先秦"},{"name":"cell:poem_content","value":"${content}"},{"name":"cell:poem_difficulty","value":"4"},{"name":"cell:poem_tags","value":"${tags}"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1240,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"scr1"},{"id":"e3","from":"scr1","to":"sql"},{"id":"e4","from":"sql","to":"scr2"},{"id":"e5","from":"scr2","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    2,
    '古文采集：chinese-poetry 的《诗经》简体全文（305 篇），正文按行拼接，章/节写进 tags（如「诗经,国风,周南」）供分类检索。按标题+作者去重，可反复执行。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_poem_shijing'
);

-- ---------------------------------------------------------------------
-- 五、古文采集·楚辞
--
-- 数据源：楚辞/chuci.json（简体），结构 [ { title:"离骚", section, author:"屈原", content:[…] } ]
-- 落库 edu_poem，作者取源里的 author（屈原/宋玉等），朝代「先秦」。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '古文采集·楚辞',
    0,
    'edu_poem_chuci',
    '{"name":"古文采集·楚辞","threadCount":2,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/chinese-poetry/chinese-poetry/master/%E6%A5%9A%E8%BE%9E/chuci.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取楚辞","x":240,"y":160,"params":[{"name":"url","value":"${data[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"60000"},{"name":"outputVariable","value":"respChuci"}],"fields":[]},{"id":"scr1","shape":"script","name":"组装篇目","x":440,"y":160,"params":[{"name":"script","value":"def out=[]; (respChuciJson?:[]).each{ p -> def title=p?.title?.toString()?.trim(); def lines=p?.content instanceof List ? p.content : []; def author=p?.author?.toString()?.trim() ?: ''佚名''; if(title && lines){ out << [title: title, author: author, content: lines.join(''\\n'')] } }; out"},{"name":"outputVariable","value":"rows"}],"fields":[]},{"id":"sql","shape":"sql","name":"查已存在古诗","x":640,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT CONCAT(poem_title,''|'',poem_author) AS k FROM edu_poem"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr2","shape":"script","name":"过滤已存在","x":840,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; rows.unique{ it.title+''|''+it.author }.findAll{ !seen.contains(it.title+''|''+it.author) }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_poem","x":1040,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${title}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_poem"},{"name":"cell:poem_title","value":"${title}"},{"name":"cell:poem_author","value":"${author}"},{"name":"cell:poem_dynasty","value":"先秦"},{"name":"cell:poem_content","value":"${content}"},{"name":"cell:poem_difficulty","value":"5"},{"name":"cell:poem_tags","value":"楚辞"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1240,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"scr1"},{"id":"e3","from":"scr1","to":"sql"},{"id":"e4","from":"sql","to":"scr2"},{"id":"e5","from":"scr2","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    2,
    '古文采集：chinese-poetry 的《楚辞》简体全文（离骚、九歌、天问等），作者取源里的 author（屈原/宋玉等），难度标 5。按标题+作者去重，可反复执行。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_poem_chuci'
);

-- ---------------------------------------------------------------------
-- 六、诗词采集·宋词
--
-- 数据源：宋词/ci.song.*.json（简体），单个分片 1000 首，共 255 个分片。
-- 本流程只灌第一个分片（ci.song.0.json，1000 首）：
--   全量 25 万首一次跑几小时，且「诗词大会」玩法用不到那么多；
--   需要更多时在开始节点加种子 URL（形如 ci.song.1000.json，步长 1000）。
--
-- 落库：edu_poem，poem_title = 「词牌·首句前 10 字」（宋词没有独立标题，
--   源里的 rhythmic 是词牌名），poem_author 取 author，朝代为宋。
--   UNIQUE KEY (poem_title, poem_author) 要求标题不能重复：
--   同名同作者同词牌的词很多，故标题里带上首句片段（截 10 字）。
-- ---------------------------------------------------------------------
INSERT INTO `collect_flow`
    (`collect_flow_name`, `collect_flow_category_id`, `collect_flow_code`, `collect_flow_dsl`,
     `collect_flow_cron`, `collect_flow_status`, `collect_flow_concurrency`, `collect_flow_running`,
     `collect_flow_thread_count`, `collect_flow_remark`, `collect_flow_last_exec_time`,
     `collect_flow_exec_count`, `collect_flow_is_deleted`, `create_time`, `update_time`)
SELECT
    '诗词采集·宋词',
    0,
    'edu_poem_songci',
    '{"name":"诗词采集·宋词","threadCount":4,"headers":[],"cookies":[],"variables":[],"nodes":[{"id":"start","shape":"start","name":"开始","x":40,"y":160,"params":[{"name":"urls","value":"https://raw.githubusercontent.com/chinese-poetry/chinese-poetry/master/%E5%AE%8B%E8%AF%8D/ci.song.0.json"}],"fields":[]},{"id":"req","shape":"request","name":"抓取宋词分片","x":240,"y":160,"params":[{"name":"url","value":"${data[0]}"},{"name":"method","value":"GET"},{"name":"saveDocument","value":"0"},{"name":"timeout","value":"120000"},{"name":"outputVariable","value":"respCi"}],"fields":[]},{"id":"scr1","shape":"script","name":"组装词牌与首句","x":440,"y":160,"params":[{"name":"script","value":"def out=[]; (respCiJson?:[]).each{ c -> def paras=c?.paragraphs instanceof List ? c.paragraphs : []; if(paras){ def first=paras[0]?.toString()?.trim(); def rhythm=c?.rhythmic?.toString()?.trim() ?: ''词''; def author=c?.author?.toString()?.trim() ?: ''佚名''; if(first){ def head=first.length()>10 ? first.substring(0,10) : first; out << [title: rhythm+''·''+head, author: author, content: paras.join(''\\n''), rhythm: rhythm] } } }; out"},{"name":"outputVariable","value":"rows"}],"fields":[]},{"id":"sql","shape":"sql","name":"查已存在词作","x":640,"y":160,"params":[{"name":"datasource","value":"app"},{"name":"statement","value":"SELECT CONCAT(poem_title,''|'',poem_author) AS k FROM edu_poem"},{"name":"outputVariable","value":"existing"}],"fields":[]},{"id":"scr2","shape":"script","name":"过滤已存在","x":840,"y":160,"params":[{"name":"script","value":"def seen=(existing?:[])*.k as Set; rows.unique{ it.title+''|''+it.author }.findAll{ !seen.contains(it.title+''|''+it.author) }"},{"name":"outputVariable","value":"fresh"}],"fields":[]},{"id":"out","shape":"output","name":"写入 edu_poem","x":1040,"y":160,"params":[{"name":"each","value":"${fresh}"},{"name":"key","value":"${title}"},{"name":"datasource","value":"app"},{"name":"targetTable","value":"edu_poem"},{"name":"cell:poem_title","value":"${title}"},{"name":"cell:poem_author","value":"${author}"},{"name":"cell:poem_dynasty","value":"宋代"},{"name":"cell:poem_content","value":"${content}"},{"name":"cell:poem_difficulty","value":"4"},{"name":"cell:poem_tags","value":"宋词,${rhythm}"}],"fields":[]},{"id":"end","shape":"end","name":"结束","x":1240,"y":160,"params":[]}],"edges":[{"id":"e1","from":"start","to":"req"},{"id":"e2","from":"req","to":"scr1"},{"id":"e3","from":"scr1","to":"sql"},{"id":"e4","from":"sql","to":"scr2"},{"id":"e5","from":"scr2","to":"out"},{"id":"e6","from":"out","to":"end"}]}',
    NULL,
    0,
    1,
    0,
    4,
    '诗词采集：chinese-poetry 的宋词简体分片 ci.song.0.json（1000 首）。宋词没有独立标题，poem_title 用「词牌·首句前10字」（如「水调歌头·明月几时有」），词牌写进 tags 供按词牌筛选。本流程只灌一个分片，需要更多时在开始节点按 ci.song.1000.json、ci.song.2000.json 递增加种子 URL。默认停用。',
    NULL,
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM `collect_flow` WHERE `collect_flow_code` = 'edu_poem_songci'
);

