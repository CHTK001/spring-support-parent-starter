CREATE TABLE IF NOT EXISTS `payment_scheduler_config` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  `task_key` VARCHAR(64) NOT NULL COMMENT '任务编码',
  `task_name` VARCHAR(128) NOT NULL COMMENT '任务名称',
  `cron_expression` VARCHAR(64) NOT NULL COMMENT 'Cron表达式',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `description` VARCHAR(512) DEFAULT NULL COMMENT '任务说明',
  `last_started_at` DATETIME DEFAULT NULL COMMENT '最近开始执行时间',
  `last_finished_at` DATETIME DEFAULT NULL COMMENT '最近完成时间',
  `last_run_status` VARCHAR(32) DEFAULT NULL COMMENT '最近运行状态',
  `last_run_message` VARCHAR(512) DEFAULT NULL COMMENT '最近运行结果',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_payment_scheduler_task_key` (`task_key`),
  KEY `idx_payment_scheduler_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付调度任务配置表';
