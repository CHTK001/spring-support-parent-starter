package com.chua.starter.device.spi.model;

/**
 * 设备厂商能力位
 * <p>用于前端按能力显示/隐藏入口，避免出现“点击后才报不支持”。</p>
 *
 * <p>取值原则：一个能力位代表一组平台官方 API 的业务域，而不是单个方法。
 * 能力矩阵（{@code DeviceCapabilityMatrix}）按「平台 × 能力位」标注该平台是否支持，
 * 不支持的平台明确标记为不支持，而不是返回空数据。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public enum DeviceCapability {

    /**
     * 设备目录：可从厂商拉取设备列表
     */
    DEVICE("设备同步"),

    /**
     * 设备写入：在厂商平台新增 / 修改 / 删除设备
     */
    DEVICE_WRITE("设备增删改"),

    /**
     * 通道目录
     */
    CHANNEL("通道"),

    /**
     * 实时视频（直播取流 / 取播放地址）
     */
    MEDIA("实时视频"),

    /**
     * 录像：录像计划查询、录像文件检索、回放地址与下载
     */
    RECORD("录像回放"),

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
     * 门禁写入：门的新增 / 修改 / 删除、人员授权下发
     */
    ACCESS_WRITE("门禁增删改"),

    /**
     * 组织架构同步（读取）
     */
    ORG("组织"),

    /**
     * 组织架构写入：部门的新增 / 修改 / 删除
     */
    ORG_WRITE("组织增删改"),

    /**
     * 人员同步（读取）
     */
    PERSON("人员"),

    /**
     * 人员写入：人员的新增 / 修改 / 删除
     */
    PERSON_WRITE("人员增删改"),

    /**
     * 视频通道抓图
     */
    CAPTURE("抓图"),

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
     *
     * @param description 能力展示名称
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
