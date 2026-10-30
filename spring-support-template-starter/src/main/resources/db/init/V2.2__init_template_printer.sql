-- =====================================================================
-- V2.2__init_template_printer.sql - 打印机配置表
-- 通道: INIT 同步建表
-- 说明: 打印机配置由内存缓存改为落库，应用重启后默认打印机等参数不丢。
--
-- 约束: 脚本会被重复执行，一律使用 CREATE TABLE IF NOT EXISTS，
--       索引随建表语句一起创建，避免二次执行报错。
-- =====================================================================

CREATE TABLE IF NOT EXISTS template_printer (

    template_printer_id VARCHAR(32) PRIMARY KEY COMMENT '打印机主键 ID',
    template_printer_name VARCHAR(100) NOT NULL COMMENT '打印机设备名',
    template_printer_address VARCHAR(255) COMMENT '连接地址（IP / 主机名）',
    template_printer_port INT COMMENT '连接端口',
    template_printer_token VARCHAR(255) COMMENT '连接 Token',
    template_printer_username VARCHAR(64) COMMENT '连接用户',
    template_printer_type VARCHAR(64) COMMENT '打印机型号',
    template_printer_remark VARCHAR(500) COMMENT '备注',
    template_printer_default TINYINT DEFAULT 0 COMMENT '是否默认：0 否、1 是',
    template_printer_created_at DATETIME COMMENT '创建时间',
    template_printer_updated_at DATETIME COMMENT '更新时间',

    UNIQUE KEY `uk_template_printer_name` (`template_printer_name`),
    KEY `idx_template_printer_default` (`template_printer_default`)

) COMMENT '打印机配置';
