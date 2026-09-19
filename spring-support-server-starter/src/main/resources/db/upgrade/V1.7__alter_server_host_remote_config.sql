-- =====================================================================
-- 存量老库手工升级脚本（db/upgrade 不在自动扫描路径内，不会随启动执行）
-- 新库请直接执行 db/init/V1.0__init_all_server.sql，下列列已并入其中。
-- 注意：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，本文件用裸 ADD COLUMN，
--       仅在目标库确实缺这些列时执行。
-- =====================================================================

-- 资产主表补列：主机级远控配置
-- 模块: spring-support-server-starter
-- 背景: 远控弹窗需要按主机配置协议(SSH/RDP/VNC)、端口与连接模式
--       (GUACD 中继 / DIRECT 原生直连)，纯 VNC 主机还需单独保存
--       VNC 口令，但建表脚本从未包含这四列，前端编辑后无法落库。
--       ms_server_host_vnc_password 存放 AES 密文（CredentialCipher），
--       仅主机详情接口解密回显。
-- 数据库: MySQL 8.x / H2 (MySQL 模式)
-- 幂等: ADD COLUMN，重复执行安全（每列单独一条语句）
-- ============================================================

ALTER TABLE `ms_server_host`
    ADD COLUMN `ms_server_host_remote_protocol` VARCHAR(16) DEFAULT NULL
        COMMENT '远控协议: SSH/RDP/VNC, 空则按操作系统类型推断';

ALTER TABLE `ms_server_host`
    ADD COLUMN `ms_server_host_remote_port` INT DEFAULT NULL
        COMMENT '远控端口, 空则取协议默认端口(SSH 22/RDP 3389/VNC 5900)';

ALTER TABLE `ms_server_host`
    ADD COLUMN `ms_server_host_remote_mode` VARCHAR(16) DEFAULT 'GUACD'
        COMMENT '远控模式: GUACD 经 guacd 中继 / DIRECT 平台原生客户端直连';

ALTER TABLE `ms_server_host`
    ADD COLUMN `ms_server_host_vnc_password` VARCHAR(512) DEFAULT NULL
        COMMENT 'VNC 口令(AES 密文)';
