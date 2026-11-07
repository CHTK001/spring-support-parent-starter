-- =====================================================================
-- V2.14__reset_edu_collect_hanging_tasks.sql - 清理悬挂的采集任务
--
-- 背景：BE 进程在任务执行中被强杀（改流程 DSL、重打包都会停进程），
-- 任务的收尾逻辑没跑到，`collect_task_status` 永久停在 READY/RUNNING。
-- 引擎的并发闸门按「同一流程已有未结束任务」拦截，这些僵尸任务会让该流程
-- 永远无法再启动（报「流程 X 已有 1 个任务未结束」）。
--
-- 修法：进程重启时这些任务已经不可能再被推进，统一收敛为 FAILURE 并写明原因。
-- 幂等：只命中 READY/RUNNING，重复执行结果一致。
-- =====================================================================

UPDATE `collect_task`
SET `collect_task_status` = 'FAILURE',
    `collect_task_error` = '服务重启时该任务仍在执行中，已标记为失败（可重新发起采集）'
WHERE `collect_task_status` IN ('READY', 'RUNNING');
