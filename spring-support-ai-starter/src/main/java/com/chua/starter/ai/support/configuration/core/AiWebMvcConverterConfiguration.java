package com.chua.starter.ai.support.configuration.core;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
* Web MVC additional 配置 for AI 启动.
* @author CH
* @since 4.0.0
 */
// @Configuration // 暂时注释，编译错误
/**
 * AiWebMvcConverter配置类，提供相关能力。
 *
 * @author CH
 * @since 1.0.0
 */
public class AiWebMvcConverterConfiguration implements WebMvcConfigurer {

    // @Override
    /**
     * extend消息Converters。
     *
     * @param converters 方法入参 converters
     */
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        boolean hasResource = converters.stream()
                .anyMatch(c -> c instanceof ResourceHttpMessageConverter);
        if (!hasResource) {
            converters.add(new ResourceHttpMessageConverter());
        }
        boolean hasByteArray = converters.stream()
                .anyMatch(c -> c instanceof ByteArrayHttpMessageConverter);
        if (!hasByteArray) {
            ByteArrayHttpMessageConverter byteArray = new ByteArrayHttpMessageConverter();
            byteArray.setSupportedMediaTypes(List.of(
                    MediaType.APPLICATION_OCTET_STREAM,
                    MediaType.IMAGE_JPEG,
                    MediaType.IMAGE_PNG,
                    MediaType.IMAGE_GIF,
                    MediaType.parseMediaType("image/webp"),
                    MediaType.APPLICATION_PDF
            ));
            converters.add(byteArray);
        }
    }
}
