-- ---------------------------------------------------------------------
-- V2.1: Git 自动发布 - 存量库补列
-- 新库由 V2.0 全量建表覆盖，本文件仅对已有库增量补列。
-- flyway 版本表保证只执行一次。
-- ---------------------------------------------------------------------

ALTER TABLE ms_server_project
    ADD COLUMN git_url        VARCHAR(512) NULL                    COMMENT 'Git仓库地址' AFTER nginx_dir,
    ADD COLUMN git_branch     VARCHAR(128) NULL DEFAULT 'main'     COMMENT 'Git分支' AFTER git_url,
    ADD COLUMN git_auth_type  VARCHAR(16)  NULL DEFAULT 'none'     COMMENT 'Git认证: none/token' AFTER git_branch,
    ADD COLUMN git_token      VARCHAR(512) NULL                    COMMENT 'Git访问令牌' AFTER git_auth_type,
    ADD COLUMN git_build_goals VARCHAR(255) NULL DEFAULT 'clean,package' COMMENT 'Maven构建目标' AFTER git_token,
    ADD COLUMN git_skip_tests TINYINT      NULL DEFAULT 1          COMMENT '是否跳过测试' AFTER git_build_goals,
    ADD COLUMN maven_home     VARCHAR(512) NULL                    COMMENT 'Maven安装路径(留空则自动检测/下载)' AFTER git_skip_tests,
    ADD COLUMN maven_version  VARCHAR(32)  NULL DEFAULT '3.9.9'    COMMENT 'Maven版本(自动下载时使用)' AFTER maven_home,
    ADD COLUMN upload_extract TINYINT      NULL DEFAULT 1          COMMENT '上传时是否自动解压(jar不解压;zip/tar解压;模型上传设0保留原文件)' AFTER maven_version,
    ADD COLUMN upload_target_dir VARCHAR(255) NULL                 COMMENT '上传目标目录(相对项目地址,如models/onnx;jar类留空则投放到主入口)' AFTER upload_extract,
    ADD COLUMN deploy_type    VARCHAR(16)  NULL DEFAULT 'main'     COMMENT '分离式部署类型: main=仅主包; lib=仅依赖; both=全部' AFTER upload_target_dir;
