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
    answer_sheet_exam_id BIGINT NOT NULL COMMENT '考试ID',
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
      -- 古诗：同一「标题 + 作者」视为同一首（与采集入库处理器的唯一键一致），保证种子脚本幂等,
    UNIQUE KEY uk_edu_poem_title_author (poem_title(191), poem_author(64))
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
ALTER TABLE edu_student
    ADD COLUMN IF NOT EXISTS student_password VARCHAR(64) DEFAULT NULL COMMENT '登录口令摘要 hmacSha256(明文,盐)，非明文',
    ADD COLUMN IF NOT EXISTS student_pwd_salt VARCHAR(32) DEFAULT NULL COMMENT '口令盐（随机十六进制，与 student_password 一一对应）',
    -- 首次登录引导改密：管理员设的初始口令置 1，学生自助改密后置 0。
    -- 老数据默认 0（EOF NULL 由业务按「无口令」处理），不回溯逼改，避免上线即扰民。
    ADD COLUMN IF NOT EXISTS student_pwd_must_change TINYINT DEFAULT 0 COMMENT '是否需引导改密: 0-否 1-是';

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
ALTER TABLE edu_paper_assign
    ADD COLUMN IF NOT EXISTS create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';



