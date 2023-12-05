package com.chua.starter.unified.client.support.configuration;

import com.chua.common.support.function.Joiner;
import com.chua.common.support.protocol.boot.*;
import com.chua.common.support.task.limit.RateLimitMappingFactory;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.unified.client.support.aspect.UnifiedLimitAspect;
import com.chua.starter.unified.client.support.properties.UnifiedClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * OSHI配置
 *
 * @author CH
 */
@Slf4j
public class UnifiedRateLimitConfiguration implements ApplicationContextAware, Runnable {

    ProtocolServer protocolServer;
    ProtocolClient protocolClient;

    @Resource
    private UnifiedClientProperties unifiedClientProperties;

    private ApplicationContext applicationContext;

    private Environment environment;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        this.environment = applicationContext.getEnvironment();
        try {
            protocolServer = applicationContext.getBean(ProtocolServer.class);
            protocolServer.addMapping(RateLimitMappingFactory.getInstance());
            protocolClient = applicationContext.getBean(ProtocolClient.class);
            ThreadUtils.newStaticThreadPool().execute(this);
        } catch (BeansException ignored) {
        }
    }


    /**
     * 注册拦截器
     *
     * @return 注册拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public LimitMethodPointcutAdvisor methodPointcutAdvisor() {
        LimitMethodPointcutAdvisor methodPointcutAdvisor = new LimitMethodPointcutAdvisor();
        methodPointcutAdvisor.setAdvice(new MethodInterceptor() {
            @Nullable
            @Override
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                Method method = invocation.getMethod();
                if (isMapping(method)) {
                    UnifiedLimitAspect aspect = new UnifiedLimitAspect();
                    return aspect.globalControllerLimit(invocation, method);
                }
                return invocation.proceed();
            }
        });
        return methodPointcutAdvisor;
    }

    static class LimitMethodPointcutAdvisor extends StaticMethodMatcherPointcutAdvisor {
        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            if (Proxy.isProxyClass(targetClass) || targetClass.getTypeName().startsWith("org.springframework.web")) {
                return false;
            }
           return isMapping(method);
        }

    }
    private static boolean isMapping(Method method) {
        return
                AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class) ||
                        AnnotatedElementUtils.hasAnnotation(method, PostMapping.class) ||
                        AnnotatedElementUtils.hasAnnotation(method, GetMapping.class) ||
                        AnnotatedElementUtils.hasAnnotation(method, DeleteMapping.class) ||
                        AnnotatedElementUtils.hasAnnotation(method, PutMapping.class);
    }
    @Bean
    @ConditionalOnMissingBean
    public UnifiedLimitAspect unifiedLimitAspect() {
        return new UnifiedLimitAspect();
    }

    @Override
    public void run() {
        if(null == protocolClient) {
            return;
        }

        if(!unifiedClientProperties.isOpen()) {
            return;
        }

        try {
            UnifiedClientProperties.SubscribeOption subscribeOption = unifiedClientProperties.getSubscribeOption(ModuleType.LIMIT);
            List<String> subscribe = null == subscribeOption ? null : subscribeOption.getSubscribe();
            if(CollectionUtils.isEmpty(subscribe)) {
                return;
            }

            BootResponse response = protocolClient.get(BootRequest.builder()
                    .moduleType(ModuleType.LIMIT)
                    .commandType(CommandType.SUBSCRIBE)
                    .appName(environment.getProperty("spring.application.name"))
                    .profile(environment.getProperty("spring.profiles.active", "default"))
                    .content(Joiner.on(",").join(subscribe))
                    .build()
            );

            RateLimitMappingFactory.getInstance()
                    .limitConfig(BootRequest.builder()
                            .content(response.getContent())
                            .build());
        } catch (Exception ignored) {
        }
    }
}
