-- =============================================
-- 策略管理模块初始化脚本
-- 版本: 1.0
-- 描述: 创建限流、熔断、防抖、调度任务等策略配置表
-- 作者: CH
-- 创建时间: 2025-12-02
-- =============================================

-- -------------------------------------------
-- 限流配置表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_limit_configuration` (
    `sys_limit_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `sys_limit_path` VARCHAR(500) NOT NULL COMMENT '服务/接口路径，支持Ant风格匹配',
    `sys_limit_name` VARCHAR(100) NOT NULL COMMENT '限流配置名称',
    `sys_limit_for_period` INT NOT NULL DEFAULT 10 COMMENT '周期内允许的请求数',
    `sys_limit_refresh_period_seconds` INT NOT NULL DEFAULT 1 COMMENT '刷新周期（秒）',
    `sys_limit_timeout_duration_millis` INT NOT NULL DEFAULT 500 COMMENT '获取令牌超时时间（毫秒）',
    `sys_limit_dimension` VARCHAR(20) NOT NULL DEFAULT 'GLOBAL' COMMENT '限流维度：GLOBAL-全局, IP-按IP, USER-按用户, API-按接口',
    `sys_limit_message` VARCHAR(200) DEFAULT '请求过于频繁，请稍后再试' COMMENT '限流提示信息',
    `sys_limit_status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用, 1-启用',
    `sys_limit_description` VARCHAR(500) DEFAULT NULL COMMENT '描述信息',
    `sys_limit_sort` INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越优先',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
    `create_name` VARCHAR(50) DEFAULT NULL COMMENT '创建人姓名',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
    `update_name` VARCHAR(50) DEFAULT NULL COMMENT '更新人姓名',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`sys_limit_id`),
    INDEX `idx_limit_path` (`sys_limit_path`),
    INDEX `idx_limit_status` (`sys_limit_status`),
    INDEX `idx_limit_sort` (`sys_limit_sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='限流配置表';

-- -------------------------------------------
-- 限流记录表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_limit_record` (
    `sys_limit_record_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `sys_limit_configuration_id` BIGINT DEFAULT NULL COMMENT '关联的限流配置ID',
    `sys_limit_path` VARCHAR(500) DEFAULT NULL COMMENT '触发限流的接口路径',
    `sys_user_id` BIGINT DEFAULT NULL COMMENT '触发限流的用户ID',
    `sys_user_name` VARCHAR(50) DEFAULT NULL COMMENT '触发限流的用户名',
    `client_ip` VARCHAR(50) DEFAULT NULL COMMENT '客户端IP地址',
    `request_method` VARCHAR(10) DEFAULT NULL COMMENT 'HTTP请求方法',
    `request_params` TEXT DEFAULT NULL COMMENT '请求参数（JSON格式）',
    `sys_limit_time` DATETIME NOT NULL COMMENT '限流触发时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`sys_limit_record_id`),
    INDEX `idx_record_config_id` (`sys_limit_configuration_id`),
    INDEX `idx_record_path` (`sys_limit_path`),
    INDEX `idx_record_user_id` (`sys_user_id`),
    INDEX `idx_record_client_ip` (`client_ip`),
    INDEX `idx_record_time` (`sys_limit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='限流记录表';

-- -------------------------------------------
-- 熔断配置表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_circuit_breaker_configuration` (
    `sys_circuit_breaker_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `sys_circuit_breaker_name` VARCHAR(100) NOT NULL COMMENT '熔断器名称',
    `sys_circuit_breaker_path` VARCHAR(500) NOT NULL COMMENT '服务/接口路径，支持Ant风格匹配',
    `failure_rate_threshold` DOUBLE NOT NULL DEFAULT 50 COMMENT '失败率阈值（百分比）',
    `slow_call_rate_threshold` DOUBLE NOT NULL DEFAULT 100 COMMENT '慢调用率阈值（百分比）',
    `slow_call_duration_threshold_ms` BIGINT NOT NULL DEFAULT 60000 COMMENT '慢调用持续时间阈值（毫秒）',
    `minimum_number_of_calls` INT NOT NULL DEFAULT 10 COMMENT '最小调用次数',
    `sliding_window_size` INT NOT NULL DEFAULT 10 COMMENT '滑动窗口大小',
    `sliding_window_type` VARCHAR(20) NOT NULL DEFAULT 'COUNT_BASED' COMMENT '滑动窗口类型：COUNT_BASED-基于计数, TIME_BASED-基于时间',
    `wait_duration_in_open_state_ms` BIGINT NOT NULL DEFAULT 60000 COMMENT '熔断器打开状态的等待时间（毫秒）',
    `permitted_calls_in_half_open_state` INT NOT NULL DEFAULT 3 COMMENT '半开状态允许的调用次数',
    `automatic_transition_from_open` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否自动从打开状态转换到半开状态',
    `fallback_method` VARCHAR(200) DEFAULT NULL COMMENT '降级方法名称',
    `fallback_value` TEXT DEFAULT NULL COMMENT '降级返回值（JSON格式）',
    `sys_circuit_breaker_status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用, 1-启用',
    `sys_circuit_breaker_description` VARCHAR(500) DEFAULT NULL COMMENT '描述信息',
    `sys_circuit_breaker_sort` INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越优先',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
    `create_name` VARCHAR(50) DEFAULT NULL COMMENT '创建人姓名',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
    `update_name` VARCHAR(50) DEFAULT NULL COMMENT '更新人姓名',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`sys_circuit_breaker_id`),
    INDEX `idx_cb_path` (`sys_circuit_breaker_path`),
    INDEX `idx_cb_status` (`sys_circuit_breaker_status`),
    INDEX `idx_cb_sort` (`sys_circuit_breaker_sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='熔断配置表';

-- -------------------------------------------
-- 熔断记录表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_circuit_breaker_record` (
    `sys_circuit_breaker_record_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `sys_circuit_breaker_id` BIGINT DEFAULT NULL COMMENT '关联的熔断配置ID',
    `sys_circuit_breaker_name` VARCHAR(100) DEFAULT NULL COMMENT '熔断器名称',
    `sys_circuit_breaker_path` VARCHAR(500) DEFAULT NULL COMMENT '触发熔断的接口路径',
    `circuit_breaker_state` VARCHAR(20) DEFAULT NULL COMMENT '熔断器状态：CLOSED-关闭, OPEN-打开, HALF_OPEN-半开',
    `trigger_reason` VARCHAR(50) DEFAULT NULL COMMENT '触发原因：FAILURE_RATE-失败率超过阈值, SLOW_CALL_RATE-慢调用率超过阈值',
    `failure_rate` DOUBLE DEFAULT NULL COMMENT '当前失败率（百分比）',
    `slow_call_rate` DOUBLE DEFAULT NULL COMMENT '当前慢调用率（百分比）',
    `sys_user_id` BIGINT DEFAULT NULL COMMENT '触发熔断的用户ID',
    `sys_user_name` VARCHAR(50) DEFAULT NULL COMMENT '触发熔断的用户名',
    `client_ip` VARCHAR(50) DEFAULT NULL COMMENT '客户端IP地址',
    `request_method` VARCHAR(10) DEFAULT NULL COMMENT 'HTTP请求方法',
    `exception_message` TEXT DEFAULT NULL COMMENT '异常信息',
    `trigger_time` DATETIME NOT NULL COMMENT '熔断触发时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`sys_circuit_breaker_record_id`),
    INDEX `idx_cb_record_id` (`sys_circuit_breaker_id`),
    INDEX `idx_cb_record_path` (`sys_circuit_breaker_path`),
    INDEX `idx_cb_record_state` (`circuit_breaker_state`),
    INDEX `idx_cb_record_time` (`trigger_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='熔断记录表';

-- -------------------------------------------
-- 防抖配置表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_debounce_configuration` (
    `sys_debounce_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `sys_debounce_name` VARCHAR(100) NOT NULL COMMENT '防抖器名称',
    `sys_debounce_path` VARCHAR(500) NOT NULL COMMENT '服务/接口路径，支持Ant风格匹配',
    `sys_debounce_duration` VARCHAR(20) NOT NULL DEFAULT '1S' COMMENT '防抖时间间隔，支持格式：1000, 1S, 1MIN, 1H',
    `sys_debounce_key` VARCHAR(200) DEFAULT NULL COMMENT '防抖键表达式，支持SpEL',
    `sys_debounce_mode` VARCHAR(20) NOT NULL DEFAULT 'global' COMMENT '防抖模式：global-全局, ip-基于IP, user-基于用户, session-基于会话',
    `sys_debounce_message` VARCHAR(200) DEFAULT '操作过于频繁，请稍后再试' COMMENT '防抖失败时的提示消息',
    `fallback_method` VARCHAR(200) DEFAULT NULL COMMENT '降级方法名称',
    `fallback_value` TEXT DEFAULT NULL COMMENT '降级返回值（JSON格式）',
    `sys_debounce_status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用, 1-启用',
    `sys_debounce_description` VARCHAR(500) DEFAULT NULL COMMENT '描述信息',
    `sys_debounce_sort` INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越优先',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
    `create_name` VARCHAR(50) DEFAULT NULL COMMENT '创建人姓名',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
    `update_name` VARCHAR(50) DEFAULT NULL COMMENT '更新人姓名',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`sys_debounce_id`),
    INDEX `idx_debounce_path` (`sys_debounce_path`),
    INDEX `idx_debounce_status` (`sys_debounce_status`),
    INDEX `idx_debounce_sort` (`sys_debounce_sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='防抖配置表';

-- -------------------------------------------
-- 调度任务配置表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_scheduler_configuration` (
    `sys_scheduler_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `sys_scheduler_name` VARCHAR(100) NOT NULL COMMENT '任务名称',
    `sys_scheduler_group` VARCHAR(100) DEFAULT 'DEFAULT' COMMENT '任务组名',
    `sys_scheduler_type` VARCHAR(20) NOT NULL DEFAULT 'cron' COMMENT '任务类型：cron-Cron表达式, fixed_rate-固定频率, fixed_delay-固定延迟',
    `sys_scheduler_cron` VARCHAR(100) DEFAULT NULL COMMENT 'Cron表达式',
    `sys_scheduler_interval` BIGINT DEFAULT NULL COMMENT '固定频率/延迟时间（毫秒）',
    `sys_scheduler_initial_delay` BIGINT DEFAULT 0 COMMENT '初始延迟时间（毫秒）',
    `sys_scheduler_bean_name` VARCHAR(200) NOT NULL COMMENT '任务执行Bean名称',
    `sys_scheduler_method_name` VARCHAR(100) NOT NULL COMMENT '任务执行方法名',
    `sys_scheduler_params` TEXT DEFAULT NULL COMMENT '任务参数（JSON格式）',
    `sys_concurrent_allowed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许并发执行',
    `sys_scheduler_status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-暂停, 1-运行',
    `last_execute_time` DATETIME DEFAULT NULL COMMENT '上次执行时间',
    `next_execute_time` DATETIME DEFAULT NULL COMMENT '下次执行时间',
    `sys_scheduler_description` VARCHAR(500) DEFAULT NULL COMMENT '描述信息',
    `sys_scheduler_sort` INT NOT NULL DEFAULT 0 COMMENT '排序值',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
    `create_name` VARCHAR(50) DEFAULT NULL COMMENT '创建人姓名',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
    `update_name` VARCHAR(50) DEFAULT NULL COMMENT '更新人姓名',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`sys_scheduler_id`),
    UNIQUE KEY `uk_scheduler_name_group` (`sys_scheduler_name`, `sys_scheduler_group`),
    INDEX `idx_scheduler_status` (`sys_scheduler_status`),
    INDEX `idx_scheduler_type` (`sys_scheduler_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调度任务配置表';

-- -------------------------------------------
-- 调度任务执行日志表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_scheduler_log` (
    `sys_scheduler_log_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `sys_scheduler_id` BIGINT NOT NULL COMMENT '任务ID',
    `sys_scheduler_name` VARCHAR(100) DEFAULT NULL COMMENT '任务名称',
    `execute_status` TINYINT NOT NULL COMMENT '执行状态：0-失败, 1-成功',
    `execute_start_time` DATETIME NOT NULL COMMENT '执行开始时间',
    `execute_end_time` DATETIME DEFAULT NULL COMMENT '执行结束时间',
    `execute_duration_ms` BIGINT DEFAULT NULL COMMENT '执行耗时（毫秒）',
    `execute_result` TEXT DEFAULT NULL COMMENT '执行结果',
    `exception_message` TEXT DEFAULT NULL COMMENT '异常信息',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`sys_scheduler_log_id`),
    INDEX `idx_log_scheduler_id` (`sys_scheduler_id`),
    INDEX `idx_log_status` (`execute_status`),
    INDEX `idx_log_start_time` (`execute_start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调度任务执行日志表';
