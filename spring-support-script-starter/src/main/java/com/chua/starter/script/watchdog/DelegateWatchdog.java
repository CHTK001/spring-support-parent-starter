package com.chua.starter.script.watchdog;

import com.chua.starter.script.bean.ScriptBean;

/**
 * 监听器
 *
 * @author CH
 */
public class DelegateWatchdog implements Watchdog {

    private static final DelegateWatchdog INSTANCE = new DelegateWatchdog();


    public static DelegateWatchdog getInstance() {
        return INSTANCE;
    }


    @Override
    public void register(Object source, ScriptBean scriptBean) {

    }

    @Override
    public void timeout(int timeout) {

    }

    @Override
    public void close() throws Exception {

    }
}
