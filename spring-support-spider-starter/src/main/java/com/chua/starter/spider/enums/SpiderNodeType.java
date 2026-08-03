package com.chua.starter.spider.enums;

/**
 * 流程节点类型枚举。
 *
 * <p>对应 RelationGraph 上的节点类型（与前端 palettes 的 {@code type} 字段对应）。
 * 每个类型由对应的 {@code NodeHandler} 执行。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public enum SpiderNodeType {

    /**
     * 起始节点（每个流程有且仅有一个）
     */
    START("start", "起始"),

    /**
     * HTTP 请求节点（GET/POST/PUT/DELETE）
     */
    REQUEST("request", "HTTP 请求"),

    /**
     * 数据抽取节点（XPath/JsonPath/正则/CSS）
     */
    EXTRACT("extract", "数据抽取"),

    /**
     * 循环节点（遍历列表变量，对每个元素执行子流）
     */
    LOOP("loop", "循环"),

    /**
     * 输出节点（打印或保存到变量）
     */
    OUTPUT("output", "输出"),

    /**
     * Groovy 脚本节点
     */
    SCRIPT("script", "脚本"),

    /**
     * 数据库执行节点（select/insert/update/delete）
     */
    DB("db", "数据库"),

    /**
     * SQL 节点（裸 SQL）
     */
    SQL("sql", "SQL"),

    /**
     * 函数调用节点
     */
    FUNCTION("function", "函数"),

    /**
     * 终止节点（流程结束）
     */
    STOP("stop", "结束");

    /**
     * 类型编码（持久化值）
     */
    private final String code;

    /**
     * 中文显示名
     */
    private final String label;

    SpiderNodeType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 获取类型编码。
     *
     * @return 编码字符串
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取中文显示名。
     *
     * @return 显示名
     */
    public String getLabel() {
        return label;
    }

    /**
     * 根据编码查找枚举。
     *
     * @param code 编码字符串
     * @return 找到返回枚举值，未找到返回 START
     */
    public static SpiderNodeType of(String code) {
        if (code == null) {
            return START;
        }
        for (SpiderNodeType t : values()) {
            if (t.code.equalsIgnoreCase(code)) {
                return t;
            }
        }
        return START;
    }
}
