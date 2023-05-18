package com.chua.starter.common.support.processor;

import com.chua.starter.common.support.model.ModelView;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.util.PatternMatchUtils;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor;

import java.io.IOException;
import java.util.List;

/**
 * model view
 *
 * @author CH
 */
public class ResponseModelViewMethodProcessor extends RequestResponseBodyMethodProcessor {

    private String redirectPatterns;

    public ResponseModelViewMethodProcessor(List<HttpMessageConverter<?>> converters) {
        super(converters);
    }

    @Override
    public boolean supportsParameter(MethodParameter returnType) {
        Class<?> paramType = returnType.getParameterType();
        return ModelView.class.isAssignableFrom(paramType);
    }

    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        Class<?> paramType = returnType.getParameterType();
        return ModelView.class.isAssignableFrom(paramType);
    }

    @Override
    public void handleReturnValue(Object returnValue, MethodParameter returnType, ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws IOException, HttpMediaTypeNotAcceptableException, HttpMessageNotWritableException {
        ModelView modelView = (ModelView) returnValue;
        Object data = modelView.getData();

        String accept = getAccept(modelView, webRequest);
        if (!accept.contains(MediaType.TEXT_HTML_VALUE)) {
            mavContainer.setRequestHandled(true);
            ServletServerHttpRequest inputMessage = createInputMessage(webRequest);
            ServletServerHttpResponse outputMessage = createOutputMessage(webRequest);

            // Try even with null return value. ResponseBodyAdvice could get involved.
            writeWithMessageConverters(data, returnType, inputMessage, outputMessage);
            return;
        }

        if (data instanceof CharSequence) {
            String viewName = data.toString();
            mavContainer.setViewName(viewName);
            if (isRedirectViewName(viewName)) {
                mavContainer.setRedirectModelScenario(true);
            }
        } else if (returnValue != null) {
            // should not happen
            throw new UnsupportedOperationException("Unexpected return type: " +
                    returnType.getParameterType().getName() + " in method: " + returnType.getMethod());
        }
    }

    private String getAccept(ModelView modelView, NativeWebRequest webRequest) {
        MediaType mediaType = modelView.getMediaType();
        if (null != mediaType) {
            return mediaType.toString();
        }
        return webRequest.getHeader("accept");
    }


    /**
     * Whether the given view name is a redirect view reference.
     * The default implementation checks the configured redirect patterns and
     * also if the view name starts with the "redirect:" prefix.
     *
     * @param viewName the view name to check, never {@code null}
     * @return "true" if the given view name is recognized as a redirect view
     * reference; "false" otherwise.
     */
    protected boolean isRedirectViewName(String viewName) {
        return (PatternMatchUtils.simpleMatch(this.redirectPatterns, viewName) || viewName.startsWith("redirect:"));
    }
}
