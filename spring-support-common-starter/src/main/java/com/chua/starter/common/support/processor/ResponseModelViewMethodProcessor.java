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
 * 模型 view
 *
 * @author CH
 * @since 4.0.0
 */
public class ResponseModelViewMethodProcessor extends RequestResponseBodyMethodProcessor {

    /**
     * Redirectpatterns
    */
    private String redirectPatterns;

    /**
     * 创建 响应模型view方法处理器 实例
     * @param converters 转换器
     */
    public ResponseModelViewMethodProcessor(List<HttpMessageConverter<?>> converters) {
        /**
         * 父。
         * @param converters 转换器
         */
        super(converters);
    }

    @Override
    /**
     * 支持参数
     * @param returnType 参数
     * @return 返回值
    */
    public boolean supportsParameter(MethodParameter returnType) {
        Class<?> paramType = returnType.getParameterType();
        return ModelView.class.isAssignableFrom(paramType);
    }

    @Override
    /**
     * 支持申报类型
     * @param returnType 参数
     * @return 返回值
    */
    public boolean supportsReturnType(MethodParameter returnType) {
        Class<?> paramType = returnType.getParameterType();
        return ModelView.class.isAssignableFrom(paramType);
    }

    @Override
    /**
     * 处理申报值
     * @param mavContainer 参数
     * @param returnType 参数
     * @param returnValue 对象
     * @param webRequest 参数
     * @return 返回值
     * @throws HttpMediaTypeNotAcceptableException 异常
     * @throws HttpMessageNotWritableException 异常
     * @throws IOException 异常
    */
    public void handleReturnValue(Object returnValue, MethodParameter returnType, ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws IOException, HttpMediaTypeNotAcceptableException, HttpMessageNotWritableException {
        ModelView modelView = (ModelView) returnValue;
        Object data = modelView.data();

        String accept = getAccept(modelView, webRequest);
        if (!accept.contains(MediaType.TEXT_HTML_VALUE)) {
            mavContainer.setRequestHandled(true);
            ServletServerHttpRequest inputMessage = createInputMessage(webRequest);
            ServletServerHttpResponse outputMessage = createOutputMessage(webRequest);

 // 尝试 even with 空 申报 值. 响应主体advice could 获取 involved.
            /**
             * 写入with消息转换器。
             * @param data 数据
             * @param returnType 申报类型
             * @param inputMessage 输入消息
             * @param outputMessage 输出消息
             */
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

    /**
     * 获取Accept
     *
     * @param modelView 模型view
     * @param webRequest web请求
     * @return 获取accept的结果
     */
    private String getAccept(ModelView modelView, NativeWebRequest webRequest) {
        MediaType mediaType = modelView.mediaType();
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

