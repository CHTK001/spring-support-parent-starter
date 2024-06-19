package com.chua.starter.monitor.server.log;

import com.chua.common.support.constant.Level;
import com.chua.common.support.lang.date.DateUtils;
import com.chua.common.support.log.Slf4jLog;
import com.chua.common.support.utils.StringUtils;
import com.chua.socketio.support.session.SocketSessionTemplate;
import jakarta.annotation.Resource;

/**
 * WebLog类实现了Log接口，用于记录网页的访问日志。
 * 该类的目的是提供一种具体的方式来实现日志记录的功能，特别是针对网页访问的场景。
 * 通过实现Log接口，WebLog类可以被其他系统组件调用，以记录和管理网页访问的日志信息。
 *
 * @author CH
 * @since 2024/6/17
 */
public class WebLog extends Slf4jLog {

    private final String pid;
    @Resource
    private SocketSessionTemplate socketSessionTemplate;


    public WebLog(String pid) {
        this.pid = pid;
    }


    @Override
    public void error(String message, Throwable e) {
        socketSessionTemplate.send("log-" + pid, message);
        super.error(message, e);
    }

    @Override
    public void error(String message, Object... args) {
        socketSessionTemplate.send("log-" + pid, DateUtils.currentString() +  " " +  StringUtils.format(message, args));
        super.error(message, args);
    }

    @Override
    public void debug(String message, Object... args) {
        socketSessionTemplate.send("log-" + pid, DateUtils.currentString() +  " " + StringUtils.format(message, args));
        super.debug(message, args);
    }

    @Override
    public void trace(String message, Object... args) {
        socketSessionTemplate.send("log-" + pid, DateUtils.currentString() +  " " + StringUtils.format(message, args));
        super.trace(message, args);
    }

    @Override
    public void warn(String message, Object... args) {
        socketSessionTemplate.send("log-" + pid, DateUtils.currentString() +  " " + StringUtils.format(message, args));
        super.warn(message, args);
    }

    @Override
    public void info(String message, Object... args) {
        socketSessionTemplate.send("log-" + pid, DateUtils.currentString() +  " " + StringUtils.format(message, args));
        super.info(message, args);
    }

    @Override
    public void error(Throwable e) {
        super.error(e);
    }

    @Override
    public void log(Level level, String message, Object... args) {
        super.log(level, message, args);
    }
}

