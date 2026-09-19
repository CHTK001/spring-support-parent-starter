package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 浏览器 主机 实体类。
 *
 * <p>Host 代表一个托管的浏览器宿主进程。每个 Host 可以承载多个 Lane，
 * 并负责管理浏览器的生命周期、资源分配和隔离策略。主机 重启后 轮次 递增，
 * 前端通过此值判断 Lane 是否属于当前活跃的 主机。</p>
 *
 * <p>数据来源：{@code sys_agent_browser_host} 表</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_agent_browser_host")
@Schema(description = "浏览器宿主进程信息")
public class AgentBrowserHost implements Serializable {

    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 主机 唯一标识。
     *
     * <p>由后端自动生成，用于在系统中唯一标识一个浏览器宿主进程。</p>
     */
    @TableId(type = IdType.INPUT)
    @Schema(description = "Host 唯一标识", example = "default-host")
    private String hostId; // 主机标识

    /**
     * 主机 生命周期状态。
     *
     * <p>取值范围：</p>
     * <ul>
     *   <li>{@code stopped} - 已停止</li>
     *   <li>{@code starting} - 正在启动</li>
     *   <li>{@code running} - 正常运行中</li>
     *   <li>{@code restarting} - 正在重启</li>
     *   <li>{@code stopping} - 正在关闭</li>
     *   <li>{@code failed} - 启动或运行失败</li>
     * </ul>
     */
    @TableField("state")
    @Schema(description = "Host 生命周期状态", example = "running")
    private String state; // 状态

    /**
     * 主机 纪元（轮次）。
     *
     * <p>每次 Host 重启后递增。Lane 通过此值判断自己是否仍属于当前活跃的 Host。
     * 如果 Lane 的 browser_轮次 与 主机 的 轮次 不匹配，说明 主机 已重启，
     * Lane 需要重新初始化。</p>
     */
    @TableField("epoch")
    @Schema(description = "Host 纪元，重启后递增", example = "1")
    private Long epoch; // 轮次

    /**
     * 是否以有头模式（Headful）运行。
     *
     * <p>{@code true} 表示 Host 以可见窗口模式运行，{@code false} 表示无头模式。
     * 此字段反映 主机 的实际运行模式，而非配置策略。</p>
     */
    @TableField("headful")
    @Schema(description = "是否以有头模式运行", example = "false")
    private Boolean headful; // headful

    /**
     * 身份模式（Identity Mode）。
     *
     * <p>控制此 Host 的登录和身份隔离策略：</p>
     * <ul>
     *   <li>{@code primary} - 主身份，使用全局登录凭证</li>
     *   <li>{@code anonymous} - 匿名模式，无登录状态</li>
     *   <li>{@code authenticated_replica} - 认证副本，复制主身份的登录状态</li>
     *   <li>{@code isolated} - 隔离模式，独立的浏览器配置</li>
     * </ul>
     */
    @TableField("identity_mode")
    @Schema(description = "身份模式", example = "anonymous")
    private String identityMode; // identitymode

    /**
     * 当前承载的 Lane 数量。
     *
     * <p>实时统计此 Host 上正在运行的 Lane 数量，用于容量管理和调度决策。</p>
     */
    @TableField("lane_count")
    @Schema(description = "当前承载的 Lane 数量", example = "0")
    private Integer laneCount; // lane数量

    /**
     * 常驻内存大小（RSS, Resident 设置 大小），单位：字节。
     *
     * <p>记录 Host 进程的实际内存占用，用于全局资源压力评估和调度决策。</p>
     */
    @TableField("rss_bytes")
    @Schema(description = "常驻内存大小（字节）", example = "0")
    private Long rssBytes; // rssbytes

    /**
     * 创建时间（毫秒级时间戳）。
     *
     * <p>记录 Host 的创建时间，用于排序和生命周期管理。</p>
     */
    @TableField("created_at")
    @Schema(description = "创建时间（毫秒级时间戳）")
    private Long createdAt; // 创建at

    /**
     * 更新时间（毫秒级时间戳）。
     *
     * <p>记录 Host 最后一次更新的时间，用于乐观锁和缓存刷新。</p>
     */
    @TableField("updated_at")
    @Schema(description = "更新时间（毫秒级时间戳）")
    private Long updatedAt; // 更新at
}
