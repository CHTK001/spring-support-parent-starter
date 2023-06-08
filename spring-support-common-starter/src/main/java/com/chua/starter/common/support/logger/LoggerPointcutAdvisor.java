package com.chua.starter.common.support.logger;

import com.chua.common.support.lang.StopWatch;
import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.lang.pinyin.Pinyin;
import com.chua.common.support.lang.pinyin.PinyinFactory;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.RandomUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.result.ResultData;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.common.support.watch.NewTrackManager;
import com.chua.starter.common.support.watch.Span;
import com.chua.starter.common.support.watch.WatchPointcutAdvisor;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.expression.BeanFactoryAccessor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

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

    ExpressionParser expressionParser = new org.springframework.expression.spel.standard.SpelExpressionParser();
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

    private static final Cache<String, Boolean> CACHE = CacheBuilder.newBuilder().expireAfterWrite(3, TimeUnit.SECONDS)
            .build();

    @Override
    public void afterPropertiesSet() throws Exception {
        PinyinFactory pinyinFactory = ServiceProvider.of(PinyinFactory.class).getNewExtension("pinyin");
        this.setAdvice(new MethodInterceptor() {
            @Nullable
            @Override
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                Object proceed = null;
                int status = 0;
                Throwable e1 = null;
                try {
                    proceed = invocation.proceed();
                } catch (Throwable e) {
                    status = 1;
                    e1 = e;
                }
                try {
                    saveLog(proceed, invocation, status);
                } catch (Exception ignored) {
                }

                if(status == 1) {
                    throw new RuntimeException(e1);
                }

                return proceed;
            }

            private void saveLog(Object proceed, MethodInvocation invocation, int status) {
                Method method = invocation.getMethod();
                String address = RequestUtils.getIpAddress(request);
                DateTime now = DateTime.now();
                Date date = now.toDate();
                HttpSession session = request.getSession();

                Logger logger = method.getDeclaredAnnotation(Logger.class);
                String key = (String) session.getAttribute("userId") + logger.value() + logger.action() + address;
                Boolean ifPresent = CACHE.getIfPresent(key);
                if(null != ifPresent) {
                    return;
                }

                CACHE.put(key, true);
                SysLog sysLog = new SysLog();
                StandardEvaluationContext standardEvaluationContext = new StandardEvaluationContext(applicationContext);
                standardEvaluationContext.addPropertyAccessor(new BeanFactoryAccessor());
                sysLog.setLogMapping(RequestUtils.getUrl(request));
                sysLog.setCreateTime(date);
                try {
                    sysLog.setCreateName((String) session.getAttribute("username"));
                    sysLog.setCreateBy((String) session.getAttribute("userId"));
                } catch (Exception ignored) {
                }
                sysLog.setMethodName(method.getName());
                sysLog.setClassName(method.getDeclaringClass().getName());
                sysLog.setLogName(logger.value());
                sysLog.setLogAction(logger.action());
                sysLog.setLogCode(getCode(logger));
                sysLog.setLogAddress(address);

                standardEvaluationContext.setVariable("ip", RequestUtils.getIpAddress());
                Object username = request.getSession().getAttribute("username");
                standardEvaluationContext.setVariable("current_username", null == username ? "": username);
                standardEvaluationContext.setVariable("now", now.toStandard());
                standardEvaluationContext.setVariable("nowDate", now);
                standardEvaluationContext.setVariable("result", proceed);
                standardEvaluationContext.setVariable("method", method);
                standardEvaluationContext.setVariable("args", invocation.getArguments());
                recordLog(sysLog, proceed, standardEvaluationContext, logger, invocation, status);
            }

            private void recordLog(SysLog sysLog, Object proceed, StandardEvaluationContext standardEvaluationContext, Logger logger, MethodInvocation invocation, int status) {
                long startTime = sysLog.getCreateTime().getTime();
                sysLog.setResult(proceed);
                sysLog.setLogCost((System.currentTimeMillis() - startTime) / 1000);

                Method method = invocation.getMethod();

                sysLog.setLogStatus("0");
                if(proceed instanceof ResultData) {
                    sysLog.setLogStatus(((ResultData<?>) proceed).getCode());
                }

                Stack<Span> spans = NewTrackManager.currentSpans();
                List<Span> build = WatchPointcutAdvisor.build(spans);
                StopWatch stopWatch = new StopWatch(method.getDeclaringClass().getTypeName() + "#" + method.getName());
                int size = build.size();
                if(size > 10) {
                    List<Span> newBuild = new ArrayList<>(10);
                    newBuild.addAll(build.subList(0, 2));
                    int min = RandomUtils.randomInt(3, size - 3);
                    IntStream.of(size, size + 6).forEach(i -> {
                        newBuild.add(build.get(min));
                    });
                    newBuild.addAll(build.subList(size - 2, size));
                }
                for (Span span : build) {
                    stopWatch.start(span.getTypeMethod());
                    stopWatch.stop();
                }


                sysLog.setLogWatch(stopWatch.prettyPrint(TimeUnit.MILLISECONDS));
                try {//+ ' 账号在( '+ #ip + ' )'  + #args[0].username + '登录系统(状态: ' + #result['code'] + '  '   #result['msg'] + ') 登录方式('WEB' ) '
                    sysLog.setLogContent(expressionParser.parseExpression(
                                    logger.content(), new TemplateParserContext())
                            .getValue(standardEvaluationContext, String.class));
                } catch (Exception ignored) {
                }
                loggerService.save(sysLog);
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
