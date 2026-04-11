package com.chua.starter.oauth.client.support.configuration;

import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.oauth.client.support.annotation.TokenValue;
import com.chua.starter.oauth.client.support.web.WebRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.lang.reflect.Parameter;

/**
 * 用户信息
 *
 * @author CH
 * @since 2022/7/25 16:48
 */
public class TokenRequestHandlerMethodArgumentResolver extends UserRequestHandlerMethodArgumentResolver {

    public TokenRequestHandlerMethodArgumentResolver(WebRequest webRequest) {
        super(webRequest);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return super.supportsParameter(parameter, TokenValue.class);
    }

    @Override
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
