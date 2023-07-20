package com.chua.starter.common.support.logger;

import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.view.view.TreeView;
import com.chua.common.support.view.view.TreeViewNode;
import com.chua.starter.common.support.result.ResultData;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.common.support.watch.NewTrackManager;
import com.chua.starter.common.support.watch.Span;
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

import static com.chua.starter.common.support.logger.LogGuidAspect.GuidhreadLocal;

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
        this.setAdvice(new MethodInterceptor() {
            @Nullable
            @Override
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                return invokeValue(invocation);
            }



        });
    }

    protected Object invokeValue(MethodInvocation invocation) {
        Object proceed = null;
        int status = 0;
        long startTime = System.nanoTime();
        Throwable e1 = null;
        try {
            proceed = invocation.proceed();
        } catch (Throwable e) {
            status = 1;
            e1 = e;
        }
        try {
            saveLog(proceed, invocation, status, startTime);
        } catch (Exception ignored) {
        }

        if(status == 1) {
            throw new RuntimeException(e1);
        }

        return proceed;
    }

    protected void saveLog(Object proceed, MethodInvocation invocation, int status, long startTime) {
        Method method = invocation.getMethod();
        String address = RequestUtils.getIpAddress(request);
        DateTime now = DateTime.now();
        Date date = now.toDate();
        HttpSession session = request.getSession();

        Logger logger = method.getDeclaredAnnotation(Logger.class);
        String key = session.getAttribute("userId") + logger.value() + logger.action() + address;
        Boolean ifPresent = CACHE.getIfPresent(key);
        if (null != ifPresent) {
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
        sysLog.setLogCode(GuidhreadLocal.get());
        sysLog.setLogAddress(address);

        standardEvaluationContext.setVariable("ip", RequestUtils.getIpAddress());
        Object username = request.getSession().getAttribute("username");
        standardEvaluationContext.setVariable("current_username", null == username ? "": username);
        standardEvaluationContext.setVariable("now", now.toStandard());
        standardEvaluationContext.setVariable("nowDate", now);
        standardEvaluationContext.setVariable("result", proceed);
        standardEvaluationContext.setVariable("method", method);
        standardEvaluationContext.setVariable("args", invocation.getArguments());
        recordLog(sysLog, proceed, standardEvaluationContext, logger, status, startTime, expressionParser, null);
    }


    public void recordLog(SysLog sysLog,
                                 Object proceed,
                                 StandardEvaluationContext standardEvaluationContext,
                                 Logger logger,
                                 int status,
                                 long startTime,
                                 ExpressionParser expressionParser,
                                  String content
                                 ) {
        sysLog.setResult(proceed);
        sysLog.setLogCost((System.nanoTime() - startTime) / 1000000.0);

        sysLog.setLogStatus("1");
        if(status == 1) {
            sysLog.setLogStatus("0");
        } if (proceed instanceof ResultData) {
            sysLog.setLogStatus(((ResultData<?>) proceed).getCode());
        }

        Stack<Span> spans = NewTrackManager.currentSpans();
        TreeViewNode treeViewNode = TreeViewNode.newBuilder("root");
        Map<String, TreeViewNode> pid = new HashMap<>(spans.size());
        long endTime = spans.get(spans.size() - 1).getEnterTime();
        for (int i = 0; i < spans.size(); i++) {
            if (i > 15) {
                break;
            }
            Span item = spans.get(i);
            TreeViewNode treeViewNode1 = null;
            if (i == 0) {
                treeViewNode1 = TreeViewNode.newBuilder(item.getTypeMethod());
            } else {
                treeViewNode1 = new TreeViewNode(pid.get(item.getPid()), item.getTypeMethod());
            }
            treeViewNode1.totalCost = item.getCostTime();
            treeViewNode1.beginTimestamp = item.getEnterTime();
            if (endTime > 0L) {
                treeViewNode1.endTimestamp = endTime;
            }
            pid.put(item.getId(), treeViewNode1);
            treeViewNode.addChildren(treeViewNode1);
        }
        TreeView treeView = new TreeView(true, treeViewNode);
        sysLog.setLogWatch(treeView.draw());
        if(null == content && null != expressionParser) {
            try {//+ ' 账号在( '+ #ip + ' )'  + #args[0].username + '登录系统(状态: ' + #result['code'] + '  '   #result['msg'] + ') 登录方式('WEB' ) '
                sysLog.setLogContent(expressionParser.parseExpression(
                                logger.content(), new TemplateParserContext())
                        .getValue(standardEvaluationContext, String.class));
            } catch (Exception ignored) {
            }
        } else {
            sysLog.setLogContent(content);
        }
        loggerService.save(sysLog);
    }

}
