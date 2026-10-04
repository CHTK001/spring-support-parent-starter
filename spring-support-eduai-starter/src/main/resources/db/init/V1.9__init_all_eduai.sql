-- ===========================================
-- 汉字基础数据表（字 / 读音 / 笔画数 / 部首 / 结构 / 笔顺 / 释义 / 字画 / 适用学段）
--
-- 用于「基础数据维护」：老师维护每个汉字的读音（拼音）、笔顺（顺序）、字画（图片或字形图）
-- 与释义，供识字/写字类模块消费。
-- ===========================================

CREATE TABLE IF NOT EXISTS edu_hanzi (
    hanzi_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_hanzi_owner (hanzi_owner_user_id),
    hanzi_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '汉字ID',
    hanzi_char VARCHAR(8) NOT NULL COMMENT '汉字（单个字）',
    hanzi_pinyin VARCHAR(64) COMMENT '读音（拼音，可带声调）',
    hanzi_strokes INT COMMENT '笔画数',
    hanzi_radical VARCHAR(8) COMMENT '部首',
    hanzi_structure VARCHAR(16) COMMENT '结构：独体/上下/左右/半包围/全包围',
    hanzi_stroke_order VARCHAR(255) COMMENT '笔顺（按书写顺序排列的笔画名，如 一丨丿丶）',
    hanzi_meaning VARCHAR(500) COMMENT '释义 / 常用组词',
    hanzi_image VARCHAR(255) COMMENT '字画（图片路径或字形图，可空）',
    hanzi_grade_level VARCHAR(20) COMMENT '适用学段（中文名）：小学/初中/高中；NULL 表示不限',
    hanzi_status TINYINT DEFAULT 1 COMMENT '状态：0 停用 / 1 启用',
    create_name VARCHAR(255) DEFAULT NULL COMMENT '创建人姓名',
    create_by INT DEFAULT NULL COMMENT '创建人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT NULL COMMENT '更新时间',
    update_name VARCHAR(255) DEFAULT NULL COMMENT '更新人姓名',
    update_by INT DEFAULT NULL COMMENT '更新人ID',
    UNIQUE KEY uk_edu_hanzi_char (hanzi_char),
    KEY edu_hanzi_level (hanzi_grade_level)
);
