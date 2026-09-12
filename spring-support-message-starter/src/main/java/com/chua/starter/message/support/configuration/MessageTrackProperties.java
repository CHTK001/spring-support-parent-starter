package com.chua.starter.message.support.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 消息阅读追踪配置属性
 * <p>
   * 开启后，发送 HTML 邮件时会在内容末尾注入追踪图（&lt;img src="{baseurl}/v2/消息/track/{令牌}"&gt;），
 * 收件人打开邮件即触发该地址，后端将日志标记为已读。
 * </p>
 *
 * <pre>
 * plugin:
 *   message:
 *     track:
 *       enabled: true                       # 是否开启阅读追踪
 *       base-url: "http://localhost:8080"   # 追踪地址基础路径（缺省回退当前服务地址）
 * </pre>
 *
 * @since 2026/08/03
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = MessageTrackProperties.PRE)
public class MessageTrackProperties {

    /**
     * 配置属性前缀
     */
    public static final String PRE = "plugin.message.track";

    /**
     * 是否开启阅读追踪
     * 默认值: false
     */
    private boolean enabled = false;

    /**
     * 追踪地址基础路径
     * <p>
     * 用于拼接追踪图地址，缺省时自动回退为当前服务地址。
     * </p>
     */
    private String baseUrl;
}
