package com.chua.starter.datasourcehub.support.model;

/**
 * JDBC 连接三元组。
 *
 * <p>数据源台账里存的是「主机 / 端口 / 库名 / 账号 / 口令」五段，而需要自行建连的调用方
 * （例如数据同步的 源 与 Sink，它们只接受 JDBC URL 与账号口令，不接受现成 Connection）
 * 必须先把它拼成可直接交给 {@code DriverManager} 的形态。本 record 就是这条只读出口，
 * 让拼装规则只留在数据源中心一处，避免各调用方各写一遍导致规则分叉。</p>
 *
 * <p>口令已由 {@code DatasourceSecrets} 还原为明文：台账里明文与 {@code b64:} 前缀两种
 * 存法共存，调用方不应再关心前缀。本类型只在服务端内部传递，不下发到前端。</p>
 *
 * @param url             可直接使用的 JDBC URL；库名字段本身是完整 URL 时原样返回
 * @param username        账号，未设置时为空串而非 空
 * @param password        明文口令，未设置时为空串而非 空
 * @param driverClassName 驱动类名，可为 空（由连接层按 URL 推断）
 * @author CH
 * @since 4.0.0.43
 */
public record DatasourceJdbcInfo(String url, String username, String password, String driverClassName) {
}