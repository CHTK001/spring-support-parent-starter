package com.chua.starter.oauth.client.support.configuration;

import com.chua.common.support.converter.Converter;
import com.chua.common.support.unit.name.NamingCase;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.annotation.UserValue;
import com.chua.starter.oauth.client.support.entity.RoleJudge;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.oauth.client.support.web.WebRequest;
import jakarta.servlet.http.HttpServletRequest;
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

import java.lang.reflect.Parameter;
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
    @SuppressWarnings("ALL")
    public Object resolveArgument(MethodParameter methodParameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        UserValue requestValue = methodParameter.getParameterAnnotation(UserValue.class);
        WebRequest webRequest1 = new WebRequest(
                this.webRequest.getAuthProperties(),
                webRequest.getNativeRequest(HttpServletRequest.class), null);
        UserResume userResume = analysis(webRequest1);
        Parameter parameter = methodParameter.getParameter();
        Class<?> parameterType = parameter.getType();

        if(RoleJudge.class.isAssignableFrom(parameterType)) {
            return new RoleJudge(userResume.getRoles());
        }

        if(UserResume.class.isAssignableFrom(parameterType)) {
            return userResume;
        }
        String paramName = StringUtils.defaultString(requestValue.name(), StringUtils.defaultString(parameter.getName(), methodParameter.getParameterName()));
        if("userId".equalsIgnoreCase(paramName)) {
            return Converter.convertIfNecessary(userResume.getUserId(), parameterType);
        }

        Map<String, Object> cacheValue = asMap(webRequest, userResume);

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

    /**
     * 转为map
     *
     * @param returnResult 结果
     * @param webRequest
     * @return 结果
     */
    @SuppressWarnings("ALL")
    private Map<String, Object> asMap(NativeWebRequest webRequest, UserResume returnResult) {
        Map<String , Object> rs = new LinkedHashMap<>();
        rs.putAll(BeanMap.create(returnResult));
        rs.put("all", returnResult);
        rs.put("token", StringUtils.defaultString(
                webRequest.getHeader(this.webRequest.getAuthProperties().getTokenName()),
                CookieUtil.getValue(RequestUtils.getRequest(), "x-oauth-cookie")
        ));
        return rs;
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
    private UserResume analysis(WebRequest webRequest) {
        AuthenticationInformation authentication = webRequest.authentication();
        return authentication.getReturnResult();

    }
}
