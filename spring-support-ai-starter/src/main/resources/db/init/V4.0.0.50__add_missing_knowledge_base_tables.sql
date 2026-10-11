-- =====================================================================================
-- V4.0.0.50 补建存量库缺失的 RAG 知识库两张表
--
-- 【为什么需要】
--   sys_ai_knowledge_base 与 sys_ai_knowledge_graph_ref 的建表语句写在
--   V1.0__init_all_ai.sql 里，但 V1.0 自 2026-09-30 起已记入 sys_database_version
--   （success=true），迁移器按**完整文件名**去重、命中即整份跳过。
--   于是：这两张表是在 V1.0 之后才被加进脚本的，所有在此之前建库的存量环境
--   ——包括本机——永远等不到它们，而全新库执行 V1.0 时又会正常建出来。
--   结果是同一份代码在不同库上表现不同：全新库有表，存量库启动即报
--   `Table 'ai_test.sys_ai_knowledge_base' doesn't exist`，
--   知识库列表接口 500，且因为只是启动期的一次查询失败，不会有人第一时间发现。
--
--   这正是「往已发布的 V1.0 末尾追加 DDL」这种做法的固有代价：
--   它对新库有效，对老库是永不执行的空操作。补历史缺口只能靠独立版本脚本。
--
-- 【缺口是怎么定位的】
--   解析 V1.0__init_all_ai.sql 中全部 CREATE TABLE（剔除注释行后匹配，避免把
--   注释里的表名算进去），共 64 张，与本库实际存在的表逐一比对，
--   缺的正好只有这两张，且都属于知识库家族。已存在的 62 张不动。
--
-- 【幂等】
--   两张表都用 CREATE TABLE IF NOT EXISTS，重复执行不会报错也不会改动已有表。
--   本脚本不含 INSERT，不写入任何种子数据，因此对已有数据零影响。
--
-- 【与 sys_ai_knowledge_library 的区别】
--   knowledge_library 是「图谱/模型库」层的资源目录，knowledge_base 是
--   「RAG 知识库」，带 root_path、托管标记、采集来源配置（kind=web 时 source 非空）。
--   两者不是同一层实体。graph_ref 则是图谱实体/关系与 RAG 知识点的关联表，
--   三者互相引用，故必须一起补齐，不能只补一张。
-- =====================================================================================

-- ---------- 1) RAG 知识库元数据 ----------
CREATE TABLE IF NOT EXISTS `sys_ai_knowledge_base` (
    `sys_ai_knowledge_base_id`           VARCHAR(64)   NOT NULL COMMENT '主键（UUID，对应内存记录的 knowledge_base_id）',
    `sys_ai_knowledge_base_name`         VARCHAR(255)  NOT NULL COMMENT '知识库名称',
    `sys_ai_knowledge_base_description`  TEXT          DEFAULT NULL COMMENT '知识库描述',
    `sys_ai_knowledge_base_root_path`    VARCHAR(1024) DEFAULT NULL COMMENT '根目录；托管库为 <uploadDir>/kb-<id>，外部库为用户指定目录',
    `sys_ai_knowledge_base_kind`         VARCHAR(32)   NOT NULL DEFAULT 'local' COMMENT '类型：local 外部本地目录 / web 网址采集',
    `sys_ai_knowledge_base_managed`      TINYINT       NOT NULL DEFAULT 0 COMMENT '目录是否由本服务托管（托管库删除时可一并清理磁盘）',
    `sys_ai_knowledge_base_is_public`    TINYINT       NOT NULL DEFAULT 0 COMMENT '是否公开可读（1 公开，0 仅归属人与管理员）',
    `sys_ai_knowledge_base_tags`         VARCHAR(1024) DEFAULT NULL COMMENT '标签，JSON 数组文本（如 ["三国","验收"]）',
    `sys_ai_knowledge_base_source`       TEXT          DEFAULT NULL COMMENT '采集来源配置（JSON，{mode,entries[]}）；kind=web 时非空',
    `sys_ai_knowledge_base_file_count`   BIGINT        NOT NULL DEFAULT 0 COMMENT '文件数（缓存值，读取时按需重算）',
    `sys_ai_knowledge_base_total_size`   BIGINT        NOT NULL DEFAULT 0 COMMENT '目录总字节数（缓存值，读取时按需重算）',
    `sys_ai_knowledge_base_chunk_max_size`     INT NOT NULL DEFAULT 1000 COMMENT '分片最大长度（字符）',
    `sys_ai_knowledge_base_chunk_overlap_size` INT NOT NULL DEFAULT 100  COMMENT '分片重叠长度（字符）',
    `sys_ai_knowledge_base_chunk_min_size`     INT NOT NULL DEFAULT 50   COMMENT '分片最小长度（字符）',
    `sys_ai_knowledge_base_user_id`      VARCHAR(64)   DEFAULT NULL COMMENT '归属用户ID（写入者，非空表示仅本人与管理员可见可写）',
    `create_name`  VARCHAR(255) DEFAULT NULL COMMENT '创建人',
    `create_by`    INT          DEFAULT NULL COMMENT '创建人ID',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_name`  VARCHAR(255) DEFAULT NULL COMMENT '更新人',
    `update_by`    INT          DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`sys_ai_knowledge_base_id`),
    KEY `sys_ai_knowledge_base_user` (`sys_ai_knowledge_base_user_id`),
    KEY `sys_ai_knowledge_base_kind` (`sys_ai_knowledge_base_kind`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG知识库元数据（目录内容由 rag-uploads/kb-<id> 承载）';

-- ---------- 2) 图谱实体/关系 与 RAG 知识点的关联 ----------
CREATE TABLE IF NOT EXISTS `sys_ai_knowledge_graph_ref` (
  `sys_ai_knowledge_graph_ref_id`                 INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sys_ai_knowledge_graph_ref_uid`                VARCHAR(64)  NOT NULL COMMENT '关联记录UID(UUID)',
  `sys_ai_knowledge_graph_library_id`             INT          NOT NULL DEFAULT 0 COMMENT '图谱库ID(对应sys_ai_knowledge_library)',
  `sys_ai_knowledge_graph_ref_target_type`        VARCHAR(32)  NOT NULL COMMENT '关联目标类型(entity 实体/relation 关系)',
  `sys_ai_knowledge_graph_ref_target_uid`         VARCHAR(64)  NOT NULL COMMENT '实体UID或关系UID',
  `sys_ai_knowledge_graph_ref_knowledge_base_id`  VARCHAR(64)  NOT NULL COMMENT 'RAG知识库ID(knowledge_base_id)',
  `sys_ai_knowledge_graph_ref_doc_path`           VARCHAR(255) NOT NULL DEFAULT '' COMMENT '知识点在知识库内的相对路径',
  `sys_ai_knowledge_graph_ref_status`             INT          NOT NULL DEFAULT 1 COMMENT '状态:1有效0失效',
  `create_name`  VARCHAR(255) DEFAULT NULL COMMENT '创建人',
  `create_by`    INT          DEFAULT NULL COMMENT '创建人ID',
  `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name`  VARCHAR(255) DEFAULT NULL COMMENT '更新人',
  `update_by`    INT          DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`sys_ai_knowledge_graph_ref_id`),
  UNIQUE KEY `sys_ai_knowledge_graph_ref_uid` (`sys_ai_knowledge_graph_ref_uid`),
  UNIQUE KEY `sys_ai_knowledge_graph_ref_binding` (
    `sys_ai_knowledge_graph_ref_target_type`,
    `sys_ai_knowledge_graph_ref_target_uid`,
    `sys_ai_knowledge_graph_ref_knowledge_base_id`,
    `sys_ai_knowledge_graph_ref_doc_path`
  ),
  KEY `sys_ai_knowledge_graph_ref_kb` (`sys_ai_knowledge_graph_ref_knowledge_base_id`, `sys_ai_knowledge_graph_ref_doc_path`),
  KEY `sys_ai_knowledge_graph_ref_target` (`sys_ai_knowledge_graph_ref_target_type`, `sys_ai_knowledge_graph_ref_target_uid`),
  KEY `sys_ai_knowledge_graph_ref_library` (`sys_ai_knowledge_graph_library_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI知识图谱-实体/关系与知识点的关联';