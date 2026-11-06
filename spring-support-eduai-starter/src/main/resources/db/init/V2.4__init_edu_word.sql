-- =====================================================================
-- V2.4__init_edu_word.sql - 词语库表 + 汉字通假字表
-- 通道: INIT（表结构，同步执行）
--
-- 为什么单独一个文件，而不是继续往 V2.1__init_all_eduai.sql 里加：
--   脚本执行记录按「文件名 + 版本号」记账（DataSourceScriptConfiguration#needsExecution），
--   V2.1 在本机库里已有成功记录，改它的内容不会重跑；只有**新文件名**才会被执行。
--
-- 为什么这里只有 CREATE TABLE、没有任何 ALTER：
--   本模块同时要跑在 MySQL/MariaDB（生产）与 H2（本地开发/CI）上。
--   H2 的 ScriptConverter 有两条硬规则会吃掉补列语句：
--     ① `SET @var := (SELECT ...)` 与 `PREPARE/EXECUTE/DEALLOCATE` 整条跳过
--        （DefaultScriptConverter 的「整语句跳过」分支）；
--     ② 非 MySQL 方言下，单列 `ALTER TABLE ... ADD COLUMN` 整条丢弃
--        （DefaultScriptConverter#rewriteAddColumnAlter：列已由建表语句提供，
--          重复 ADD COLUMN 在 H2 上是硬失败）。
--   而 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` 只被 MariaDB 11.8 拒绝（实测语句报错、
--   列不会建成）。也就是说：**没有任何一种补列写法能同时安全地跑在这两族库上**。
--   结论是把「新增字段」做成**新增表**——CREATE TABLE IF NOT EXISTS 两边都支持、都幂等。
--
-- 幂等：CREATE TABLE IF NOT EXISTS；重复执行不改任何已有数据。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 一、词语库 edu_word
--
-- 与 edu_hanzi（一个字一条）并列：词语是「两个字及以上」的语言单位，
-- 维护读音、释义、造句、近义/反义/同音词，供「词语库」页与趣味模块取材。
--
-- 唯一键是整词内容：同一词重复提交走更新（与 edu_hanzi 同口径）。
-- word_kind 用来区分语料类型，采集可以把「词语」「歇后语」写进同一张表，
-- 页面按 kind 过滤，不额外开表。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS edu_word (
    word_owner_user_id VARCHAR(64) COMMENT '归属用户 ID',
    KEY edu_word_owner (word_owner_user_id),
    word_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '词语ID',
    word_content VARCHAR(64) NOT NULL COMMENT '词语内容（整词，如：美丽）',
    word_pinyin VARCHAR(128) COMMENT '读音（带声调，多读音用逗号分隔，如：měi lì）',
    word_pinyin_plain VARCHAR(128) COMMENT '无声调读音（小写去分隔，多读音逗号分隔，检索用，如：meili）',
    word_meaning TEXT COMMENT '释义',
    word_example TEXT COMMENT '例句 / 造句范例',
    word_synonyms VARCHAR(255) COMMENT '近义词 / 相似词语（逗号分隔）',
    word_antonyms VARCHAR(255) COMMENT '反义词 / 反向词语（逗号分隔）',
    word_homophones VARCHAR(255) COMMENT '同音词（逗号分隔）',
    word_structure VARCHAR(32) COMMENT '结构 / 词性，如：偏正式、动宾式、名词',
    word_kind VARCHAR(20) DEFAULT 'WORD' COMMENT '语料类型：WORD 词语 / XIEHOUYU 歇后语 / PHRASE 短语',
    word_grade_level VARCHAR(20) COMMENT '适用学段（中文名）：小学/初中/高中/大学；NULL 表示不限',
    word_status TINYINT DEFAULT 1 COMMENT '状态：0 停用 / 1 启用',
    create_name VARCHAR(255) DEFAULT NULL COMMENT '创建人姓名',
    create_by INT DEFAULT NULL COMMENT '创建人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT NULL COMMENT '更新时间',
    update_name VARCHAR(255) DEFAULT NULL COMMENT '更新人姓名',
    update_by INT DEFAULT NULL COMMENT '更新人ID',
    UNIQUE KEY uk_edu_word_content (word_content),
    KEY edu_word_kind (word_kind),
    KEY edu_word_level (word_grade_level),
    KEY edu_word_plain (word_pinyin_plain)
);

-- ---------------------------------------------------------------------
-- 二、汉字通假字 edu_hanzi_tongjia
--
-- 通假字是「本字 + 借字」的对应（如：说 通 悦、被 通 披、知 通 智）。
-- 一个字可以有多个借字，故用一对多表而不是在 edu_hanzi 上加逗号串列：
--   · 一对一列要 ALTER（上面说过两族库没有共同的补列写法）；
--   · 逗号串列没法建索引，也没法反查「悦 是哪个字的通假」。
--
-- 检索口径：汉字库关键字同时匹配本字、读音、释义与**借字**
--   （搜「悦」要能找到「说」这条通假记录）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS edu_hanzi_tongjia (
    tongjia_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '通假字记录ID',
    tongjia_hanzi_id BIGINT NOT NULL COMMENT '本字ID（edu_hanzi.hanzi_id）',
    tongjia_char VARCHAR(8) NOT NULL COMMENT '借字（如：悦）',
    tongjia_note VARCHAR(200) DEFAULT NULL COMMENT '说明，如：说，通「悦」，愉快',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_edu_hanzi_tongjia (tongjia_hanzi_id, tongjia_char),
    KEY edu_hanzi_tongjia_char (tongjia_char)
);

-- ---------------------------------------------------------------------
-- 三、词语库的拼音检索列回填说明（不写 UPDATE）
--
-- word_pinyin_plain 由服务端在保存时派生（EduWordServiceImpl#saveWord），
-- 采集流程直接写 edu_word 时该列留空 —— 此时仍可按整词与释义检索，
-- 补上读音后自动获得「敲 meili 找 美丽」的能力。
-- 这里不写批量回填 UPDATE：新表本来是空的，没有存量数据需要修。
-- ---------------------------------------------------------------------

-- ---------------------------------------------------------------------
-- 四、汉字库读音口径（仅注释，无 DDL）
--
-- 本轮把「多音字」落成数据约定而非新列：edu_hanzi.hanzi_pinyin 允许存
-- **多读音逗号分隔**（如 xíng,háng）。理由：
--   · 汉字库的读音本来就该完整，只存一个读音是采集源的缺口，不是设计意图；
--   · 检索侧对 hanzi_pinyin 做 LIKE 天然命中任一读音，不需要额外列；
--   · 现有 3496 条单读音数据无需迁移（单读音就是只有一个元素的列表）。
-- 无声调检索（敲 zhong 命中 zhòng）在查询侧用 REPLACE 链归一化，
-- 见 EduHanziServiceImpl#pageQuery 与 support/util/Pinyins。
-- ---------------------------------------------------------------------
