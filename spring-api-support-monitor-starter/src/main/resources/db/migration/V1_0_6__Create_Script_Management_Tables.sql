-- 脚本管理表
CREATE TABLE IF NOT EXISTS monitor_sys_gen_script (
    monitor_sys_gen_script_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '脚本ID',
    monitor_sys_gen_script_name VARCHAR(255) NOT NULL COMMENT '脚本名称',
    monitor_sys_gen_script_type VARCHAR(50) NOT NULL COMMENT '脚本类型(shell,powershell,python,javascript,batch等)',
    monitor_sys_gen_script_description TEXT COMMENT '脚本描述',
    monitor_sys_gen_script_content LONGTEXT NOT NULL COMMENT '脚本内容',
    monitor_sys_gen_script_parameters TEXT COMMENT '默认参数(JSON格式)',
    monitor_sys_gen_script_timeout INT DEFAULT 300 COMMENT '执行超时时间(秒)',
    monitor_sys_gen_script_status TINYINT DEFAULT 1 COMMENT '脚本状态(0:禁用,1:启用)',
    monitor_sys_gen_script_tags VARCHAR(500) COMMENT '脚本标签(逗号分隔)',
    monitor_sys_gen_script_category VARCHAR(100) COMMENT '脚本分类',
    monitor_sys_gen_script_version VARCHAR(50) DEFAULT '1.0.0' COMMENT '脚本版本',
    monitor_sys_gen_script_author VARCHAR(100) COMMENT '脚本作者',
    monitor_sys_gen_script_last_execute_time DATETIME COMMENT '最后执行时间',
    monitor_sys_gen_script_execute_count INT DEFAULT 0 COMMENT '执行次数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by VARCHAR(100) COMMENT '创建人',
    update_by VARCHAR(100) COMMENT '更新人',
    
    INDEX idx_script_name (monitor_sys_gen_script_name),
    INDEX idx_script_type (monitor_sys_gen_script_type),
    INDEX idx_script_status (monitor_sys_gen_script_status),
    INDEX idx_script_category (monitor_sys_gen_script_category),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='脚本管理表';

-- 脚本执行历史表
CREATE TABLE IF NOT EXISTS monitor_sys_gen_script_execution (
    monitor_sys_gen_script_execution_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '执行记录ID',
    monitor_sys_gen_script_id INT NOT NULL COMMENT '脚本ID',
    monitor_sys_gen_script_execution_parameters TEXT COMMENT '执行参数(JSON格式)',
    monitor_sys_gen_script_execution_status VARCHAR(20) NOT NULL COMMENT '执行状态(RUNNING,SUCCESS,FAILED,TIMEOUT,CANCELLED)',
    monitor_sys_gen_script_execution_start_time DATETIME NOT NULL COMMENT '开始执行时间',
    monitor_sys_gen_script_execution_end_time DATETIME COMMENT '结束执行时间',
    monitor_sys_gen_script_execution_duration BIGINT COMMENT '执行耗时(毫秒)',
    monitor_sys_gen_script_execution_exit_code INT COMMENT '退出码',
    monitor_sys_gen_script_execution_stdout LONGTEXT COMMENT '标准输出',
    monitor_sys_gen_script_execution_stderr LONGTEXT COMMENT '错误输出',
    monitor_sys_gen_script_execution_error_message TEXT COMMENT '错误信息',
    monitor_sys_gen_script_execution_process_id BIGINT COMMENT '进程ID',
    monitor_sys_gen_script_execution_server_id INT COMMENT '执行服务器ID',
    monitor_sys_gen_script_execution_trigger_type VARCHAR(20) DEFAULT 'MANUAL' COMMENT '触发类型(MANUAL:手动,SCHEDULED:定时,API:接口)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    create_by VARCHAR(100) COMMENT '执行人',
    
    INDEX idx_script_id (monitor_sys_gen_script_id),
    INDEX idx_execution_status (monitor_sys_gen_script_execution_status),
    INDEX idx_start_time (monitor_sys_gen_script_execution_start_time),
    INDEX idx_server_id (monitor_sys_gen_script_execution_server_id),
    FOREIGN KEY (monitor_sys_gen_script_id) REFERENCES monitor_sys_gen_script(monitor_sys_gen_script_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='脚本执行历史表';

-- 脚本分类表
CREATE TABLE IF NOT EXISTS monitor_sys_gen_script_category (
    monitor_sys_gen_script_category_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '分类ID',
    monitor_sys_gen_script_category_name VARCHAR(100) NOT NULL COMMENT '分类名称',
    monitor_sys_gen_script_category_description TEXT COMMENT '分类描述',
    monitor_sys_gen_script_category_parent_id INT COMMENT '父分类ID',
    monitor_sys_gen_script_category_sort INT DEFAULT 0 COMMENT '排序',
    monitor_sys_gen_script_category_status TINYINT DEFAULT 1 COMMENT '状态(0:禁用,1:启用)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by VARCHAR(100) COMMENT '创建人',
    update_by VARCHAR(100) COMMENT '更新人',
    
    UNIQUE KEY uk_category_name (monitor_sys_gen_script_category_name),
    INDEX idx_parent_id (monitor_sys_gen_script_category_parent_id),
    INDEX idx_sort (monitor_sys_gen_script_category_sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='脚本分类表';

-- 插入默认分类
INSERT INTO monitor_sys_gen_script_category (monitor_sys_gen_script_category_name, monitor_sys_gen_script_category_description, monitor_sys_gen_script_category_sort) VALUES
('系统管理', '系统管理相关脚本', 1),
('监控检查', '系统监控和健康检查脚本', 2),
('数据处理', '数据处理和分析脚本', 3),
('自动化运维', '自动化运维脚本', 4),
('其他', '其他类型脚本', 99);

-- 插入示例脚本
INSERT INTO monitor_sys_gen_script (
    monitor_sys_gen_script_name,
    monitor_sys_gen_script_type,
    monitor_sys_gen_script_description,
    monitor_sys_gen_script_content,
    monitor_sys_gen_script_category,
    monitor_sys_gen_script_author
) VALUES
('系统信息查看', 'shell', '查看系统基本信息', '#!/bin/bash\necho "=== 系统信息 ==="\nuname -a\necho "=== 内存使用 ==="\nfree -h\necho "=== 磁盘使用 ==="\ndf -h', '系统管理', 'system'),
('进程监控', 'shell', '监控系统进程状态', '#!/bin/bash\necho "=== TOP 10 CPU进程 ==="\nps aux --sort=-%cpu | head -11\necho "=== TOP 10 内存进程 ==="\nps aux --sort=-%mem | head -11', '监控检查', 'system'),
('Hello World', 'python', 'Python示例脚本', 'print("Hello, World!")\nprint("当前时间:", __import__("datetime").datetime.now())', '其他', 'system');
