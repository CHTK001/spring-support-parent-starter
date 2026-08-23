-- ============================================================
-- Agent Browser 模块 - 表结构定义
-- 
-- 功能：管理浏览器实例（Lane）、标签页（Tab）、宿主进程（Host）和全局设置
-- 版本：V1.4
-- ============================================================

-- ==================== Lane 表 ====================
-- Lane 是浏览器平台的核心调度单元，对应一个由 Managed Browser Host 托管的浏览器实例
-- 每个 Lane 可以承载多个 Tab，并通过 owner 信息追踪是由哪个 agent/对话/用户发起的
CREATE TABLE IF NOT EXISTS sys_agent_browser_lane (
    -- Lane 唯一标识（UUID 格式），由后端自动生成
    lane_id             VARCHAR(64)     PRIMARY KEY,
    
    -- Lane 显示名称，可选字段，用于 UI 展示友好的名称
    lane_name           VARCHAR(255),
    
    -- Lane 生命周期状态：queued(排队)/starting(启动中)/running(运行)/frozen(冻结)/stopping(停止中)/failed(失败)
    lifecycle_state     VARCHAR(32)     NOT NULL DEFAULT 'running',
    
    -- 浏览器纪元（Browser Epoch），用于匹配 Lane 与 Host 的版本
    -- Host 重启后 epoch 递增，前端通过此值判断 Lane 是否属于当前活跃的 Host
    browser_epoch       BIGINT          DEFAULT 0,
    
    -- ==================== Owner 信息 ====================
    -- 发起用户的 ID，标识此 Lane 属于哪个用户
    user_id             VARCHAR(64),
    -- 关联的对话 ID，标识此 Lane 是为哪个对话服务的
    conversation_id     VARCHAR(64),
    -- 运行时实例 ID，标识 agent 运行时的唯一实例
    runtime_instance_id VARCHAR(64),
    -- 执行 ID，标识一次具体的 agent 执行
    execution_id        VARCHAR(64),
    -- 尝试 ID，标识一次具体的尝试（agent 可能会重试）
    attempt_id          VARCHAR(64),
    -- Agent ID，标识使用此 Lane 的 agent 类型
    agent_id            VARCHAR(64),
    -- Agent 显示名称，用于 UI 展示
    agent_name          VARCHAR(128),
    -- 显示来源（Surface）：web(网页端)/desktop(桌面端)/api(API调用)
    surface             VARCHAR(64),
    
    -- ==================== Identity 信息 ====================
    -- 身份模式：primary(主身份)/anonymous(匿名)/authenticated_replica(认证副本)/isolated(隔离)
    identity_mode       VARCHAR(32)     DEFAULT 'anonymous',
    -- 身份标签，用于 UI 展示身份的描述信息
    identity_label      VARCHAR(128),
    
    -- ==================== Queue 信息 ====================
    -- 排队位置，当资源不足时 Lane 会进入排队状态
    queue_position      INT             DEFAULT 0,
    -- 排队原因代码：resource_limit(资源上限)/concurrency_limit(并发限制)/host_busy(Host繁忙)
    queue_reason_code   VARCHAR(64),
    
    -- ==================== Tab 信息 ====================
    -- 当前活跃 Tab 的 ID，指向此 Lane 中当前处于前台的 Tab
    active_tab_id       VARCHAR(64),
    -- Lane 标题，通常为当前活跃 Tab 的页面标题
    title               VARCHAR(512),
    -- Lane 当前 URL，通常为当前活跃 Tab 的页面 URL
    url                 VARCHAR(2048),
    
    -- ==================== 资源信息 ====================
    -- 资源估算字节数，此 Lane 预估占用的内存大小
    resource_estimate_bytes BIGINT      DEFAULT 0,
    -- 活跃操作数量，当前 Lane 正在执行的操作数
    active_operation_count  INT         DEFAULT 0,
    
    -- ==================== 错误信息 ====================
    -- 错误代码，当 Lane 进入 failed 状态时记录具体的错误类型
    error_code          VARCHAR(64),
    -- 错误详细信息，记录错误的完整描述
    error_message       TEXT,
    -- 是否可恢复，标识此错误是否可以通过重试恢复
    recoverable         BOOLEAN         DEFAULT TRUE,
    
    -- ==================== 时间戳 ====================
    -- 最后活跃时间（毫秒级时间戳），记录 Lane 最后一次执行操作的时间
    last_active_at      BIGINT,
    -- 创建时间（毫秒级时间戳）
    created_at          BIGINT          NOT NULL,
    -- 更新时间（毫秒级时间戳）
    updated_at          BIGINT          NOT NULL,
    
    -- 索引
    INDEX idx_lane_conversation (conversation_id),
    INDEX idx_lane_lifecycle (lifecycle_state),
    INDEX idx_lane_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览器 Lane 信息';


-- ==================== Tab 表 ====================
-- Tab 代表浏览器实例中的一个标签页
-- 每个 Lane 可以包含多个 Tab，但同一时刻只有一个 Tab 是活跃的
CREATE TABLE IF NOT EXISTS sys_agent_browser_tab (
    -- Tab 唯一标识（UUID 格式）
    tab_id              VARCHAR(64)     PRIMARY KEY,
    
    -- 所属 Lane 的 ID，外键关联 sys_agent_browser_lane.lane_id
    lane_id             VARCHAR(64)     NOT NULL,
    
    -- Tab 页面标题，从浏览器获取的当前页面标题
    title               VARCHAR(512),
    -- Tab 页面 URL，从浏览器获取的当前页面 URL
    url                 VARCHAR(2048),
    -- 是否为当前活跃 Tab，true 表示此 Tab 是当前处于前台的 Tab
    active              BOOLEAN         DEFAULT FALSE,
    -- 是否已崩溃，true 表示此 Tab 的页面进程已崩溃
    crashed             BOOLEAN         DEFAULT FALSE,
    
    -- 创建时间（毫秒级时间戳）
    created_at          BIGINT          NOT NULL,
    -- 更新时间（毫秒级时间戳）
    updated_at          BIGINT          NOT NULL,
    
    -- 索引
    INDEX idx_tab_lane (lane_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览器 Tab 信息';


-- ==================== Host 表 ====================
-- Host 代表一个托管的浏览器宿主进程
-- 每个 Host 可以承载多个 Lane，并负责管理浏览器的生命周期、资源分配和隔离策略
CREATE TABLE IF NOT EXISTS sys_agent_browser_host (
    -- Host 唯一标识
    host_id             VARCHAR(64)     PRIMARY KEY,
    
    -- Host 生命周期状态：stopped(已停止)/starting(启动中)/running(运行)/restarting(重启中)/stopping(停止中)/failed(失败)
    state               VARCHAR(32)     NOT NULL DEFAULT 'running',
    
    -- Host 纪元（Epoch），每次 Host 重启后递增
    -- Lane 通过此值判断自己是否仍属于当前活跃的 Host
    epoch               BIGINT          DEFAULT 1,
    
    -- 是否以有头模式（Headful）运行
    -- true 表示 Host 以可见窗口模式运行，false 表示无头模式
    headful             BOOLEAN         DEFAULT FALSE,
    
    -- 身份模式：primary(主身份)/anonymous(匿名)/authenticated_replica(认证副本)/isolated(隔离)
    identity_mode       VARCHAR(32)     DEFAULT 'anonymous',
    
    -- 当前承载的 Lane 数量，实时统计
    lane_count          INT             DEFAULT 0,
    
    -- 常驻内存大小（RSS, Resident Set Size），单位：字节
    rss_bytes           BIGINT          DEFAULT 0,
    
    -- 创建时间（毫秒级时间戳）
    created_at          BIGINT          NOT NULL,
    -- 更新时间（毫秒级时间戳）
    updated_at          BIGINT          NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览器宿主进程信息';


-- ==================== Setting 表 ====================
-- 以 Key-Value 形式存储浏览器平台的全局设置
-- 包括显示模式、资源策略、登录状态等
CREATE TABLE IF NOT EXISTS sys_agent_browser_setting (
    -- 设置项键名（主键）
    setting_key         VARCHAR(128)    PRIMARY KEY,
    
    -- 设置项值（JSON 格式）
    -- display_mode: 字符串值 "auto"/"headless"/"external"
    -- resource_policy: JSON 对象 {"preset":"automatic",...}
    -- login_status: JSON 对象 {"open":false}
    setting_value       TEXT,
    
    -- 更新时间（毫秒级时间戳）
    updated_at          BIGINT          NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览器全局设置';
