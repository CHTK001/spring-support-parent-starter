package com.chua.starter.common.support.logger;

import com.chua.common.support.lang.expression.parser.ExpressionParser;
import com.chua.common.support.lang.pinyin.Pinyin;
import com.chua.common.support.lang.pinyin.PinyinFactory;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.expression.SpelExpressionParser;
import com.chua.starter.common.support.utils.RequestUtils;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.AnnotatedElementUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Date;
import java.util.Optional;

/**
 * 切面
 *
 * @author CH
 */
@Lazy
public class LoggerPointcutAdvisor extends StaticMethodMatcherPointcutAdvisor implements InitializingBean {

    @Autowired(required = false)
    HttpServletRequest request;
    @Autowired(required = false)
    HttpServletResponse response;
    private LoggerService loggerService;
    private ApplicationContext applicationContext;

    public LoggerPointcutAdvisor(LoggerService loggerService) {
        this.loggerService = Optional.ofNullable(loggerService).orElse(DefaultLoggerService.getInstance());
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        if (Proxy.isProxyClass(targetClass)) {
            return false;
        }
        return AnnotatedElementUtils.hasAnnotation(method, Logger.class) ||
                AnnotatedElementUtils.hasAnnotation(targetClass, Logger.class);

    }

    @Override
    public void afterPropertiesSet() throws Exception {
        PinyinFactory pinyinFactory = ServiceProvider.of(PinyinFactory.class).getNewExtension("pinyin");
        this.setAdvice(new MethodInterceptor() {
            @Nullable
            @Override
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                SysLog sysLog = new SysLog();
                Method method = invocation.getMethod();
                Logger logger = method.getDeclaredAnnotation(Logger.class);
                ExpressionParser expressionParser = new SpelExpressionParser();

                sysLog.setLogMapping(RequestUtils.getUrl(request));
                sysLog.setCreateTime(new Date());
                sysLog.setMethodName(method.getName());
                sysLog.setClassName(method.getDeclaringClass().getName());
                sysLog.setLogName(logger.value());
                sysLog.setLogAction(logger.action());
                sysLog.setLogCode(getCode(logger));
                sysLog.setLogAddress(RequestUtils.getIpAddress(request));

                long startTime = System.currentTimeMillis();
                Object proceed = null;
                try {
                    proceed = invocation.proceed();
                } finally {
                    sysLog.setResult(proceed);
                    sysLog.setLogCost((System.currentTimeMillis() - startTime) / 1000);
                    expressionParser.setVariable("result", proceed);
                    expressionParser.setVariable("method", method);
                    expressionParser.setVariable("args", invocation.getArguments());
                    try {
                        sysLog.setLogContent(expressionParser.parseExpression(logger.content()).getStringValue());
                    } catch (Exception ignored) {
                    }
                    loggerService.save(sysLog);
                }

                return proceed;
            }

            private String getCode(Logger logger) {
                if (StringUtils.isNotEmpty(logger.code())) {
                    return logger.code();
                }
                StringBuilder stringBuilder = new StringBuilder();
                for (String s : logger.value().split("")) {
                    Pinyin pinyin = pinyinFactory.first(s);
                    if (null != pinyin) {
                        stringBuilder.append(pinyin.getFirst().toUpperCase());
                    }
                }

                return stringBuilder.toString();
            }
        });
    }

}
