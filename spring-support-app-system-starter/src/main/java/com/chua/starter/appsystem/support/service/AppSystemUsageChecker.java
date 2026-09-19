package com.chua.starter.appsystem.support.service;

/**
 * 业务系统占用检查器
 * <p>
 * 由下游模块（如部署项目模块）实现并注册为 Bean：删除业务系统前逐个询问，
 * 任一实现报告仍被引用即拒绝删除。本模块不反向依赖下游，避免装配环。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface AppSystemUsageChecker {

    /**
     * 统计引用该业务系统的下游记录数
     *
     * @param appSystemId 业务系统标识
     * @return 引用数量，0 表示未被占用
     */
    int countBySystem(Long appSystemId);

    /**
     * 占用方名称，用于拼出可读的拒绝原因（如「部署项目」）
     *
     * @return 占用方名称
     */
    String usageName();
}
