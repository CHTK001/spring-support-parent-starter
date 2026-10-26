package com.chua.starter.common.support.media;

import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.common.support.lang.NamingCase;
import com.chua.starter.common.support.media.formatter.Formatter;
import org.springframework.util.ReflectionUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

/**
 * @author CH
 * @since 4.0.0
 */
@Spi({"xml"})
public class XmlMediaTypeHandler implements MediaTypeHandler {

    /**
     * 日志
     */
    private static final System.Logger LOG = System.getLogger(XmlMediaTypeHandler.class.getName());

    @Override
    /**
     * asbytearray
    */
    public byte[] asByteArray(Object o) {
        /**
         * 尝试。
         * @param ByteArrayOutputStream( bytearray输出流(
         */
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();) {
            /**
             * 导出。
             * @param byteArrayOutputStream bytearray输出流
             * @param o o
             */
            export(byteArrayOutputStream, o);
            return byteArrayOutputStream.toByteArray();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.DEBUG, "[media] XML 序列化失败: " + e.getMessage(), e);
        }
        return new byte[0];
    }

    /**
     * 导出
     *
     * @param outputStream 输出流
     * @param data 数据
     */
    public void export(OutputStream outputStream, Object data) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        stringBuilder.append("<root>");
        stringBuilder.append(analysis(data));
        stringBuilder.append("</root>");
        try {
            outputStream.write(stringBuilder.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.log(System.Logger.Level.DEBUG, "[media] 写出 XML 失败: " + e.getMessage(), e);
        }
    }


    /**
     * Analysis
     *
     * @param data 数据
     * @return analysis的结果
     */
    public StringBuilder analysis(Object data) {
        if (data instanceof Collection) {
            return analysis((Collection) data);
        }

        if (data instanceof Map) {
            return analysis((Map) data);
        }

        return analysisObject(data);
    }

    /**
     * analysis对象
     *
     * @param data 数据
     * @return analysis对象的结果
     */
    public StringBuilder analysisObject(Object data) {
        StringBuilder buffer = new StringBuilder();
        Class<?> aClass = data.getClass();
        ReflectionUtils.doWithFields(aClass, field -> {
            if (Modifier.isStatic(field.getModifiers())) {
                return;
            }

            String name = field.getName();
            Method method = ReflectionUtils.findMethod(aClass, "get" + NamingCase.toFirstUpperCase(name));
            Object value = null;
            if (null != method) {
                ReflectionUtils.makeAccessible(method);
                value = ReflectionUtils.invokeMethod(method, data);
            } else {
                ReflectionUtils.makeAccessible(field);
                value = ReflectionUtils.getField(field, data);
            }

            buffer.append("<").append(name).append(">");
            if (null == value || ClassUtils.isJavaType(value.getClass())) {
                buffer.append(formatter(value, null == value ? null : value.getClass(), field)).append("</").append(name).append(">");
                return;
            }
            buffer.append(analysis(value)).append("</").append(name).append(">");
        });
        return buffer;
    }

    /**
     * Formatter
     *
     * @param value 值
     * @param aClass a类
     * @param field 字段
     * @return formatter的结果
     */
    private Object formatter(Object value, Class<?> aClass, Field field) {
        if (null == value) {
            return "";
        }

        return ServiceProvider.of(Formatter.class).getExtension(aClass.getSimpleName()).format(value, field);
    }

    /**
     * Analysis
     *
     * @param data 数据
     * @return analysis的结果
     */
    public StringBuilder analysis(Map data) {
        StringBuilder buffer = new StringBuilder();
        data.forEach((name, value) -> {
            buffer.append("<").append(name).append(">");
            if (null == value || ClassUtils.isJavaType(value.getClass())) {
                buffer.append(value).append("</").append(name).append(">");
                return;
            }
            buffer.append(analysis(value)).append("</").append(name).append(">");
        });
        return buffer;
    }

    /**
     * Analysis
     *
     * @param data 数据
     * @return analysis的结果
     */
    public StringBuilder analysis(Collection data) {
        StringBuilder buffer = new StringBuilder();
        buffer.append("<items>");
        for (Object datum : data) {
            buffer.append(analysis(datum));
        }
        buffer.append("</items>");
        return buffer;
    }
}
