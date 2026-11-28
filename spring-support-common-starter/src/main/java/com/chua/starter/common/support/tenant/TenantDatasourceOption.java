package com.chua.starter.common.support.tenant;

/**
 * 租户库可选项（前端「数据库来源」下拉的一项）。
 *
 * <p>只含实例级信息，<b>不含口令</b>：这个列表会随租户表单下发到浏览器，
 * 口令由各自模块在服务端保管。</p>
 *
 * @param id       取值，直接写入 {@code sys_tenant_datasource_id}：{@code 0}=自填字段、{@code -1}=平台自身实例、{@code >0}=台账数据源 id
 * @param name     展示名
 * @param type     数据源类型（jdbc 等）；「自填」与「自身」为固定标记
 * @param host     实例地址
 * @param port     实例端口
 * @param database 台账里登记的模板库名（仅供参考，实际库名由租户自己填）
 * @param online   最近一次连通性测试是否通过；{@code null} 表示未测过
 * @since 4.0.0.52
 * @author CH
 */
public record TenantDatasourceOption(long id,
                                     String name,
                                     String type,
                                     String host,
                                     Integer port,
                                     String database,
                                     Boolean online) {
}
