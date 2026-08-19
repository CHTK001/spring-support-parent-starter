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

    /**
     * 创建 MysqlSqlMethodMarker 实例
     * @param timeout timeout
     * @param boolean boolean
     */
    public MysqlSqlMethodMarker(int timeout, boolean watchdog) {
        this.timeout = timeout;
    }

    @Override
    /** Analysis */
    public List<AbstractMethod> analysis(String source) {
        return Collections.emptyList();
    }

    @Override
    /** Refresh */
    public void refresh(String name) {

    }

    @Override
    /** 销毁 */
    public void destroy() throws Exception {

    }

    @Override
    /** RefreshAll */
    public void refreshAll() {

    }
}
