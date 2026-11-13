package com.chua.starter.device.support.timeseries;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备时序点（时序引擎的统一存储单元）。
 *
 * <p>字段是「设备时序」而不是某个后端专有的结构：bean 引擎落 MySQL 明细表，
 * InfluxDB 落 measurement + tag，Redis 落 ZSET 成员（JSON）。三者读写同一份字段，
 * 因此 yaml 换引擎不需要动业务代码。</p>
 *
 * <p>{@code extra} 存放不适合建模成独立字段的展示信息（门点、抓拍地址、车型等）。
 * 它是字符串键值对而不是任意对象：InfluxDB 的 tag/field、Redis 的成员都要能无损序列化。</p>
 *
 * @since 4.0.0.55
 * @author CH
 */
@Data
public class DeviceSeriesPoint {

    /**
     * 分类编码，取值见 {@link DeviceSeriesCategory#code()}
     */
    private String category;

    /**
     * 设备 ID
     */
    private String deviceId;

    /**
     * 通道 / 门点 / 车闸标识，可空
     */
    private String channel;

    /**
     * 序列键：传感器节点标识、卡号、车牌号（同一 key 在同设备下构成一条序列）
     */
    private String key;

    /**
     * 展示名：节点名称、人员姓名、车牌号
     */
    private String name;

    /**
     * 记录值：读数、事件类型、进出方向
     */
    private String value;

    /**
     * 单位（仅传感器有意义），可空
     */
    private String unit;

    /**
     * 发生时间（厂商侧时间；厂商未回传时取采集时间）
     */
    private LocalDateTime time;

    /**
     * 附加字段（门点、抓拍地址、卡号、车型等），保持插入顺序便于阅读
     */
    private Map<String, String> extra = new LinkedHashMap<>();

    /**
     * 构造一个最小可用的时序点。
     *
     * @param category 分类
     * @param deviceId 设备 ID
     * @param key      序列键
     * @return 时序点
     */
    public static DeviceSeriesPoint of(DeviceSeriesCategory category, String deviceId, String key) {
        DeviceSeriesPoint point = new DeviceSeriesPoint();
        point.category = category == null ? null : category.code();
        point.deviceId = deviceId;
        point.key = key;
        return point;
    }

    /**
     * 设置通道。
     *
     * @param value 通道 / 门点 / 车闸标识
     * @return this
     */
    public DeviceSeriesPoint channel(String value) {
        this.channel = value;
        return this;
    }

    /**
     * 设置展示名。
     *
     * @param value 展示名
     * @return this
     */
    public DeviceSeriesPoint name(String value) {
        this.name = value;
        return this;
    }

    /**
     * 设置记录值。
     *
     * @param value 读数 / 事件类型 / 方向
     * @return this
     */
    public DeviceSeriesPoint value(String value) {
        this.value = value;
        return this;
    }

    /**
     * 设置单位。
     *
     * @param value 单位
     * @return this
     */
    public DeviceSeriesPoint unit(String value) {
        this.unit = value;
        return this;
    }

    /**
     * 设置发生时间。
     *
     * @param value 发生时间
     * @return this
     */
    public DeviceSeriesPoint time(LocalDateTime value) {
        this.time = value;
        return this;
    }

    /**
     * 追加一个附加字段；值为空时不落，避免存一堆空串。
     *
     * @param name  字段名
     * @param value 字段值
     * @return this
     */
    public DeviceSeriesPoint extra(String name, String value) {
        if (name == null || name.isBlank() || value == null || value.isBlank()) {
            return this;
        }
        if (this.extra == null) {
            this.extra = new LinkedHashMap<>();
        }
        this.extra.put(name, value);
        return this;
    }

    /**
     * 读取附加字段。
     *
     * @param name 字段名
     * @return 字段值；不存在返回 {@code null}
     */
    public String extraOf(String name) {
        return this.extra == null ? null : this.extra.get(name);
    }
}
