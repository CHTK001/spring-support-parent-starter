-- =====================================================================
-- V2.4__add_template_source_code.sql - template_info 三方模板 ID
-- 通道: INIT 同步建表
-- 说明: 模板分「自定义 / 固定」两类：
--         - 自定义：本系统设计（source=hiprint），内容由本系统维护；
--         - 固定  ：三方同步进来（source=message/paper），内容归三方，
--                   本系统只读，改内容要回来源模块。
--       固定模板必须能拿到「三方那边的模板 ID」，才能按 ID 回查模板信息
--       （见 GET /api/template/{id}/detail，参数对齐 MessagePush 需要的形态）。
--       此前该值被临时写在 template_info_subtitle 上，语义混淆且副标题没地方放，
--       这里补成独立字段。
-- 增加字段:
--   template_info_source_code : 三方模板 ID（短信模板 sys_message_template_code 等）
--
-- 注意: 脚本按版本表记账，正常只跑一次；但版本表丢失 / 重建 / 手工重跑时
--       MySQL 的 ADD COLUMN 不支持 IF NOT EXISTS（那是 MariaDB 语法），
--       会直接报「列已存在」。因此这里用 information_schema 先判存在再执行，
--       让脚本本身可重复执行。
-- =====================================================================

-- ----------------------------
-- 字段
-- ----------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'template_info'
      AND COLUMN_NAME = 'template_info_source_code'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE template_info ADD COLUMN template_info_source_code VARCHAR(128) COMMENT ''三方模板 ID（来源侧模板编码，如短信模板 sys_message_template_code；hiprint 自建模板为空）''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'template_info'
      AND INDEX_NAME = 'idx_template_info_source_code'
);
SET @ddl := IF(@idx_exists = 0,
    'CREATE INDEX idx_template_info_source_code ON template_info(template_info_source_code)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------
-- 存量数据回填：此前「消息」模板的三方模板 ID 写在 subtitle 上，搬回新字段
-- 只处理 source='message'：组卷（paper）的 subtitle 是样式名，不是三方模板 ID，
-- 搬过来会变成错的编码。
-- 回填自身幂等：搬过一次后 subtitle 已被置空，再跑不会命中。
-- ----------------------------
UPDATE template_info
SET template_info_source_code = template_info_subtitle,
    template_info_subtitle = NULL
WHERE template_info_source = 'message'
  AND template_info_subtitle IS NOT NULL
  AND template_info_subtitle <> '';
