-- ============================================================
-- Agent Browser 模块 - 初始数据
-- 
-- 功能：插入浏览器平台的默认配置数据
-- 版本：V1.5
-- ============================================================

-- ==================== 默认设置 ====================
-- 全局显示策略：auto(自动)/headless(无头)/external(外部)
INSERT INTO sys_agent_browser_setting (setting_key, setting_value, updated_at) VALUES 
    ('display_mode', 'auto', UNIX_TIMESTAMP() * 1000);

-- 资源策略配置，preset 取值：automatic(自动)/resource_saving(省资源)/high_concurrency(高并发)/custom(自定义)
INSERT INTO sys_agent_browser_setting (setting_key, setting_value, updated_at) VALUES 
    ('resource_policy', '{"preset":"automatic"}', UNIX_TIMESTAMP() * 1000);

-- 登录窗口状态，open 表示登录窗口是否打开
INSERT INTO sys_agent_browser_setting (setting_key, setting_value, updated_at) VALUES 
    ('login_status', '{"open":false}', UNIX_TIMESTAMP() * 1000);

-- ==================== 默认 Host ====================
-- 初始化一个默认的浏览器宿主进程
INSERT INTO sys_agent_browser_host (host_id, state, epoch, headful, identity_mode, lane_count, rss_bytes, created_at, updated_at)
VALUES ('default-host', 'running', 1, FALSE, 'anonymous', 0, 0, UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000);
