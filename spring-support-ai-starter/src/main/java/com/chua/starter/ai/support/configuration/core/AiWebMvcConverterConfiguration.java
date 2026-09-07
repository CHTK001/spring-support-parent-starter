package com.chua.starter.ai.support.configuration.core;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC additional configuration for AI starter.
 *
 * <p>Adds the default Spring {@link ResourceHttpMessageConverter} and
 * {@link ByteArrayHttpMessageConverter} back to the converter list. The shared
 * {@code spring-support-common-starter} configurer replaces the default
 * {@code AllEncompassingFormHttpMessageConverter} with Jackson/Xml/Yaml
 * converters but does not register binary converters, so controllers
 * returning {@code byte[]} or {@code Resource} for media types like
 * {@code image/jpeg} would otherwise fail with
 * {@code HttpMessageNotWritableException}.</p>
 *
 * <p>These converters are only added when the existing list does not already
 * contain one of the same type, so we do not duplicate or override the
 * shared starter.</p>
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
                    MediaType.IMAGE_WEBP,
                    MediaType.APPLICATION_PDF,
                    new MediaType("image", "svg+xml")
            ));
            converters.add(byteArray);
        }
    }
}
