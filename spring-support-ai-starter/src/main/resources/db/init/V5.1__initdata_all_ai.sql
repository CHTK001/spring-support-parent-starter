-- =====================================================================
-- V5.1__initdata_all_ai.sql - spring-support-ai-starter 全量种子
-- 通道: INITDATA 虚拟线程异步；表结构就位后执行。
-- 说明: 本文件是该模块唯一的种子数据入口。此前分散的
--   V1.0__initdata_all_ai.sql（空占位）与 V4.0.0.49 / V4.0.0.55 的种子
--   已在本次收敛中并入本文件并删除；V5.6 的三国 NPC 属性种子与
--   V5.8 / V5.9 的存量回填 UPDATE 在后续收敛中并入（原增量脚本已删）。
-- 约束: 全部语句必须幂等（INSERT IGNORE / 条件 UPDATE），可反复执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 内置提示词形态（原 V4.0.0.49）
-- build 恒存、不可删除；前端切换器按 form_sort 排。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_ai_prompt_form`
    (`user_id`, `form_key`, `form_name`, `form_icon`, `form_description`, `sys_ai_prompt_form_builtin`, `form_sort`, `sys_ai_prompt_form_status`, `sys_ai_prompt_form_delete`)
VALUES
    ('admin', 'build', '构建', 'flash',      '直接执行，边做边确认，适合日常任务',            1, 1, 1, 0),
    ('admin', 'plan',  '规划', 'clipboard',  '先出方案再动手，批准后才执行',                1, 2, 1, 0);

-- ---------------------------------------------------------------------
-- 归属口径（user_id）
--
-- 种子里的 user_id 必须落成「登录用户的主键」，不能写字面量 admin：业务侧
-- AiUserContext 取的是数字 sysUserId（admin 登录 = 2），角色 / 世界 / 场景 /
-- NPC / 关系 / 事件 / 消息一律按 user_id 过滤，写成字符串 admin 的行在任何
-- 环境的列表里都查不到（表现为「助手列表为空、世界不存在」）。
-- 这里统一解析一次，取不到管理员账号时退回 admin，保证语句仍可执行。
-- 例外：上面 sys_ai_prompt_form 的 user_id 由
-- SysAiPromptFormService.BUILTIN_OWNER 按字符串 admin 过滤，保持原样。
-- ---------------------------------------------------------------------
SET @owner_id = COALESCE((SELECT `sys_user_id` FROM `sys_user`
                          WHERE `sys_user_username` = 'admin' ORDER BY `sys_user_id` LIMIT 1), 'admin');

-- ---------------------------------------------------------------------
-- 内置通用助手（code = general-assistant，原 V4.0.0.55 的 UPDATE 复位）
--
-- 历史问题：这里原本只有一条 `UPDATE ... WHERE sys_ai_role_id = 1` 的复位，
-- 依赖「通用助手主键一定是 1」的假设；而第 1 节的三国角色是按自增插入的，
-- 主键 1 实际是刘备，内置通用助手从来没有 INSERT 语句 —— 任何环境都不会有
-- 这一行，复位永远空转，角色列表里也就永远选不到「通用助手」。
-- 现在的口径：按 code 播种，幂等靠唯一键 (user_id, sys_ai_role_code)。
-- 正文与 characters-builtin/general-assistant/CHARACTER.md 一致；换行用
-- CHAR(10) 拼（本脚本禁止字符串内出现反斜杠）。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_ai_role`
    (`user_id`, `sys_ai_role_name`, `sys_ai_role_code`, `sys_ai_role_description`,
     `sys_ai_role_system_prompt`, `sys_ai_role_model`, `sys_ai_role_avatar`,
     `sys_ai_role_temperature`, `sys_ai_role_settings`, `sys_ai_role_sort`,
     `sys_ai_role_status`, `sys_ai_role_delete`, `create_name`, `create_time`, `update_time`)
VALUES
    (@owner_id, '通用助手', 'general-assistant',
     '全能型助手，擅长日常对话、信息查询、问题解答与任务协助。',
     CONCAT('你是一个专业、友好的通用助手。你的职责是帮助用户解决各种问题。',
            CHAR(10), CHAR(10), '## 行为准则', CHAR(10), CHAR(10),
            '- 回答简洁明了，重点突出', CHAR(10),
            '- 遇到不确定的问题要诚实说明', CHAR(10),
            '- 主动提供相关信息和建议', CHAR(10),
            '- 保持礼貌友好的语气', CHAR(10), CHAR(10),
            '## 输出要求', CHAR(10), CHAR(10),
            '- 结构清晰，分点说明', CHAR(10),
            '- 重要信息加粗突出', CHAR(10),
            '- 适当使用示例帮助理解'),
     NULL, NULL, 0.7, NULL, 0, 1, 0, 'admin', NOW(), NOW());

-- ---------------------------------------------------------------------
-- 厂商指标系数种子（来源：models.dev 公开目录 2026-10-22）
-- 口径：输入/输出价 = 该厂商全部模型单价中位数（USD/百万 token），上下文 = 中位数窗口。
-- 幂等：INSERT IGNORE + 厂商键唯一索引，可反复执行。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_ai_provider_metric`
    (`sys_ai_provider_metric_provider`, `sys_ai_provider_metric_provider_name`,
     `sys_ai_provider_metric_price_input`, `sys_ai_provider_metric_price_output`,
     `sys_ai_provider_metric_context_window`, `sys_ai_provider_metric_model_count`,
     `sys_ai_provider_metric_source`)
VALUES
    ('alibaba', 'Alibaba', 0.435, 2.125, 131072, 59, 'models.dev'),
    ('anthropic', 'Anthropic', 4, 20, 1000000, 17, 'models.dev'),
    ('cerebras', 'Cerebras', 0.67, 1.12, 131072, 2, 'models.dev'),
    ('cohere', 'Cohere', 1.5, 5.75, 128000, 17, 'models.dev'),
    ('commandcode', 'CommandCode', 1.25, 4.4, 1000000, 71, 'models.dev'),
    ('deepseek', 'DeepSeek', 0.15, 0.6, 1048576, 4, 'models.dev'),
    ('google', 'Google', 0.75, 10, 655360, 38, 'models.dev'),
    ('groq', 'Groq', 0.075, 0.3, 131072, 16, 'models.dev'),
    ('huggingface', 'Hugging Face', 0.3, 1.2, 262144, 84, 'models.dev'),
    ('kilo', 'Kilo Gateway', 0.37, 1.5, 262144, 397, 'models.dev'),
    ('minimax', 'MiniMax (minimax.io)', 0.3, 1.2, 204800, 8, 'models.dev'),
    ('mistral', 'Mistral', 0.25, 0.75, 262144, 40, 'models.dev'),
    ('modelscope', 'ModelScope', 0, 0, 262144, 7, 'models.dev'),
    ('moonshotai', 'Moonshot AI', 1.425, 6, 262144, 4, 'models.dev'),
    ('nvidia', 'Nvidia', 0, 0, 131072, 51, 'models.dev'),
    ('openai', 'OpenAI', 2, 10, 400000, 51, 'models.dev'),
    ('opencode', 'OpenCode', 0.425, 2.145, 450000, 130, 'models.dev'),
    ('openrouter', 'OpenRouter', 0.31225, 1.5, 262144, 390, 'models.dev'),
    ('perplexity', 'Perplexity', 2, 8, 128000, 4, 'models.dev'),
    ('sensenova', 'SenseNova (China)', 0, 0, 1024288, 6, 'models.dev'),
    ('siliconflow', 'SiliconFlow', 0.26, 1, 262000, 57, 'models.dev'),
    ('stepfun', 'StepFun (China)', 0.62394, 2.099735, 256000, 9, 'models.dev'),
    ('volcengine', 'Volcengine Ark', 0.282025, 1.261685, 256000, 16, 'models.dev'),
    ('xai', 'xAI', 1.25, 2.5, 500000, 13, 'models.dev'),
    ('zai', 'Z.AI', 0.6, 2, 200000, 18, 'models.dev'),
    ('zhipuai', 'Zhipu AI', 0.29879, 1.04577, 200000, 17, 'models.dev'),
    ('302ai', '302.AI', 0.95, 4, 1000000, 125, 'models.dev'),
    ('abacus', 'Abacus', 1.25, 4.4, 400000, 108, 'models.dev'),
    ('abliteration-ai', 'abliteration.ai', 5, 5, 1000000, 3, 'models.dev'),
    ('above', 'above.dev', 0.61685, 1.5967, 1000000, 10, 'models.dev'),
    ('agentrouter', 'AgentRouter', NULL, NULL, 1000000, 5, 'models.dev'),
    ('agnes', 'Agnes AI', 0, 0, 512000, 4, 'models.dev'),
    ('ai-router', 'AI-ROUTER', 2.5, 15, 1050000, 5, 'models.dev'),
    ('ai21', 'AI21 Labs', 1.1, 4.2, 256000, 2, 'models.dev'),
    ('aiand', 'ai&', 0.4, 2.5, 1048550, 13, 'models.dev'),
    ('aihubmix', 'AIHubMix', 0.6, 2.7048, 1000000, 155, 'models.dev'),
    ('aimlapi', 'AI/ML API', 0.882372, 2.4334, 262144, 177, 'models.dev'),
    ('ainetcafe', 'ainetcafe', 2.1, 10.5, 262144, 1, 'models.dev'),
    ('aixy', 'Aixy', 2, 10, 1050000, 13, 'models.dev'),
    ('aki-io', 'AKI.IO', 0.2243, 0.6729, 262144, 7, 'models.dev');

INSERT IGNORE INTO `sys_ai_provider_metric`
    (`sys_ai_provider_metric_provider`, `sys_ai_provider_metric_provider_name`,
     `sys_ai_provider_metric_price_input`, `sys_ai_provider_metric_price_output`,
     `sys_ai_provider_metric_context_window`, `sys_ai_provider_metric_model_count`,
     `sys_ai_provider_metric_source`)
VALUES
    ('alibaba-cn', 'Alibaba (China)', 0.273, 0.918, 131072, 91, 'models.dev'),
    ('alibaba-coding-plan', 'Alibaba Coding Plan', 0, 0, 262144, 10, 'models.dev'),
    ('alibaba-coding-plan-cn', 'Alibaba Coding Plan (China)', 0, 0, 262144, 10, 'models.dev'),
    ('alibaba-token-plan', 'Alibaba Token Plan', 0, 0, 1000000, 29, 'models.dev'),
    ('alibaba-token-plan-cn', 'Alibaba Token Plan (China)', 0, 0, 1000000, 29, 'models.dev'),
    ('amazon-bedrock', 'Amazon Bedrock', 2, 6, 1000000, 199, 'models.dev'),
    ('ambient', 'Ambient', 0.32, 2, 202752, 17, 'models.dev'),
    ('amd', 'AMD', 0, 0, 262144, 11, 'models.dev'),
    ('anyapi', 'AnyAPI', NULL, NULL, 1000000, 30, 'models.dev'),
    ('arcee', 'Arcee', 0.5, 1.2, 1000000, 11, 'models.dev'),
    ('atomic-chat', 'Atomic Chat', 0, 0, 32768, 5, 'models.dev'),
    ('auriko', 'Auriko', 1.25, 3.96, 1000000, 15, 'models.dev'),
    ('azure', 'Azure', 1.25, 5.7, 262144, 97, 'models.dev'),
    ('azure-cognitive-services', 'Azure Cognitive Services', 1.25, 5.7, 262144, 97, 'models.dev'),
    ('bailing', 'Bailing', 0.57, 2.29, 128000, 2, 'models.dev'),
    ('baseten', 'Baseten', 0.6, 2.7, 1048576, 26, 'models.dev'),
    ('bee', 'Bee by HEOSSI', 1.875, 7.5, 262144, 6, 'models.dev'),
    ('berget', 'Berget.AI', 0.398225, 2.03, 294912, 6, 'models.dev'),
    ('blueclaw', 'Blue Claw', NULL, NULL, 131072, 1, 'models.dev'),
    ('bothub', 'Bothub', 0.21215, 1.0642, 1000000, 8, 'models.dev'),
    ('boundless', 'Boundless', 0.31, 1.15, 1048576, 20, 'models.dev'),
    ('chutes', 'Chutes', 0.36945, 1.285, 262144, 14, 'models.dev'),
    ('clarifai', 'Clarifai', 0.3, 0.72406, 233472, 12, 'models.dev'),
    ('claudinio', 'Claudinio', 1.75, 5, 256000, 2, 'models.dev'),
    ('cline-pass', 'ClinePass', 0.5475, 1.79, 1000000, 18, 'models.dev'),
    ('cloudferro-sherlock', 'CloudFerro Sherlock', 0.67, 2.92, 131000, 5, 'models.dev'),
    ('cloudflare-ai-gateway', 'Cloudflare AI Gateway', 1.5, 6, 1000000, 73, 'models.dev'),
    ('cloudflare-workers-ai', 'Cloudflare Workers AI', 0.3505, 0.8, 131000, 28, 'models.dev'),
    ('coralbricks', 'CoralBricks', 0.25, 1.2, 1048576, 1, 'models.dev'),
    ('cortecs', 'Cortecs', 0.4605, 1.6675, 262144, 106, 'models.dev'),
    ('crossmodel', 'CrossModel', 1, 3.925, 1000000, 68, 'models.dev'),
    ('crusoe', 'Crusoe', 0.3, 1.5, 262144, 11, 'models.dev'),
    ('daoxe', 'DaoXE', 2.5, 15, 1000000, 9, 'models.dev'),
    ('databricks', 'Databricks', 1.4, 10, 1000000, 31, 'models.dev'),
    ('deepinfra', 'Deep Infra', 0.24, 0.95, 262144, 77, 'models.dev'),
    ('digitalocean', 'DigitalOcean', 1, 4, 262144, 105, 'models.dev'),
    ('dinference', 'DInference', 0.6, 2.025, 200000, 6, 'models.dev'),
    ('drun', 'D.Run (China)', 0.29, 1.16, 131072, 3, 'models.dev'),
    ('ebcloud', 'EBCloud', 0.64285, 2.14285, 631072, 4, 'models.dev'),
    ('echo', 'Echo', 10, 50, 262144, 1, 'models.dev');

INSERT IGNORE INTO `sys_ai_provider_metric`
    (`sys_ai_provider_metric_provider`, `sys_ai_provider_metric_provider_name`,
     `sys_ai_provider_metric_price_input`, `sys_ai_provider_metric_price_output`,
     `sys_ai_provider_metric_context_window`, `sys_ai_provider_metric_model_count`,
     `sys_ai_provider_metric_source`)
VALUES
    ('edenai', 'Eden AI', 0.5, 1.98, 262144, 360, 'models.dev'),
    ('empiriolabs', 'EmpirioLabs AI', 0.3, 1.2, 1000000, 63, 'models.dev'),
    ('engy', 'engy', 0.045, 0.32, 1001536, 9, 'models.dev'),
    ('evroc', 'evroc', 0.2875, 1.035, 96768, 16, 'models.dev'),
    ('fastrouter', 'FastRouter', 0.875, 3.24, 262144, 44, 'models.dev'),
    ('fireworks-ai', 'Fireworks AI', 1.25, 4.25, 1048572, 27, 'models.dev'),
    ('freemodel', 'FreeModel', 4, 20, 1000000, 10, 'models.dev'),
    ('friendli', 'Friendli', 0.88, 2.73, 655360, 6, 'models.dev'),
    ('frogbot', 'FrogBot', 0.85, 3.48, 256000, 26, 'models.dev'),
    ('github-copilot', 'GitHub Copilot', 2, 10, 1000000, 36, 'models.dev'),
    ('gitlab', 'GitLab Duo', 0, 0, 1000000, 30, 'models.dev'),
    ('gmicloud', 'GMI Cloud', 1.392, 3.6, 409600, 17, 'models.dev'),
    ('google-vertex', 'Vertex', 1, 4.4, 524288, 61, 'models.dev'),
    ('google-vertex-anthropic', 'Vertex (Anthropic)', 5, 25, 1000000, 17, 'models.dev'),
    ('greenpt', 'GreenPT', 0.7752, 2.907, 262144, 40, 'models.dev'),
    ('helicone', 'Helicone', 0.465, 1.84, 200000, 90, 'models.dev'),
    ('hetzner', 'Hetzner', 0, 0, 262144, 2, 'models.dev'),
    ('hpc-ai', 'HPC-AI', 0.95, 3.48, 1000000, 9, 'models.dev'),
    ('hyper', 'Charm Hyper', 0.6, 3, 1000000, 23, 'models.dev'),
    ('iflowcn', 'iFlow', 0, 0, 228000, 14, 'models.dev'),
    ('impossibl', 'Impossibl', 1.25, 4.4, 450000, 76, 'models.dev'),
    ('inception', 'Inception', 0.225, 0.75, 194000, 4, 'models.dev'),
    ('inceptron', 'Inceptron', 0.475, 1.574, 1048576, 6, 'models.dev'),
    ('inco', 'Inco', 0.6, 2.4, 1000000, 7, 'models.dev'),
    ('infer', 'Infer by Flow7', 7.5, 37.5, 271999, 2, 'models.dev'),
    ('inference', 'Inference', 0.038, 0.055, 16000, 9, 'models.dev'),
    ('inferx', 'InferX', 0, 0, 262000, 12, 'models.dev'),
    ('infomaniak', 'Infomaniak', 0.31, 0.715, 150000, 10, 'models.dev'),
    ('io-net', 'IO.NET', 0.35, 1.0226, 128000, 17, 'models.dev'),
    ('iteracompute', 'IteraCompute', 0.495, 1.785, 1048576, 10, 'models.dev'),
    ('jalapeno', 'Jalapeno Cloud', 0.4, 3, 262144, 17, 'models.dev'),
    ('jiekou', 'Jiekou.AI', 0.55, 2.3, 262144, 61, 'models.dev'),
    ('kenari', 'Kenari', 0.342857, 1.457143, 750000, 64, 'models.dev'),
    ('kimi-code-plan-cn', 'Kimi For Coding (kimi.com)', 0, 0, 655360, 4, 'models.dev'),
    ('kimi-code-plan-global', 'Kimi For Coding (kimi.ai)', 0, 0, 655360, 4, 'models.dev'),
    ('klokintegration', 'klokintegration.se', 0.23, 1.16, 200000, 3, 'models.dev'),
    ('kosmik', 'Kosmik Compute', 0.35, 2.2, 262144, 1, 'models.dev'),
    ('kuae-cloud-coding-plan', 'KUAE Cloud Coding Plan', 0, 0, 204800, 1, 'models.dev'),
    ('lilac', 'Lilac', 0.49, 2.05, 393216, 4, 'models.dev'),
    ('liquid', 'Liquid Inference', NULL, NULL, 10000000, 1, 'models.dev');

INSERT IGNORE INTO `sys_ai_provider_metric`
    (`sys_ai_provider_metric_provider`, `sys_ai_provider_metric_provider_name`,
     `sys_ai_provider_metric_price_input`, `sys_ai_provider_metric_price_output`,
     `sys_ai_provider_metric_context_window`, `sys_ai_provider_metric_model_count`,
     `sys_ai_provider_metric_source`)
VALUES
    ('llama', 'Llama', 0, 0, 128000, 7, 'models.dev'),
    ('llmgateway', 'DevPass (LLM Gateway)', 0.5, 2.0615, 262144, 214, 'models.dev'),
    ('llmgateway-providers', 'LLM Gateway', 0.6, 2.5, 524288, 433, 'models.dev'),
    ('llmtech', 'LLM Tech', 0.25, 2.09, 262144, 1, 'models.dev'),
    ('llmtr', 'LLMTR', 0.4, 1.5, 262144, 32, 'models.dev'),
    ('lmstudio', 'LMStudio', 0, 0, 262144, 3, 'models.dev'),
    ('longcat', 'LongCat', 0.3, 1.2, 1048576, 2, 'models.dev'),
    ('lowrouter', 'LowRouter', 0.5229, 2, 400000, 31, 'models.dev'),
    ('lucidquery', 'LucidQuery', 2.401875, 10.91125, 275000, 4, 'models.dev'),
    ('lynkr', 'Lynkr', 0, 0, 128000, 1, 'models.dev'),
    ('meganova', 'Meganova', 0.275, 1.1, 196608, 19, 'models.dev'),
    ('melious', 'Melious', 0.69552, 2.95596, 262144, 13, 'models.dev'),
    ('merge-gateway', 'Merge Gateway', 0.6, 2.47545, 262144, 211, 'models.dev'),
    ('meta', 'Meta', 1.25, 4.25, 1048576, 6, 'models.dev'),
    ('minimax-cn', 'MiniMax (minimax.cn)', 0.3137, 1.2549, 204800, 8, 'models.dev'),
    ('minimax-cn-coding-plan', 'MiniMax Token Plan (minimax.cn)', 0, 0, 204800, 9, 'models.dev'),
    ('minimax-coding-plan', 'MiniMax Token Plan (minimax.io)', 0, 0, 204800, 9, 'models.dev'),
    ('mixlayer', 'Mixlayer', 0.3, 2.4, 262144, 5, 'models.dev'),
    ('moark', 'Moark', 0.46, 1.7, 1000000, 10, 'models.dev'),
    ('modal', 'Modal', 1.225, 4.325, 1005000, 8, 'models.dev'),
    ('model-oracle-ai', 'Model Oracle AI', NULL, NULL, 1000000, 15, 'models.dev'),
    ('modelis', 'Modelis', 1.25, 9, 1000000, 9, 'models.dev'),
    ('moonshotai-cn', 'Moonshot AI (China)', 1.4566, 6.0505, 262144, 4, 'models.dev'),
    ('morph', 'Morph', 0.85, 1.55, 32000, 3, 'models.dev'),
    ('nan', 'NaN', 0, 0, 1000000, 8, 'models.dev'),
    ('nano-gpt', 'NanoGPT', 0.4, 1.2, 262144, 624, 'models.dev'),
    ('nearai', 'NEAR AI Cloud', 1.25, 5.875, 1000000, 57, 'models.dev'),
    ('nebius', 'Nebius Token Factory', 0.3, 1.2, 1024000, 24, 'models.dev'),
    ('neon', 'Neon', 1.5, 7.5, 1000000, 57, 'models.dev'),
    ('neosmith', 'NeoSmith', 1.49, 6.38, 1000000, 4, 'models.dev'),
    ('neuralwatt', 'Neuralwatt', 0.87, 2.925, 1048560, 31, 'models.dev'),
    ('nova', 'Nova', 0, 0, 1000000, 2, 'models.dev'),
    ('novita-ai', 'Novita AI', 0.29, 1.007, 262144, 86, 'models.dev'),
    ('oci', 'OCI Generative AI', 0.72, 0.72, 500000, 9, 'models.dev'),
    ('ofox', 'Ofox', 0.5, 2.535, 1000000, 154, 'models.dev'),
    ('ollama-cloud', 'Ollama Cloud', 0.4, 1.74, 976000, 29, 'models.dev'),
    ('opencode-go', 'OpenCode Go', 0.175, 0.735, 1000000, 34, 'models.dev'),
    ('openreason', 'OpenReason', 0.1371, 0.422, 262144, 3, 'models.dev'),
    ('opper', 'Opper', 1.75, 5.811, 1000000, 57, 'models.dev'),
    ('orcarouter', 'OrcaRouter', 0.63, 3, 500000, 117, 'models.dev');

INSERT IGNORE INTO `sys_ai_provider_metric`
    (`sys_ai_provider_metric_provider`, `sys_ai_provider_metric_provider_name`,
     `sys_ai_provider_metric_price_input`, `sys_ai_provider_metric_price_output`,
     `sys_ai_provider_metric_context_window`, `sys_ai_provider_metric_model_count`,
     `sys_ai_provider_metric_source`)
VALUES
    ('ovhcloud', 'OVHcloud AI Endpoints', 0.13, 0.39, 131072, 12, 'models.dev'),
    ('pareto', 'Pareto Inference', 0.06, 0.2, 1000000, 1, 'models.dev'),
    ('pendra', 'Pendra', 0, 0, 231072, 6, 'models.dev'),
    ('perplexity-agent', 'Perplexity Agent', 2, 10, 1000000, 47, 'models.dev'),
    ('pioneer', 'Pioneer', 0.3225, 1.1625, 256000, 117, 'models.dev'),
    ('poe', 'Poe', 1.1, 7.2727, 200000, 142, 'models.dev'),
    ('poolside', 'Poolside', 0, 0, 262144, 3, 'models.dev'),
    ('prime', 'Prime Inference', 1.4, 6, 1000000, 69, 'models.dev'),
    ('privatemode-ai', 'Privatemode AI', 0.2311, 0.7511, 256000, 11, 'models.dev'),
    ('qihang-ai', 'QiHang', 0.14, 1.14, 400000, 9, 'models.dev'),
    ('qiniu-ai', 'Qiniu', 0.304348, 1.26087, 200000, 91, 'models.dev'),
    ('qvac', 'QVAC', 0, 0, 131072, 9, 'models.dev'),
    ('reflection', 'Reflection', 0, 0, 1048576, 1, 'models.dev'),
    ('regolo-ai', 'Regolo AI', 0.38, 1.6, 100000, 18, 'models.dev'),
    ('requesty', 'Requesty', 0.95, 3.75, 1000000, 180, 'models.dev'),
    ('routing-run', 'routing.run', 0.348, 1.1, 262144, 15, 'models.dev'),
    ('runinfra', 'RunInfra', 0.12, 0.425, 655360, 8, 'models.dev'),
    ('sakana', 'Sakana AI', 5, 30, 1000000, 4, 'models.dev'),
    ('salad-cloud', 'SaladCloud AI Gateway', 0.09, 0.6, 262144, 1, 'models.dev'),
    ('sambanova', 'SambaNova', 0.6, 1.8, 131072, 6, 'models.dev'),
    ('sap-ai-core', 'SAP AI Core', 1.5, 9, 400000, 55, 'models.dev'),
    ('sarvam', 'Sarvam AI', 0.157903, 0.631611, 98304, 2, 'models.dev'),
    ('scaleway', 'Scaleway', 0.67245, 1.3458, 256000, 17, 'models.dev'),
    ('scnet-token-plan', 'SCNet Token Plan', 0, 0, 1000000, 19, 'models.dev'),
    ('scx-ai', 'SCX.ai', 0.515, 1.787, 598304, 4, 'models.dev'),
    ('siliconflow-cn', 'SiliconFlow (China)', 0.149447, 0.807016, 262000, 44, 'models.dev'),
    ('snowflake-cortex', 'Snowflake Cortex', 2.25, 14.5, 1000000, 25, 'models.dev'),
    ('stackit', 'STACKIT', 0.53, 0.76, 129500, 8, 'models.dev'),
    ('standardcompute', 'Standard Compute', 0.68, 2.5, 1000000, 40, 'models.dev'),
    ('stepfun-ai', 'StepFun (Global)', 0.6, 1.925, 256000, 9, 'models.dev'),
    ('stepfun-ai-step-plan', 'StepFun Step Plan (Global)', NULL, NULL, 256000, 4, 'models.dev'),
    ('stepfun-step-plan', 'StepFun Step Plan (China)', NULL, NULL, 256000, 5, 'models.dev'),
    ('subconscious', 'Subconscious', 0.77, 2.34, 5000000, 2, 'models.dev'),
    ('submodel', 'submodel', 0.2, 0.8, 131072, 9, 'models.dev'),
    ('synthetic', 'Synthetic', 0.6, 1.2, 524288, 12, 'models.dev'),
    ('tempr', 'Tempr Gateway', 0.875, 3.1, 262144, 82, 'models.dev'),
    ('tencent-coding-plan', 'Tencent Coding Plan (China)', 0, 0, 131072, 8, 'models.dev'),
    ('tencent-token-plan', 'Tencent Token Plan', 0.417, 1.2505, 640000, 2, 'models.dev'),
    ('tencent-tokenhub', 'Tencent TokenHub', 0, 0, 256000, 3, 'models.dev'),
    ('tensorx', 'TensorX', 0.66, 3.2, 262144, 25, 'models.dev');

INSERT IGNORE INTO `sys_ai_provider_metric`
    (`sys_ai_provider_metric_provider`, `sys_ai_provider_metric_provider_name`,
     `sys_ai_provider_metric_price_input`, `sys_ai_provider_metric_price_output`,
     `sys_ai_provider_metric_context_window`, `sys_ai_provider_metric_model_count`,
     `sys_ai_provider_metric_source`)
VALUES
    ('the-grid-ai', 'The Grid AI', NULL, NULL, 1000000, 17, 'models.dev'),
    ('thinkingmachines', 'Thinking Machines', 0.575, 1.44, 65536, 38, 'models.dev'),
    ('tinfoil', 'Tinfoil', 0.525, 1.225, 196608, 8, 'models.dev'),
    ('togetherai', 'Together AI', 0.55, 1.225, 262144, 32, 'models.dev'),
    ('tokengo', 'TokenGo', 0.435, 2.65, 262144, 13, 'models.dev'),
    ('tokenrouter', 'TokenRouter', 0, 0, 1000000, 1, 'models.dev'),
    ('trustedrouter', 'TrustedRouter', NULL, NULL, 1000000, 7, 'models.dev'),
    ('umans-ai', 'Umans AI', 0.15, 0.6, 1048576, 8, 'models.dev'),
    ('umans-ai-coding-plan', 'Umans AI Coding Plan', 0, 0, 1048576, 9, 'models.dev'),
    ('unorouter', 'UnoRouter', 0.0625, 0.125, 1000000, 23, 'models.dev'),
    ('upstage', 'Upstage', 0.25, 0.25, 131072, 5, 'models.dev'),
    ('vancine', 'Vancine', 0.455, 1.48, 1000000, 8, 'models.dev'),
    ('venice', 'Venice AI', 0.9375, 3.5, 1000000, 124, 'models.dev'),
    ('vercel', 'Vercel AI Gateway', 0.64, 2.5, 262144, 419, 'models.dev'),
    ('vispark', 'Vispark', 4.21, 12.63, 1000000, 3, 'models.dev'),
    ('vivgrid', 'Vivgrid', 1.35, 10, 1000000, 37, 'models.dev'),
    ('volcengine-coding-plan', 'Volcengine Ark Coding Plan', 0, 0, 1000000, 10, 'models.dev'),
    ('vultr', 'Vultr', 0.15, 0.6, 1048576, 15, 'models.dev'),
    ('wafer.ai', 'Wafer', 1.14, 4.4, 1048576, 5, 'models.dev'),
    ('wallaby', 'Wallaby', 2.7, 13.5, 1048576, 1, 'models.dev'),
    ('wandb', 'CoreWeave', 0.23, 0.71, 262144, 23, 'models.dev'),
    ('watsonx', 'watsonx.ai', 0.159, 0.636, 131072, 5, 'models.dev'),
    ('xiaomi', 'Xiaomi', 0.435, 0.87, 1048576, 9, 'models.dev'),
    ('xiaomi-token-plan-ams', 'Xiaomi Token Plan (Europe)', 0, 0, 1048576, 9, 'models.dev'),
    ('xiaomi-token-plan-cn', 'Xiaomi Token Plan (China)', 0, 0, 1048576, 9, 'models.dev'),
    ('xiaomi-token-plan-sgp', 'Xiaomi Token Plan (Singapore)', 0, 0, 1048576, 9, 'models.dev'),
    ('xpersona', 'Xpersona', 1.5, 9.25, 372000, 13, 'models.dev'),
    ('zai-coding-plan', 'Z.AI Coding Plan', 0, 0, 1000000, 7, 'models.dev'),
    ('zeldoc', 'Zeldoc', 0, 0, 1000000, 1, 'models.dev'),
    ('zenifra', 'Zenifra', 0.19, 0.48, 262144, 1, 'models.dev'),
    ('zenmux', 'ZenMux', 0.6, 2.5, 262144, 137, 'models.dev'),
    ('zhipuai-coding-plan', 'Zhipu AI Coding Plan', 0, 0, 1000000, 4, 'models.dev');

-- =============================================================================
-- V5.5 —— 模拟（world-sim）三国演义初始化数据
--
-- 依赖：V5.4 六张表已建（本脚本为 INITDATA 通道，异步执行，晚于 DDL）。
-- 归属：user_id = @owner_id（管理员账号的数字主键，见文件开头解析；早期写死
--       字面量 admin，与业务侧按数字 sysUserId 过滤的口径不一致，列表恒为空）。
-- 玩法：为一个「三国演义」世界批量灌入 —— 角色库（sys_ai_role）→ 世界
--       （sys_ai_world）→ 场景（sys_ai_world_scene）→ 世界NPC（sys_ai_world_npc）
--       → NPC 关系（sys_ai_world_relationship）→ 事件时间线（sys_ai_world_event）
--       → 场景对话（sys_ai_world_message）。
--
-- 幂等纪律（重要）：
--   角色     依赖唯一键 (user_id, sys_ai_role_code) → INSERT IGNORE
--   世界/场景/NPC/事件/消息 无唯一键 → INSERT ... SELECT ... WHERE NOT EXISTS 守卫
--   关系     依赖唯一键 (world_id, from_npc_id, to_npc_id) → INSERT IGNORE
--   全部语句可反复执行，不会产生重复行。
--
-- 拆分纪律：本脚本经 FlywayScripts.splitStatements 逐条解析 ——
--   只用 -- 行注释（禁用块注释）；字符串内不得出现英文单引号、分号、反斜杠。
--   （中文标点与全角引号不受影响。）
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1) 三国角色库（sys_ai_role，唯一键 user_id + sys_ai_role_code）
--    sys_ai_role_system_prompt NOT NULL；visibility 缺省即 1（普通用户可见）。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_ai_role`
    (`user_id`, `sys_ai_role_name`, `sys_ai_role_code`, `sys_ai_role_description`,
     `sys_ai_role_system_prompt`, `sys_ai_role_model`, `sys_ai_role_avatar`,
     `sys_ai_role_temperature`, `sys_ai_role_settings`, `sys_ai_role_sort`,
     `sys_ai_role_status`, `sys_ai_role_delete`, `create_name`, `create_time`, `update_time`)
VALUES
    (@owner_id, '刘备', 'liu-bei',
     '蜀汉昭烈帝，汉室宗亲，以仁义立身，桃园结义之首。',
     '你是刘备字玄德，蜀汉昭烈帝。你为人宽厚仁义，礼贤下士，常说「惟贤惟德，能服于人」。你三顾茅庐请出诸葛亮，立志匡扶汉室、中兴天下。与人交谈时谦逊温和，挂念黎民与兄弟，谈吐间自有一股王者气象。',
     NULL, NULL, 0.8, NULL, 1, 1, 0, 'admin', NOW(), NOW()),
    (@owner_id, '关羽', 'guan-yu',
     '蜀汉五虎上将之首，义薄云天，温酒斩华雄、过五关斩六将。',
     '你是关羽字云长，蜀汉五虎上将之首。你重义轻财、恩怨分明，赤面长髯、青龙偃月刀，被尊为武圣。你忠于大哥刘备，傲上而不忍下。言谈刚正果决，自带凛然威势，容不得背信弃义之人。',
     NULL, NULL, 0.8, NULL, 2, 1, 0, 'admin', NOW(), NOW()),
    (@owner_id, '张飞', 'zhang-fei',
     '蜀汉五虎上将，豹头环眼、虎背熊腰，长坂坡一吼退敌十万。',
     '你是张飞字翼德，蜀汉五虎上将。你性如烈火、肝胆照人，丈八蛇矛威震当阳。你敬重读书人，更重兄弟情义，粗中有细。说话声若洪钟、直来直去，一言不合便要与人拼命，但对大哥刘备、二哥关羽言听计从。',
     NULL, NULL, 0.8, NULL, 3, 1, 0, 'admin', NOW(), NOW()),
    (@owner_id, '赵云', 'zhao-yun',
     '蜀汉五虎上将，常山赵子龙，一身是胆，长坂坡七进七出。',
     '你是赵云字子龙，蜀汉五虎上将之一。你文武双全、智勇兼备，白袍银枪，常山赵子龙之名威震四海。你忠诚沉稳、行事谨慎，单骑救主亦面不改色。说话简洁有力，谦而不卑，将忠义刻进骨子里。',
     NULL, NULL, 0.8, NULL, 4, 1, 0, 'admin', NOW(), NOW()),
    (@owner_id, '诸葛亮', 'zhuge-liang',
     '蜀汉丞相，卧龙凤雏之一，隆中对定三分，鞠躬尽瘁死而后已。',
     '你是诸葛亮字孔明，人称卧龙。你身长八尺、羽扇纶巾，隐居隆中时便已洞悉天下大势。你精于谋略、通晓天文地理，隆中一对为刘备定下三分天下之策。你深知天道无亲，然受三顾之恩，鞠躬尽瘁。言谈从容自若，处处透着料事如神的气度。',
     NULL, NULL, 0.8, NULL, 5, 1, 0, 'admin', NOW(), NOW()),
    (@owner_id, '黄月英', 'huang-yue-ying',
     '诸葛亮之妻，博学多才，能工巧匠，发明木牛流马之助。',
     '你是黄月英，诸葛孔明之妻，荆州名士黄承彦之女。你博学多识、心灵手巧，善制奇器机关，木牛流马亦有你的助益。你性格通达聪慧，看事物鞭辟入里，与孔明论道时互为知己。说话温婉而机敏，常以巧思解人疑难。',
     NULL, NULL, 0.8, NULL, 6, 1, 0, 'admin', NOW(), NOW()),
    (@owner_id, '曹操', 'cao-cao',
     '魏武帝，挟天子以令诸侯，煮酒论英雄，治世能臣乱世奸雄。',
     '你是曹操字孟德，魏武帝。你雄才大略、知人善任，挟天子以令诸侯，麾下谋臣猛将如云。你行事果决、机变百出，既有安邦定国之志，亦有宁教我负天下人的狠辣。言谈纵横捭阖，善以权谋驭人，唯独对求贤若渴之心分毫不假。',
     NULL, NULL, 0.8, NULL, 7, 1, 0, 'admin', NOW(), NOW()),
    (@owner_id, '貂蝉', 'diao-chan',
     '古代四大美女之一，连环计离间董卓吕布，义女之身报国恩。',
     '你是貂蝉，司徒王允之义女。你有倾国倾城之貌，更有一颗忠义之心，以美人连环计离间董卓与吕布。你心思细密、深明大义，身处乱世权谋之中亦不改初衷。言谈柔中带刚，暗含决断，举手投足间自有一段风姿。',
     NULL, NULL, 0.8, NULL, 8, 1, 0, 'admin', NOW(), NOW()),
    (@owner_id, '孙权', 'sun-quan',
     '东吴大帝，紫髯碧眼，坐断东南，赤壁决断终成三分。',
     '你是孙权字仲谋，东吴大帝。你少年即位，紫髯碧眼、雄姿英发，知人善用、广纳贤才，坐断东南三分天下。你气量恢弘、能屈能伸，赤壁之战力排众议与刘备联合抗曹。言谈沉稳持重，明察秋毫，颇有帝王气度。',
     NULL, NULL, 0.8, NULL, 9, 1, 0, 'admin', NOW(), NOW()),
    (@owner_id, '周瑜', 'zhou-yu',
     '东吴大都督，美周郎，赤壁火攻破曹，一世儒将风流。',
     '你是周瑜字公瑾，东吴大都督。你风流儒雅、精通音律，赤壁之战火烧连营大破曹军。你才华横溢却气量偏狭，常叹「既生瑜，何生亮」。对待孙吴忠心耿耿，剑胆琴心，谈笑间樯橹灰飞烟灭。',
     NULL, NULL, 0.8, NULL, 10, 1, 0, 'admin', NOW(), NOW());

-- ---------------------------------------------------------------------
-- 2) 三国世界（sys_ai_world）
--    无唯一键 → NOT EXISTS 守卫，杜绝重复建世界。
-- ---------------------------------------------------------------------
INSERT INTO `sys_ai_world`
    (`name`, `description`, `era`, `weather`, `time_label`, `tick_count`, `status`,
     `user_id`, `create_time`, `update_time`)
SELECT '三国演义',
       '东汉末年，天下大乱，群雄并起。桃园结义、三顾茅庐、赤壁鏖兵……这个世界的画卷以黄巾之乱为幕，在三方势力的合纵连横中徐徐展开。玩家可游走于桃园、隆中、许都与江东之间，亲历谋士纵横、猛将交锋的乱世风云。',
       '古代', '晴', '第 1 天 上午', 0, 1, @owner_id, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world` WHERE `user_id` = @owner_id AND `name` = '三国演义');

SET @world_id = (SELECT `id` FROM `sys_ai_world`
                 WHERE `user_id` = @owner_id AND `name` = '三国演义' ORDER BY `id` LIMIT 1);

-- ---------------------------------------------------------------------
-- 3) 场景（sys_ai_world_scene）—— 无唯一键 → NOT EXISTS 守卫
-- ---------------------------------------------------------------------
INSERT INTO `sys_ai_world_scene`
    (`world_id`, `name`, `description`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id, '桃园', '一片桃花漫开的义园。刘关张于此焚香结义，三兄弟的誓言在这棵老桃树下定了下来。', 0, @owner_id, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_scene` WHERE `world_id` = @world_id AND `name` = '桃园');
SET @scene_peach = (SELECT `id` FROM `sys_ai_world_scene`
                    WHERE `world_id` = @world_id AND `name` = '桃园' ORDER BY `id` LIMIT 1);

INSERT INTO `sys_ai_world_scene`
    (`world_id`, `name`, `description`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id, '隆中草庐', '卧龙岗上的一座茅庐，诸葛亮于此躬耕陇亩、静观天下。门外翠竹摇影，松风送凉。', 1, @owner_id, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_scene` WHERE `world_id` = @world_id AND `name` = '隆中草庐');
SET @scene_hut = (SELECT `id` FROM `sys_ai_world_scene`
                  WHERE `world_id` = @world_id AND `name` = '隆中草庐' ORDER BY `id` LIMIT 1);

INSERT INTO `sys_ai_world_scene`
    (`world_id`, `name`, `description`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id, '许都王宫', '曹操挟天子以令诸侯的都城所在。宫阙巍峨之下，机谋暗涌，朝堂之上各怀心思。', 2, @owner_id, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_scene` WHERE `world_id` = @world_id AND `name` = '许都王宫');
SET @scene_xuchang = (SELECT `id` FROM `sys_ai_world_scene`
                      WHERE `world_id` = @world_id AND `name` = '许都王宫' ORDER BY `id` LIMIT 1);

INSERT INTO `sys_ai_world_scene`
    (`world_id`, `name`, `description`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id, '江东大营', '孙权坐镇东南的水陆大营，帐旗猎猎，战船连江，赤壁之战的烽火便从这里点燃。', 3, @owner_id, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_scene` WHERE `world_id` = @world_id AND `name` = '江东大营');
SET @scene_jiangdong = (SELECT `id` FROM `sys_ai_world_scene`
                        WHERE `world_id` = @world_id AND `name` = '江东大营' ORDER BY `id` LIMIT 1);

INSERT INTO `sys_ai_world_scene`
    (`world_id`, `name`, `description`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id, '荆州议事厅', '刘备集团驻守荆州的议事之处。桃园三兄弟与众将常于此商略军国大事，昔日借荆州之说亦由此起。', 4, @owner_id, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_scene` WHERE `world_id` = @world_id AND `name` = '荆州议事厅');
SET @scene_jingzhou = (SELECT `id` FROM `sys_ai_world_scene`
                       WHERE `world_id` = @world_id AND `name` = '荆州议事厅' ORDER BY `id` LIMIT 1);

-- ---------------------------------------------------------------------
-- 4) 世界 NPC（sys_ai_world_npc）—— 复用 sys_ai_role；无唯一键 → NOT EXISTS 守卫
--    按 (world_id, role_id) 判重；每个 NPC 的 id 写入会话变量供关系/事件引用。
-- ---------------------------------------------------------------------
INSERT INTO `sys_ai_world_npc`
    (`world_id`, `role_id`, `scene_id`, `nickname`, `avatar`, `model_override`,
     `temperature`, `persona`, `is_active`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id,
       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'liu-bei' LIMIT 1),
       @scene_peach, '刘备', NULL, NULL, 0.8,
       '乱世仁主，桃园结义的大哥，心中念念不忘匡扶汉室。', 1, 0, @owner_id, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'liu-bei' LIMIT 1));
SET @npc_liubei = (SELECT `id` FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
                   (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'liu-bei' LIMIT 1));

INSERT INTO `sys_ai_world_npc`
    (`world_id`, `role_id`, `scene_id`, `nickname`, `avatar`, `model_override`,
     `temperature`, `persona`, `is_active`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id,
       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'guan-yu' LIMIT 1),
       @scene_peach, '关羽', NULL, NULL, 0.8,
       '义薄云天的美髯公，青龙偃月刀在手，视大哥刘备为性命所系。', 1, 1, @owner_id, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'guan-yu' LIMIT 1));
SET @npc_guanyu = (SELECT `id` FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
                   (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'guan-yu' LIMIT 1));

INSERT INTO `sys_ai_world_npc`
    (`world_id`, `role_id`, `scene_id`, `nickname`, `avatar`, `model_override`,
     `temperature`, `persona`, `is_active`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id,
       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'zhang-fei' LIMIT 1),
       @scene_peach, '张飞', NULL, NULL, 0.8,
       '豹头环眼的燕人张翼德，性如烈火，对两位哥哥唯命是从。', 1, 2, @owner_id, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'zhang-fei' LIMIT 1));
SET @npc_zhangfei = (SELECT `id` FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
                     (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'zhang-fei' LIMIT 1));

INSERT INTO `sys_ai_world_npc`
    (`world_id`, `role_id`, `scene_id`, `nickname`, `avatar`, `model_override`,
     `temperature`, `persona`, `is_active`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id,
       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'zhao-yun' LIMIT 1),
       @scene_jingzhou, '赵云', NULL, NULL, 0.8,
       '常山赵子龙，白袍银枪，一身是胆，如今暂驻荆州。', 1, 3, @owner_id, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'zhao-yun' LIMIT 1));
SET @npc_zhaoyun = (SELECT `id` FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
                    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'zhao-yun' LIMIT 1));

INSERT INTO `sys_ai_world_npc`
    (`world_id`, `role_id`, `scene_id`, `nickname`, `avatar`, `model_override`,
     `temperature`, `persona`, `is_active`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id,
       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'zhuge-liang' LIMIT 1),
       @scene_hut, '诸葛亮', NULL, NULL, 0.8,
       '卧龙孔明，隆中隐居，静候明主三顾之约。', 1, 4, @owner_id, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'zhuge-liang' LIMIT 1));
SET @npc_zhugeliang = (SELECT `id` FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
                       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'zhuge-liang' LIMIT 1));

INSERT INTO `sys_ai_world_npc`
    (`world_id`, `role_id`, `scene_id`, `nickname`, `avatar`, `model_override`,
     `temperature`, `persona`, `is_active`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id,
       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'huang-yue-ying' LIMIT 1),
       @scene_hut, '黄月英', NULL, NULL, 0.8,
       '孔明之妻，兰心蕙质，常在草庐中与夫君论天下机关。', 1, 5, @owner_id, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'huang-yue-ying' LIMIT 1));
SET @npc_huangyueying = (SELECT `id` FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
                         (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'huang-yue-ying' LIMIT 1));

INSERT INTO `sys_ai_world_npc`
    (`world_id`, `role_id`, `scene_id`, `nickname`, `avatar`, `model_override`,
     `temperature`, `persona`, `is_active`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id,
       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'cao-cao' LIMIT 1),
       @scene_xuchang, '曹操', NULL, NULL, 0.8,
       '魏王孟德，雄踞许都，挟天子以令诸侯，志在天下。', 1, 6, @owner_id, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'cao-cao' LIMIT 1));
SET @npc_caocao = (SELECT `id` FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
                   (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'cao-cao' LIMIT 1));

INSERT INTO `sys_ai_world_npc`
    (`world_id`, `role_id`, `scene_id`, `nickname`, `avatar`, `model_override`,
     `temperature`, `persona`, `is_active`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id,
       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'diao-chan' LIMIT 1),
       @scene_xuchang, '貂蝉', NULL, NULL, 0.8,
       '绝色佳人，深明大义，如今身处许都宫闱之间。', 1, 7, @owner_id, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'diao-chan' LIMIT 1));
SET @npc_diaochan = (SELECT `id` FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
                     (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'diao-chan' LIMIT 1));

INSERT INTO `sys_ai_world_npc`
    (`world_id`, `role_id`, `scene_id`, `nickname`, `avatar`, `model_override`,
     `temperature`, `persona`, `is_active`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id,
       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'sun-quan' LIMIT 1),
       @scene_jiangdong, '孙权', NULL, NULL, 0.8,
       '吴主仲谋，紫髯碧眼，坐断东南。', 1, 8, @owner_id, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'sun-quan' LIMIT 1));
SET @npc_sunquan = (SELECT `id` FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
                    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'sun-quan' LIMIT 1));

INSERT INTO `sys_ai_world_npc`
    (`world_id`, `role_id`, `scene_id`, `nickname`, `avatar`, `model_override`,
     `temperature`, `persona`, `is_active`, `sort`, `user_id`, `create_time`, `update_time`)
SELECT @world_id,
       (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'zhou-yu' LIMIT 1),
       @scene_jiangdong, '周瑜', NULL, NULL, 0.8,
       '江东美周郎，赤壁火攻的主角，胸怀大略而对孔明心有芥蒂。', 1, 9, @owner_id, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
    (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'zhou-yu' LIMIT 1));
SET @npc_zhouyu = (SELECT `id` FROM `sys_ai_world_npc` WHERE `world_id` = @world_id AND `role_id` =
                   (SELECT `sys_ai_role_id` FROM `sys_ai_role` WHERE `user_id` = @owner_id AND `sys_ai_role_code` = 'zhou-yu' LIMIT 1));

-- ---------------------------------------------------------------------
-- 5) NPC 关系（sys_ai_world_relationship）—— 唯一键 (world_id, from, to) → INSERT IGNORE
--    affinity 取值均在 [-100, 100]。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_ai_world_relationship`
    (`world_id`, `from_npc_id`, `to_npc_id`, `affinity`, `relation_tag`, `memo`, `user_id`, `create_time`, `update_time`)
VALUES
    (@world_id, @npc_liubei,   @npc_zhugeliang,   65, '君臣', '三顾茅庐之后拜孔明为军师', @owner_id, NOW(), NOW()),
    (@world_id, @npc_zhugeliang, @npc_liubei,     55, '知遇', '感玄德三顾之恩，愿效犬马之劳', @owner_id, NOW(), NOW()),
    (@world_id, @npc_liubei,   @npc_guanyu,       85, '兄弟', '桃园结义二弟关云长', @owner_id, NOW(), NOW()),
    (@world_id, @npc_guanyu,   @npc_liubei,       85, '兄弟', '桃园结义大哥刘玄德', @owner_id, NOW(), NOW()),
    (@world_id, @npc_liubei,   @npc_zhangfei,     85, '兄弟', '桃园结义三弟张翼德', @owner_id, NOW(), NOW()),
    (@world_id, @npc_zhangfei, @npc_liubei,       85, '兄弟', '桃园结义大哥刘玄德', @owner_id, NOW(), NOW()),
    (@world_id, @npc_guanyu,   @npc_zhangfei,     70, '兄弟', '结义三弟张翼德', @owner_id, NOW(), NOW()),
    (@world_id, @npc_zhangfei, @npc_guanyu,       70, '兄弟', '结义二哥关云长', @owner_id, NOW(), NOW()),
    (@world_id, @npc_liubei,   @npc_zhaoyun,      40, '主从', '常山赵子龙弃袁绍来投', @owner_id, NOW(), NOW()),
    (@world_id, @npc_zhaoyun,  @npc_liubei,       45, '忠于', '感玄德仁德，誓死相随', @owner_id, NOW(), NOW()),
    (@world_id, @npc_zhugeliang, @npc_huangyueying, 80, '夫妻', '隆中结缡，琴瑟和鸣', @owner_id, NOW(), NOW()),
    (@world_id, @npc_huangyueying, @npc_zhugeliang, 80, '夫妻', '夫君孔明，相敬如宾', @owner_id, NOW(), NOW()),
    (@world_id, @npc_caocao,   @npc_liubei,       -60, '敌对', '煮酒论英雄，视玄德为大敌', @owner_id, NOW(), NOW()),
    (@world_id, @npc_caocao,   @npc_guanyu,        40, '欣赏', '爱其武勇忠义，曾以厚礼相待', @owner_id, NOW(), NOW()),
    (@world_id, @npc_guanyu,   @npc_caocao,        10, '旧恩', '身在曹营时曾受丞相厚待', @owner_id, NOW(), NOW()),
    (@world_id, @npc_caocao,   @npc_diaochan,      20, '侍奉', '连环计后貂蝉身在许都', @owner_id, NOW(), NOW()),
    (@world_id, @npc_diaochan, @npc_caocao,       -10, '隐忍', '身在虎穴，暗怀心事', @owner_id, NOW(), NOW()),
    (@world_id, @npc_sunquan,  @npc_zhouyu,        70, '君臣', '倚为大都督，委以江东军事', @owner_id, NOW(), NOW()),
    (@world_id, @npc_zhouyu,   @npc_sunquan,       75, '效忠', '公瑾此生唯效吴主', @owner_id, NOW(), NOW()),
    (@world_id, @npc_sunquan,  @npc_caocao,       -50, '敌对', '赤壁结怨，南北对峙', @owner_id, NOW(), NOW()),
    (@world_id, @npc_zhouyu,   @npc_zhugeliang,  -30, '宿敌', '既生瑜，何生亮', @owner_id, NOW(), NOW()),
    (@world_id, @npc_zhugeliang, @npc_zhouyu,     20, '同盟', '赤壁共抗曹，虽和而不同', @owner_id, NOW(), NOW());

-- ---------------------------------------------------------------------
-- 6) 事件时间线（sys_ai_world_event）—— 无唯一键 → NOT EXISTS 守卫（按世界+标题判重）
-- ---------------------------------------------------------------------
INSERT INTO `sys_ai_world_event`
    (`world_id`, `scene_id`, `event_type`, `role_ids`, `title`, `content`, `user_id`, `create_time`)
SELECT @world_id, @scene_peach, 'system',
       CONCAT(@npc_liubei, ',', @npc_guanyu, ',', @npc_zhangfei),
       '桃园三结义',
       '东汉末年黄巾四起，涿郡郊外的桃园中，刘备、关羽、张飞三人焚香结义，誓同生死。此后三兄弟携手闯荡天下，「不求同年同月同日生，但求同年同月同日死」的誓言，便是这个乱世传奇的开端。',
       @owner_id, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_event`
                  WHERE `world_id` = @world_id AND `title` = '桃园三结义');

INSERT INTO `sys_ai_world_event`
    (`world_id`, `scene_id`, `event_type`, `role_ids`, `title`, `content`, `user_id`, `create_time`)
SELECT @world_id, @scene_hut, 'advance',
       CONCAT(@npc_liubei, ',', @npc_zhugeliang),
       '三顾茅庐',
       '刘备三顾卧龙岗，前两番皆空手而返，第三番终于得见孔明。茅庐之中，诸葛亮纵论天下大势，隆中对定下「先取荆州为家，再图益州成鼎足之势」三分天下之策。刘备如鱼得水，当即拜孔明为军师，相偕出山。',
       @owner_id, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_event`
                  WHERE `world_id` = @world_id AND `title` = '三顾茅庐');

INSERT INTO `sys_ai_world_event`
    (`world_id`, `scene_id`, `event_type`, `role_ids`, `title`, `content`, `user_id`, `create_time`)
SELECT @world_id, @scene_jiangdong, 'advance',
       CONCAT(@npc_zhouyu, ',', @npc_zhugeliang, ',', @npc_caocao),
       '赤壁之战',
       '曹操率八十万大军南下，孙刘联军屯兵赤壁。周瑜用火攻之策，借东风之便，一夜之间火烧连营，曹军大败。这场以少胜多的经典战役，自此奠定了魏蜀吴三分天下的格局。',
       @owner_id, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_event`
                  WHERE `world_id` = @world_id AND `title` = '赤壁之战');

INSERT INTO `sys_ai_world_event`
    (`world_id`, `scene_id`, `event_type`, `role_ids`, `title`, `content`, `user_id`, `create_time`)
SELECT @world_id, @scene_jingzhou, 'system',
       CONCAT(@npc_liubei, ',', @npc_zhaoyun),
       '赵云归附',
       '常山赵子龙识破袁绍徒有其表，辗转来投刘备。二人纵论天下，刘备以仁德相待，赵云以「吾终不背德也」相许，自此刘备帐下又添一员虎将。',
       @owner_id, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_event`
                  WHERE `world_id` = @world_id AND `title` = '赵云归附');

-- ---------------------------------------------------------------------
-- 7) 场景对话（sys_ai_world_message）—— 无唯一键 → NOT EXISTS 守卫
--    预置一段「三顾茅庐」场景的开场对白。
-- ---------------------------------------------------------------------
INSERT INTO `sys_ai_world_message`
    (`world_id`, `scene_id`, `speaker_type`, `speaker_id`, `speaker_name`, `content`,
     `model_name`, `at_role_id`, `user_id`, `create_time`)
SELECT @world_id, @scene_hut, 'user', @owner_id, '刘备',
       '先生，汉室倾颓，奸臣窃命，备不度德量力，欲信大义于天下。今日再三造访，实是恳请先生出山相助，共济苍生。',
       NULL, @npc_zhugeliang, @owner_id, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_message`
                  WHERE `world_id` = @world_id AND `scene_id` = @scene_hut AND `speaker_name` = '刘备' AND `content` = '先生，汉室倾颓，奸臣窃命，备不度德量力，欲信大义于天下。今日再三造访，实是恳请先生出山相助，共济苍生。');

INSERT INTO `sys_ai_world_message`
    (`world_id`, `scene_id`, `speaker_type`, `speaker_id`, `speaker_name`, `content`,
     `model_name`, `at_role_id`, `user_id`, `create_time`)
SELECT @world_id, @scene_hut, 'ai', @npc_zhugeliang, '诸葛亮',
       '亮久闻皇叔仁义之名播于四海，三顾之诚已铭感五内。将军既不相弃，亮愿效犬马之劳，为将军鞠躬尽瘁，死而后已。',
       NULL, NULL, @owner_id, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_world_message`
                  WHERE `world_id` = @world_id AND `scene_id` = @scene_hut AND `speaker_name` = '诸葛亮' AND `content` = '亮久闻皇叔仁义之名播于四海，三顾之诚已铭感五内。将军既不相弃，亮愿效犬马之劳，为将军鞠躬尽瘁，死而后已。');

-- ---------------------------------------------------------------------
-- 8) 三国演义 NPC 角色属性种子（年龄/性别/职业/状态，原 V5.6 增量并入）
--    这三列是角色设定，不依赖 LLM 生成，故随 initdata 一次写好。
--    UPDATE 本身幂等：用 (world_id, role_id) 匹配已生成的 NPC，只写对应行，
--    执行多次不覆盖。
-- ---------------------------------------------------------------------
UPDATE sys_ai_world_npc n
LEFT JOIN sys_ai_role r ON r.sys_ai_role_id = n.role_id
SET n.age = CASE r.sys_ai_role_name
                 WHEN '曹操' THEN 55
                 WHEN '刘备' THEN 54
                 WHEN '关羽' THEN 52
                 WHEN '张飞' THEN 50
                 WHEN '赵云' THEN 24
                 WHEN '诸葛亮' THEN 48
                 WHEN '黄月英' THEN 24
                 WHEN '周瑜' THEN 36
                 WHEN '孙权' THEN 30
                 WHEN '貂蝉' THEN 21
                 WHEN '吕布' THEN 32
                 WHEN '司马懿' THEN 45
                 ELSE 0
             END,
    n.age_enabled = 1,
    n.status = 'alive',
    n.gender = CASE r.sys_ai_role_name WHEN '貂蝉' THEN 'female' ELSE 'male' END,
    n.profession = CASE r.sys_ai_role_name
                 WHEN '曹操' THEN '丞相/魏'
                 WHEN '刘备' THEN '诸侯/蜀'
                 WHEN '关羽' THEN '将军/蜀'
                 WHEN '张飞' THEN '将军/蜀'
                 WHEN '诸葛亮' THEN '军师/蜀'
                 WHEN '周瑜' THEN '都督/吴'
                 WHEN '孙权' THEN '诸侯/吴'
                 WHEN '貂蝉' THEN '歌姬/美人'
                 WHEN '吕布' THEN '武将/群雄'
                 WHEN '司马懿' THEN '太尉/魏'
                 ELSE 'NPC'
             END
WHERE n.world_id = @world_id
  AND r.sys_ai_role_name IS NOT NULL;

-- ---------------------------------------------------------------------
-- 9) AppKey 存量清零（原 V5.8 增量并入；列由 INIT 阶段 V6.11 [三] 段补出）
--    只清 NULL/0 之外的旧行，不覆盖真实余额，重复执行无副作用。
-- ---------------------------------------------------------------------
UPDATE `sys_ai_app_key`
   SET `sys_ai_app_key_balance` = 0.000000, `sys_ai_app_key_plan_id` = 0,
       `sys_ai_app_key_overdraft_enabled` = 0, `sys_ai_app_key_minute_rate` = 0.000000,
       `sys_ai_app_key_total_cost` = 0.000000
 WHERE `sys_ai_app_key_balance` IS NULL OR `sys_ai_app_key_balance` = 0;

-- ---------------------------------------------------------------------
-- 10) 渠道熔断存量回填（原 V5.9 增量并入）：空/旧值一律归口 AUTO 跟随探针
-- ---------------------------------------------------------------------
UPDATE `sys_ai_channel_health`
   SET `circuit_state` = 'AUTO'
 WHERE `circuit_state` IS NULL OR `circuit_state` = '';

-- =============================================================================
-- 结束 —— 三国演义世界初始化数据灌装完成（全量幂等，可反复执行）。
-- =============================================================================
