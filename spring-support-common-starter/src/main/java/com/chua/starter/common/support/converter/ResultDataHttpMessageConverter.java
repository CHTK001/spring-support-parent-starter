package com.chua.starter.common.support.converter;


import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Fastjson for Spring MVC Converter.
 * <p>
 * Compatible fastjson 1.2.x
 *
 * @author VictorZeng
 * @see AbstractHttpMessageConverter
 * @see GenericHttpMessageConverter
 * @since 2.0.2
 */

public class ResultDataHttpMessageConverter
        extends MappingJackson2HttpMessageConverter {
    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        if(
               null != mediaType && mediaType.includes(MediaType.APPLICATION_JSON)
        ) {
            return true;
        }

        return false;
    }


    @Override
    protected void writeInternal(Object object, Type type, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        super.writeInternal(object, type, outputMessage);
    }
}

