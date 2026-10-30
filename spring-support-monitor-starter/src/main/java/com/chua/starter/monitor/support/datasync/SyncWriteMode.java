package com.chua.starter.monitor.support.datasync;

/**
 * 数据同步写入模式。
 *
 * <p>只影响目标端生成的写语句，不影响源的读取方式：</p>
 * <ul>
 *   <li>{@link #INSERT} 追加插入，重复主键会失败；</li>
 *   <li>{@link #REPLACE} 存在即整行替换（MySQL {@code REPLACE INTO}）；</li>
 *   <li>{@link #UPSERT} 存在即更新非主键列（MySQL {@code ON DUPLICATE KEY UPDATE}）；</li>
 *   <li>{@link #OVERWRITE} 先清空目标表再插入，语义是「全量覆盖」。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.43
 */
public enum SyncWriteMode {

    /**
     * 追加插入
     */
    INSERT("INSERT", "追加插入"),

    /**
     * 存在即替换
     */
    REPLACE("REPLACE", "存在即替换"),

    /**
     * 存在即更新
     */
    UPSERT("UPSERT", "存在即更新"),

    /**
     * 先清空再插入
     */
    OVERWRITE("OVERWRITE", "先清空再插入");

    /**
     * 存储编码
     */
    private final String code;

    /**
     * 中文说明
     */
    private final String label;

    /**
     * 构造写入模式。
     * @param code 存储编码
     * @param label 中文说明
     * @return 返回值
    */
    SyncWriteMode(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 获取存储编码。
     *
     * @return 存储编码
     */
    public String code() {
        return code;
    }

    /**
     * 获取中文说明。
     *
     * @return 中文说明
     */
    public String label() {
        return label;
    }

    /**
     * 由存储编码解析写入模式。
     *
     * <p>空值或未知编码一律退化为 {@link #INSERT}：存量任务里该列可能有历史取值，
     * 解析失败时应该能用「最保守的追加」而不是抛错让任务跑不起来。</p>
     *
     * @param code 存储编码，可为 空
     * @return 写入模式，不会为 空
     */
    public static SyncWriteMode from(String code) {
        if (code == null || code.isBlank()) {
            return INSERT;
        }
        for (SyncWriteMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code.trim())) {
                return mode;
            }
        }
        return INSERT;
    }
}