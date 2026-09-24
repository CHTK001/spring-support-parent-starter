-- V1.16 model metrics: Artificial Analysis benchmark score columns
-- generated from aa_fields.json; V1.0 baseline kept in sync

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_gpqa_score` DOUBLE NULL COMMENT 'GPQA 得分（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_aime25_score` DOUBLE NULL COMMENT 'AIME 2025 数学基准得分（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_livecodebench_score` DOUBLE NULL COMMENT 'LiveCodeBench 编程基准得分（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_scicode_score` DOUBLE NULL COMMENT 'SciCode 科学编程基准得分（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_ifbench_score` DOUBLE NULL COMMENT 'IFBench 指令遵循基准得分（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_critpt_score` DOUBLE NULL COMMENT 'CritPt 物理基准得分（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_mmmu_pro_score` DOUBLE NULL COMMENT 'MMMU-Pro 多模态基准得分（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_tau2_score` DOUBLE NULL COMMENT 'Tau2-Bench 工具调用基准得分（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_tau_banking_score` DOUBLE NULL COMMENT 'Tau-Banking 银行场景基准得分（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_lcr_score` DOUBLE NULL COMMENT 'LCR 长上下文推理得分（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_terminalbench_hard_score` DOUBLE NULL COMMENT 'Terminal-Bench Hard 得分（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_terminal_bench21_score` DOUBLE NULL COMMENT 'Terminal-Bench 2.1 得分（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_terminal_bench40_score` DOUBLE NULL COMMENT 'Terminal-Bench 4.0 得分（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_automation_bench_score` DOUBLE NULL COMMENT 'AutomationBench 自动化基准得分（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_gdpval_normalized_score` DOUBLE NULL COMMENT 'GDPval 归一化得分（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_briefcase_score` DOUBLE NULL COMMENT 'Briefcase 专业任务基准得分（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_harvey_lab_score` DOUBLE NULL COMMENT 'Harvey Lab 法律任务基准得分（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_apex_agents_score` DOUBLE NULL COMMENT 'Apex Agents 基准得分（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_itbench_sre_score` DOUBLE NULL COMMENT 'ITBench SRE 运维基准得分（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_omniscience_accuracy` DOUBLE NULL COMMENT '全知指数-准确率（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_omniscience_non_hallucination` DOUBLE NULL COMMENT '全知指数-非幻觉率（0~1）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_elo` DOUBLE NULL COMMENT 'Artificial Analysis Elo 评分';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_cost_per_task` DOUBLE NULL COMMENT '单任务平均成本（USD）';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_json_mode` TINYINT(1) NULL COMMENT '是否支持 JSON 输出模式';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_openai_compatible` TINYINT(1) NULL COMMENT '是否兼容 OpenAI 协议';

ALTER TABLE `sys_ai_model_metrics`
    ADD COLUMN `sys_ai_model_metrics_intelligence_index_estimated` TINYINT(1) NULL COMMENT '智能指数是否为估算值';

