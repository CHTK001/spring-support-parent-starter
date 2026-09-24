-- =====================================================================
-- V1.15 模型指标库：补充 HLE（Humanity's Last Exam，人类最后考试）
-- 与全知指数（Omniscience Index）两列
-- 数据取自 Artificial Analysis 的 hle / omniscience 字段，
-- 供指标库展示、手动修改并锁定。
-- 幂等：依赖 spring.sql.init continue-on-error，已存在列时重复执行仅产生可忽略错误
-- =====================================================================

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_hle_score` DOUBLE NULL COMMENT 'HLE 人类最后考试得分（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_omniscience_index` DOUBLE NULL COMMENT '全知指数（知识准确-幻觉，可为负）';
