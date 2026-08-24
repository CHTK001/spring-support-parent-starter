-- ============================================================================
-- V2__init_data.sql - 初始化数据（DML）
-- 包含：AI 模块种子数据、Agent 浏览器默认配置。
-- ============================================================================

INSERT IGNORE INTO `sys_ai_face_group` (`sys_ai_face_group_name`, `sys_ai_face_group_sort`)
VALUES ('default', 0);

INSERT IGNORE INTO `sys_ai_knowledge_library` (
  `sys_ai_knowledge_library_name`, `sys_ai_knowledge_library_description`, `sys_ai_knowledge_library_sort`
) VALUES ('默认知识�?, '系统默认知识�?, 0);

INSERT IGNORE INTO `sys_ai_knowledge_library` (
  `sys_ai_knowledge_library_name`, `sys_ai_knowledge_library_description`,
  `sys_ai_knowledge_library_summary`, `sys_ai_knowledge_library_sort`
) VALUES ('三国演义', '《三国演义》历史知识图�?- 184年黄巾起义至280年西晋统一', '## 提取范围

三国时期�?84�?280年）是中国历史上一个群雄割据的动荡时期。请提取以下各类实体和关系�?

## 鏍稿績浜虹墿锛堟寜闃佃惀锛?

### 蜀汉（刘备阵营�?
- 刘备（字玄德，蜀汉开国皇帝）
- 关羽（字云长，五虎上将之首）
- 张飞（字翼德，五虎上将）
- 诸葛亮（字孔明，丞相�?
- 赵云（字子龙，五虎上将）
- 马超（字孟起，五虎上将）
- 黄忠（字汉升，五虎上将）
- 魏延（字文长，汉中太守）
- 姜维（字伯约，蜀汉大将军�?
- 庞统（字士元，军师中郎将�?
- 法正（字孝直，尚书令�?
- 马谡（字幼常，参军）

### 曹魏（曹操阵营）
- 鏇规搷锛堝瓧瀛熷痉锛岄瓘姝﹀笣锛?
- 曹丕（字子桓，魏文帝�?
- 曹植（字子建，陈王）
- 司马懿（字仲达，太傅�?
- 夏侯惇（字元让，大将军）
- 寮犺窘锛堝瓧鏂囪繙锛屽墠灏嗗啗锛?
- 许褚（字仲康，武卫将军）
- 郭嘉（字奉孝，军师祭酒）
- 荀彧（字文若，尚书令）
- 贾诩（字文和，太尉）
- 典韦（曹操近卫统领）
- 徐晃（字公明，右将军�?

### 东吴（孙权阵营）
- 孙坚（字文台，破虏将军）
- 孙策（字伯符，讨逆将军）
- 瀛欐潈锛堝瓧浠茶皨锛屽惔澶у笣锛?
- 周瑜（字公瑾，大都督�?
- 鲁肃（字子敬，都督）
- 吕蒙（字子明，都督）
- 陆逊（字伯言，丞相）
- 黄盖（字公覆，偏将军�?
- 甘宁（字兴霸，折冲将军）
- 太史慈（字子义，建昌都尉�?

### 群雄/其他
- 吕布（字奉先，温侯）
- 董卓（字仲颖，太师）
- 袁绍（字本初，大将军�?
- 袁术（字公路，仲家皇帝）
- 貂蝉（王允义女）
- 汉献帝刘协（东汉末代皇帝�?
- 王允（字子师，司徒）
- 刘表（字景升，荆州牧�?
- 张角（太平道创始人）

## 需要提取的关系类型

- 鍚涜嚕锛坰erved_as锛夛細浜虹墿鏈嶅姟浜庢煇鍚涗富鎴栨斂鏉?
- 父子（father_of）：父子血缘关�?
- 鍏勫紵锛坆rother_of锛夛細鍏勫紵鍏崇郴锛堝惈缁撲箟锛?
- 夫妻（married_to）：配偶关系
- 盟友（allied_with）：军事/政治同盟
- 敌对（enemy_of）：战场敌对关系
- 师徒（teacher_of）：传授关系
- 举荐（recommended）：推荐人才
- 击败（defeated）：在战役中击败对手
- 继承（succeeded_by）：职位/政权继承
- 暗杀（assassinated）：刺杀行为
- 鎶曢檷锛坰urrendered_to锛夛細鎶曢檷鏌愬娍鍔?

## 重要事件时间�?

- 184骞?榛勫肪璧蜂箟鐖嗗彂
- 189骞?钁ｅ崜鍏ヤ含涔辨斂
- 190骞?鍏充笢璇镐警璁ㄨ懀
- 192骞?鐜嬪厑杩炵幆璁℃潃钁ｅ崜
- 196�?曹操迎汉献帝迁都许昌
- 200�?官渡之战（曹操大败袁绍）
- 207�?三顾茅庐请诸葛亮
- 208�?赤壁之战（孙刘联军败曹操�?
- 214�?刘备入蜀夺取益州
- 219骞?鍏崇窘姘存饭涓冨啗锛屽悤钂欑櫧琛ｆ浮姹熷彇鑽嗗窞
- 220骞?鏇逛笗绉板笣寤洪瓘锛屾眽鏈濈伃浜?
- 221�?刘备称帝建蜀汉，发动夷陵之战
- 222�?陆逊火烧连营，蜀汉大�?
- 225�?诸葛亮七擒孟获平定南�?
- 228�?诸葛亮第一次北伐（失街亭斩马谡�?
- 228-234�?诸葛亮六出祁�?
- 234�?五丈原诸葛亮病�?
- 249�?高平陵之变司马懿夺权
- 263骞?閭撹壘閽熶細浼愯渶锛岃渶姹夌伃浜?
- 265骞?鍙搁┈鐐庡缓绔嬭タ鏅?
- 280�?西晋灭吴统一天下

## 势力阵营

蜀汉、曹魏、东吴三方鼎立，每个角色应标注所属势力�?, 1);

INSERT INTO `sys_ai_manufacturer` (
  `sys_ai_manufacturer_code`, `sys_ai_manufacturer_name`,
  `sys_ai_manufacturer_factory`, `sys_ai_manufacturer_base_url`,
  `sys_ai_manufacturer_app_key`,
  `sys_ai_manufacturer_openai_compatible`,
  `sys_ai_manufacturer_claude_compatible`,
  `sys_ai_manufacturer_status`, `sys_ai_manufacturer_sort`,
  `sys_ai_manufacturer_remark`
)
SELECT * FROM (

  SELECT 'local-onnx' AS code, '本地ONNX模型' AS name,
    NULL AS factory, NULL AS base_url,
    NULL AS app_key,
    0 AS openai_compat, 0 AS claude_compat, 1 AS status, 5 AS sort,
    '本地 ONNX 推理模型（人脸检�?特征提取/OCR/ReID 等）' AS remark
  UNION ALL
  SELECT 'baishan', '白山智算',
    NULL, 'https://api.edgefn.net/v1',
    'sk-REPLACE_WITH_BAISHAN_KEY',
    1, 0, 1, 10,
    '鐧藉北鏅虹畻 API锛屾敮鎸?KAT-Coder / DeepSeek-R1 绛夋ā鍨?
  UNION ALL
  SELECT 'infer-ai', 'Infer AI',
    NULL, 'https://newapis.xyz/v1',
    'sk-REPLACE_WITH_INFER_KEY',
    1, 1, 1, 20,
    'Claude Code 使用�?Infer AI 代理，支�?deepseek-v4-pro（Claude 协议�?
  UNION ALL
  SELECT 'claude-test', 'Claude 测试',
    NULL, 'https://newapis.xyz/v1',
    'sk-REPLACE_WITH_INFER_KEY',
    1, 1, 1, 25,
    'Claude 协议测试厂家，支�?deepseek-v4-pro'
  UNION ALL
  SELECT 'modelscope', 'ModelScope',
    NULL, 'https://api-inference.modelscope.cn/v1',
    'ms-REPLACE_WITH_MODELSCOPE_KEY',
    1, 0, 1, 30,
    '闃块噷 ModelScope 鎺ㄧ悊 API锛屾敮鎸?Qwen3 绛夋ā鍨?
  UNION ALL

  SELECT 'openai', 'OpenAI',
    NULL, 'https://api.openai.com/v1',
    '',
    1, 0, 1, 100,
    'OpenAI API锛屾敮鎸?GPT-4o / GPT-4o-mini / o3 绛夋ā鍨?
  UNION ALL

  SELECT 'siliconflow', '纭呭熀娴佸姩锛圫iliconFlow锛?,
    NULL, 'https://api.siliconflow.cn/v1',
    '',
    1, 0, 1, 110,
    'SiliconFlow 开源模�?API 服务，支�?DeepSeek-V3 / Qwen2.5 / Llama �?
  UNION ALL

  SELECT 'deepseek', 'DeepSeek',
    NULL, 'https://api.deepseek.com',
    '',
    1, 0, 1, 115,
    'DeepSeek 瀹樻柟 API锛屾敮鎸?deepseek-chat / deepseek-reasoner'
  UNION ALL

  SELECT 'aliyun', '阿里云通义千问',
    NULL, 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    '',
    1, 0, 1, 120,
    '阿里�?DashScope API，支�?Qwen-Plus / Qwen-Max 等通义千问模型'
  UNION ALL

  SELECT 'ollama', 'Ollama（本地）',
    NULL, 'http://localhost:11434',
    '',
    1, 0, 1, 130,
    'Ollama 本地大模型服务，支持 Llama / Qwen / DeepSeek 等本地运�?
  UNION ALL

  SELECT 'doubao', '璞嗗寘澶фā鍨?,
    NULL, 'https://ark.cn-beijing.volces.com/api/v3',
    '',
    1, 0, 1, 140,
    '瀛楄妭璺冲姩璞嗗寘澶фā鍨?API锛屾敮鎸?doubao-pro / doubao-lite 绯诲垪'
  UNION ALL

  SELECT 'tencent', '腾讯混元',
    NULL, 'https://api.hunyuan.cloud.tencent.com/v1',
    '',
    1, 0, 1, 145,
    '腾讯混元大模�?API，支�?hunyuan-pro / hunyuan-standard'
  UNION ALL

  SELECT 'kimi', '鏈堜箣鏆楅潰锛圞imi锛?,
    NULL, 'https://api.moonshot.cn/v1',
    '',
    1, 0, 1, 150,
    '鏈堜箣鏆楅潰 Kimi API锛屾敮鎸?moonshot-v1 / kimi-k2 绯诲垪'
  UNION ALL

  SELECT 'minimax', 'MiniMax',
    NULL, 'https://api.minimax.chat/v1',
    '',
    1, 0, 1, 155,
    'MiniMax API锛屾敮鎸?abab6.5 绯诲垪妯″瀷'
  UNION ALL

  SELECT 'zhipu', '鏅鸿氨锛圸hipu AI锛?,
    NULL, 'https://open.bigmodel.cn/api/paas/v4',
    '',
    1, 0, 1, 160,
    '智谱 AI 开放平台，支持 GLM-4 系列模型'
  UNION ALL

  SELECT 'google', 'Google Gemini',
    NULL, 'https://generativelanguage.googleapis.com/v1beta',
    '',
    0, 0, 1, 165,
    'Google Gemini API锛屾敮鎸?gemini-2.0-flash / gemini-2.0-pro'
  UNION ALL

  SELECT 'huawei', '鍗庝负浜戠洏鍙?,
    NULL, 'https://pangu.cn-north-4.myhuaweicloud.com/v1',
    '',
    1, 0, 1, 170,
    '鍗庝负浜戠洏鍙ゅぇ妯″瀷 API锛屾敮鎸?pangu-text 绯诲垪'
  UNION ALL

  SELECT 'claude', 'Anthropic Claude',
    NULL, 'https://api.anthropic.com',
    '',
    0, 1, 1, 175,
    'Anthropic Claude API锛屾敮鎸?Claude Opus / Sonnet 绯诲垪'
  UNION ALL

  SELECT 'xunfei', '讯飞星火',
    NULL, 'https://spark-api.xf-yun.com/v3.5',
    '',
    0, 0, 1, 180,
    '讯飞星火大模�?API，支�?Spark Pro / Max / V3.5 系列'
  UNION ALL

  SELECT 'zai', 'Z.AI（智谱生态）',
    NULL, 'https://api.z.ai/api/paas/v4',
    'REPLACE_WITH_ZHIPU_KEY',
    1, 0, 1, 185,
    'Z.AI API锛堟櫤璋辩敓鎬侊級锛屾彁渚?GLM-4.7 / GLM-4.6 / Cogview / CogVideo 绛夋棗鑸版ā鍨?
  UNION ALL

  SELECT 'openrouter', 'OpenRouter',
    NULL, 'https://openrouter.ai/api/v1/chat/completions',
    'sk-or-REPLACE_WITH_OPENROUTER_KEY',
    1, 0, 1, 195,
    'OpenRouter API 聚合平台，提供多种开�?商业模型的统一访问入口'
  UNION ALL

  SELECT 'deepseek-default', 'DeepSeek 本地',
    NULL, 'http://127.0.0.1:27777/v1',
    'sk-REPLACE_WITH_DEEPSEEK_KEY',
    1, 0, 1, 200,
    '本地部署�?DeepSeek 模型，兼�?OpenAI API'
  UNION ALL

  SELECT 'sixthsense', 'Sixthsense LLM',
    NULL, 'https://sixthsense-llm.com',
    'sk-REPLACE_WITH_SIXTHSENSE_KEY',
    0, 1, 1, 205,
    'Sixthsense LLM API，Claude Code 兼容协议，支�?deepseek-v4-flash-l'
  UNION ALL

  SELECT 'safetensors', 'SafeTensors锛堟湰鍦癙ython锛?,
    NULL, NULL,
    NULL,
    0, 0, 1, 210,
    'SafeTensors 本地 Python 推理引擎，支�?Qwen2.5 / Llama3.2 / MiniMind2 �?safetensors 格式模型'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_ai_manufacturer`
  WHERE `sys_ai_manufacturer_code` = seed.code
);

UPDATE `sys_ai_manufacturer`
SET `sys_ai_manufacturer_base_url` = 'https://newapis.xyz/v1',
    `sys_ai_manufacturer_claude_compatible` = 1
WHERE `sys_ai_manufacturer_code` = 'infer-ai'
  AND (`sys_ai_manufacturer_claude_compatible` = 0 OR `sys_ai_manufacturer_base_url` = 'https://newapis.xyz');

INSERT INTO `sys_ai_module` (
  `sys_ai_module_name`, `sys_ai_module_code`, `sys_ai_module_type`,
  `sys_ai_module_vendor`, `sys_ai_module_status`, `sys_ai_module_sort`,
  `sys_ai_module_vlm`, `sys_ai_module_support_think`,
  `sys_ai_module_concurrency`,
  `sys_ai_module_input_price`, `sys_ai_module_output_price`,
  `sys_ai_module_remark`
)
SELECT * FROM (

  SELECT 'KAT-Coder-Exp-72B-1010' AS name, 'KAT-Coder-Exp-72B-1010' AS code,
    'CHAT' AS type, 'baishan' AS vendor, 1 AS status, 10 AS sort,
    0 AS vlm, 0 AS think,
    1 AS concurrency,
    0.0000 AS in_price, 0.0000 AS out_price,
    '鐧藉北鏅虹畻 - KAT Coder 72B 瀹為獙鐗? AS remark
  UNION ALL
  SELECT 'DeepSeek-R1-0528-Qwen3-8B', 'DeepSeek-R1-0528-Qwen3-8B',
    'CHAT', 'baishan', 1, 20,
    0, 1,
    1,
    0.0000, 0.0000,
    '白山智算 - DeepSeek R1 推理模型(Qwen3-8B 蒸馏�?'
  UNION ALL
  SELECT 'DeepSeek-V4-Pro', 'deepseek-v4-pro',
    'CHAT', 'infer-ai', 1, 30,
    0, 0,
    1,
    0.0000, 0.0000,
    'Infer AI / Claude 测试 - DeepSeek V4 Pro（Claude 协议�?
  UNION ALL
  SELECT 'Qwen3-32B', 'Qwen/Qwen3-32B',
    'CHAT', 'modelscope', 1, 40,
    0, 0,
    1,
    0.0000, 0.0000,
    'ModelScope - 通义千问3 32B'
  UNION ALL
  SELECT 'DeepSeek-V4-Flash (ModelScope)', 'deepseek-ai/DeepSeek-V4-Flash',
    'CHAT', 'modelscope', 1, 45,
    0, 0,
    1,
    0.0000, 0.0000,
    'ModelScope - DeepSeek V4 Flash（免费，1M上下文）'

  UNION ALL

  SELECT 'GPT-4o', 'gpt-4o',
    'CHAT', 'openai', 1, 101,
    1, 0,
    1,
    2.5000, 10.0000,
    'OpenAI - GPT-4o 多模态通用模型，支持图像和文本'
  UNION ALL
  SELECT 'GPT-4o-mini', 'gpt-4o-mini',
    'CHAT', 'openai', 1, 102,
    1, 0,
    1,
    0.1500, 0.6000,
    'OpenAI - GPT-4o-mini 轻量多模态模型，速度快成本低'
  UNION ALL
  SELECT 'GPT-5.2', 'gpt-5.2',
    'CHAT', 'openai', 1, 100,
    0, 1,
    1,
    1.7500, 14.0000,
    'OpenAI - GPT-5.2 最新旗舰模�?

  UNION ALL

  SELECT 'DeepSeek-V3 (SiliconFlow)', 'deepseek-ai/DeepSeek-V3',
    'CHAT', 'siliconflow', 1, 111,
    0, 0,
    1,
    0.2800, 0.4200,
    'SiliconFlow - DeepSeek V3 高性能模型'
  UNION ALL
  SELECT 'Qwen2.5-72B-Instruct (SiliconFlow)', 'Qwen/Qwen2.5-72B-Instruct',
    'CHAT', 'siliconflow', 1, 112,
    0, 0,
    1,
    0.6000, 0.6000,
    'SiliconFlow - Qwen2.5 72B 高性能开源模�?
  UNION ALL
  SELECT 'Llama-3.1-8B-Instruct (SiliconFlow)', 'meta-llama/Meta-Llama-3.1-8B-Instruct',
    'CHAT', 'siliconflow', 1, 113,
    0, 0,
    1,
    0.1000, 0.1000,
    'SiliconFlow - Meta Llama 3.1 8B 轻量模型'

  UNION ALL

  SELECT 'DeepSeek Chat', 'deepseek-chat',
    'CHAT', 'deepseek', 1, 116,
    0, 0,
    1,
    0.1400, 0.2800,
    'DeepSeek - 通用对话模型，中文表现优秀'
  UNION ALL
  SELECT 'DeepSeek Reasoner', 'deepseek-reasoner',
    'CHAT', 'deepseek', 1, 117,
    0, 1,
    1,
    0.5500, 2.1900,
    'DeepSeek - 推理强化模型，深度思考能�?
  UNION ALL
  SELECT 'DeepSeek Coder', 'deepseek-coder',
    'CHAT', 'deepseek', 1, 118,
    0, 0,
    1,
    0.1400, 0.2800,
    'DeepSeek - 代码专用模型，编程能力强'

  UNION ALL

  SELECT 'Qwen-Plus', 'qwen-plus',
    'CHAT', 'aliyun', 1, 121,
    0, 0,
    1,
    0.1150, 0.4600,
    '阿里�?- Qwen-Plus 高性能对话模型，平衡性能与成�?
  UNION ALL
  SELECT 'Qwen-Max', 'qwen-max',
    'CHAT', 'aliyun', 1, 122,
    0, 0,
    1,
    1.2000, 6.0000,
    '阿里�?- Qwen-Max 旗舰模型，最强性能'
  UNION ALL
  SELECT 'Qwen-Turbo', 'qwen-turbo',
    'CHAT', 'aliyun', 1, 123,
    0, 0,
    1,
    0.0350, 0.1400,
    '阿里�?- Qwen-Turbo 轻量快速模�?

  UNION ALL

  SELECT 'Ollama Llama 3.2', 'llama3.2',
    'CHAT', 'ollama', 1, 131,
    0, 0,
    1,
    0.0000, 0.0000,
    'Ollama - Meta Llama 3.2 本地免费运行'
  UNION ALL
  SELECT 'Ollama Qwen 2.5', 'qwen2.5',
    'CHAT', 'ollama', 1, 132,
    0, 0,
    1,
    0.0000, 0.0000,
    'Ollama - Qwen 2.5 本地免费运行'
  UNION ALL
  SELECT 'Ollama DeepSeek R1', 'deepseek-r1',
    'CHAT', 'ollama', 1, 133,
    0, 0,
    1,
    0.0000, 0.0000,
    'Ollama - DeepSeek R1 本地免费运行'

  UNION ALL

  SELECT 'Doubao Pro 32K', 'doubao-pro-32k',
    'CHAT', 'doubao', 1, 141,
    0, 0,
    1,
    0.8000, 2.0000,
    '瀛楄妭璺冲姩 - doubao-pro-32k 涓撲笟鐗堥暱涓婁笅鏂囨ā鍨?
  UNION ALL
  SELECT 'Doubao Lite 32K', 'doubao-lite-32k',
    'CHAT', 'doubao', 1, 142,
    0, 0,
    1,
    0.4000, 1.0000,
    '瀛楄妭璺冲姩 - doubao-lite-32k 杞婚噺鐗堥暱涓婁笅鏂囨ā鍨?

  UNION ALL

  SELECT 'Hunyuan Pro', 'hunyuan-pro',
    'CHAT', 'tencent', 1, 146,
    0, 0,
    1,
    0.5000, 2.0000,
    '腾讯混元 - hunyuan-pro 专业版模�?
  UNION ALL
  SELECT 'Hunyuan Standard', 'hunyuan-standard',
    'CHAT', 'tencent', 1, 147,
    0, 0,
    1,
    0.2000, 0.8000,
    '腾讯混元 - hunyuan-standard 标准版模�?

  UNION ALL

  SELECT 'Moonshot v1 32K', 'moonshot-v1-32k',
    'CHAT', 'kimi', 1, 151,
    0, 0,
    1,
    12.0000, 12.0000,
    '鏈堜箣鏆楅潰 - moonshot-v1-32k 闀夸笂涓嬫枃妯″瀷锛堟寜 楼/鐧句竾tokens 璁¤垂锛?
  UNION ALL
  SELECT 'Kimi K2 Thinking', 'kimi-k2-thinking',
    'CHAT', 'kimi', 1, 152,
    0, 1,
    1,
    0.0000, 0.0000,
    '月之暗面 - kimi-k2-thinking 推理强化模型，MoE 架构'

  UNION ALL

  SELECT 'MiniMax abab6.5s', 'abab6.5s-chat',
    'CHAT', 'minimax', 1, 156,
    0, 0,
    1,
    0.0000, 0.0000,
    'MiniMax - abab6.5s-chat 閫氱敤瀵硅瘽妯″瀷锛屽揩閫熷搷搴?
  UNION ALL
  SELECT 'MiniMax abab6.5 Pro', 'abab6.5-pro',
    'CHAT', 'minimax', 1, 157,
    0, 0,
    1,
    0.0000, 0.0000,
    'MiniMax - abab6.5-pro 专业版模型，最强性能'

  UNION ALL

  SELECT 'GLM-4-Plus', 'glm-4-plus',
    'CHAT', 'zhipu', 1, 161,
    0, 0,
    1,
    0.5000, 2.0000,
    '智谱 - GLM-4-Plus 旗舰模型'
  UNION ALL
  SELECT 'GLM-4-Flash', 'glm-4-flash',
    'CHAT', 'zhipu', 1, 162,
    0, 0,
    1,
    0.0000, 0.0000,
    '智谱 - GLM-4-Flash 免费模型'
  UNION ALL
  SELECT 'GLM-4-Air', 'glm-4-air',
    'CHAT', 'zhipu', 1, 163,
    0, 0,
    1,
    0.0100, 0.0100,
    '智谱 - GLM-4-Air 轻量模型'

  UNION ALL

  SELECT 'Gemini 2.0 Flash', 'gemini-2.0-flash',
    'CHAT', 'google', 1, 166,
    1, 0,
    1,
    0.1000, 0.4000,
    'Google - Gemini 2.0 Flash 快速多模态模�?
  UNION ALL
  SELECT 'Gemini 2.0 Pro', 'gemini-2.0-pro',
    'CHAT', 'google', 1, 167,
    1, 0,
    1,
    1.0000, 4.0000,
    'Google - Gemini 2.0 Pro 鏃楄埌澶氭ā鎬佹ā鍨?

  UNION ALL

  SELECT '盘古 Text Chat', 'pangu-text-chat',
    'CHAT', 'huawei', 1, 171,
    0, 0,
    1,
    0.0080, 0.0080,
    '鍗庝负浜?- pangu-text-chat 閫氱敤瀵硅瘽妯″瀷锛堟寜 楼/鍗僼okens 璁¤垂锛?
  UNION ALL
  SELECT '盘古 Text Turbo', 'pangu-text-turbo',
    'CHAT', 'huawei', 1, 172,
    0, 0,
    1,
    0.0050, 0.0050,
    '华为�?- pangu-text-turbo 极速响应模型（�?¥/千tokens 计费�?

  UNION ALL

  SELECT 'Claude Opus 4.7', 'claude-opus-4-7',
    'CHAT', 'claude', 1, 176,
    1, 0,
    1,
    15.0000, 75.0000,
    'Anthropic - Claude Opus 4.7 旗舰智能模型'
  UNION ALL
  SELECT 'Claude Sonnet 4.6', 'claude-sonnet-4-6',
    'CHAT', 'claude', 1, 177,
    1, 0,
    1,
    3.0000, 15.0000,
    'Anthropic - Claude Sonnet 4.6 最佳性价比模�?
  UNION ALL
  SELECT 'Claude Haiku 4.5', 'claude-haiku-4-5-20251001',
    'CHAT', 'claude', 1, 178,
    1, 0,
    1,
    1.0000, 5.0000,
    'Anthropic - Claude Haiku 4.5 轻量快速模�?

  UNION ALL

  SELECT 'Spark Pro', 'Spark Pro',
    'CHAT', 'xunfei', 1, 181,
    0, 0,
    1,
    0.0080, 0.0080,
    '讯飞 - Spark Pro 专业版模型（�?¥/千tokens 计费�?
  UNION ALL
  SELECT 'Spark Max', 'Spark Max',
    'CHAT', 'xunfei', 1, 182,
    0, 0,
    1,
    0.0120, 0.0120,
    '讯飞 - Spark Max 旗舰模型（按 ¥/千tokens 计费�?

  UNION ALL

  SELECT 'GLM-4.7 (Z.AI)', 'GLM-4.7',
    'CHAT', 'zai', 1, 186,
    0, 0,
    1,
    0.6000, 2.2000,
    'Z.AI - GLM-4.7 最新旗舰文本模型（最佳性能�?
  UNION ALL
  SELECT 'GLM-4.7-Flash (Z.AI)', 'GLM-4.7-Flash',
    'CHAT', 'zai', 1, 187,
    0, 0,
    1,
    0.0000, 0.0000,
    'Z.AI - GLM-4.7-Flash 免费文本模型'
  UNION ALL
  SELECT 'GLM-4.6V-Flash', 'GLM-4.6V-Flash',
    'CHAT', 'zai', 1, 188,
    1, 0,
    1,
    0.0000, 0.0000,
    'Z.AI - GLM-4.6V-Flash 免费视觉模型（价格请以官方为准）'
  UNION ALL
  SELECT 'GLM-4.1V-Thinking-Flash', 'GLM-4.1V-Thinking-Flash',
    'CHAT', 'zai', 1, 189,
    1, 1,
    5,
    0.3000, 0.9000,
    'Z.AI - GLM-4.1V-Thinking-Flash 视觉推理模型（价格请以官方为准）'
  UNION ALL
  SELECT 'GLM-4V-Flash', 'GLM-4V-Flash',
    'CHAT', 'zai', 1, 190,
    1, 0,
    10,
    0.0000, 0.0000,
    'Z.AI - GLM-4V-Flash 视觉模型，高并发'
  UNION ALL
  SELECT 'GLM-5.1', 'GLM-5.1',
    'CHAT', 'zai', 1, 191,
    0, 1,
    10,
    2.0000, 8.0000,
    'Z.AI - GLM-5.1 最新旗舰文本模型（价格请以官方为准�?
  UNION ALL
  SELECT 'Cogview-3-Flash', 'Cogview-3-Flash',
    'IMAGE', 'zai', 1, 192,
    0, 0,
    1,
    0.0000, 0.0000,
    'Z.AI - Cogview-3-Flash 图像生成模型，支�?1024x1024 / 768x1344 / 864x1152 / 1344x768 / 1152x864 / 1440x720 / 720x1440 等多种分辨率'
  UNION ALL
  SELECT 'CogVideoX-Flash', 'CogVideoX-Flash',
    'VIDEO', 'zai', 1, 193,
    0, 0,
    3,
    0.0000, 0.0000,
    'Z.AI - CogVideoX-Flash 视频生成模型'
  UNION ALL

  SELECT 'Nemotron-3-Nano-Omni-30B-A3B-Reasoning (OpenRouter)', 'nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free',
    'CHAT', 'openrouter', 1, 196,
    0, 1,
    5,
    0.0000, 0.0000,
    'OpenRouter - Nvidia Nemotron 3 Nano Omni 30B A3B Reasoning（免费，256K上下文）'
  UNION ALL
  SELECT 'DeepSeek-V4-Flash (OpenRouter)', 'deepseek/deepseek-v4-flash:free',
    'CHAT', 'openrouter', 1, 197,
    0, 0,
    10,
    0.0000, 0.0000,
    'OpenRouter - DeepSeek V4 Flash（免费，1M上下文）'
  UNION ALL
  SELECT 'GLM-5.1 (OpenRouter)', 'z-ai/glm-5.1',
    'CHAT', 'openrouter', 1, 198,
    0, 1,
    10,
    0.0000, 0.0000,
    'OpenRouter - Z.AI GLM-5.1 鏃楄埌鏂囨湰妯″瀷锛?03K涓婁笅鏂囷紝鍏嶈垂锛?
  UNION ALL
  SELECT 'Gemma-4-26B-A4B-IT (OpenRouter)', 'google/gemma-4-26b-a4b-it:free',
    'CHAT', 'openrouter', 1, 199,
    0, 0,
    5,
    0.0000, 0.0000,
    'OpenRouter - Google Gemma 4 26B A4B IT（免费，262K上下文）'
  UNION ALL

  SELECT 'DeepSeek-Default', 'deepseek-default',
    'CHAT', 'deepseek-default', 1, 200,
    0, 0,
    10,
    0.0000, 0.0000,
    '本地 DeepSeek 默认模型（http://127.0.0.1:27777�?
  UNION ALL

  SELECT 'DeepSeek-V4-Flash-L', 'deepseek-v4-flash-l',
    'CHAT', 'sixthsense', 1, 201,
    0, 0,
    5,
    0.0000, 0.0000,
    'Sixthsense LLM - DeepSeek V4 Flash L（Claude Code 兼容协议�?
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_ai_module`
  WHERE `sys_ai_module_code` = seed.code
);

UPDATE `sys_ai_module`
SET `sys_ai_module_concurrency` = 1
WHERE `sys_ai_module_code` IN ('GLM-4.7-Flash', 'GLM-4.7', 'GLM-4.6V', 'GLM-4.6')
  AND (`sys_ai_module_concurrency` IS NULL OR `sys_ai_module_concurrency` = 0);

INSERT INTO `sys_ai_module` (
  `sys_ai_module_name`, `sys_ai_module_code`, `sys_ai_module_model_id`, `sys_ai_module_type`,
  `sys_ai_module_vendor`, `sys_ai_module_version`, `sys_ai_module_local`, `sys_ai_module_status`,
  `sys_ai_module_sort`, `sys_ai_module_remark`
)
SELECT * FROM (

  SELECT '本地 ONNX 图像上色 (DDColor)' AS name, 'local-onnx-colorization' AS code,
    'traced_ddcolor' AS model_id, 'COLORIZATION' AS type,
    'local-onnx' AS vendor, 'onnx' AS ver, 1 AS local, 1 AS status, 10 AS sort,
    '内置本地 ONNX DDColor 图像上色模块' AS remark
  UNION ALL

  SELECT '本地 ONNX 图片增强 (Real-ESRGAN)', 'local-onnx-resolution',
    'real_esrgan', 'RESOLUTION',
    'local-onnx', 'onnx', 1, 1, 20,
    '内置本地 ONNX Real-ESRGAN 图片增强模块'
  UNION ALL

  SELECT '本地 ONNX 人脸增强 (GFPGAN)', 'local-onnx-face-enhance',
    'gfpgan', 'FACE',
    'local-onnx', 'onnx', 1, 1, 30,
    '内置本地 ONNX GFPGAN 人脸增强模块'
  UNION ALL
  SELECT '本地 ONNX 人脸增强 (RestoreFormer++)', 'local-onnx-face-enhance-restoreformer',
    'restoreformer_plusplus', 'FACE',
    'local-onnx', 'onnx', 1, 1, 31,
    '内置本地 ONNX RestoreFormer++ 人脸增强模块'
  UNION ALL

  SELECT '本地 ONNX 人脸检�?(Slim)', 'local-onnx-face-detect-slim',
    'slim', 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 40,
    '内置本地 ONNX UltraFace Slim 人脸检测模�?
  UNION ALL
  SELECT '本地 ONNX 人脸检�?(MobileNet)', 'local-onnx-face-detect-mobilenet',
    'mobilenet', 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 41,
    '内置本地 ONNX UltraFace MobileNet 人脸检测模�?
  UNION ALL
  SELECT '本地 ONNX 人脸检�?(RFB)', 'local-onnx-face-detect-rfb',
    'RFB', 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 42,
    '内置本地 ONNX UltraFace RFB 人脸检测模�?
  UNION ALL
  SELECT '本地 ONNX 人脸检�?(SCRFD)', 'local-onnx-face-detect-scrfd',
    'scrfd_2_5g_bnkps', 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 43,
    '内置本地 ONNX SCRFD 2.5G 人脸检测模�?
  UNION ALL
  SELECT '本地 ONNX 人脸检�?(RetinaFace 640)', 'local-onnx-face-retina-640',
    NULL, 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 44,
    '基于 RetinaFace �?ONNX 人脸检测模型（640px�?
  UNION ALL
  SELECT '本地 ONNX 人脸检�?(RetinaFace 1080x720)', 'local-onnx-face-retina-1080x720',
    NULL, 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 45,
    '基于 RetinaFace �?ONNX 人脸检测模型（1080x720�?
  UNION ALL
  SELECT '本地 ONNX 人脸检�?(UltraLight)', 'local-onnx-face-ultralight',
    NULL, 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 46,
    'UltraLight 快速通用 ONNX 人脸检测模�?
  UNION ALL
  SELECT '本地 ONNX 人脸检�?(YOLOv5 320)', 'local-onnx-face-yolov5-320',
    NULL, 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 47,
    '基于 YOLOv5 �?ONNX 人脸检测模型（320px�?
  UNION ALL
  SELECT '本地 ONNX 人脸检�?(YOLOv5 640)', 'local-onnx-face-yolov5-640',
    NULL, 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 48,
    '基于 YOLOv5 �?ONNX 人脸检测模型（640px�?
  UNION ALL

  SELECT '本地 ONNX 人脸特征 (ArcFace 512D)', 'local-onnx-face-feature-arcface',
    'face_feature', 'FACE_FEATURE',
    'local-onnx', 'onnx', 1, 1, 50,
    '内置本地 ONNX ArcFace 512D 人脸特征提取模块'
  UNION ALL
  SELECT '本地 ONNX 人脸特征 (ResNet-50 512D)', 'local-onnx-face-feature-r50',
    'face_feature_r50', 'FACE_FEATURE',
    'local-onnx', 'onnx', 1, 1, 51,
    '内置本地 ONNX ResNet-50 512D 人脸特征提取模块'
  UNION ALL

  SELECT '本地 ONNX 抠图 (U2Net)', 'local-onnx-matting-u2net',
    'u2net', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 60,
    '内置本地 ONNX U2Net 图像抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (U2Net-P 轻量)', 'local-onnx-matting-u2netp',
    'u2netp', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 61,
    '内置本地 ONNX U2Net-P 轻量级图像抠图模�?
  UNION ALL
  SELECT '本地 ONNX 抠图 (U2Net_UNet)', 'local-onnx-matting-u2net-unet',
    'u2net_unet', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 62,
    '内置本地 ONNX U2Net_UNet 图像抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (Anime 动漫)', 'local-onnx-matting-anime',
    'anime', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 63,
    '内置本地 ONNX Anime 动漫风格图像抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (Human 人像)', 'local-onnx-matting-human',
    'human', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 64,
    '内置本地 ONNX Human 人像抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (Silueta)', 'local-onnx-matting-silueta',
    'silueta', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 65,
    '内置本地 ONNX Silueta 图像抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (Cloth 衣物)', 'local-onnx-matting-cloth',
    'cloth', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 66,
    '内置本地 ONNX Cloth 衣物抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (ISNet 通用)', 'local-onnx-matting-isnet-general-use',
    'isnet-general-use', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 67,
    '内置本地 ONNX ISNet 通用图像抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (RMBG-2.0)', 'local-onnx-matting-rmbg',
    'RMBG-2.0', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 68,
    '内置本地 ONNX RMBG-2.0 高精度图像抠图模�?
  UNION ALL

  SELECT '本地 ONNX 图像特征 (CLIP ViT-B-32)', 'local-onnx-image-feature-clip-b32',
    'CLIP-ViT-B-32-IMAGE', 'IMAGE_FEATURE',
    'local-onnx', 'onnx', 1, 1, 70,
    '内置本地 ONNX CLIP ViT-B-32 图像特征提取模块�?12维，用于以图搜图�?
  UNION ALL
  SELECT '本地 ONNX 图像特征 (CLIP ViT-B-16)', 'local-onnx-image-feature-clip-b16',
    'CLIP-ViT-B-16-IMAGE', 'IMAGE_FEATURE',
    'local-onnx', 'onnx', 1, 1, 71,
    '内置本地 ONNX CLIP ViT-B-16 图像特征提取模块�?12维，更高精度�?
  UNION ALL
  SELECT '本地 ONNX 图像特征 (DINOv2)', 'local-onnx-image-feature-dinov2',
    'DINOv2-IMAGE', 'IMAGE_FEATURE',
    'local-onnx', 'onnx', 1, 1, 72,
    '内置本地 ONNX DINOv2 图像特征提取模块（视觉基础模型�?
  UNION ALL
  SELECT '本地 ONNX 图像特征 (MobileCLIP)', 'local-onnx-image-feature-mobileclip',
    'MobileCLIP-IMAGE', 'IMAGE_FEATURE',
    'local-onnx', 'onnx', 1, 1, 73,
    '内置本地 ONNX MobileCLIP 轻量级图像特征提取模�?
  UNION ALL

  SELECT '本地 ONNX 文字识别 (PP-OCRv4)', 'local-onnx-ocr-ppv4',
    'PP-OCRv4_mobile_det_infer', 'OCR',
    'local-onnx', 'onnx', 1, 1, 80,
    '内置本地 ONNX PP-OCRv4 文字检测识别模块（中英文）'
  UNION ALL
  SELECT '本地 ONNX 文字识别 (PP-OCRv5)', 'local-onnx-ocr-ppv5',
    'PP-OCRv5_mobile_det_infer', 'OCR',
    'local-onnx', 'onnx', 1, 1, 81,
    '内置本地 ONNX PP-OCRv5 文字检测识别模块（中英文）'
  UNION ALL
  SELECT '本地 ONNX 通用文字识别 (LightweightEdge)', 'local-onnx-ocr-lightweight',
    'cv_LightweightEdge_ocr-recognitoin-general_damo', 'OCR',
    'local-onnx', 'onnx', 1, 1, 82,
    '内置本地 ONNX LightweightEdge 通用文字识别模块'
  UNION ALL
  SELECT '本地 ONNX 手写文字识别 (ConvNext)', 'local-onnx-ocr-handwritten',
    'cv_convnextTiny_ocr-recognition-handwritten_damo', 'OCR',
    'local-onnx', 'onnx', 1, 1, 83,
    '内置本地 ONNX ConvNext 手写文字识别模块'
  UNION ALL
  SELECT '本地 ONNX 车牌识别 (ConvNext)', 'local-onnx-ocr-licenseplate',
    'cv_convnextTiny_ocr-recognition-licenseplate_damo', 'OCR',
    'local-onnx', 'onnx', 1, 1, 84,
    '内置本地 ONNX ConvNext 车牌识别模块'
  UNION ALL

  SELECT '本地 ONNX 动漫/真人二分�?, 'local-onnx-cls-anime-real',
    'anime_real_cls', 'CLASSIFICATION',
    'local-onnx', 'onnx', 1, 1, 90,
    '内置本地 ONNX 动漫/真人二分类模�?
  UNION ALL
  SELECT '本地 ONNX 动漫图片分类 (CL Tagger)', 'local-onnx-cls-anime-image',
    'anime_image_classifier', 'CLASSIFICATION',
    'local-onnx', 'onnx', 1, 1, 91,
    '内置本地 ONNX CLIP + CL-Tagger 轻量动漫图片二分类器'
  UNION ALL

  SELECT '本地 ONNX 行人重识�?(YouTu ReID 768D)', 'local-onnx-person-reid-youtu',
    NULL, 'REID',
    'local-onnx', 'onnx', 1, 1, 100,
    '基于 YouTu ReID 的本�?ONNX 行人重识别模型（768d�?
  UNION ALL
  SELECT '本地 ONNX 行人重识�?(OSNet-AIN 512D)', 'local-onnx-person-reid-osnet',
    NULL, 'REID',
    'local-onnx', 'onnx', 1, 1, 101,
    '基于 OSNet-AIN 的本�?ONNX 行人重识别模型（512d�?
  UNION ALL

  SELECT '本地 SafeTensor Qwen2.5-0.5B', 'local-safetensor-qwen2.5-0.5b',
    'qwen2.5-0.5b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 201,
    'Qwen2.5 0.5B 超轻量文本生成模型，适合低资源环�?
  UNION ALL
  SELECT '本地 SafeTensor Qwen2.5-1.5B', 'local-safetensor-qwen2.5-1.5b',
    'qwen2.5-1.5b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 202,
    'Qwen2.5 1.5B 轻量文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor Qwen2.5-3B', 'local-safetensor-qwen2.5-3b',
    'qwen2.5-3b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 203,
    'Qwen2.5 3B 标准文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor Qwen2.5-7B', 'local-safetensor-qwen2.5-7b',
    'qwen2.5-7b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 204,
    'Qwen2.5 7B 高性能文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor Qwen2.5-14B', 'local-safetensor-qwen2.5-14b',
    'qwen2.5-14b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 205,
    'Qwen2.5 14B 大容量文本生成模�?
  UNION ALL
  SELECT '本地 SafeTensor Qwen2.5-Coder-7B', 'local-safetensor-qwen2.5-coder-7b',
    'qwen2.5-coder-7b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 206,
    'Qwen2.5 Coder 7B 代码生成专用模型'
  UNION ALL

  SELECT '本地 SafeTensor Qwen2-0.5B', 'local-safetensor-qwen2-0.5b',
    'qwen2-0.5b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 207,
    'Qwen2 0.5B 超轻量文本生成模�?
  UNION ALL
  SELECT '本地 SafeTensor Qwen2-1.5B', 'local-safetensor-qwen2-1.5b',
    'qwen2-1.5b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 208,
    'Qwen2 1.5B 轻量文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor Qwen2-7B', 'local-safetensor-qwen2-7b',
    'qwen2-7b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 209,
    'Qwen2 7B 标准文本生成模型'
  UNION ALL

  SELECT '本地 SafeTensor Llama3.2-1B', 'local-safetensor-llama3.2-1b',
    'llama3.2-1b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 211,
    'Meta Llama 3.2 1B 轻量文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor Llama3.2-3B', 'local-safetensor-llama3.2-3b',
    'llama3.2-3b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 212,
    'Meta Llama 3.2 3B 高效文本生成模型'
  UNION ALL

  SELECT '本地 SafeTensor MiniMind2-104M', 'local-safetensor-minimind2-104m',
    'minimind2-104m', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 213,
    'MiniMind2 104M 极致轻量文本生成模型，适合教学/实验场景'
  UNION ALL
  SELECT '本地 SafeTensor MiniMind2-Small', 'local-safetensor-minimind2-small',
    'minimind2-small', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 214,
    'MiniMind2 Small 轻量文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor MiniMind-3o (Omni)', 'local-safetensor-minimind-3o',
    'minimind-3o', 'ASR',
    'safetensors', 'safetensor', 1, 1, 215,
    'MiniMind-3o Omni 多模态模型，支持 ASR 语音转文�?/ TTS / 图文理解'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_ai_module`
  WHERE `sys_ai_module_code` = seed.code
);

INSERT INTO `sys_ai_global_setting` (
  `sys_ai_global_setting_group`,
  `sys_ai_global_setting_field`,
  `sys_ai_global_setting_value`,
  `sys_ai_global_setting_value_type`,
  `sys_ai_global_setting_description`,
  `sys_ai_global_setting_sort`,
  `sys_ai_global_setting_status`,
  `create_name`,
  `create_by`,
  `update_name`,
  `update_by`
)
SELECT * FROM (

  SELECT 'runtime' AS grp, 'enabled' AS fld, 'true' AS val, 'BOOLEAN' AS vtype,
    '是否启用 AI ChatClient' AS descr, 5 AS sort, 1 AS st, 'system' AS cn, 1 AS cb, 'system' AS un, 1 AS ub
  UNION ALL
  SELECT 'runtime', 'factory', 'default', 'STRING', 'ChatClient SPI 工厂名称', 8, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'provider', 'zhipu', 'STRING', '默认 LLM 提供�?, 9, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'default_model', 'GLM-4V-Flash', 'STRING', '默认聊天模型编码', 10, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'system_prompt', '', 'STRING', '默认系统提示�?, 15, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'temperature', '0.7', 'NUMBER', '默认采样温度', 20, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'max_tokens', '4096', 'NUMBER', '默认最大输�?token', 30, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'timeout_millis', '30000', 'NUMBER', '默认超时时间(毫秒)', 35, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'app_key_enabled', 'true', 'BOOLEAN', '是否启用 AI AppKey 校验', 40, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'input_optimization_enabled', 'false', 'BOOLEAN', '是否启用输入优化', 45, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'context_compression_enabled', 'false', 'BOOLEAN', '是否启用上下文压�?, 50, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'context_compression_threshold', '12', 'NUMBER', '触发上下文压缩的历史阈�?, 55, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'context_compression_retain_messages', '6', 'NUMBER', '压缩后保留的消息数量', 60, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'provider_base_url', 'https://open.bigmodel.cn/api/paas/v4/', 'STRING', '智谱 AI 基础地址（必须以 / 结尾�?, 70, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'provider_api_key', '', 'STRING', '智谱 API Key', 71, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'charge_enabled', 'false', 'BOOLEAN', '每次请求扣减额度', 41, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'stop_when_balance_lt_0', 'true', 'BOOLEAN', '余额不足时停止服�?, 42, 1, 'system', 1, 'system', 1

  UNION ALL
  SELECT 'face_recognition', 'default_face_detect_model', 'local-onnx-face-detect-slim', 'STRING', '默认人脸检测模�?, 10, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'face_recognition', 'default_face_feature_model', 'local-onnx-face-feature-arcface', 'STRING', '默认特征提取模型', 20, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'face_recognition', 'default_face_enhance_model', 'local-onnx-face-enhance', 'STRING', '默认人脸增强模型', 30, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'face_recognition', 'feature_dimension', '512', 'INT', '特征向量维度', 40, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'face_recognition', 'min_similarity', '0.5', 'FLOAT', '最低相似度阈�?, 50, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'face_recognition', 'vector_storage_engine', 'jvector', 'STRING', '向量存储引擎(memory/jvector/milvus/mysql)', 60, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'face_recognition', 'animation_face_filter_enabled', 'true', 'BOOLEAN', '动漫人脸过滤(开启后可防止动�?插画图片通过人脸识别)', 70, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'face_recognition', 'anime_face_detect_model', '', 'STRING', '动漫人脸检测模�?留空则自动查�?', 80, 1, 'system', 1, 'system', 1

  UNION ALL
  SELECT 'image_search', 'default_image_feature_model', 'CLIP-ViT-B-32-IMAGE', 'STRING', '默认图像特征提取模型', 10, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'image_search', 'feature_dimension', '512', 'INT', '特征向量维度', 20, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'image_search', 'min_similarity', '0.3', 'FLOAT', '最低相似度阈�?, 30, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'image_search', 'vector_storage_engine', 'jvector', 'STRING', '向量存储引擎(memory/jvector/milvus/mysql)', 40, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'image_search', 'max_image_size', '1024', 'INT', '图像最大边�?超此值压�?', 50, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'image_search', 'face_enhance_enabled', 'false', 'BOOLEAN', '是否启用图像增强', 60, 1, 'system', 1, 'system', 1

  UNION ALL
  SELECT 'ocr', 'default_ocr_model', 'local-onnx-ocr-ppv5', 'STRING', '默认OCR模型', 10, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'ocr_language', 'ch', 'STRING', '识别语言(ch/en/jp)', 20, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'ocr_confidence_threshold', '0.5', 'FLOAT', '置信度阈�?, 30, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'ocr_max_image_size', '2048', 'INT', '图像最大边�?, 40, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'enhance_contrast', 'true', 'BOOLEAN', '对比度增�?, 50, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'enhance_brightness', '0', 'INT', '亮度调整(-100~100)', 60, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'enhance_denoise', 'true', 'BOOLEAN', '降噪处理', 70, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'enhance_binarize', 'false', 'BOOLEAN', '二值化处理', 80, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'enhance_deskew', 'true', 'BOOLEAN', '倾斜校正', 90, 1, 'system', 1, 'system', 1

  UNION ALL
  SELECT 'knowledge_graph', 'enabled', 'true', 'BOOLEAN', '是否启用知识图谱功能', 5, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'knowledge_graph', 'extraction_prompt_template', '', 'STRING', '实体抽取提示词模�?留空使用默认)', 30, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'knowledge_graph', 'relation_extraction_enabled', 'true', 'BOOLEAN', '是否启用关系抽取', 40, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'knowledge_graph', 'max_entities_per_doc', '50', 'INT', '单文档最大抽取实体数', 50, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'knowledge_graph', 'embedding_dimension', '512', 'INT', '实体嵌入向量维度', 60, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'knowledge_graph', 'vector_storage_engine', 'jvector', 'STRING', '实体嵌入向量存储引擎', 70, 1, 'system', 1, 'system', 1

  UNION ALL
  SELECT 'person_reid', 'default_reid_model', 'local-onnx-person-reid-youtu', 'STRING',
    '默认 ReID 模型', 10, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'person_reid', 'reid_feature_dimension', '768', 'INT',
    'ReID 特征向量维度', 20, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'person_reid', 'reid_min_similarity', '0.5', 'FLOAT',
    'ReID 最低相似度阈�?, 30, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'person_reid', 'reid_vector_storage_engine', 'jvector', 'STRING',
    'ReID 向量存储引擎(memory/jvector/milvus/mysql)', 40, 1, 'system', 1, 'system', 1
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_ai_global_setting`
  WHERE `sys_ai_global_setting_group` = seed.grp
    AND `sys_ai_global_setting_field` = seed.fld
);

DELETE FROM `sys_ai_module`
WHERE `sys_ai_module_type` = 'LLM'
  AND `sys_ai_module_code` NOT IN (
    'KAT-Coder-Exp-72B-1010', 'DeepSeek-R1-0528-Qwen3-8B',
    'deepseek-v4-pro', 'Qwen/Qwen3-32B', 'deepseek-ai/DeepSeek-V4-Flash',
    'gpt-4o', 'gpt-4o-mini', 'gpt-5.2',
    'deepseek-ai/DeepSeek-V3', 'Qwen/Qwen2.5-72B-Instruct',
    'meta-llama/Meta-Llama-3.1-8B-Instruct',
    'deepseek-chat', 'deepseek-reasoner', 'deepseek-coder',
    'qwen-plus', 'qwen-max', 'qwen-turbo',
    'llama3.2', 'qwen2.5', 'deepseek-r1',
    'doubao-pro-32k', 'doubao-lite-32k',
    'hunyuan-pro', 'hunyuan-standard',
    'moonshot-v1-32k', 'kimi-k2-thinking',
    'abab6.5s-chat', 'abab6.5-pro',
    'glm-4-plus', 'glm-4-flash', 'glm-4-air',
    'gemini-2.0-flash', 'gemini-2.0-pro',
    'pangu-text-chat', 'pangu-text-turbo',
    'claude-opus-4-7', 'claude-sonnet-4-6', 'claude-haiku-4-5-20251001',
    'Spark Pro', 'Spark Max',
    'GLM-4.7', 'GLM-4.7-Flash', 'GLM-4.6V-Flash',
    'GLM-4.1V-Thinking-Flash', 'GLM-4V-Flash', 'GLM-5.1',
    'Cogview-3-Flash', 'CogVideoX-Flash',
    'nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free',
    'deepseek/deepseek-v4-flash:free', 'z-ai/glm-5.1',
    'google/gemma-4-26b-a4b-it:free',
    'deepseek-default', 'deepseek-v4-flash-l'
  );

UPDATE `sys_ai_module`
SET `sys_ai_module_type` = 'CHAT'
WHERE `sys_ai_module_type` = 'LLM';

INSERT IGNORE INTO `sys_ai_group` (`sys_ai_group_id`, `sys_ai_group_name`, `sys_ai_group_desc`, `sys_ai_group_sort`, `sys_ai_group_status`)
VALUES (1, '全部模型', '可访问所有已启用的模�?, 0, 1);

INSERT INTO `sys_ai_role` (`sys_ai_role_name`, `sys_ai_role_code`, `sys_ai_role_description`, `sys_ai_role_system_prompt`, `sys_ai_role_sort`, `sys_ai_role_status`, `sys_ai_role_delete`)
VALUES ('通用助手', 'default', '通用 AI 助手，无特殊人设', '你是一个智�?AI 助手，请用中文友好地回答用户的问题�?, 0, 1, 0)
ON DUPLICATE KEY UPDATE `sys_ai_role_name` = VALUES(`sys_ai_role_name`);

INSERT INTO `sys_ai_role` (`sys_ai_role_name`, `sys_ai_role_code`, `sys_ai_role_description`, `sys_ai_role_system_prompt`, `sys_ai_role_sort`, `sys_ai_role_status`, `sys_ai_role_delete`)
VALUES ('Java 开发助�?, 'java-dev', '专业�?Java 后端开发工程师', '你是一个资深的 Java 后端开发工程师，精�?Spring Boot、MyBatis、微服务架构。请用中文回答技术问题，提供代码示例时遵循最佳实践�?, 1, 1, 0)
ON DUPLICATE KEY UPDATE `sys_ai_role_name` = VALUES(`sys_ai_role_name`);

INSERT INTO `sys_ai_role` (`sys_ai_role_name`, `sys_ai_role_code`, `sys_ai_role_description`, `sys_ai_role_system_prompt`, `sys_ai_role_sort`, `sys_ai_role_status`, `sys_ai_role_delete`)
VALUES ('UI 设计�?, 'ui-designer', '前端 UI/UX 设计�?, '你是一个优秀的前�?UI/UX 设计师，精�?Vue 3、Element Plus、Tailwind CSS。设计风格简洁现代，参�?Vercel/Linear 设计语言�?, 2, 1, 0)
ON DUPLICATE KEY UPDATE `sys_ai_role_name` = VALUES(`sys_ai_role_name`);

INSERT IGNORE INTO `sys_ai_group_model` (
    `sys_ai_group_model_id`, `sys_ai_group_id`, `sys_ai_module_id`, `create_time`
) VALUES (
    1, 1, 1, CURRENT_TIMESTAMP
);

INSERT IGNORE INTO `sys_ai_pricing` (
    `provider`, `model`, `input_price_per1k`, `output_price_per1k`,
    `currency`, `effective_date`, `source`
) VALUES
    ('mock', 'mock-gpt-4o', 0.000000, 0.000000, 'CNY', CURRENT_TIMESTAMP, 'manual'),
    ('openai', 'gpt-4o', 0.015000, 0.060000, 'USD', CURRENT_TIMESTAMP, 'manual'),
    ('openai', 'gpt-4o-mini', 0.001500, 0.006000, 'USD', CURRENT_TIMESTAMP, 'manual'),
    ('deepseek', 'deepseek-chat', 0.002000, 0.008000, 'CNY', CURRENT_TIMESTAMP, 'manual'),
    ('zhipu', 'glm-4-flash', 0.000000, 0.000000, 'CNY', CURRENT_TIMESTAMP, 'manual');

INSERT INTO sys_agent_browser_setting (setting_key, setting_value, updated_at) VALUES 
    ('display_mode', 'auto', UNIX_TIMESTAMP() * 1000);

INSERT INTO sys_agent_browser_setting (setting_key, setting_value, updated_at) VALUES 
    ('resource_policy', '{"preset":"automatic"}', UNIX_TIMESTAMP() * 1000);

INSERT INTO sys_agent_browser_setting (setting_key, setting_value, updated_at) VALUES 
    ('login_status', '{"open":false}', UNIX_TIMESTAMP() * 1000);

INSERT INTO sys_agent_browser_host (host_id, state, epoch, headful, identity_mode, lane_count, rss_bytes, created_at, updated_at)
VALUES ('default-host', 'running', 1, FALSE, 'anonymous', 0, 0, UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000);
