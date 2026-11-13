package com.chua.starter.device.support.timeseries;

/**
 * 设备时序分类。
 *
 * <p>每类记录共用同一个时序点结构 {@link DeviceSeriesPoint}，区别只在「key / value 代表什么」：</p>
 * <ul>
 *   <li>{@link #METRIC}：传感器读数，{@code key}=节点标识，{@code value}=读数，{@code unit}=单位；</li>
 *   <li>{@link #ACCESS}：门禁通行，{@code key}=卡号（无卡号时用人员姓名），{@code value}=事件类型；</li>
 *   <li>{@link #VEHICLE}：车闸过车，{@code key}=车牌号，{@code value}=进出方向。</li>
 * </ul>
 *
 * <p>分类编码会落库/落 Redis 键，只允许小写字母，避免各后端键名大小写不一致。</p>
 *
 * @since 4.0.0.55
 * @author CH
 */
public enum DeviceSeriesCategory {

    /** 传感器（环境指标）读数 */
    METRIC("metric", "传感器记录", "key=节点标识，value=读数，unit=单位"),

    /** 门禁通行事件 */
    ACCESS("access", "门禁记录", "key=卡号或人员，value=事件类型，extra 带门点/方向"),

    /** 车闸过车事件 */
    VEHICLE("vehicle", "车闸记录", "key=车牌号，value=进出方向，extra 带车闸/车型");

    /**
     * 分类编码（落库与键名使用）
     */
    private final String code;

    /**
     * 中文名（页面与接口说明使用）
     */
    private final String label;

    /**
     * key / value 口径说明
     */
    private final String description;

    DeviceSeriesCategory(String code, String label, String description) {
        this.code = code;
        this.label = label;
        this.description = description;
    }

    /**
     * 分类编码。
     *
     * @return 编码，例如 {@code metric}
     */
    public String code() {
        return code;
    }

    /**
     * 中文名。
     *
     * @return 中文名，例如「传感器记录」
     */
    public String label() {
        return label;
    }

    /**
     * key / value 口径说明。
     *
     * @return 说明文本
     */
    public String description() {
        return description;
    }

    /**
     * 按编码解析分类（忽略大小写与首尾空白）。
     *
     * @param code 分类编码
     * @return 分类；未识别返回 {@code null}（由调用方决定是报错还是当作「全部分类」）
     */
    public static DeviceSeriesCategory of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String value = code.trim();
        for (DeviceSeriesCategory category : values()) {
            if (category.code.equalsIgnoreCase(value)) {
                return category;
            }
        }
        return null;
    }
}
