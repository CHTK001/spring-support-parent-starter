package com.chua.starter.device.support.model;

/**
 * 过车拉取结果。
 *
 * @param created 本次新增条数
 * @param cursor  拉取后的平台游标（无可用游标时保持原值）
 * @since 4.0.0.51
 * @author CH
 */
public record VehiclePassPullResult(int created, long cursor) {
}
