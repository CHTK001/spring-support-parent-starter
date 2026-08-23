package com.chua.starter.ai.support.nomifun.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 浏览器设置实体类。
 *
 * <p>以 Key-Value 形式存储浏览器平台的全局设置，包括显示模式、资源策略、
 * 登录状态等。每个设置项通过 setting_key 唯一标识，setting_value 存储
 * JSON 格式的配置内容。</p>
 *
 * <p>数据来源：{@code sys_agent_browser_setting} 表</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_agent_browser_setting")
@Schema(description = "浏览器全局设置")
public class AgentBrowserSetting implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 设置项键名（主键）。
     *
     * <p>设置项的唯一标识，常见键名：</p>
     * <ul>
     *   <li>{@code display_mode} - 全局显示策略（headless/auto/external）</li>
     *   <li>{@code resource_policy} - 资源策略配置</li>
     *   <li>{@code login_status} - 登录窗口状态</li>
     * </ul>
     */
    @TableId(type = IdType.INPUT)
    @Schema(description = "设置项键名", example = "display_mode")
    private String settingKey;

    /**
     * 设置项值（JSON 格式）。
     *
     * <p>存储具体的配置内容，根据 setting_key 的不同，值的结构也不同：</p>
     * <ul>
     *   <li>{@code display_mode} - 字符串值：{@code "auto"} / {@code "headless"} / {@code "external"}</li>
     *   <li>{@code resource_policy} - JSON 对象：{@code {"preset":"automatic","memory_ratio":0.3,...}}</li>
     *   <li>{@code login_status} - JSON 对象：{@code {"open":false}}</li>
     * </ul>
     */
    @TableField("setting_value")
    @Schema(description = "设置项值（JSON 格式）")
    private String settingValue;

    /**
     * 更新时间（毫秒级时间戳）。
     *
     * <p>记录设置项最后更新的时间，用于缓存刷新和并发控制。</p>
     */
    @TableField("updated_at")
    @Schema(description = "更新时间（毫秒级时间戳）")
    private Long updatedAt;
}
