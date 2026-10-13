-- =====================================================================================
-- V4.0.0.53 模型连通性测试：让「从未测试 / 测试失败 / 测试成功」三态可区分
--
-- 【为什么必须加列 —— 现有两列表达不了三态】
--   sys_ai_model 上现有的两列是：
--     sys_ai_model_last_latency   BIGINT NULL   最近一次连通性测试延迟(毫秒)
--     sys_ai_model_last_test_time DATETIME NULL 最近一次连通性测试时间
--
--   AiModelServiceImpl.testLatency 的控制流把三态压成了两态：
--     1) 从未测试        -> 两列均 NULL
--     2) 测试成功        -> 两列均有值（延迟 = 实测毫秒）
--     3) 测试失败        -> 两列**同样均有值**
--   原因在 catch 块（约 215~219 行）与 statusCode>=400 分支（约 202~208 行）：
--   两条路径都会 setModelLastLatency(latency) + setModelLastTestTime(now) + updateById，
--   随后才抛 RuntimeException。也就是说「API 返回 401」与「API 返回 200」在表里
--   **长得完全一样**。前端若按 `lastLatency != null` 判定「测过且正常」，
--   会把一批鉴权失败的模型显示成绿色 —— 这是比「不显示」更危险的错误。
--
--   根因是**语义混列**：latency 这个数字回答的是「这个模型有多快」，
--   而失败场景下根本不存在「快慢」，只存在「通不通」。把两者塞进同一列，
--   无论前端怎么写判断都要靠猜。
--
-- 【本脚本的解法：成功才写延迟，失败写状态】
--   列1 sys_ai_model_last_status VARCHAR(16)
--        收紧 last_latency 的语义为「最近一次**成功**的延迟」。失败路径不再写它，
--        于是 last_latency 有值 ⟺ 上一次测试成功，判定无需再依赖任何新列。
--   列2 sys_ai_model_last_error  VARCHAR(512)
--        失败原因（HTTP 状态 / 超时 / 连接被拒 / Key 缺失），供前端 tooltip 展示，
--        避免用户只看到一句「测试失败」还不知道该改 Key 还是改地址。
--
-- 【为什么只加 2 列，且不加索引】
--   - 不加 status 索引：列表页一期不做「按健康度排序 / 只看故障模型」筛选，
--     sys_ai_model 全表行数是「账号数 × 十余个模型」量级，全表扫描无压力。
--     真要加筛选时再加，届时是独立决策。
--   - 不加 last_test_time 索引：过期判定在 Service 层逐行做（行数同上），
--     走不了索引。
--
-- 【存量数据如何处理 —— 必须显式声明「历史一律未知」】
--   新增两列在存量库上全是 NULL，而**旧的 last_latency 有值**。若前端按
--   「last_latency 有值 → 上次成功」推断，会把历史上写进去的**失败**延迟
--   当成成功并染绿 —— 正是本次要修的 bug 换个地方复现。
--   故契约规定：status 为 NULL 时一律视为 UNKNOWN（不染色、不显示颜色），
--   **不得由 last_latency 反推**。存量行在下次手动测试前不显示健康度。
--   这也是本脚本不回填任何历史数据的原因：数据里没有足够信息正确回填。
--
-- 【为什么不追加到 V1.0__init_all_ai.sql 末尾】
--   V1.0 自 2026-09-30 起已记入 sys_database_version（success=true），
--   DataSourceFlyway 按**完整文件名**去重、命中即整份跳过。追加在末尾的语句
--   对存量库是永不执行的空操作；而这两列若写进 CREATE TABLE，全新库执行 V1.0
--   时又会在随后执行本脚本时报 1060 重复列。故只发独立版本脚本。
--
-- 【为什么全部用预处理语句（幂等）】
--   迁移器按分号把脚本拆成一条条执行，下面每组要拆成
--   SET / PREPARE / EXECUTE / DEALLOCATE 四条 —— 每条都是合法 SQL，
--   合起来能按 information_schema 判断目标是否已存在。
--   严禁使用 ALTER TABLE ... ADD COLUMN IF NOT EXISTS：那是 **MariaDB 语法，
--   MySQL 不支持**（实测 MySQL 8.4.9 首次执行即报 1064），且本仓不存在
--   做方言展开的 MysqlScriptConverter，SQL 原样透传给 MySQL。
--   本写法沿用 V4.0.0.47 的既有模式，实测连续执行两遍均成功。
--
-- 【执行顺序】
--   两组加列无相互依赖，顺序任意。
-- =====================================================================================

-- ---------- 1) 最近一次测试状态（NULL = 从未测试 / 历史未知，见上方说明） ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_model'
          AND COLUMN_NAME = 'sys_ai_model_last_status'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_model` ADD COLUMN `sys_ai_model_last_status` VARCHAR(16) NULL COMMENT ''最近一次连通性测试状态(success/failed)，NULL=未知'''));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 2) 最近一次失败原因（仅 status=failed 时有值） ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_model'
          AND COLUMN_NAME = 'sys_ai_model_last_error'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_model` ADD COLUMN `sys_ai_model_last_error` VARCHAR(512) NULL COMMENT ''最近一次连通性测试失败原因(错误种类: HTTP状态/超时/连接被拒/Key缺失)'''));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;