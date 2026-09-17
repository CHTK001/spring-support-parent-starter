package com.chua.starter.monitor.support.service.db;

/**
 * 数据源配置记录。
 *
 * <p>承载数据源连接所需的全部字段,由前端以 JSON 形式提交;
 * 其中 {@code id} 与 {@code createTime}/{@code updateTime} 由后端生成,前端可不传。</p>
 *
 * @param id         数据源标识,后端分配,前端可不传
 * @param name       数据源名称,不能为空
 * @param type       数据源类型,取值见 {@link DataSourceStoreService#supportedTypes()}
 * @param host       主机地址,jdbc 类型必填
 * @param port       端口,jdbc 类型默认为 3306
 * @param database   数据库名,jdbc 类型必填
 * @param username   用户名,jdbc 类型可选
 * @param password   密码,jdbc 类型可选,敏感信息,禁止日志明文
 * @param url        完整 JDBC URL,提供时优先于 host/port/database 拼接
 * @param createTime 创建时间,后端生成
 * @param updateTime 更新时间,后端生成
 * @author CH
 * @since 4.0.0.42
 */
public record DataSourceConfig(
        Long id,
        String name,
        String type,
        String host,
        Integer port,
        String database,
        String username,
        String password,
        String url,
        String createTime,
        String updateTime
) {
}
