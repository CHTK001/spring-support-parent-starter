package com.chua.starter.script.watchdog;

import com.chua.starter.script.bean.ScriptBean;

/**
 * 监听器
 *
 * @author CH
 */
public interface Watchdog extends AutoCloseable {
    /**
     * 注册bean
     *
     * @param source     source
     * @param scriptBean bean
     */
    void register(Object source, ScriptBean scriptBean);

    /**
     * 超时时间
     *
     * @param timeout 超时时间
     */
    void timeout(int timeout);
}
