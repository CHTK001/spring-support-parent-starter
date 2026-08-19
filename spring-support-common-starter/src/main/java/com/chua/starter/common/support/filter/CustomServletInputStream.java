package com.chua.starter.common.support.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * 自定义Servlet输入流
 * <p>
 * 基于字节数组实现的ServletInputStream，支持从缓存的请求体数据中读取内容。
 * 配合 {@link CustomHttpServletRequestWrapper} 使用，实现请求体的多次读取。
 * </p>
 *
 * @since 2023/09/08
 * @version 1.0.0
 * @see CustomHttpServletRequestWrapper
 * @author CH
 */
public class CustomServletInputStream extends ServletInputStream {

    /**
     * 底层字节数组输入流
     */
    private final ByteArrayInputStream inputStream;

    /**
     * 创建 CustomServletInputStream 实例
     * @param body body
     */
    public CustomServletInputStream(byte[] body) {
        this.inputStream = new ByteArrayInputStream(body);
    }

    @Override
    /** 是否Finished */
    public boolean isFinished() {
        return inputStream.available() == 0;
    }

    @Override
    /** 是否Ready */
    public boolean isReady() {
        return true;
    }

    @Override
    /** 设置读取Listener */
    public void setReadListener(ReadListener readListener) {

    }

    @Override
    /** 读取 */
    public int read() throws IOException {
        return inputStream.read();
    }
}

