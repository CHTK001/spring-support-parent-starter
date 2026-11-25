package com.chua.starter.message.support.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 消息阅读追踪配置属性
 * <p>
 * 开启后，发送 HTML 邮件时会在内容末尾注入追踪图（&lt;img src="{base-url}/v2/message/track/{token}"&gt;），
 * 收件人打开邮件即触发该地址，后端将日志标记为已读。追踪仅对 email/smtp 渠道、
 * 内容类型为 html / text/html / EMAIL 的消息生效；关闭时不生成 track_token。
 * </p>
 *
 * <pre>
 * plugin:
 *   message:
 *     track:
 *       enabled: true                       # 是否开启阅读追踪，默认 false
 *       base-url: "http://your-host:8080"   # 追踪地址基础路径；为空时不注入追踪图
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
     * <p>
     * true 时对可追踪的 HTML 邮件注入 1x1 追踪图并生成 track_token；
     * 默认 false，不追踪，日志 read_status 恒为空。
     * </p>
     *
     * <pre>
     * plugin:
     *   message:
     *     track:
     *       enabled: true
     * </pre>
     */
    private boolean enabled = false;

    /**
     * 追踪地址基础路径
     * <p>
     * 用于拼接追踪图地址：{base-url}/v2/message/track/{token}，末尾多余的 / 会被去除。
     * 为空（null 或空白）时「不注入追踪图」，邮件内容原样发送——代码不会回退为当前服务地址。
     * </p>
     *
     * <pre>
     * plugin:
     *   message:
     *     track:
     *       base-url: "http://your-host:8080"
     * </pre>
     */
    private String baseUrl;
}
