package com.chua.starter.common.support.media;


import com.chua.common.support.spi.annotations.Extension;

import java.io.ByteArrayOutputStream;

/**
* @author CH
* @since 4.0.0
 */
@Extension("csv")
public class CsvMediaTypeHandler implements MediaTypeHandler {
    @Override
    /** asbytearray */
    public byte[] asByteArray(Object o) {
        /**
    * 尝试。
    * @param ByteArrayOutputStream( bytearray输出流(
    */
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();) {
            return byteArrayOutputStream.toByteArray();
        } catch (Exception ignored) {
        }
        return new byte[0];
    }

}
