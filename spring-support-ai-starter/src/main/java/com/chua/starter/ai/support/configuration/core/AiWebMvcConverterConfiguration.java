package com.chua.starter.ai.support.configuration.core;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC additional configuration for AI starter.
 */
@Configuration
public class AiWebMvcConverterConfiguration implements WebMvcConfigurer {

    @Override
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
