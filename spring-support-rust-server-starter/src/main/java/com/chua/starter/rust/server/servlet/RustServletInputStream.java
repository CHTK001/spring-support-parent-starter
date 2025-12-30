package com.chua.starter.rust.server.servlet;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Rust Servlet 输入流
 * <p>
 * 包装从 Rust 接收的请求体数据。
 * </p>
 *
 * @author CH
 * @since 4.0.0
 */
public class RustServletInputStream extends ServletInputStream {

    private final ByteArrayInputStream delegate;
    private boolean finished = false;
    private ReadListener readListener;

    public RustServletInputStream(byte[] data) {
        this.delegate = new ByteArrayInputStream(data != null ? data : new byte[0]);
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        this.readListener = readListener;
        try {
            if (readListener != null) {
                readListener.onDataAvailable();
            }
        } catch (IOException e) {
            if (readListener != null) {
                readListener.onError(e);
            }
        }
    }

    @Override
    public int read() throws IOException {
        int data = delegate.read();
        if (data == -1) {
            finished = true;
            if (readListener != null) {
                readListener.onAllDataRead();
            }
        }
        return data;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int read = delegate.read(b, off, len);
        if (read == -1) {
            finished = true;
            if (readListener != null) {
                readListener.onAllDataRead();
            }
        }
        return read;
    }

    @Override
    public int available() throws IOException {
        return delegate.available();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
