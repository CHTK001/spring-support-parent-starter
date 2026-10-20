package com.chua.starter.device.support.model;

/**
 * 设备台账统计
 *
 * <p>列表页顶部卡片使用，避免前端为了 4 个数字发多次分页请求。
 * 统计范围与列表的组织/厂商过滤一致，不受关键字与在线过滤影响，
 * 否则“在线”卡片在筛选离线时会变成 0，失去参考意义。</p>
 *
 * @param total      设备总数
 * @param online     在线数
 * @param offline    离线数
 * @param noLocation 未配置坐标数
 * @since 4.0.0.42
 * @author CH
 */
public record DeviceStats(
        Long total,
        Long online,
        Long offline,
        Long noLocation) {
}
