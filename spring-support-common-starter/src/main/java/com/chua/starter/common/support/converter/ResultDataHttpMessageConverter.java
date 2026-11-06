package com.chua.starter.common.support.converter;


import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.io.IOException;
// 注：Type 为 GenericHttpMessageConverter 接口签名要求，豁免
import java.lang.reflect.Type;

/**
   * Fastjson for Spring MVC 转换器.
   * <p>
   * Compatible fastjson 1.2.x
   *
   * @author VictorZeng
   * @see AbstractHttpMessageConverter
   * @see GenericHttpMessageConverter
   * @since 2.0.2
   * @author CH
   */

@SuppressWarnings({"removal"})
public class ResultDataHttpMessageConverter
        extends MappingJackson2HttpMessageConverter {
    @Override
      /**
       * 是否可以写入
       * @param clazz 参数
       * @param mediaType 参数
       * @return 返回值
      */
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        if(
               null != mediaType && mediaType.includes(MediaType.APPLICATION_JSON)
        ) {
            return true;
        }

        return false;
    }


    @Override
      /**
       * 写入内部
       * @param object 对象
       * @param outputMessage 参数
       * @param type 参数
       * @return 返回值
       * @throws HttpMessageNotWritableException 异常
       * @throws IOException 异常
      */
    protected void writeInternal(Object object, Type type, HttpOutputMessage outputMessage) throws IOException
        , HttpMessageNotWritableException {
        super.writeInternal(object, type, outputMessage);
    }
}

