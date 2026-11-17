package com.chua.starter.mybatis.marker;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;

import java.util.Collections;
import java.util.List;

/**
 * 方法构建器
 *
 * @author CH
 * @since 4.0.0
 */
public class MysqlSqlMethodMarker implements SqlMethodMarker {

    /**
     * 超时
    */
    private int timeout;

    /**
     * 创建 mysqlsql方法记号笔 实例
     * @param timeout 超时
     * @param watchdog 布尔值
     * @param watchdog watchdog
     */
    public MysqlSqlMethodMarker(int timeout, boolean watchdog) {
        this.timeout = timeout;
    }

    /**
     * Analysis
     * @param source 字符串
     * @return 返回值
    */
    @Override
    public List<AbstractMethod> analysis(String source) {
        return Collections.emptyList();
    }

    /**
     * Refresh
     * @param name 字符串
     * @return 返回值
    */
    @Override
    public void refresh(String name) {

    }

    /**
     * 销毁
     * @return 返回值
     * @throws Exception 异常
    */
    @Override
    public void destroy() throws Exception {

    }

    /**
     * refresh全部
    */
    @Override
    public void refreshAll() {

    }
}
