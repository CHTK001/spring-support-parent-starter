package com.chua.starter.common.support.log;

import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import reactor.core.publisher.Hooks;

/**
 * Reactor 响应式链路的 MDC 自动传播配置。
 *
 * <p>一次性开启 Reactor 官方的 automatic context propagation
 * （{@code Hooks.enableAutomaticContextPropagation()}，Reactor 3.5+），
 * 并把 {@link MdcThreadLocalAccessor} 注册进 {@code ContextRegistry}：
 * 此后所有 {@code Flux}/{@code Mono} 在订阅时自动快照订阅线程的 MDC，
 * 信号在任意线程执行前恢复、执行后还原。</p>
 *
 * <p>覆盖场景：MVC 下 Controller 返回 Flux/Mono 的异步段、WebClient 响应回调、
 * flatMap 内层链路、publishOn / subscribeOn 线程切换；后台调度等无请求上下文
 * 的链路快照为空，自动退化为不传播，不会误写入任何 traceId。</p>
 *
 * <p>条件装配：仅当 classpath 同时存在 Reactor 与 micrometer context-propagation
 * 时生效，纯 Servlet 宿主不受影响。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@ConditionalOnClass(name = {"reactor.core.publisher.Flux", "io.micrometer.context.ContextRegistry"})
public class MdcReactorPropagationConfiguration {

    /**
     * 日志。
     */
    private static final Logger log = LoggerFactory.getLogger(MdcReactorPropagationConfiguration.class);

    /**
     * 注册 MDC 适配器并开启 Reactor 自动上下文传播。
     * <p>必须在任何响应式订阅发生前执行；自动装配阶段满足该时机。</p>
     */
    @PostConstruct
    public void enablePropagation() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(new MdcThreadLocalAccessor());
        Hooks.enableAutomaticContextPropagation();
        log.info(">>>>>>> Reactor MDC 自动传播已开启 [Hooks.enableAutomaticContextPropagation]");
    }
}
