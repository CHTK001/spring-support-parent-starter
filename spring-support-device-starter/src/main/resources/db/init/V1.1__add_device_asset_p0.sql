-- =====================================================================
-- V1.1__add_device_asset_p0.sql
-- 设备资产 P0：资产字段、负责人、最后在线、同步/任务基础表
--
-- 说明：
--   1. 本脚本由 datasource-starter 的 Flyway 风格迁移器执行，文件名含 __add_，
--      同一脚本只执行一次，因此这里不写 IF NOT EXISTS（MySQL 的 ADD COLUMN 不支持）。
--   2. 生产库为 MySQL 8；H2 若需执行请改用 H2 对应的 ALTER 语法。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. device_info：资产台账字段
-- ---------------------------------------------------------------------
ALTER TABLE device_info
    ADD COLUMN device_info_code            VARCHAR(64)   COMMENT '设备编码（资产编码/厂商序列号，列表可复制）',
    ADD COLUMN device_info_external_id     VARCHAR(128)  COMMENT '厂商侧设备唯一标识（同步幂等键）',
    ADD COLUMN device_info_credential_id   VARCHAR(32)   COMMENT '绑定的提供商凭据 ID',
    ADD COLUMN device_info_owner_id        VARCHAR(32)   COMMENT '负责人用户 ID',
    ADD COLUMN device_info_owner_name      VARCHAR(64)   COMMENT '负责人姓名（冗余，列表直出）',
    ADD COLUMN device_info_owner_dept      VARCHAR(128)  COMMENT '负责人部门',
    ADD COLUMN device_info_owner_phone     VARCHAR(32)   COMMENT '负责人电话',
    ADD COLUMN device_info_source          VARCHAR(16)   DEFAULT 'local' COMMENT '来源：local 手工、vendor 厂商同步',
    ADD COLUMN device_info_stream_main_url VARCHAR(1024) COMMENT '主码流地址',
    ADD COLUMN device_info_stream_sub_url  VARCHAR(1024) COMMENT '子码流地址',
    ADD COLUMN device_info_last_online_at  DATETIME      COMMENT '最后在线时间',
    ADD COLUMN device_info_last_offline_at DATETIME      COMMENT '最后离线时间',
    ADD COLUMN device_info_install_at      DATE          COMMENT '安装日期',
    ADD COLUMN device_info_warranty_end    DATE          COMMENT '维保到期',
    ADD COLUMN device_info_area            VARCHAR(128)  COMMENT '区域/楼层',
    ADD COLUMN device_info_tags            VARCHAR(255)  COMMENT '标签，逗号分隔',
    ADD COLUMN device_info_status          VARCHAR(16)   DEFAULT 'normal' COMMENT '状态：normal/disabled/maintenance/scrapped',
    ADD COLUMN device_info_remark          VARCHAR(500)  COMMENT '备注',
    ADD COLUMN device_info_version         INT           DEFAULT 0 COMMENT '乐观锁/增量序号';

-- 密码改为加密存储后长度会变长（AES Hex），原 100 不够
ALTER TABLE device_info
    MODIFY COLUMN device_info_password VARCHAR(255) COMMENT '密码（加密存储）';

-- 同步幂等键：同一厂商同一外部设备只允许一行；手工录入 external_id 为 NULL 不受影响
CREATE UNIQUE INDEX uk_device_vendor_external
    ON device_info (device_info_vendor, device_info_external_id);

CREATE INDEX idx_device_online_last
    ON device_info (device_info_online, device_info_last_online_at);

CREATE INDEX idx_device_owner
    ON device_info (device_info_owner_id);

-- ---------------------------------------------------------------------
-- 2. device_provider_credential：SK 加密后长度变长
-- ---------------------------------------------------------------------
ALTER TABLE device_provider_credential
    MODIFY COLUMN device_credential_app_secret VARCHAR(512) COMMENT 'AppSecret（SK），加密存储';

-- ---------------------------------------------------------------------
-- 3. device_organization：外部 ID、排序、删除标记
-- ---------------------------------------------------------------------
ALTER TABLE device_organization
    ADD COLUMN device_organization_external_id VARCHAR(128) COMMENT '厂商侧组织唯一标识',
    ADD COLUMN device_organization_sort        INT          DEFAULT 0 COMMENT '排序号',
    ADD COLUMN device_organization_deleted     TINYINT      DEFAULT 0 COMMENT '厂商已删除：0 正常、1 删除',
    ADD COLUMN device_organization_updated_at  DATETIME     COMMENT '更新时间';

-- ---------------------------------------------------------------------
-- 4. 在线状态流水：在线率、最后在线、离线时长
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_online_log (
    device_online_log_id        VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_online_log_device_id VARCHAR(32) NOT NULL COMMENT '设备 ID',
    device_online_log_online    TINYINT     NOT NULL COMMENT '1 在线、0 离线',
    device_online_log_at        DATETIME    NOT NULL COMMENT '发生时间',
    KEY idx_online_log_device (`device_online_log_device_id`, `device_online_log_at`)
) COMMENT '设备在线状态流水';

-- ---------------------------------------------------------------------
-- 5. 同步状态/记录/推送队列（设备目录同步）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_sync_state (
    device_sync_state_id       VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_sync_state_vendor   VARCHAR(32) NOT NULL COMMENT '厂商标识',
    device_sync_state_resource VARCHAR(32) NOT NULL COMMENT '资源：device/channel/org/access/parking',
    device_sync_state_cursor   VARCHAR(255) COMMENT '增量游标',
    device_sync_state_last_at  DATETIME     COMMENT '最后同步时间',
    device_sync_state_last_ok  TINYINT      DEFAULT 1 COMMENT '最后结果：1 成功、0 失败',
    device_sync_state_message  VARCHAR(500) COMMENT '最后结果说明',
    UNIQUE KEY uk_sync_state (`device_sync_state_vendor`, `device_sync_state_resource`)
) COMMENT '厂商同步游标状态';

CREATE TABLE IF NOT EXISTS device_sync_record (
    device_sync_record_id          VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_sync_record_task_id     VARCHAR(32) NOT NULL COMMENT '任务 ID',
    device_sync_record_vendor      VARCHAR(32) COMMENT '厂商标识',
    device_sync_record_mode        VARCHAR(16) COMMENT 'pull/push/preview',
    device_sync_record_phase       VARCHAR(32) COMMENT '阶段',
    device_sync_record_total       INT DEFAULT 0 COMMENT '总数',
    device_sync_record_added       INT DEFAULT 0 COMMENT '新增数',
    device_sync_record_updated     INT DEFAULT 0 COMMENT '更新数',
    device_sync_record_removed     INT DEFAULT 0 COMMENT '删除数',
    device_sync_record_failed      INT DEFAULT 0 COMMENT '失败数',
    device_sync_record_status      VARCHAR(16) COMMENT 'running/success/partial/failed',
    device_sync_record_message     VARCHAR(1000) COMMENT '说明',
    device_sync_record_started_at  DATETIME COMMENT '开始时间',
    device_sync_record_finished_at DATETIME COMMENT '结束时间',
    KEY idx_sync_record_time (`device_sync_record_started_at`)
) COMMENT '同步执行记录';

CREATE TABLE IF NOT EXISTS device_sync_outbox (
    device_sync_outbox_id        VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_sync_outbox_device_id VARCHAR(32) COMMENT '设备 ID',
    device_sync_outbox_vendor    VARCHAR(32) COMMENT '厂商标识',
    device_sync_outbox_action    VARCHAR(16) COMMENT 'create/update/delete',
    device_sync_outbox_payload   TEXT COMMENT '推送载荷',
    device_sync_outbox_status    VARCHAR(16) DEFAULT 'pending' COMMENT 'pending/sent/failed/dead',
    device_sync_outbox_retry     INT DEFAULT 0 COMMENT '重试次数',
    device_sync_outbox_next_at   DATETIME COMMENT '下次重试时间',
    device_sync_outbox_message   VARCHAR(500) COMMENT '失败原因',
    device_sync_outbox_created_at DATETIME COMMENT '创建时间',
    KEY idx_outbox_due (`device_sync_outbox_status`, `device_sync_outbox_next_at`)
) COMMENT '设备变更推送队列';

-- ---------------------------------------------------------------------
-- 6. 任务配置（时间轮/可配置检查时间）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_task_config (
    device_task_config_id           VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_task_config_code         VARCHAR(64) NOT NULL COMMENT '任务编码',
    device_task_config_name         VARCHAR(100) COMMENT '任务名称',
    device_task_config_enabled      TINYINT DEFAULT 1 COMMENT '是否启用',
    device_task_config_cron         VARCHAR(64) COMMENT 'cron 表达式（优先）',
    device_task_config_interval     INT COMMENT '固定间隔（秒）',
    device_task_config_next_at      DATETIME COMMENT '下次执行时间',
    device_task_config_last_at      DATETIME COMMENT '最后执行时间',
    device_task_config_last_status  VARCHAR(16) COMMENT '最后状态',
    device_task_config_last_message VARCHAR(1000) COMMENT '最后结果说明',
    device_task_config_timeout      INT DEFAULT 300 COMMENT '超时（秒）',
    device_task_config_params       TEXT COMMENT '参数 JSON（厂商、组织、批量大小）',
    device_task_config_lock_owner   VARCHAR(64) COMMENT '锁持有者',
    device_task_config_lock_until   DATETIME COMMENT '锁到期时间',
    UNIQUE KEY uk_task_config_code (`device_task_config_code`)
) COMMENT '设备域定时任务配置';
