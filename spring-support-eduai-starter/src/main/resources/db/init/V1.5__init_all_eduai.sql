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
    UNIQUE KEY uk_edu_marking_task_task_id (marking_task_task_id),
    KEY edu_marking_task_status (marking_task_status)
) COMMENT '阅卷任务（异步：创建任务→后台处理→可重启续跑）';