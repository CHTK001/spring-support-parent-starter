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
    */
    public boolean supportsParameter(MethodParameter returnType) {
        Class<?> paramType = returnType.getParameterType();
        return ModelView.class.isAssignableFrom(paramType);
    }

    @Override
    /**
     * 支持申报类型
    */
    public boolean supportsReturnType(MethodParameter returnType) {
        Class<?> paramType = returnType.getParameterType();
        return ModelView.class.isAssignableFrom(paramType);
    }

    @Override
    /**
     * 处理申报值
    */
    public void handleReturnValue(Object returnValue, MethodParameter returnType, ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws IOException, HttpMediaTypeNotAcceptableException, HttpMessageNotWritableException {
        ModelView modelView = (ModelView) returnValue;
        Object data = modelView.getData();

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
        MediaType mediaType = modelView.getMediaType();
        if (null != mediaType) {
            return mediaType.toString();
        }
        return webRequest.getHeader("accept");
    }


    /**
     * Whether the given view 名称 是否 a redirect view 引用.
     * The 默认 implementation 检查 the configured redirect 模式 和
     * also if the view 名称 启动 with the "redirect:" 前缀.
     *
     * @param viewName the view 名称 转为 检查, 从不 {@code null}
     * @return "true" if the given view 名称 是否 recognized as a redirect view
     * 引用; "false" otherwise.
     */
    protected boolean isRedirectViewName(String viewName) {
        return (PatternMatchUtils.simpleMatch(this.redirectPatterns, viewName) || viewName.startsWith("redirect:"));
    }
}

