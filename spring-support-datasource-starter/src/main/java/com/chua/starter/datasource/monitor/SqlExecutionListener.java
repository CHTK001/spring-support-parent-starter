package com.chua.starter.datasource.monitor;

/**
* SQL 执行监听器
*
* <p>SPI 接口，由使用者实现并注册为 Spring Bean，即可在每条 SQL 执行完成后收到回调。
* 相比 Spring 事件，自定义监听器无需在事件中继承额外对象，更轻量。</p>
*
* @since 4.0.0.42
* @author CH
 */
public interface SqlExecutionListener {

    /**
    * SQL 执行完成回调
    *
    * @param event SQL 执行事件
    */
    void onSqlExecuted(SqlExecutionEvent event);
}
