-- ============================================================================
-- Agnes AI 厂商及模型初始化数据
-- ============================================================================

-- 插入 Agnes AI 厂商（如果不存在）
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
  SELECT 'agnes' AS code, 'Agnes AI' AS name,
    NULL AS factory, 'https://apihub.agnes-ai.cn/v1' AS base_url,
    'sk-CaAjBgv7OAHMTzBKW5fYxfJaUa1Aq3xPE5Oj9UEUPGJPFjAg' AS app_key,
    1 AS openai_compat, 0 AS claude_compat, 1 AS status, 50 AS sort,
    'Agnes AI - agnes-2.0-flash / agnes-2.5-flash / agnes-3.0-flash' AS remark
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_ai_manufacturer`
  WHERE `sys_ai_manufacturer_code` = seed.code
);

-- 插入 Agnes AI 模型（如果不存在）
INSERT INTO `sys_ai_module` (
  `sys_ai_module_name`, `sys_ai_module_code`, `sys_ai_module_type`,
  `sys_ai_module_vendor`, `sys_ai_module_status`, `sys_ai_module_sort`,
  `sys_ai_module_vlm`, `sys_ai_module_support_think`,
  `sys_ai_module_concurrency`,
  `sys_ai_module_input_price`, `sys_ai_module_output_price`,
  `sys_ai_module_remark`
)
SELECT * FROM (
  SELECT 'agnes-3.0-flash' AS name, 'agnes-3.0-flash' AS code,
    'CHAT' AS type, 'agnes' AS vendor, 1 AS status, 1 AS sort,
    0 AS vlm, 0 AS think,
    1 AS concurrency,
    0.0000 AS in_price, 0.0000 AS out_price,
    'Agnes AI - agnes-3.0-flash (最新)' AS remark
  UNION ALL
  SELECT 'agnes-2.5-flash', 'agnes-2.5-flash',
    'CHAT', 'agnes', 1, 2,
    0, 0,
    1,
    0.0000, 0.0000,
    'Agnes AI - agnes-2.5-flash'
  UNION ALL
  SELECT 'agnes-2.5-pro', 'agnes-2.5-pro',
    'CHAT', 'agnes', 1, 3,
    0, 0,
    1,
    0.0000, 0.0000,
    'Agnes AI - agnes-2.5-pro'
  UNION ALL
  SELECT 'agnes-2.5-pro-alpha', 'agnes-2.5-pro-alpha',
    'CHAT', 'agnes', 1, 4,
    0, 0,
    1,
    0.0000, 0.0000,
    'Agnes AI - agnes-2.5-pro-alpha'
  UNION ALL
  SELECT 'agnes-2.5-pro-beta', 'agnes-2.5-pro-beta',
    'CHAT', 'agnes', 1, 5,
    0, 0,
    1,
    0.0000, 0.0000,
    'Agnes AI - agnes-2.5-pro-beta'
  UNION ALL
  SELECT 'agnes-2.0-flash', 'agnes-2.0-flash',
    'CHAT', 'agnes', 1, 6,
    0, 0,
    1,
    0.0000, 0.0000,
    'Agnes AI - agnes-2.0-flash'
  UNION ALL
  SELECT 'agnes-image-2.5-flash', 'agnes-image-2.5-flash',
    'CHAT', 'agnes', 1, 10,
    1, 0,
    1,
    0.0000, 0.0000,
    'Agnes AI - agnes-image-2.5-flash (图像生成)'
  UNION ALL
  SELECT 'agnes-image-2.1-flash', 'agnes-image-2.1-flash',
    'CHAT', 'agnes', 1, 11,
    1, 0,
    1,
    0.0000, 0.0000,
    'Agnes AI - agnes-image-2.1-flash (图像生成)'
  UNION ALL
  SELECT 'agnes-image-2.0-flash', 'agnes-image-2.0-flash',
    'CHAT', 'agnes', 1, 12,
    1, 0,
    1,
    0.0000, 0.0000,
    'Agnes AI - agnes-image-2.0-flash (图像生成)'
  UNION ALL
  SELECT 'agnes-video-2.5-flash', 'agnes-video-2.5-flash',
    'CHAT', 'agnes', 1, 20,
    0, 0,
    1,
    0.0000, 0.0000,
    'Agnes AI - agnes-video-2.5-flash (视频生成)'
  UNION ALL
  SELECT 'agnes-video-2.5', 'agnes-video-2.5',
    'CHAT', 'agnes', 1, 21,
    0, 0,
    1,
    0.0000, 0.0000,
    'Agnes AI - agnes-video-2.5 (视频生成)'
  UNION ALL
  SELECT 'agnes-video-v2.0', 'agnes-video-v2.0',
    'CHAT', 'agnes', 1, 22,
    0, 0,
    1,
    0.0000, 0.0000,
    'Agnes AI - agnes-video-v2.0 (视频生成)'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_ai_module`
  WHERE `sys_ai_module_code` = seed.code
);
