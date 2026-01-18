package com.chua.starter.oauth.client.support.configuration;

import com.chua.common.support.base.converter.Converter;
import com.chua.common.support.math.unit.name.NamingCase;
import com.chua.common.support.core.utils.MapUtils;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.annotation.TokenValue;
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
public class TokenRequestHandlerMethodArgumentResolver extends UserRequestHandlerMethodArgumentResolver {

    private final WebRequest webRequest;
    @Setter
    private ConfigurableBeanFactory configurableBeanFactory;
    private BeanExpressionContext expressionContext;
    private ConversionService conversionService;

    public TokenRequestHandlerMethodArgumentResolver(WebRequest webRequest) {
        super(webRequest);
        this.webRequest = webRequest;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return super.supportsParameter(parameter, TokenValue.class);
    }

    @Override
    @SuppressWarnings("ALL")
    public Object resolveArgument(MethodParameter methodParameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        TokenValue requestValue = methodParameter.getParameterAnnotation(TokenValue.class);
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
                    return StringUtils.defaultString(requestValue.value(),
                            StringUtils.defaultString(parameter.getName(), methodParameter.getParameterName()));
                }
        );
    }

}
