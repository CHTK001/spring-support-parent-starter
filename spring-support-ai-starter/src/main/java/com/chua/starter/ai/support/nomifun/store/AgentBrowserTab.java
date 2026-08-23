package com.chua.starter.ai.support.nomifun.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 浏览器 Tab 实体类。
 *
 * <p>Tab 代表浏览器实例中的一个标签页。每个 Lane 可以包含多个 Tab，
 * 但同一时刻只有一个 Tab 是活跃的（active=true）。Tab 记录了页面的
 * 标题、URL、是否崩溃等状态信息。</p>
 *
 * <p>数据来源：{@code sys_agent_browser_tab} 表</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_agent_browser_tab")
public class AgentBrowserTab {

    /**
     * Tab 唯一标识（UUID 格式）。
     *
     * <p>由后端自动生成，用于在 Lane 内唯一标识一个 Tab。</p>
     */
    @TableId(type = IdType.INPUT)
    private String tabId;

    /**
     * 所属 Lane 的 ID。
     *
     * <p>外键关联 {@link AgentBrowserLane#laneId}，标识此 Tab 属于哪个浏览器实例。</p>
     */
    @TableField("lane_id")
    private String laneId;

    /**
     * Tab 页面标题。
     *
     * <p>从浏览器获取的当前页面标题，用于 UI 展示。可能为 null（页面未加载完成时）。</p>
     */
    @TableField("title")
    private String title;

    /**
     * Tab 页面 URL。
     *
     * <p>从浏览器获取的当前页面 URL，用于 UI 展示和导航判断。可能为 null（空白页时）。</p>
     */
    @TableField("url")
    private String url;

    /**
     * 是否为当前活跃 Tab。
     *
     * <p>{@code true} 表示此 Tab 是当前 Lane 中处于前台的 Tab；
     * {@code false} 表示此 Tab 在后台运行。</p>
     */
    @TableField("active")
    private Boolean active;

    /**
     * 是否已崩溃。
     *
     * <p>{@code true} 表示此 Tab 的页面进程已崩溃，需要重新加载或恢复。</p>
     */
    @TableField("crashed")
    private Boolean crashed;

    /**
     * 创建时间（毫秒级时间戳）。
     *
     * <p>记录 Tab 的创建时间，用于排序和生命周期管理。</p>
     */
    @TableField("created_at")
    private Long createdAt;

    /**
     * 更新时间（毫秒级时间戳）。
     *
     * <p>记录 Tab 最后一次更新的时间，用于乐观锁和缓存刷新。</p>
     */
    @TableField("updated_at")
    private Long updatedAt;
}
