-- =====================================================================
-- V5.0__initdata_all_ai.sql - spring-support-ai-starter 全量种子
-- 通道: INITDATA 虚拟线程异步；表结构就位后执行。
-- 说明: 本文件是该模块唯一的种子数据入口。此前分散的
--   V1.0__initdata_all_ai.sql（空占位）与 V4.0.0.49 / V4.0.0.55 的种子
--   已在本次收敛中并入本文件并删除。
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
-- 通用助手角色复位（原 V4.0.0.55）
-- 绑定器补列时顺带把内置角色改回启用 —— 早期版本建角色时没置启用位，
-- 导致「通用助手」在角色列表里选不到。
-- ---------------------------------------------------------------------
UPDATE `sys_ai_role`
SET `sys_ai_role_delete` = 0,
    `sys_ai_role_status` = 1
WHERE `sys_ai_role_id` = 1
  AND `sys_ai_role_code` = 'general-assistant';

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

