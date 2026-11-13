package com.chua.starter.device.support.timeseries;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

/**
 * 设备时序写入器。
 *
 * <p>把「业务记录」翻译成时序点，是传感器 / 门禁 / 车闸三类记录写时序的唯一入口。
 * 三个记录口径在这里各写一次，避免散落在 webhook、定时抽取、手工录入里各拼一套字段：</p>
 * <ul>
 *   <li>传感器：{@code key}=节点标识、{@code value}=读数、{@code unit}=单位；</li>
 *   <li>门禁：{@code key}=卡号（无卡号用人员姓名）、{@code value}=事件类型、extra 带门点/方向/抓拍；</li>
 *   <li>车闸：{@code key}=车牌号、{@code value}=进出方向、extra 带车闸/车型/抓拍。</li>
 * </ul>
 *
 * <p><b>写入失败不阻断业务</b>：时序是旁路存储，设备入库/告警/考勤不能因为它失败而回滚；
 * 失败只记 WARN（带上分类、设备、序列键），页面查不到时能顺着日志定位。</p>
 *
 * <p>另外按 {@code plugin.device.timeseries.retention-days} 每天凌晨清理过期点；
 * 引擎不可用或保留期为 0 时什么也不做。</p>
 *
 * @since 4.0.0.55
 * @author CH
 */
@Slf4j
public class DeviceSeriesRecorder {

    /**
     * 序列键最大长度，与 bean 引擎明细表字段一致
     */
    private static final int MAX_KEY_LENGTH = 128;

    /**
     * 保底序列键：门禁记录既没卡号也没姓名时用它，避免丢掉整条事件
     */
    private static final String UNKNOWN_KEY = "unknown";

    /**
     * 时序引擎
     */
    private final DeviceTimeSeriesEngine engine;

    /**
     * 保留天数
     */
    private final int retentionDays;

    /**
     * 构造写入器。
     *
     * @param engine     时序引擎
     * @param properties 时序配置
     */
    public DeviceSeriesRecorder(DeviceTimeSeriesEngine engine, DeviceTimeSeriesProperties properties) {
        this.engine = engine;
        this.retentionDays = properties == null ? 0 : properties.getRetentionDays();
    }

    /**
     * 时序存储当前是否可用。
     *
     * @return 可用返回 true
     */
    public boolean enabled() {
        return engine != null && engine.isAvailable();
    }

    /**
     * 当前引擎标识。
     *
     * @return {@code bean} / {@code influxdb} / {@code redis} / {@code none}
     */
    public String engineName() {
        return engine == null ? "none" : engine.engine();
    }

    /**
     * 写入一个时序点（失败只记日志）。
     *
     * @param point 时序点
     */
    public void record(DeviceSeriesPoint point) {
        if (point == null || !enabled()) {
            return;
        }
        if (point.getTime() == null) {
            point.setTime(LocalDateTime.now());
        }
        try {
            engine.write(point);
        } catch (Exception ex) {
            log.warn("[Device][Series] 时序写入失败: engine={}, category={}, device={}, key={}, message={}",
                    engine.engine(), point.getCategory(), point.getDeviceId(), point.getKey(), ex.getMessage());
        }
    }

    /**
     * 记录一条传感器读数。
     *
     * @param deviceId 设备 ID
     * @param key      节点标识
     * @param name     节点名称
     * @param value    读数
     * @param unit     单位
     * @param time     数据时间（空则取当前时间）
     */
    public void recordMetric(String deviceId, String key, String name, String value, String unit,
                             LocalDateTime time) {
        if (deviceId == null || deviceId.isBlank() || key == null || key.isBlank()) {
            return;
        }
        record(DeviceSeriesPoint.of(DeviceSeriesCategory.METRIC, deviceId, clip(key))
                .name(name)
                .value(value)
                .unit(unit)
                .time(time));
    }

    /**
     * 记录一条门禁通行。
     *
     * @param deviceId  设备 ID
     * @param doorId    门点标识，可空
     * @param cardNo    卡号，可空
     * @param userName  人员姓名，可空
     * @param eventType 事件类型，可空
     * @param direction 进出方向，可空
     * @param photoUrl  抓拍地址，可空
     * @param time      通行时间，可空（取当前时间）
     */
    public void recordAccess(String deviceId, String doorId, String cardNo, String userName,
                             String eventType, String direction, String photoUrl, LocalDateTime time) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        String key = firstText(cardNo, userName, UNKNOWN_KEY);
        record(DeviceSeriesPoint.of(DeviceSeriesCategory.ACCESS, deviceId, clip(key))
                .channel(doorId)
                .name(userName)
                .value(eventType)
                .time(time)
                .extra("cardNo", cardNo)
                .extra("userName", userName)
                .extra("doorId", doorId)
                .extra("direction", direction)
                .extra("photoUrl", photoUrl));
    }

    /**
     * 记录一条车闸过车。
     *
     * @param deviceId    设备 ID
     * @param gateId      车闸标识，可空
     * @param plateNo     车牌号
     * @param direction   进出方向，可空
     * @param vehicleType 车型，可空
     * @param photoUrl    抓拍地址，可空
     * @param time        过车时间，可空（取当前时间）
     */
    public void recordVehiclePass(String deviceId, String gateId, String plateNo, String direction,
                                  String vehicleType, String photoUrl, LocalDateTime time) {
        if (deviceId == null || deviceId.isBlank() || plateNo == null || plateNo.isBlank()) {
            return;
        }
        record(DeviceSeriesPoint.of(DeviceSeriesCategory.VEHICLE, deviceId, clip(plateNo))
                .channel(gateId)
                .name(plateNo)
                .value(direction)
                .time(time)
                .extra("gateId", gateId)
                .extra("vehicleType", vehicleType)
                .extra("photoUrl", photoUrl));
    }

    /**
     * 按保留期清理过期时序点（每天 03:30 执行一次）。
     *
     * <p>{@code retention-days<=0} 表示不清理；引擎不可用时直接跳过。</p>
     */
    // [P3C A20 豁免] A20: cron 表达式属于调度配置字面量
    @Scheduled(cron = "0 30 3 * * ?")
    public void purgeExpired() {
        if (!enabled() || retentionDays <= 0) {
            return;
        }
        LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
        try {
            int deleted = engine.purge(null, before);
            log.info("[Device][Series] 保留期清理完成: engine={}, before={}, deleted={}",
                    engine.engine(), before, deleted);
        } catch (Exception ex) {
            log.warn("[Device][Series] 保留期清理失败: engine={}, message={}", engine.engine(), ex.getMessage());
        }
    }

    /**
     * 截断超长序列键，避免明细表/索引键超长。
     *
     * @param value 原键
     * @return 截断后的键
     */
    private static String clip(String value) {
        String text = value.trim();
        return text.length() <= MAX_KEY_LENGTH ? text : text.substring(0, MAX_KEY_LENGTH);
    }

    /**
     * 取第一个非空文本。
     *
     * @param values 候选值
     * @return 第一个非空值；全空返回最后一个候选（调用方已保证非空）
     */
    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return UNKNOWN_KEY;
    }
}
