package com.chua.starter.monitor.support.datasync;

/**
 * 同步 Hook（HTTP 回调）的通知时机。
 *
 * <ul>
 *   <li>{@link #DONE} 完成通知：每轮执行结束发一条，成功/失败都发（以 {@code status} 区分）。
 *       <b>全量模式固定用这一档</b>。</li>
 *   <li>{@link #ROW} 每条数据 + 完成通知：增量模式下除完成通知外，每写入一条数据
 *       再推送一条该行数据（异步发送，绝不阻塞同步主流程）。</li>
 * </ul>
 *
 * <p>通知发送失败只记日志，不影响同步执行结果——回调是旁路，不是数据面的一部分。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public enum SyncHookNotify {

    /**
     * 仅完成通知（成功/失败各一条）
     */
    DONE("DONE", "完成通知"),

    /**
     * 每条数据 + 完成通知
     */
    ROW("ROW", "每条数据+完成");

    /**
     * 存储编码
     */
    private final String code;

    /**
     * 中文说明
     */
    private final String label;

    /**
     * 构造通知时机。
     * @param code 存储编码
     * @param label 中文说明
     * @return 返回值
    */
    SyncHookNotify(String code, String label) {
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
     * 由存储编码解析通知时机。
     *
     * @param code 存储编码，可为 空
     * @return 通知时机，不会为 空
     */
    public static SyncHookNotify from(String code) {
        if (code == null || code.isBlank()) {
            return DONE;
        }
        for (SyncHookNotify notify : values()) {
            if (notify.code.equalsIgnoreCase(code.trim())) {
                return notify;
            }
        }
        return DONE;
    }
}
