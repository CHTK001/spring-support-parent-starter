-- =====================================================================
-- V2.1__add_template_source.sql - template_info 来源扩展
-- 通道: INIT 同步建表
-- 说明: 为 template_info 增加“来源”维度，使得消息模板/组卷模板等非
--       绘制模板也能进入统一模板库（pages/template）展示，
--       而不必让复写（hiprint）设计器接管全部模板类型。
-- 增加字段:
--   template_info_source  : 来源  hiprint(绘制)/message(消息模板)/paper(组卷模板)
--   template_info_source_id:来源行 ID（如短信模板在 sys_message_template 的 ID）
--   template_info_provider:来源提供方（如消息提供商 ID）
-- =====================================================================

-- ----------------------------
-- 字段
-- ----------------------------
ALTER TABLE template_info
    ADD COLUMN template_info_source VARCHAR(32)                                               NOT NULL DEFAULT 'hiprint'
        COMMENT '来源：hiprint/手机/组卷';

ALTER TABLE template_info
    ADD COLUMN template_info_source_id VARCHAR(64)                                               COMMENT '来源行 ID（如短信模板在 sys_message_template 的 ID，v2.1 起与 template_info_id 对应）';

ALTER TABLE template_info
    ADD COLUMN template_info_provider VARCHAR(64)                                                COMMENT '来源提供方（如消息提供商 ID，v2.1 起与 template_info_id 对应）';

CREATE INDEX idx_template_info_source ON template_info(template_info_source);
CREATE INDEX idx_template_info_source_id ON template_info(template_info_source_id);
CREATE INDEX idx_template_info_provider ON template_info(template_info_provider);

-- ----------------------------
-- 原唯一名称约束：改为（名称 + 来源）唯一，避免不同来源模板同名
-- ----------------------------
DROP INDEX IF EXISTS uk_template_info_name ON template_info;
CREATE UNIQUE INDEX uk_template_info_name_source
    ON template_info (template_info_name, template_info_source);
