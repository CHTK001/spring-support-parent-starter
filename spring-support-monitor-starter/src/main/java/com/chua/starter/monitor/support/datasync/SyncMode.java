package com.chua.starter.monitor.support.datasync;

/**
 * 数据同步的同步模式：全量 / 增量。
 *
 * <p>与 {@link SyncWriteMode}（只管目标端写语句长什么样）是两个维度：</p>
 * <ul>
 *   <li>{@link #FULL} 全量：每轮把源数据<b>整体</b>读一遍。单次流式跑到底，失败后下一轮
 *       重新清空重来，<b>不做断点续传</b>——适用于小数据范围；配合 OVERWRITE 即「先清空后添加」。</li>
 *   <li>{@link #INCREMENTAL} 增量：按<b>增量字段</b>水位取新数据。JDBC 源把原 SQL 包一层
 *       {@code WHERE 增量字段 > 水位 ORDER BY 增量字段 ASC LIMIT 每批数量}，HTTP 源把水位
 *       作为查询参数携带；每轮成功后把本批最大值写回水位，供下一轮使用。
 *       升序取批保证积压数据不漏不重地分轮消化（倒序+LIMIT 会把水位推到最大、
 *       夹在中间的数据永远丢）。</li>
 * </ul>
 *
 * <p>仅 JDBC / HTTP 源支持增量；文件/目录源固定全量。空值或未知编码一律退化为
 * {@link #FULL}，与存量任务（该列为后补、历史行为空）保持兼容。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public enum SyncMode {

    /**
     * 全量：每轮读取全部源数据
     */
    FULL("FULL", "全量"),

    /**
     * 增量：按增量字段水位分批取新数据
     */
    INCREMENTAL("INCREMENTAL", "增量");

    /**
     * 存储编码
     */
    private final String code;

    /**
     * 中文说明
     */
    private final String label;

    /**
     * 构造同步模式。
     *
     * @param code  存储编码
     * @param label 中文说明
     */
    SyncMode(String code, String label) {
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
     * 由存储编码解析同步模式。
     *
     * @param code 存储编码，可为 空
     * @return 同步模式，不会为 空
     */
    public static SyncMode from(String code) {
        if (code == null || code.isBlank()) {
            return FULL;
        }
        for (SyncMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code.trim())) {
                return mode;
            }
        }
        return FULL;
    }
}
