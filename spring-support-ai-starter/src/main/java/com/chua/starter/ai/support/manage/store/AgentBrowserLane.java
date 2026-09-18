package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
* 浏览器 Lane 实体类。
*
* <p>Lane 是浏览器平台的核心调度单元，对应一个由 Managed Browser Host 托管的
* 浏览器实例。每个 Lane 可以承载多个 Tab，并通过 owner 信息追踪是由哪个
* 智能体/对话/用户发起的。</p>
*
* <p>数据来源：{@code sys_agent_browser_lane} 表</p>
*
* @author CH
* @since 4.0.0.42
 */
@Data
@TableName("sys_agent_browser_lane")
public class AgentBrowserLane {

    /**
    * Lane 唯一标识（UUID 格式）。
    *
    * <p>由后端自动生成，前端通过此 ID 执行 close/foreground/background 等操作。</p>
    */
    @TableId(type = IdType.INPUT)
    private String laneId; // laneid

    /**
    * Lane 显示名称。
    *
    * <p>可选字段，用于在 UI 中展示友好的名称。若为空则使用 lane_id 作为显示名。</p>
    */
    @TableField("lane_name")
    private String laneName; // lane名称

    /**
    * Lane 生命周期状态。
    *
    * <p>取值范围：</p>
    * <ul>
    *   <li>{@code queued} - 排队等待资源分配</li>
    *   <li>{@code starting} - 正在启动浏览器实例</li>
    *   <li>{@code running} - 正常运行中</li>
    *   <li>{@code frozen} - 已冻结（释放资源但保留状态）</li>
    *   <li>{@code stopping} - 正在关闭</li>
    *   <li>{@code failed} - 启动或运行失败</li>
    * </ul>
    */
    @TableField("lifecycle_state")
    private String lifecycleState; // lifecycle状态

    /**
    * 浏览器纪元（Browser 轮次）。
    *
    * <p>用于匹配 Lane 与 Host 的版本。Host 重启后 epoch 递增，
    * 前端通过此值判断 Lane 是否属于当前活跃的 主机。</p>
    */
    @TableField("browser_epoch")
    private Long browserEpoch; // browser轮次

    // ==================== Owner 信息 ====================

    /**
    * 发起用户的 标识。
    *
    * <p>标识此 Lane 属于哪个用户。在多租户场景下用于权限控制。</p>
    */
    @TableField("user_id")
    private String userId; // 用户标识

    /**
    * 关联的对话 标识。
    *
    * <p>标识此 Lane 是为哪个对话服务的。关闭对话时需要联动关闭其关联的 Lane。</p>
    */
    @TableField("conversation_id")
    private String conversationId; // conversationid

    /**
    * 运行时实例 标识。
    *
    * <p>标识 agent 运行时的唯一实例，用于区分同一对话下的不同 agent 执行。</p>
    */
    @TableField("runtime_instance_id")
    private String runtimeInstanceId; // runtimeinstanceid

    /**
    * 执行 标识。
    *
    * <p>标识一次具体的 agent 执行（可能包含多个步骤/工具调用）。</p>
    */
    @TableField("execution_id")
    private String executionId; // 执行标识

    /**
    * 尝试 标识。
    *
    * <p>标识一次具体的尝试（agent 可能会重试），用于追踪重试历史。</p>
    */
    @TableField("attempt_id")
    private String attemptId; // 尝试标识

    /**
    * 智能体 标识。
    *
    * <p>标识使用此 Lane 的 agent 类型（如 browser_use、playwright 等）。</p>
    */
    @TableField("agent_id")
    private String agentId; // 智能体标识

    /**
    * 智能体 显示名称。
    *
    * <p>用于在 UI 中展示 agent 的友好名称。</p>
    */
    @TableField("agent_name")
    private String agentName; // 智能体名称

    /**
    * 显示来源（Surface）。
    *
    * <p>标识此 Lane 的使用场景，如 web（网页端）、desktop（桌面端）、api（API 调用）等。</p>
    */
    @TableField("surface")
    private String surface; // surface

    // ==================== Identity 信息 ====================

    /**
    * 身份模式（Identity Mode）。
    *
    * <p>控制浏览器的登录和身份隔离策略：</p>
    * <ul>
    *   <li>{@code primary} - 主身份，使用全局登录凭证</li>
    *   <li>{@code anonymous} - 匿名模式，无登录状态</li>
    *   <li>{@code authenticated_replica} - 认证副本，复制主身份的登录状态</li>
    *   <li>{@code isolated} - 隔离模式，独立的浏览器配置</li>
    * </ul>
    */
    @TableField("identity_mode")
    private String identityMode; // identitymode

    /**
    * 身份标签。
    *
    * <p>可选字段，用于在 UI 中显示身份的描述信息。</p>
    */
    @TableField("identity_label")
    private String identityLabel; // identity标签

 // ==================== 队列 信息 ====================

    /**
    * 排队位置。
    *
    * <p>当资源不足时，Lane 会进入排队状态。此值表示在队列中的位置（从 0 开始）。</p>
    */
    @TableField("queue_position")
    private Integer queuePosition; // 队列立场

    /**
    * 排队原因代码。
    *
    * <p>标识排队的具体原因，如：</p>
    * <ul>
    *   <li>{@code resource_limit} - 资源达到上限</li>
    *   <li>{@code concurrency_limit} - 并发限制</li>
    *   <li>{@code host_busy} - Host 繁忙</li>
    * </ul>
    */
    @TableField("queue_reason_code")
    private String queueReasonCode; // 队列ReasonMLmlml编码

    // ==================== Tab 信息 ====================

    /**
    * 当前活跃 Tab 的 标识。
    *
    * <p>指向此 Lane 中当前处于前台的 Tab。可能为 null（无 Tab 时）。</p>
    */
    @TableField("active_tab_id")
    private String activeTabId; // 活跃tabid

    /**
    * Lane 标题。
    *
    * <p>通常为当前活跃 Tab 的页面标题，用于 UI 展示。</p>
    */
    @TableField("title")
    private String title; // title

    /**
    * Lane 当前 URL。
    *
    * <p>通常为当前活跃 Tab 的页面 URL，用于 UI 展示。</p>
    */
    @TableField("url")
    private String url; // url

    // ==================== 资源信息 ====================

    /**
    * 资源估算字节数。
    *
    * <p>此 Lane 预估占用的内存大小（字节），用于全局资源调度和容量管理。</p>
    */
    @TableField("resource_estimate_bytes")
    private Long resourceEstimateBytes; // resourceestimatebytes

    /**
    * 活跃操作数量。
    *
    * <p>当前 Lane 正在执行的操作数（如页面导航、截图等），用于判断是否繁忙。</p>
    */
    @TableField("active_operation_count")
    private Integer activeOperationCount; // 活跃operation数量

    // ==================== 错误信息 ====================

    /**
    * 错误代码。
    *
    * <p>当 Lane 进入 {@code failed} 状态时，记录具体的错误类型代码。</p>
    */
    @TableField("error_code")
    private String errorCode; // 错误编码

    /**
    * 错误详细信息。
    *
    * <p>记录错误的完整描述，用于调试和日志追踪。</p>
    */
    @TableField("error_message")
    private String errorMessage; // 错误消息

    /**
    * 是否可恢复。
    *
    * <p>标识此错误是否可以通过重试恢复。{@code true} 表示可以自动重试，
    * {@code false} 表示需要人工介入。</p>
    */
    @TableField("recoverable")
    private Boolean recoverable; // recoverable

    // ==================== 时间戳 ====================

    /**
    * 最后活跃时间（毫秒级时间戳）。
    *
    * <p>记录 Lane 最后一次执行操作的时间，用于超时检测和资源回收。</p>
    */
    @TableField("last_active_at")
    private Long lastActiveAt; // 最后一个活跃at

    /**
    * 创建时间（毫秒级时间戳）。
    *
    * <p>记录 Lane 的创建时间，用于排序和生命周期管理。</p>
    */
    @TableField("created_at")
    private Long createdAt; // 创建at

    /**
    * 更新时间（毫秒级时间戳）。
    *
    * <p>记录 Lane 最后一次更新的时间，用于乐观锁和缓存刷新。</p>
    */
    @TableField("updated_at")
    private Long updatedAt; // 更新at
}
