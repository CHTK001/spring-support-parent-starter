package com.chua.starter.monitor.support.model;

/**
 * 数据同步可选的库表列信息。
 *
 * <p>用于「字段映射」预览：源侧来自读取 SQL 的结果集元数据，目标侧来自目标表的列元数据。
 * 只保留映射界面需要的最小集，不返回长度、精度等详细定义。</p>
 *
 * @param name    列名（源侧取结果集标签，即 {@code AS} 别名优先）
 * @param type    列类型名，取不到时为空串
 * @param comment 列注释，取不到时为空串
 * @author CH
 * @since 4.0.0.43
 */
public record SyncColumnVO(String name, String type, String comment) {
}