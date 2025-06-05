package com.chua.starter.common.support.cmd;

import com.chua.common.support.constant.Projects;
import lombok.Getter;
import org.apache.commons.exec.LogOutputStream;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * 自定义输出流，用于实时捕获命令输出
 *
 * @author CH
 */
public class RealtimeLogOutputStream extends LogOutputStream {
    @Getter
    private final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();
    private final StringBuilder lineBuffer = new StringBuilder();
    private final boolean isStdOut;
    private final Consumer<String> realtimeConsumer;

    public RealtimeLogOutputStream(boolean isStdOut, Consumer<String> realtimeConsumer) {
        super(999, Projects.defaultCharset());
        this.isStdOut = isStdOut;
        this.realtimeConsumer = realtimeConsumer;
    }

    @Override
    protected void processLine(String line, int logLevel) {
        if (realtimeConsumer != null) {
            realtimeConsumer.accept((isStdOut ? "[OUT] " : "[ERR] ") + line);
        }
        logQueue.offer(line);
    }

    @Override
    public void write(int b) {
        // 处理原始字节流，构建完整行
        char c = (char) b;
        if (c == '\n') {
            String line = lineBuffer.toString();
            lineBuffer.setLength(0);
            processLine(line, 0);
        } else if (c != '\r') {
            lineBuffer.append(c);
        }
    }

}