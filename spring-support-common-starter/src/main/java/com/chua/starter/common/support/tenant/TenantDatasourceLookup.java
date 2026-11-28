package com.chua.starter.common.support.tenant;

/**
 * 数据源中心台账查询口。
 *
 * <p><b>为什么是 SPI 而不是直接依赖 {@code datapool-starter}</b>：tenanted 侧经常
 * 只装 tenant-starter，装上 datapool 会连带拉起数据源中心的控制器、驱动类加载器、
 * 连接 TTL 清扫等一整套东西。反向依赖又会把两个 starter 绑死，谁先建表都成了问题。
 * 因此共享查询契约位于 common 层，由数据源台账模块实现并注册为 Bean，租户模块只消费契约。</p>
 *
 * <p><b>没有实现时必须显式失败</b>：租户配了 {@code sys_tenant_datasource_id} 却查不到台账，
 * 说明部署不完整。此时若静默回退到租户记录里的旧字段（多半为空），请求会落到平台主库——
 * 那是数据越权，不是降级。调用方的租户数据源解析器一律返回 {@code null}
 * 并打出明确告警，交由 fail-closed 拒绝。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
public interface TenantDatasourceLookup {

    /**
     * 按台账 id 查询数据源实例信息。
     *
     * @param datasourceId 台账主键
     * @return 实例信息；不存在时返回 {@code null}
     */
    TenantDatasourceInfo find(long datasourceId);

    /**
     * 列出可作为租户库的台账数据源（供租户表单下拉）。
     *
     * <p>默认返回空列表：只关心「能不能建池」的部署不必实现它。
     * 装了数据源中心的实现会返回启用的 jdbc 实例，且不含口令。</p>
     *
     * @return 可选项；无台账实现时为空列表
     */
    default java.util.List<TenantDatasourceOption> options() {
        return java.util.List.of();
    }

    /**
     * 按主键取台账数据源名称，仅用于前端只读回显与日志。
     *
     * <p>默认实现退化为扫一遍 {@link #options()}：只实现 {@code options()} 的部署
     * 不必再写一遍。装了台账的应用应覆盖成按主键查，避免为一个名字拉全表。</p>
     *
     * @param datasourceId 台账主键
     * @return 展示名；不存在或未实现时返回 {@code null}
     */
    default String name(long datasourceId) {
        for (TenantDatasourceOption option : options()) {
            if (option.id() == datasourceId) {
                return option.name();
            }
        }
        return null;
    }
}
