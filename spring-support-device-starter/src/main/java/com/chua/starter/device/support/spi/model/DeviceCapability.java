package com.chua.starter.device.spi.model;

/**
 * 设备厂商能力位
 * <p>用于前端按能力显示/隐藏入口，避免出现“点击后才报不支持”。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public enum DeviceCapability {

    /**
     * 设备目录：可从厂商拉取、回写设备
     */
    DEVICE("设备同步"),

    /**
     * 通道目录
     */
    CHANNEL("通道"),

    /**
     * 实时视频/回放
     */
    MEDIA("视频"),

    /**
     * 云台与预置位
     */
    PTZ("云台"),

    /**
     * 事件订阅/轮询
     */
    EVENT("事件"),

    /**
     * 门禁：门列表、出入记录、远程开门
     */
    ACCESS("门禁"),

    /**
     * 组织架构同步
     */
    ORG("组织"),

    /**
     * 停车场：车场、车道、过车记录
     */
    PARKING("停车场");

    /**
     * 展示名称
     */
    private final String description;

    /**
     * Device Capability
     * @param description 字符串
     * @return 返回值
    */
    DeviceCapability(String description) {
        this.description = description;
    }

    /**
     * 获取展示名称
     *
     * @return 展示名称
     */
    public String getDescription() {
        return description;
    }
}
