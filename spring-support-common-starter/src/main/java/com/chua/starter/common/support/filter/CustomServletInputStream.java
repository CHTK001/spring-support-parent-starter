package com.chua.starter.common.support.filter;

import com.chua.common.support.mysql.io.ByteArrayInputStream;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import java.io.IOException;

/**
 * 自定义servlet输入流
 *
 * @author CH
 * @since 2023/09/08
 */
public class CustomServletInputStream extends ServletInputStream {

    private ByteArrayInputStream inputStream;

    public CustomServletInputStream(byte[] body) {
        this.inputStream = new ByteArrayInputStream(body);
    }

    @Override
    public boolean isFinished() {
        try {
            return inputStream.available() == 0;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setReadListener(ReadListener readListener) {

    }

    @Override
    public int read() throws IOException {
        try {
            return inputStream.read();
        } catch (IOException e) {
            return -1;
        }
    }
}
