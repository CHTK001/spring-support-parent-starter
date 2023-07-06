package com.chua.starter.common.support.converter;


import com.alibaba.fastjson2.JSONException;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.media.MediaTypeHandler;
import org.springframework.http.*;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
        extends AbstractHttpMessageConverter<Object>
        implements GenericHttpMessageConverter<Object> {

    /**
     * Can serialize/deserialize all types.
     */
    public ResultDataHttpMessageConverter() {
        super(MediaType.APPLICATION_JSON);
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return true;
    }

    @Override
    public boolean canRead(Type type, Class<?> contextClass, MediaType mediaType) {
        return false;
    }

    @Override
    public boolean canWrite(Type type, Class<?> clazz, MediaType mediaType) {
        return false;
    }

    protected boolean canWrite(@Nullable MediaType mediaType) {
        return false;
    }

    @Override
    public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return null;
    }

    @Override
    public void write(
            Object o,
            Type type,
            MediaType contentType,
            HttpOutputMessage outputMessage
    ) throws IOException, HttpMessageNotWritableException {
        final HttpHeaders headers = outputMessage.getHeaders();
        addDefaultHeaders(headers, o, contentType);

        if (outputMessage instanceof StreamingHttpOutputMessage) {
            StreamingHttpOutputMessage streamingOutputMessage = (StreamingHttpOutputMessage) outputMessage;
            streamingOutputMessage.setBody(outputStream -> writeInternal(o, new HttpOutputMessage() {
                @Override
                public OutputStream getBody() {
                    return outputStream;
                }

                @Override
                public HttpHeaders getHeaders() {
                    return headers;
                }
            }));
            return;
        }
        output(contentType, o, outputMessage);
        outputMessage.getBody().flush();

    }

    private void output(MediaType contentType, Object o, HttpOutputMessage outputMessage) {
        String subtype = contentType.getSubtype();
        MediaTypeHandler mediaTypeHandler = ServiceProvider.of(MediaTypeHandler.class).getExtension(subtype);
        byte[] bytes = new byte[0];
        if (null != mediaTypeHandler) {
            bytes = mediaTypeHandler.asByteArray(o);
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            HttpHeaders headers = outputMessage.getHeaders();
            baos.write(bytes);
            headers.setContentLength(bytes.length);
            baos.writeTo(outputMessage.getBody());
        } catch (JSONException ex) {
            throw new HttpMessageNotWritableException("Could not write JSON: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new HttpMessageNotWritableException("I/O error while writing output message", ex);
        }
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage) throws HttpMessageNotReadableException {
        return null;
    }

    @Override
    protected void writeInternal(Object o, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {

    }


}
