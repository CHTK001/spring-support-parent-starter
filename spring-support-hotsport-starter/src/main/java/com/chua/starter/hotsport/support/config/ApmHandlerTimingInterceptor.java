package com.chua.starter.hotsport.support.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 控制器方法耗时采集 —— 记录本次请求真实命中的 {@code class.method} 与它自身的真实耗时。
 *
 * <p>{@link SlowCallFilter} 站在 Servlet 层，只拿得到 {@code METHOD URL}；要还原
 * 「首层 HTTP → 子层 class.method」的调用层级，就必须知道 Spring MVC 实际分派到了
 * 哪个控制器方法、以及这个方法自身消耗了多少毫秒。本拦截器在 {@code preHandle}
 * 打点、{@code afterCompletion} 结算，把结果写进请求属性，由 {@link SlowCallFilter}
 * 随传输记录一起落库。</p>
 *
 * <p>采集到的三个值全部是实测值，不做任何估算：类名/方法名取自真实的
 * {@link HandlerMethod}，耗时取自真实的时间差。</p>
 *
 * <p>本类只做观测：任何异常都被吞掉并降级为「本次不记录」，绝不阻断宿主请求。</p>
 *
 * @author CH
 * @since 4.1.0
 */
@Slf4j
public class ApmHandlerTimingInterceptor implements HandlerInterceptor {

    /**
     * 命中控制器类名（真实 {@code HandlerMethod#getBeanType()}，已脱掉 CGLIB 代理）。
     */
    public static final String ATTR_CLASS = "apm.handler.class";

    /**
     * 命中控制器方法名（真实 {@code Method#getName()}）。
     */
    public static final String ATTR_METHOD = "apm.handler.method";

    /**
     * 控制器方法开始时间（epoch 毫秒）。
     */
    public static final String ATTR_START = "apm.handler.start";

    /**
     * 控制器方法自身耗时（毫秒）。
     */
    public static final String ATTR_DURATION = "apm.handler.duration";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        try {
            if (handler instanceof HandlerMethod handlerMethod) {
                Class<?> beanType = ClassUtils.getUserClass(handlerMethod.getBeanType());
                request.setAttribute(ATTR_CLASS, beanType.getName());
                request.setAttribute(ATTR_METHOD, handlerMethod.getMethod().getName());
                request.setAttribute(ATTR_START, System.currentTimeMillis());
            }
        } catch (Exception e) {
            log.debug("[Hotsport] 控制器打点失败: {}", e.getMessage());
        }
        // 永不拦截：观测组件不能改变请求走向
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            Object start = request.getAttribute(ATTR_START);
            if (start instanceof Long begin && begin > 0) {
                long duration = System.currentTimeMillis() - begin;
                request.setAttribute(ATTR_DURATION, Math.max(0L, duration));
            }
        } catch (Exception e) {
            log.debug("[Hotsport] 控制器耗时结算失败: {}", e.getMessage());
        }
    }
}
