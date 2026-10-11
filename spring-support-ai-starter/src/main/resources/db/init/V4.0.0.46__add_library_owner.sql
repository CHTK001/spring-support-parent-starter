-- =============================================================================
-- V4.0.0.46 人脸 / 图像底库：补归属账号 + 向量关联键按账号复合
-- =============================================================================
--
-- 【背景】
--   sys_ai_face_library / sys_ai_image_library 两张表自 V1.0 建表起就是「空壳」：
--   没有任何实体类与 Mapper，运行期一行数据都没有。底库的元数据全部躺在磁盘快照
--   index.json 里（FaceStores / ImageSearchStore），向量躺在 jvector 里。
--   本次把元数据落库，用 *_vector_id 关联向量，使新增 / 修改 / 删除三处可联动。
--
-- 【为什么必须先加归属列】
--   两张表原本**没有账号列**，而 FaceStores / ImageSearchStores 一直是严格按账号
--   分目录的（accountDir(account)）。直接把数据写进这两张表 = 所有账号共用一个池子，
--   是安全倒退而不是持久化。归属列是这一步的前提，不是可选项。
--
-- 【唯一键必须一并改成复合】
--   原唯一键是 vector_id 全局唯一。人脸 id 由前端生成，格式为
--   face-${Date.now()}-${随机5位}，即时间戳 + 36^5 空间；两个账号在同一毫秒
--   撞 id 概率虽低但非零，全局唯一键会让其中一方登记直接失败。
--   改成 (user_id, vector_id) 复合唯一后，各账号在自己的命名空间内登记。
--
-- 【为什么全部改用「预处理语句」，而不是 IF NOT EXISTS】
--   ALTER TABLE ... ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS /
--   DROP INDEX IF EXISTS 这套写法是 **MariaDB 语法，MySQL 不支持**。
--   本机 MySQL 8.4.9 实测：带 IF NOT EXISTS 的 ADD COLUMN 首次执行即报 1064 语法错。
--   （本模块早前的 V1.1 正是这么写的，它在 MySQL 上从未成功执行过。）
--
--   而迁移器会按分号把脚本拆成一条条语句执行，所以下面这套写法要拆成
--   SET / PREPARE / EXECUTE / DEALLOCATE 四条 —— 拆开后每条都是合法 SQL，
--   组合起来又能按 information_schema 判断「目标是否已存在」，达到幂等。
--   实测连续执行两遍均成功，列不会重复创建。
--
-- 【版本号为何用 4.0.0.46 而不是 1.4】
--   sys_database_version 的主键是 version，且该表被**所有模块共用**。
--   本库已存在 1.1 / 1.2 / 1.3 / 1.4（分别属于 usage 索引、model pipeline、
--   model alias、model latency 等其它模块的脚本），本模块若再用 1.4 会直接
--   DuplicateKeyException，连执行记录都写不进去，脚本每次启动都会重跑。
--   该表里本模块近期的脚本已用 4.0.0.43 / 44 / 45，本脚本顺延为 4.0.0.46。
--
-- 【执行顺序不可调换】
--   加列 → 建归属索引 → 删旧全局唯一键 → 建复合唯一键。
--   复合键依赖新列；删旧键必须晚于加列、早于建新键，否则中途失败会留下
--   vector_id 全局唯一约束，跨账号登记依旧撞键。
--
-- 【存量数据】
--   两张表当前均为空，无需回填。MySQL 唯一索引中 NULL 互不相等，
--   故无主历史行之间不受约束，符合预期。
-- =============================================================================

-- ---------- 1) 人脸底库归属列 ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_face_library'
          AND COLUMN_NAME = 'sys_ai_face_library_user_id'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_face_library` ADD COLUMN `sys_ai_face_library_user_id` VARCHAR(64) NULL'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 2) 图像底库归属列 ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_image_library'
          AND COLUMN_NAME = 'sys_ai_image_library_user_id'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_image_library` ADD COLUMN `sys_ai_image_library_user_id` VARCHAR(64) NULL'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 3) 归属列普通索引（供按账号过滤，全库扫描必须走它） ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_face_library'
          AND INDEX_NAME = 'sys_ai_face_library_user'),
        'SELECT 1',
        'CREATE INDEX `sys_ai_face_library_user` ON `sys_ai_face_library` (`sys_ai_face_library_user_id`)'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_image_library'
          AND INDEX_NAME = 'sys_ai_image_library_user'),
        'SELECT 1',
        'CREATE INDEX `sys_ai_image_library_user` ON `sys_ai_image_library` (`sys_ai_image_library_user_id`)'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 4) 去掉 vector_id 全局唯一键 ----------
-- 两种命名都要删：V1.0__init_all_ai.sql 里写的是 sys_ai_face_library_face_vector_id，
-- 而存量库里实际存在的是 uk_face_vector_id（该库并非由当前 V1.0 脚本创建）。
-- 只删一个名字的话，另一套库上旧的全局唯一键会留下，跨账号登记照样撞键 ——
-- 而这种失败只在特定库上出现，极难排查。
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_face_library'
          AND INDEX_NAME = 'sys_ai_face_library_face_vector_id'),
        'ALTER TABLE `sys_ai_face_library` DROP INDEX `sys_ai_face_library_face_vector_id`',
        'SELECT 1'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_face_library'
          AND INDEX_NAME = 'uk_face_vector_id'),
        'ALTER TABLE `sys_ai_face_library` DROP INDEX `uk_face_vector_id`',
        'SELECT 1'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_image_library'
          AND INDEX_NAME = 'sys_ai_image_library_image_vector_id'),
        'ALTER TABLE `sys_ai_image_library` DROP INDEX `sys_ai_image_library_image_vector_id`',
        'SELECT 1'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_image_library'
          AND INDEX_NAME = 'uk_image_vector_id'),
        'ALTER TABLE `sys_ai_image_library` DROP INDEX `uk_image_vector_id`',
        'SELECT 1'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 5) 建立（归属, 向量标识）复合唯一键 ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_face_library'
          AND INDEX_NAME = 'sys_ai_face_library_owner_vector'),
        'SELECT 1',
        'CREATE UNIQUE INDEX `sys_ai_face_library_owner_vector` ON `sys_ai_face_library` (`sys_ai_face_library_user_id`, `sys_ai_face_library_vector_id`)'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_image_library'
          AND INDEX_NAME = 'sys_ai_image_library_owner_vector'),
        'SELECT 1',
        'CREATE UNIQUE INDEX `sys_ai_image_library_owner_vector` ON `sys_ai_image_library` (`sys_ai_image_library_user_id`, `sys_ai_image_library_vector_id`)'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;
