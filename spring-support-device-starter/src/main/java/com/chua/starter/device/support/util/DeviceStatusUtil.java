package com.chua.starter.device.support.util;

/**
 * 设备状态工具
 *
 * <p>设备台账的状态只有四个取值：{@code normal}（正常）、{@code disabled}（停用）、
 * {@code maintenance}（维护）、{@code scrapped}（报废）。</p>
 *
 * <p>「停用」与「报废」表示这台设备不再参与平台的后台采集：在线探测、事件轮询、
 * 门禁记录抽取等定时任务一律跳过，已建立的会话也会在停用时注销。这里集中判定，
 * 避免每个定时任务各写一份字符串比较后走偏。</p>
 *
 * @since 4.0.0.53
 * @author CH
 */
public final class DeviceStatusUtil {

    /**
     * 正常
     */
    public static final String NORMAL = "normal";

    /**
     * 停用
     */
    public static final String DISABLED = "disabled";

    /**
     * 维护
     */
    public static final String MAINTENANCE = "maintenance";

    /**
     * 报废
     */
    public static final String SCRAPPED = "scrapped";

    private DeviceStatusUtil() {
    }

    /**
     * 该状态是否要求停止后台采集
     *
     * <p>停用与报废都算：报废设备同样不应该继续建连、轮询事件。</p>
     *
     * @param status 设备状态
     * @return true 表示后台定时采集应跳过该设备
     */
    public static boolean collectionStopped(String status) {
        return DISABLED.equalsIgnoreCase(status) || SCRAPPED.equalsIgnoreCase(status);
    }

    /**
     * 状态是否合法
     *
     * @param status 设备状态
     * @return true 表示是四种已知状态之一
     */
    public static boolean valid(String status) {
        return NORMAL.equalsIgnoreCase(status) || DISABLED.equalsIgnoreCase(status)
                || MAINTENANCE.equalsIgnoreCase(status) || SCRAPPED.equalsIgnoreCase(status);
    }
}
