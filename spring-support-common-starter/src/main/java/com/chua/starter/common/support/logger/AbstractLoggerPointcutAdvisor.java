package com.chua.starter.common.support.logger;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.utils.AnnotationUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.chua.common.support.constant.NameConstant.*;

/**
 * 抽象日志切入点顾问
 * <p>提供日志切面的通用功能，包括参数提取、SpEL表达式解析、Swagger注解识别等</p>
 *
 * @author CH
 * @since 2024-01-01
 */
@Lazy
@Slf4j
public abstract class AbstractLoggerPointcutAdvisor extends StaticMethodMatcherPointcutAdvisor implements InitializingBean {

    /**
     * 请求指纹头
     */
    public static final String X_REQ_FINGERPRINT = "x-req-fingerprint";

    @Autowired
    protected HttpServletRequest request; // 请求

    @Autowired
    protected HttpServletResponse response; // 响应

    @Autowired
    protected ApplicationContext applicationContext; // application上下文

    /**
     * 按类名解析注解类型。
     *
     * @param className 全限定类名，不能为 null
     * @return 注解类型；类不存在时返回 null
     */
    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> resolveAnnotationClass(String className) {
        Class<?> clazz = ReflectUtils.forName(className);
        if (clazz == null) {
            return null;
        }
        return (Class<? extends Annotation>) clazz.asSubclass(Annotation.class);
    }

    /**
     * Swagger 3.x Operation 注解类
     */
    private static final Class<? extends Annotation> OPERATION = resolveAnnotationClass("io.swagger.v3.oas.annotations.Operation");

    /**
     * Swagger 2.x apioperation 注解类
     */
    private static final Class<? extends Annotation> API_OPERATION = resolveAnnotationClass("io.swagger.annotations.ApiOperation");

    /**
     * spel 表达式缓存，避免每次调用都重新解析
     */
    private static final ConcurrentHashMap<String, Expression> EXPRESSION_CACHE = new ConcurrentHashMap<>();
    private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser(); // EXPRESSION_PARSER
    private static final ParserContext TEMPLATE_PARSER_CONTEXT = new TemplateParserContext(); // templateparser上下文

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

    /**
     * 执行方法并记录日志
     *
     * @param invocation 方法调用
     * @return 方法返回值
     * @throws Throwable 方法执行异常
     */
    protected Object invokeValue(MethodInvocation invocation) throws Throwable {
        Object proceed = null;
        Throwable throwable = null;
        long startTime = System.currentTimeMillis();
        try {
            proceed = invocation.proceed();
        } catch (Throwable e) {
            throwable = e;
        }
        /**
         * 创建operate日志记录器。
         * @param proceed proceed
         * @param throwable 抛出
         * @param invocation invocation
         * @param startTime 启动时间
         */
        createOperateLogger(proceed, throwable, invocation, startTime);
        if (null != throwable) {
            throw throwable;
        }
        return proceed;
    }

    /**
     * 创建操作日志
     * <p>子类需要实现此方法来构建具体的日志信息对象并发布事件</p>
     *
     * @param proceed    方法执行结果
     * @param throwable  方法执行异常，为空表示执行成功
     * @param invocation 方法调用信息
     * @param startTime  方法执行开始时间戳
     */
    protected abstract void createOperateLogger(Object proceed, Throwable throwable, MethodInvocation invocation, long startTime);

    /**
     * 获取操作名称
     * <p>优先级：注解name > Swagger 3.x Operation.summary > Swagger 2.x ApiOperation.value</p>
     *
     * @param annotationName 注解中指定的名称
     * @param method         方法对象
     * @return 操作名称，如果无法获取返回null
     */
    protected String getName(String annotationName, Method method) {
        if (StringUtils.isNotBlank(annotationName)) {
            return annotationName;
        }

        // 从 Swagger 3.x 注解获取
        if (null != OPERATION) {
            Annotation declaredAnnotation = method.getDeclaredAnnotation(OPERATION);
            if (null != declaredAnnotation) {
                Map<String, Object> annotationAttributes = AnnotationUtils.getAnnotationAttributes(declaredAnnotation);
                String summary = MapUtils.getString(annotationAttributes, "summary");
                if (StringUtils.isNotBlank(summary)) {
                    return summary;
                }
            }
        }

        // 从 Swagger 2.x 注解获取（兼容老版本）
        if (null != API_OPERATION) {
            Annotation declaredAnnotation = method.getDeclaredAnnotation(API_OPERATION);
            if (null != declaredAnnotation) {
                Map<String, Object> annotationAttributes = AnnotationUtils.getAnnotationAttributes(declaredAnnotation);
                String value = MapUtils.getString(annotationAttributes, "value");
                if (StringUtils.isNotBlank(value)) {
                    return value;
                }
            }
        }

        return null;
    }

    /**
     * 根据方法名自动识别操作模块类型
     *
     * @param method 方法对象
     * @return 操作模块名称
     */
    protected String getModule(Method method) {
        String name = method.getName().toUpperCase();
        if (name.contains(SAVE) || name.contains(INSERT) || name.contains(ADD)) {
            return "添加";
        }
        if (name.contains(UPDATE) || name.contains(MODIFY)) {
            return "修改";
        }
        if (name.contains(DELETE) || name.contains(DROP) || name.contains(REMOVE)) {
            return "删除";
        }
        if (name.contains(RESET)) {
            return "重置";
        }
        return "查询";
    }

    /**
     * 构建请求参数的JSON字符串
     *
     * @param invocation 方法调用信息
     * @return 参数的JSON字符串，如果无参数或构建失败返回null
     */
    protected String buildParamJson(MethodInvocation invocation) {
        List<Object> params = new LinkedList<>();
        for (Object argument : invocation.getArguments()) {
            if (null == argument) {
                continue;
            }
            Class<?> aClass = argument.getClass();
            String typeName = aClass.getTypeName();
            if (HttpServletResponse.class.isAssignableFrom(aClass)
                    || HttpServletRequest.class.isAssignableFrom(aClass)
                    || typeName.startsWith("org.spring")
                    || typeName.startsWith("javax")
                    || typeName.startsWith("java")) {
                continue;
            }
            params.add(argument);
        }

        try {
            return Json.prettyFormat(params);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 解析spel表达式内容
     *
     * @param content    表达式内容
     * @param invocation 方法调用信息
     * @param proceed    方法返回值
     * @return 解析后的内容
     */
    protected String getContent(String content, MethodInvocation invocation, Object proceed) {
        if (StringUtils.isEmpty(content)) {
            return "";
        }

        // 复用缓存的 Expression，避免每次重新解析
        Expression expression = EXPRESSION_CACHE.computeIfAbsent(content,
                key -> EXPRESSION_PARSER.parseExpression(key, TEMPLATE_PARSER_CONTEXT));

        EvaluationContext evaluationContext = new StandardEvaluationContext();
        Method method = invocation.getMethod();
        Object[] arguments = invocation.getArguments();
        for (int i = 0; i < arguments.length; i++) {
            evaluationContext.setVariable("$arg" + i, arguments[i]);
            // 别名：模板里写 #{#arg0} 比 #{#$arg0} 更易读，且不依赖 SpEL
            // 是否把 '$' 当合法标识符起始字符（旧写法 $arg0 保留，行为不变）。
            evaluationContext.setVariable("arg" + i, arguments[i]);
        }
        evaluationContext.setVariable("$method", method);
        evaluationContext.setVariable("$result", proceed);

        return Optional.ofNullable(expression.getValue(evaluationContext)).orElse(content).toString();
    }

    /**
     * 发布日志事件
     *
     * @param event 日志事件对象
     */
    protected void publishEvent(Object event) {
        applicationContext.publishEvent(event);
    }

    /**
     * 获取用户标识
     *
     * <p>优先走 {@link RequestUtils}（网关注入的 X-User-Id 头/请求属性）；
     * 单体部署（OAuth 服务端与本服务同进程）下没有网关写头，此时回退到
     * {@link #resolveTokenUser(String)} 从令牌解析 —— classpath 无 oauth-client
     * 时回退静默失效，行为与原来一致。</p>
     *
     * @return 用户ID
     */
    protected String getUserId() {
        String value = RequestUtils.getUserId();
        if (null != value) {
            return value;
        }
        return resolveTokenUser("getUserId");
    }

    /**
     * 获取用户名
     *
     * <p>回退策略同 {@link #getUserId()}。</p>
     *
     * @return 用户名
     */
    protected String getUsername() {
        String value = RequestUtils.getUsername();
        if (null != value) {
            return value;
        }
        return resolveTokenUser("getUsername");
    }

    /**
     * 从请求令牌解析当前用户属性（单体部署的回退路径）。
     *
     * <p>单体模式没有网关写 {@code X-User-Id}/{@code X-Username} 头，而
     * AuthFilter（写 username 请求属性的组件）在 classpath 含 OAuth 服务端时
     * 被 {@code @ConditionalOnMissingClass(SsoServer)} 排除，导致操作日志的
     * 操作人恒为空。这里从 {@code Authorization: Bearer} / {@code x-oauth-token}
     * 头提取令牌，经 {@code AuthClientExecute#getUserResult(token)}（与
     * {@code /v2/user/me} 同一解析路径）还原身份。</p>
     *
     * <p>common-starter 不依赖 oauth-client（依赖倒置），因此用反射 + 一次性
     * 类探测：classpath 没有该类时永久跳过，所有反射元数据缓存，任何一步
     * 失败都静默返回 null —— 操作人缺失只是少一个字段，不能拖垮主流程。</p>
     *
     * @param getter 结果对象上的取值方法名（getUserId / getUsername）
     * @return 用户属性；取不到返回 null
     */
    private String resolveTokenUser(String getter) {
        if (!OAUTH_CLIENT_PRESENT) {
            log.debug("[操作日志] classpath 无 oauth-client，操作人无法从令牌还原（{}）", getter);
            return null;
        }
        try {
            HttpServletRequest current = RequestUtils.getRequest();
            if (null == current) {
                log.debug("[操作日志] 无当前请求（异步线程？），操作人无法还原（{}）", getter);
                return null;
            }
            String token = current.getHeader("x-oauth-token");
            if (StringUtils.isEmpty(token)) {
                String authorization = current.getHeader("Authorization");
                if (null != authorization && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    token = authorization.substring(7).trim();
                }
            }
            if (StringUtils.isEmpty(token)) {
                log.debug("[操作日志] 请求未带 x-oauth-token / Authorization，操作人无法还原（{}）", getter);
                return null;
            }
            // 这两个方法句柄可能为 null（类在但方法签名变了）—— 原来直接 invoke 会 NPE，
            // 被下面那个「什么都吞」的 catch 吃掉，表现为「操作人恒为空」而无任何线索。
            if (null == OAUTH_GET_INSTANCE || null == OAUTH_GET_USER_RESULT) {
                log.debug("[操作日志] AuthClientExecute 方法句柄缺失（instance={} userResult={}）",
                        null != OAUTH_GET_INSTANCE, null != OAUTH_GET_USER_RESULT);
                return null;
            }
            Object execute = OAUTH_GET_INSTANCE.invoke(null);
            if (null == execute) {
                log.debug("[操作日志] AuthClientExecute.getInstance() 返回 null");
                return null;
            }
            Object userResult = OAUTH_GET_USER_RESULT.invoke(execute, token);
            if (null == userResult) {
                log.debug("[操作日志] getUserResult(token) 返回 null，令牌可能已失效");
                return null;
            }
            Method accessor = userResult.getClass().getMethod(getter);
            Object value = accessor.invoke(userResult);
            // 取到了 null/空串时继续往下退：用户名取不到时用 userId 顶上，
            // 审计日志里「是谁」有一个可追溯的 id 也比整列为空强。
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
            String other = "getUsername".equals(getter) ? "getUserId" : "getUsername";
            try {
                Method alt = userResult.getClass().getMethod(other);
                Object altValue = alt.invoke(userResult);
                if (altValue instanceof String altText && !altText.isBlank()) {
                    return altText;
                }
            } catch (NoSuchMethodException ignored) {
                // 结果对象上只有一种身份取值方式，属正常情况
            }
            log.debug("[操作日志] getUserResult 返回对象上取不到非空身份（{} / {}）", getter, other);
            return null;
        } catch (Exception e) {
            // 令牌解析失败（过期/无 oauth-client/内部异常）只影响操作人字段，不拖垮主流程 ——
            // 但必须留下痕迹：完全静默时「操作人恒为空」这个真因永远查不出来。
            log.debug("[操作日志] 令牌解析用户失败（{}）：{}", getter, e.toString());
            return null;
        }
    }

    /**
     * oauth-client 类是否存在于 classpath（懒探测，一次性）。
     */
    private static final boolean OAUTH_CLIENT_PRESENT = detectOauthClient();

    /**
     * AuthClientExecute.getInstance() 的反射句柄（classpath 有该类时才可用）。
     */
    private static final Method OAUTH_GET_INSTANCE = findOauthMethod("getInstance");
    /**
     * AuthClientExecute.getUserResult(String) 的反射句柄。
     */
    private static final Method OAUTH_GET_USER_RESULT = findOauthMethod("getUserResult");

    /**
     * 探测 oauth-client 是否在 classpath。
     *
     * @return 存在返回 true
     */
    private static boolean detectOauthClient() {
        try {
            Class.forName("com.chua.starter.oauth.client.support.execute.AuthClientExecute");
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * 反射定位 AuthClientExecute 的静态方法；类缺失或方法不存在时返回 null（调用侧判空）。
     *
     * @param name 方法名
     * @return 方法句柄或 null
     */
    private static Method findOauthMethod(String name) {
        try {
            Class<?> clazz = Class.forName("com.chua.starter.oauth.client.support.execute.AuthClientExecute");
            return "getInstance".equals(name)
                    ? clazz.getMethod("getInstance")
                    : clazz.getMethod(name, String.class);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 获取客户端IP
     *
     * @return 客户端IP
     */
    protected String getClientIp() {
        return RequestUtils.getIpAddress(request);
    }

    /**
     * 获取请求URL
     *
     * @return 请求URL
     */
    protected String getRequestUrl() {
        return RequestUtils.getUrl(request);
    }

    /**
     * 获取请求头
     *
     * @param name 头名称
     * @return 头值
     */
    protected String getHeader(String name) {
        return RequestUtils.getHeader(request, name);
    }
}

