package com.chua.starter.oauth.client.support.configuration;

import com.chua.common.support.unit.name.NamingCase;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.annotation.UserValue;
import com.chua.starter.oauth.client.support.exception.OauthException;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.oauth.client.support.web.WebRequest;
import lombok.Setter;
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

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户信息
 *
 * @author CH
 * @since 2022/7/25 16:48
 */
public class UserRequestHandlerMethodArgumentResolver implements HandlerMethodArgumentResolver {

    private final WebRequest webRequest;
    @Setter
    private ConfigurableBeanFactory configurableBeanFactory;
    private BeanExpressionContext expressionContext;
    private ConversionService conversionService;

    public UserRequestHandlerMethodArgumentResolver(WebRequest webRequest) {
        this.webRequest = webRequest;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (null == configurableBeanFactory) {
            try {
                configurableBeanFactory = (ConfigurableBeanFactory) SpringBeanUtils.getApplicationContext().getAutowireCapableBeanFactory();
            } catch (Throwable ignored) {
            }
        }

        if (null == configurableBeanFactory) {
            return false;
        }

        if (null == expressionContext) {
            this.expressionContext = new BeanExpressionContext(configurableBeanFactory, new RequestScope());
        }

        if (null == conversionService) {
            conversionService = configurableBeanFactory.getConversionService();
        }

        UserValue requestValue = parameter.getParameterAnnotation(UserValue.class);
        return null != requestValue;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        UserValue requestValue = parameter.getParameterAnnotation(UserValue.class);
        Map<String, Object> cacheValue = analysisAsParamMap(webRequest);
        Parameter parameter1 = parameter.getParameter();
        String paramName = StringUtils.defaultString(requestValue.name(), StringUtils.defaultString(parameter1.getName(), parameter.getParameterName()));

        Class<?> parameterType = parameter.getParameterType();
        Object o = cacheValue.get(paramName);

        if (null == o) {
            Map beanMap = com.chua.common.support.bean.BeanMap.create(cacheValue.get("ext"));
            o = MapUtils.getString(beanMap, paramName);
            if (null == o) {
                o = MapUtils.getString(beanMap, NamingCase.toCamelCase(paramName));
            }
        }


        if (null == o) {
            o = requestValue.defaultValue();
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
                newInstance = parameterType.newInstance();
            } catch (Exception e) {
                return null;
            }
            BeanMap beanMap = BeanMap.create(newInstance);
            beanMap.putAll(cacheValue);
            return beanMap.getBean();
        }


        return convert;
    }

    private Object resolveEmbeddedValuesAndExpressions(String value) {
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
    private Map<String, Object> analysisAsParamMap(NativeWebRequest webRequest) {
        WebRequest webRequest1 = new WebRequest(
                this.webRequest.getAuthProperties(),
                webRequest.getNativeRequest(HttpServletRequest.class), null);

        Map<String, Object> rs = new LinkedHashMap<>();
        rs.put("token", StringUtils.defaultString(
                webRequest.getHeader(this.webRequest.getAuthProperties().getTokenName()),
                        CookieUtil.getValue(RequestUtils.getRequest(), "x-oauth-cookie")
                ));

        AuthenticationInformation authentication = webRequest1.authentication();
        UserResume returnResult = authentication.getReturnResult();
        if(null == returnResult) {
            return Collections.emptyMap();
        }
        rs.putAll(returnResult);
        rs.put("all", returnResult);
        rs.remove("password");
        rs.remove("beanType");
        rs.remove("salt");
        Object ext = rs.get("ext");
        if (null != ext && ext instanceof Map) {
            ((Map<?, ?>) ext).remove("password");
            ((Map<?, ?>) ext).remove("salt");
        }
        returnResult.remove("password");
        returnResult.remove("salt");
        returnResult.remove("beanType");
        Object ext1 = returnResult.get("ext");
        if (null != ext1 && ext1 instanceof Map) {
            ((Map<?, ?>) ext1).remove("password");
            ((Map<?, ?>) ext1).remove("salt");
        }
        return rs;
    }
}
