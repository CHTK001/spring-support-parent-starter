package com.chua.starter.common.support.media;


import com.chua.common.support.spi.annotations.Extension;

import java.io.ByteArrayOutputStream;

/**
 * @author CH
 * @since 4.0.0
 */
@Extension("csv")
public class CsvMediaTypeHandler implements MediaTypeHandler {

    /**
     * 日志
     */
    private static final System.Logger LOG = System.getLogger(CsvMediaTypeHandler.class.getName());
    @Override
    /**
     * asbytearray
     * @param o 对象
     * @return 返回值
    */
    public byte[] asByteArray(Object o) {
        /**
         * 尝试。
         * @param ByteArrayOutputStream( bytearray输出流(
         */
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();) {
            return byteArrayOutputStream.toByteArray();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.DEBUG, "[media] CSV 序列化失败: " + e.getMessage(), e);
        }
        return new byte[0];
    }

}
