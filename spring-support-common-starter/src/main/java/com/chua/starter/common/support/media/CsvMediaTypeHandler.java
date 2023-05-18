package com.chua.starter.common.support.media;

import com.chua.common.support.annotations.Extension;

import java.io.ByteArrayOutputStream;

/**
 * @author CH
 */
@Extension("csv")
public class CsvMediaTypeHandler implements MediaTypeHandler {
    @Override
    public byte[] asByteArray(Object o) {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();) {
            return byteArrayOutputStream.toByteArray();
        } catch (Exception ignored) {
        }
        return new byte[0];
    }

}
