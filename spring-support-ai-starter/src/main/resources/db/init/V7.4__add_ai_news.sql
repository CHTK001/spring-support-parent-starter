-- =============================================================================
-- V7.4 — AIHOT 资讯同步：sys_ai_news
-- 背景：模型管理页需要展示 AIHOT（aihot.news）精选的最新 AI 资讯。资讯由后端
--       从 https://aihot.news/feed.xml（RSS 2.0）同步落库，前端模型管理页右侧
--       的资讯面板直读本表；后端没有数据时面板整体不渲染。
-- 去重口径：link 唯一键（AIHOT 站内链接）；同一条资讯反复同步只更新，不重复插入。
-- 幂等纪律：CREATE TABLE IF NOT EXISTS，可反复执行。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_ai_news` (
    `sys_ai_news_id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `sys_ai_news_title`         VARCHAR(500) NOT NULL COMMENT '资讯标题',
    `sys_ai_news_link`          VARCHAR(512) NOT NULL COMMENT 'AIHOT 站内链接（唯一去重键）',
    `sys_ai_news_origin_url`    VARCHAR(512) NULL COMMENT '原文链接（描述里解析出的站外地址，空=无原文）',
    `sys_ai_news_summary`       TEXT         NULL COMMENT '摘要（纯文本，去除 HTML 标签）',
    `sys_ai_news_category`      VARCHAR(64)  NULL COMMENT '分类（RSS category，如「教程」「行业」）',
    `sys_ai_news_source`        VARCHAR(32)  NOT NULL DEFAULT 'aihot' COMMENT '来源标识',
    `sys_ai_news_published_at`  BIGINT       NULL COMMENT '发布时间（毫秒，取 RSS pubDate）',
    `create_time`               BIGINT       NULL COMMENT '创建时间（毫秒）',
    `update_time`               BIGINT       NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_ai_news_id`),
    UNIQUE KEY `uk_ai_news_link` (`sys_ai_news_link`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AIHOT 精选资讯（RSS 同步，按 link 去重）';
