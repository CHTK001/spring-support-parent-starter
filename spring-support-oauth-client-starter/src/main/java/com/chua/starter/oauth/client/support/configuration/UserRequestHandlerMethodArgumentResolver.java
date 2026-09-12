package com.chua.starter.oauth.client.support.configuration;

import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.lang.NamingCase;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.annotation.TokenValue;
import com.chua.starter.oauth.client.support.annotation.UserValue;
import com.chua.starter.oauth.client.support.entity.RoleJudge;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.oauth.client.support.web.WebRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.cglib.beans.BeanMap;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestScope;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 用户信息
 *
 * @since 2022/7/25 16:48
 * @author CH
 */
public class UserRequestHandlerMethodArgumentResolver implements HandlerMethodArgumentResolver {

    /** Web请求 */
    private final WebRequest webRequest;
    /** Configurablebean工厂 */
    private ConfigurableBeanFactory configurableBeanFactory;
    /** 表达式上下文 */
    private BeanExpressionContext expressionContext;
    /** 转换服务 */
    private ConversionService conversionService;

    /**
      * 创建 用户请求处理器方法参数解析器 实例
     * @param webRequest web请求
     */
    public UserRequestHandlerMethodArgumentResolver(WebRequest webRequest) {
        this.webRequest = webRequest;
    }

    /**
     * 设置configurableBean工厂
     *
     * @param configurableBeanFactory configurableBean工厂
     */
    public void setConfigurableBeanFactory(ConfigurableBeanFactory configurableBeanFactory) {
        this.configurableBeanFactory = configurableBeanFactory;
        if (configurableBeanFactory == null) {
            return;
        }
        this.expressionContext = new BeanExpressionContext(configurableBeanFactory, new RequestScope());
        this.conversionService = configurableBeanFactory.getConversionService();
    }

    @Override
    /** 支持参数 */
    public boolean supportsParameter(MethodParameter parameter) {
        return this.supportsParameter(parameter, UserValue.class);
    }

    @Override
    @SuppressWarnings("ALL")
    /**
      * 解析参数
     * @param methodParameter 方法参数
     * @param mavContainer mav容器
     * @param webRequest web请求
     * @param binderFactory binder工厂
     */
    public Object resolveArgument(MethodParameter methodParameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) throws Exception {
        UserValue requestValue = methodParameter.getParameterAnnotation(UserValue.class);
        Parameter parameter = methodParameter.getParameter();
        return resolveEmbeddedValuesAndExpressions(
                methodParameter,
                mavContainer,
                webRequest,
                binderFactory,
                methodParameter1 -> {
                    return requestValue.defaultValue();
                },
                methodParameter1 -> {
                    return StringUtils.defaultString(requestValue.name(),
                            StringUtils.defaultString(parameter.getName(), methodParameter.getParameterName()));
                }
        );
    }

    /**
     * 解析参数
     *
     * @param mavContainer    容器
     * @param webRequest      请求
     * @param binderFactory   工厂
     * @return 结果
     */

    public Object resolveEmbeddedValuesAndExpressions(MethodParameter methodParameter,
                                                      ModelAndViewContainer mavContainer,
                                                      NativeWebRequest webRequest,
                                                      WebDataBinderFactory binderFactory,
                                                      Function<MethodParameter, String> defaultFunction,
                                                      Function<MethodParameter, String> nameFunction) throws Exception {
        WebRequest webRequest1 = new WebRequest(
                this.webRequest.getAuthProperties(),
                webRequest.getNativeRequest(HttpServletRequest.class), null);
        UserResume userResume = analysis(webRequest1);
        Parameter parameter = methodParameter.getParameter();
        Class<?> parameterType = parameter.getType();

        if (RoleJudge.class.isAssignableFrom(parameterType)) {
            /**
             * 角色judge。
             * @param userResume.getRoles( 用户resume.getroles(
             * @return 角色judge的结果
             */
            return new RoleJudge(userResume.getRoles());
        }

        if (UserResume.class.isAssignableFrom(parameterType)) {
            return userResume;
        }

        String paramName = nameFunction.apply(methodParameter);
        if ("userId".equalsIgnoreCase(paramName)) {
            return conversionService.convert(userResume.getUserId(), parameterType);
        }

        if (null == userResume) {
            return null;
        }

        Map<String, Object> cacheValue = asMap(webRequest, userResume);

        Object o = cacheValue.get(paramName);

        if (null == o) {
 // ext 为扩展信息 映射，直接按键取值；兼容 ext 为 POJO 的情况走 Bean映射
            Object extValue = cacheValue.get("ext");
            if (extValue instanceof Map<?, ?> extMap) {
                o = extMap.get(paramName);
                if (null == o) {
                    o = extMap.get(NamingCase.toCamelCase(paramName));
                }
            } else {
                Map<String, Object> beanMap = com.chua.starter.common.support.bean.BeanMap.of(extValue, true);
                o = MapUtils.getString(beanMap, paramName);
                if (null == o) {
                    o = MapUtils.getString(beanMap, NamingCase.toCamelCase(paramName));
                }
            }
        }


        if (null == o) {
            o = defaultFunction.apply(methodParameter);
        }


        if (o instanceof String) {
            o = resolveEmbeddedValuesAndExpressions(o.toString());
        }

        if (parameterType.isPrimitive()) {
            return conversionService.convert(o, parameterType);
        }

        Object convert = null;
        if (conversionService.canConvert(o.getClass(), parameterType)) {
            try {
                convert = conversionService.convert(o, parameterType);
            } catch (Exception ignored) {
            }
        }
        if (null == convert) {
            Object newInstance = null;
            try {
                newInstance = parameterType.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                return null;
            }
            BeanMap beanMap = BeanMap.create(newInstance);
            beanMap.putAll(cacheValue);
            return beanMap.getBean();
        }


        return convert;
    }

    /**
      * 转为映射
     *
     * @param returnResult 结果
     * @param webRequest
     * @return 结果
     */
    @SuppressWarnings("ALL")
    protected Map<String, Object> asMap(NativeWebRequest webRequest, UserResume returnResult) {
        Map<String, Object> rs = new LinkedHashMap<>();
        rs.putAll(BeanMap.create(returnResult));
        rs.put("all", returnResult);
        rs.put("token", StringUtils.defaultString(
                webRequest.getHeader(this.webRequest.getAuthProperties().getTokenName()),
                CookieUtil.getValue(RequestUtils.getRequest(), "x-oauth-cookie")
        ));
        return rs;
    }

    /**
     * 解析embedded价值观andexpressions
     *
     * @param value 价值观
     * @return resolveembedded价值观andexpressions的结果
     */
    protected Object resolveEmbeddedValuesAndExpressions(String value) {
        if (this.configurableBeanFactory == null || this.expressionContext == null) {
            return value;
        }
        String placeholdersResolved = this.configurableBeanFactory.resolveEmbeddedValue(value);
        BeanExpressionResolver exprResolver = this.configurableBeanFactory.getBeanExpressionResolver();
        if (exprResolver == null) {
            return value;
        }
        return exprResolver.evaluate(placeholdersResolved, this.expressionContext);
    }

    /**
     * 分析参数
     *
     * @param webRequest 请求参数
     * @return 结果
     */
    protected UserResume analysis(WebRequest webRequest) {
        AuthenticationInformation authentication = webRequest.authentication();
        return authentication.getReturnResult();

    }

    /**
     * 支持参数
     *
     * @param annotationType 注解类型
     * @return 结果
     * @param methodParameter 方法参数
     */
    protected boolean supportsParameter(MethodParameter methodParameter, Class<? extends Annotation> annotationType) {
        if (null == configurableBeanFactory) {
            return false;
        }

        return null != methodParameter.getParameterAnnotation(annotationType);
    }
}