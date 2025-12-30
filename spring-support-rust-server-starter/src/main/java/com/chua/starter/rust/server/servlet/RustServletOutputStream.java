package com.chua.starter.rust.server.servlet;

import jakarta.servlet.WriteListener;
import jakarta.servlet.ServletOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Rust Servlet 输出流
 * <p>
 * 收集响应数据，最终发送给 Rust HTTP Server。
 * </p>
 *
 * @author CH
 * @since 4.0.0
 */
public class RustServletOutputStream extends ServletOutputStream {

    private final ByteArrayOutputStream delegate = new ByteArrayOutputStream(4096);
    private WriteListener writeListener;

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        this.writeListener = writeListener;
        try {
            if (writeListener != null) {
                writeListener.onWritePossible();
            }
        } catch (IOException e) {
            if (writeListener != null) {
                writeListener.onError(e);
            }
        }
    }

    @Override
    public void write(int b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        delegate.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    /**
     * 获取写入的数据
     *
     * @return 响应体数据
     */
    public byte[] toByteArray() {
        return delegate.toByteArray();
    }

    /**
     * 获取已写入的字节数
     *
     * @return 字节数
     */
    public int size() {
        return delegate.size();
    }

    /**
     * 重置输出流
     */
    public void reset() {
        delegate.reset();
    }
}
