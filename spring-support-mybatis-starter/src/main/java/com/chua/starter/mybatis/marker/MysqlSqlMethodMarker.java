package com.chua.starter.mybatis.marker;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;

import java.util.Collections;
import java.util.List;

/**
 * 方法构建器
 *
 * @author CH
 */
public class MysqlSqlMethodMarker implements SqlMethodMarker {

    /** 超时 */
    private int timeout;

    public MysqlSqlMethodMarker(int timeout, boolean watchdog) {
        this.timeout = timeout;
    }

    @Override
    public List<AbstractMethod> analysis(String source) {
        return Collections.emptyList();
    }

    @Override
    public void refresh(String name) {

    }

    @Override
    public void destroy() throws Exception {

    }

    @Override
    public void refreshAll() {

    }
}
