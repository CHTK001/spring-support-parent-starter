package com.chua.starter.osgi.support.realtime;

import com.chua.socket.support.session.SocketSessionTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 插件操作实时日志通道配置。
 *
 * <p>默认装配空发布器，保证 osgi 模块在没有实时通道时也能独立启动；
 * 宿主引入 socket starter 后，自动改用 {@link SocketOsgiLogPublisher}，
 * 把上传 / 安装 / 升级 / 启停 / 卸载 / 删除 jar 的日志广播到全局 socket 连接。</p>
 *
 * <p><b>两个 Bean 为什么分成内外两层</b>：</p>
 * <ul>
 *   <li>{@code spring-support-socket-starter} 是 optional 依赖，宿主完全可能没有把它放进
 *       运行期 classpath。若把「参数类型是 {@code SocketSessionTemplate}」的 {@code @Bean}
 *       方法写在当前类上，Spring 反射解析 Bean 方法签名时会因为缺少参数类型抛
 *       {@code NoClassDefFoundError}，整个应用起不来。放进嵌套类后，外层类的解析从不触碰
 *       socket 类型，嵌套类又只在 {@code @ConditionalOnClass} 命中时才被解析。</li>
 *   <li>{@link ConditionalOnMissingBean} 在注册时求值，而 Spring 解析配置类时
 *       <b>先处理成员类、再收集本类的 {@code @Bean} 方法</b>，所以嵌套类的 socket 发布器
 *       一定先注册；外层的空实现随后求值，发现已有同类 Bean 便自动让位。
 *       反过来写会让 socket 发布器被判定「已存在同类 Bean」而静默跳过，
 *       表现为「明明引入了 socket 却永远收不到日志，且不报错」。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@AutoConfiguration
public class OsgiRealtimeConfiguration {

    /**
     * 空发布器：只记录 debug 日志，不做任何网络发送。
     *
     * <p>嵌套类里的 socket 发布器已注册时本方法让位；socket 缺失时由本方法兜底，
     * 保证 {@link OsgiLogPublisher} 这个依赖永远可注入。</p>
     *
     * @return 空发布器
     */
    @Bean
    @ConditionalOnMissingBean(OsgiLogPublisher.class)
    public OsgiLogPublisher noopOsgiLogPublisher() {
        return event -> log.debug("[OSGi][Log] 实时通道未启用，跳过: action={}, status={}, symbolicName={}",
                event.action(), event.status(), event.symbolicName());
    }

    /**
     * 宿主引入 socket starter 时才生效的实时通道配置。
     *
     * <p>用 {@code @ConditionalOnClass} 的字符串形式：条件判断读的是注解里的字符串常量，
     * 不需要先把类加载进来，因此 socket 缺失时这一段会被静默跳过。</p>
     *
     * @author CH
     * @since 4.0.0.51
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.chua.socket.support.session.SocketSessionTemplate")
    public static class SocketLogConfiguration {

        /**
         * 基于全局 socket 通道的插件操作日志发布器。
         *
         * <p>带 {@link ConditionalOnMissingBean}：业务方若自己提供了发布器（如落库 + 推送双写），
         * 这里让位而不是制造同类型双 Bean。</p>
         *
         * @param templateProvider 会话模板提供者（延迟解析，socket 未启用时为空）
         * @return 广播发布器
         */
        @Bean
        @ConditionalOnMissingBean(OsgiLogPublisher.class)
        public OsgiLogPublisher socketOsgiLogPublisher(
                ObjectProvider<SocketSessionTemplate> templateProvider) {
            return new SocketOsgiLogPublisher(templateProvider);
        }
    }
}
