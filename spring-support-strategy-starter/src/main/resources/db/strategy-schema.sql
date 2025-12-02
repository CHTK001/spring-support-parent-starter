-- ============================================
-- 策略管理模块数据库脚本
-- 包含限流、熔断、降级等策略配置表
-- @author CH
-- @version 1.0.0
-- @since 2025-12-02
-- ============================================

-- -------------------------------------------
-- 限流配置表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_limit_configuration` (
    `sys_limit_configuration_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `sys_limit_path` VARCHAR(500) NOT NULL COMMENT '接口路径，支持Ant风格匹配',
    `sys_limit_name` VARCHAR(100) NOT NULL COMMENT '限流规则名称',
    `sys_limit_for_period` INT NOT NULL DEFAULT 100 COMMENT '每个周期允许的请求数量',
    `sys_limit_refresh_period_seconds` INT NOT NULL DEFAULT 1 COMMENT '限流刷新周期（秒）',
    `sys_limit_timeout_duration_millis` BIGINT NOT NULL DEFAULT 500 COMMENT '获取许可的超时时间（毫秒）',
    `sys_limit_dimension` VARCHAR(20) NOT NULL DEFAULT 'GLOBAL' COMMENT '限流维度：GLOBAL-全局, IP-按IP, USER-按用户, API-按接口',
    `sys_limit_key_expression` VARCHAR(500) DEFAULT NULL COMMENT '自定义键表达式（SpEL）',
    `sys_limit_fallback_method` VARCHAR(200) DEFAULT NULL COMMENT '降级方法名称',
    `sys_limit_message` VARCHAR(500) DEFAULT '请求过于频繁，请稍后再试' COMMENT '限流触发时的错误消息',
    `sys_limit_status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用, 1-启用',
    `sys_limit_description` VARCHAR(500) DEFAULT NULL COMMENT '描述信息',
    `sys_limit_sort` INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越优先',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
    `create_name` VARCHAR(50) DEFAULT NULL COMMENT '创建人姓名',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
    `update_name` VARCHAR(50) DEFAULT NULL COMMENT '更新人姓名',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`sys_limit_configuration_id`),
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
    `sys_limit_name` VARCHAR(100) DEFAULT NULL COMMENT '限流规则名称',
    `sys_limit_path` VARCHAR(500) DEFAULT NULL COMMENT '触发限流的接口路径',
    `sys_limit_dimension` VARCHAR(20) DEFAULT NULL COMMENT '限流维度',
    `sys_limit_key` VARCHAR(200) DEFAULT NULL COMMENT '限流键值（如IP地址、用户ID等）',
    `sys_user_id` BIGINT DEFAULT NULL COMMENT '触发限流的用户ID',
    `sys_user_name` VARCHAR(50) DEFAULT NULL COMMENT '触发限流的用户名',
    `client_ip` VARCHAR(50) DEFAULT NULL COMMENT '客户端IP地址',
    `request_method` VARCHAR(10) DEFAULT NULL COMMENT 'HTTP请求方法',
    `request_params` TEXT DEFAULT NULL COMMENT '请求参数（JSON格式）',
    `user_agent` VARCHAR(500) DEFAULT NULL COMMENT '用户代理信息',
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
-- 初始化默认限流规则
-- -------------------------------------------
INSERT INTO `sys_limit_configuration` 
    (`sys_limit_path`, `sys_limit_name`, `sys_limit_for_period`, `sys_limit_refresh_period_seconds`, 
     `sys_limit_dimension`, `sys_limit_message`, `sys_limit_status`, `sys_limit_description`, `sys_limit_sort`)
VALUES 
    ('/api/**', '全局API限流', 100, 1, 'GLOBAL', '请求过于频繁，请稍后再试', 1, '默认的全局API限流规则', 100),
    ('/v2/**', 'V2接口限流', 200, 1, 'GLOBAL', '请求过于频繁，请稍后再试', 1, 'V2版本接口限流规则', 99);

-- -------------------------------------------
-- 初始化默认熔断规则
-- -------------------------------------------
INSERT INTO `sys_circuit_breaker_configuration`
    (`sys_circuit_breaker_name`, `sys_circuit_breaker_path`, `failure_rate_threshold`, `slow_call_rate_threshold`,
     `slow_call_duration_threshold_ms`, `minimum_number_of_calls`, `sliding_window_size`, `sliding_window_type`,
     `wait_duration_in_open_state_ms`, `permitted_calls_in_half_open_state`, `sys_circuit_breaker_status`,
     `sys_circuit_breaker_description`, `sys_circuit_breaker_sort`)
VALUES
    ('全局熔断器', '/api/**', 50, 100, 60000, 10, 10, 'COUNT_BASED', 60000, 3, 1, '默认的全局熔断规则', 100);
