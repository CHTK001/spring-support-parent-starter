-- ============================================================
-- 数据同步执行日志（补丁脚本）
--
-- 为什么单独成文件而不并入 V2.1__init_all_monitor.sql：
--   INIT 脚本按「文件名+版本」记账，V2.1 在存量库里早已 success=true，
--   之后再改内容只会触发 checksum 告警并跳过执行 —— 新表永远建不出来。
--   本文件是 NORMAL 脚本（文件名不含 init/initdata），按自身 checksum 记账，
--   首次启动执行一次，之后稳定跳过。
--
-- 由 SyncTaskRunLogBuffer 在一次执行结束时写入、SyncTaskServiceImpl 读回。
-- 在此之前调试日志只存在内存环形缓冲里（每任务 10 次、后端重启即清空），
-- 排障时「昨天 3 点那次为什么失败」根本查不到。
--
-- 一次执行一行，正文用 LONGTEXT 存「时间<TAB>级别<TAB>内容」逐行拼接：
-- 日志正文本身含换行（ERROR 堆栈）与引号，用 JSON 需要额外转义，
-- 而这份数据的唯一用途就是原样还给界面重新拼成行。
--
-- 不建外键：任务删除后日志仍需保留以便事后追溯；由保留策略按时间裁剪。
-- ============================================================
CREATE TABLE IF NOT EXISTS `ms_sync_task_run_log` (
    `id`                                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_sync_task_run_log_task_id`          BIGINT       NOT NULL COMMENT '任务 ID(ms_sync_task.id)，任务删除后本行保留',
    `ms_sync_task_run_log_trigger`          VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT '触发方式 MANUAL手动/SCHEDULED定时/REALTIME实时录入',
    `ms_sync_task_run_log_status`           VARCHAR(16)  NOT NULL COMMENT '执行状态 SUCCESS/FAILED',
    `ms_sync_task_run_log_run_time`         DATETIME     NOT NULL COMMENT '执行开始时刻',
    `ms_sync_task_run_log_cost_ms`          BIGINT       DEFAULT NULL COMMENT '本次执行总耗时(毫秒)',
    `ms_sync_task_run_log_lines`            LONGTEXT     DEFAULT NULL COMMENT '过程日志正文：逐行「时间\\t级别\\t内容」，换行分隔',
    `ms_sync_task_run_log_line_count`       INT          NOT NULL DEFAULT 0 COMMENT '日志行数（列表展示用，免解析正文）',
    `ms_sync_task_run_log_create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '入库时刻',
    PRIMARY KEY (`id`),
    KEY `idx_ms_sync_task_run_log_task_time` (`ms_sync_task_run_log_task_id`, `ms_sync_task_run_log_run_time`),
    KEY `idx_ms_sync_task_run_log_time` (`ms_sync_task_run_log_run_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据同步执行日志';
