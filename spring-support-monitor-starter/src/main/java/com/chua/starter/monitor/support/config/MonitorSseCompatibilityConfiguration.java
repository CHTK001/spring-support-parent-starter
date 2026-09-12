package com.chua.starter.monitor.support.config;

import com.chua.starter.sse.support.socket.SseSocketSessionTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
   * SSE 会话模板兼容 Bean（监控 独立联调用）
 * <p>
   * 服务端-启动 的 {@code MailboxSseService}（邮箱 SSE 推送 @服务）构造注入
 * {@code ObjectProvider<SseSocketSessionTemplate>}。socketio 单协议模式下该具体类型
   * 无容器 Bean（套接字配置 仅按协议把 template 存入 映射），Spring 7 严格校验
   * 会直接抛 Bean创建异常 导致整个应用启动失败。此处条件化补一个空实现 Bean，
   * 保证容器可启动；监控 业务不创建 SSE 连接，此 Bean 不参与任何推送。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Configuration
@ConditionalOnClass(SseSocketSessionTemplate.class)
@ConditionalOnProperty(prefix = "plugin.socket", name = "enable", havingValue = "true")
public class MonitorSseCompatibilityConfiguration {

    /**
      * SSE 会话模板（无连接的空实现，仅供 mailboxsse服务 构造注入）
     *
     * @return SseSocketSessionTemplate 实例
     */
    @Bean
    public SseSocketSessionTemplate sseSocketSessionTemplate() {
        /**
         * sse套接字会话template。
         * @return sse套接字会话template的结果
         */
        return new SseSocketSessionTemplate();
    }
}
