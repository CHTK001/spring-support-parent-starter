package com.chua.starter.software.support.realtime;

import com.chua.socket.support.session.SocketSessionTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 软件安装进度实时通道配置
 *
 * <p>默认装配空发布器，保证 software 模块在没有实时通道时也能独立启动；
 * 宿主引入 socket starter 后，自动改用 {@link SocketInstallProgressPublisher}，
 * 把安装进度广播到全局 socket 连接。</p>
 *
 * <p><b>两个 Bean 为什么分成内外两层</b>：</p>
 * <ul>
 *   <li>{@code spring-support-socket-starter} 是 optional 依赖，宿主完全可能没有把它放进
 *       运行期 classpath。若把「参数类型是 {@code SocketSessionTemplate}」的 {@code @Bean}
 *       方法写在当前类上，Spring 反射 {@code getDeclaredMethods()} 解析 Bean 方法签名时
 *       会因为缺少参数类型抛 {@code NoClassDefFoundError}，整个应用起不来。
 *       {@code @ConditionalOnClass} 决定的是「要不要创建 Bean」，不是「要不要反射这个方法」。
 *       放进嵌套类后，外层类的反射从不触碰 socket 类型，嵌套类又只在
 *       {@code @ConditionalOnClass} 命中时才被解析。</li>
 *   <li>{@link ConditionalOnMissingBean} 般在注册时求值。Spring 解析配置类时
 *       <b>先处理成员类、再收集本类的 {@code @Bean} 方法</b>，所以嵌套类的 socket 发布器
 *       一定先注册；外层的空实现随后求值，发现已有同类 Bean 便自动让位。
 *       反过来写（空实现先注册）会让 socket 发布器被判定「已存在同类 Bean」而静默跳过，
 *       表现为「明明引入了 socket 却永远收不到进度，且不报错」。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class SoftwareRealtimeConfiguration {

    /**
     * 空发布器：只记录 debug 日志，不做任何网络发送
     *
     * <p>嵌套类里的 socket 发布器已注册时本方法让位；socket 缺失时由本方法兜底，
     * 保证 {@code InstallProgressPublisher} 这个依赖永远可注入。</p>
     *
     * @return 空发布器
     */
    @Bean
    @ConditionalOnMissingBean(InstallProgressPublisher.class)
    public InstallProgressPublisher noopInstallProgressPublisher() {
        return event -> log.debug("[Software][Progress] 实时通道未启用，跳过: recordId={}, progress={}",
                event.recordId(), event.progress());
    }

    /**
     * 宿主引入 socket starter 时才生效的实时通道配置
     *
     * <p>用 {@code @ConditionalOnClass} 的字符串形式：条件判断读的是注解里的字符串常量，
     * 不需要先把类加载进来，因此 socket 缺失时这一段会被静默跳过。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.chua.socket.support.session.SocketSessionTemplate")
    public static class SocketProgressConfiguration {

        /**
         * 基于全局 socket 通道的安装进度发布器
         *
         * <p>带 {@link ConditionalOnMissingBean}：业务方若自己提供了发布器（如落库 + 推送双写），
         * 这里让位而不是制造同类型双 Bean。嵌套配置先于外层被解析，因此本方法求值时
         * 外层的空实现尚未注册，不会被它误挡。</p>
         *
         * @param templateProvider 会话模板提供者（延迟解析，socket 未启用时为空）
         * @return 广播发布器
         */
        @Bean
        @ConditionalOnMissingBean(InstallProgressPublisher.class)
        public InstallProgressPublisher socketInstallProgressPublisher(
                ObjectProvider<SocketSessionTemplate> templateProvider) {
            return new SocketInstallProgressPublisher(templateProvider);
        }
    }
}
