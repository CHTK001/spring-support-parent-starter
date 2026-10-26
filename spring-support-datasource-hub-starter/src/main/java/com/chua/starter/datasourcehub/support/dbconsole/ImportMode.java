package com.chua.starter.datasourcehub.support.dbconsole;

import java.util.Locale;

/**
 * 导入模式（对齐 Navicat 第 6 步「导入模式」）。
 *
 * <p>六种模式的语义完全由<b>目标表</b>是否已存在匹配行决定，与源文件无关。
 * 「匹配」的判据是 {@link #keyColumns}：空表示按表的主键（Navicat 同口径），
 * 非空表示按用户指定的列组合。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
public enum ImportMode {

    /** 追加：无条件插入，冲突时按驱动/表约束报错（逐行降级后计入错误） */
    APPEND("append", "追加", false, false, false),

    /**
     * 更新：只更新已存在的行。
     *
     * <p>Navicat 的语义是「目标表没有该行时<b>什么都不做</b>」，不是插入。
     * 这一点与「追加或更新」不同，不能合并实现。</p>
     */
    UPDATE("update", "更新", true, false, false),

    /** 追加或更新：存在则更新，不存在则插入（标准 upsert） */
    UPSERT("upsert", "追加或更新", true, true, false),

    /** 不更新追加：存在则跳过，不存在则插入 */
    INSERT_IGNORE("insert-ignore", "不更新追加", false, true, true),

    /** 删除：只删除目标表里与源匹配的行（不写数据） */
    DELETE("delete", "删除", false, false, false),

    /** 复制：先清空目标表，再全量插入 */
    COPY("copy", "复制", false, true, false);

    /** 协议字符串（前后端约定的取值） */
    private final String code;

    /** 中文名（用于日志与错误文案） */
    private final String label;

    /** 是否需要「已存在则更新」的语义 */
    private final boolean updateExisting;

    /** 是否需要「不存在则插入」的语义 */
    private final boolean insertMissing;

    /** 是否需要「已存在则跳过」的语义 */
    private final boolean skipExisting;

    ImportMode(String code, String label, boolean updateExisting, boolean insertMissing, boolean skipExisting) {
        this.code = code;
        this.label = label;
        this.updateExisting = updateExisting;
        this.insertMissing = insertMissing;
        this.skipExisting = skipExisting;
    }

    /**
     * 协议码。
     *
     * @return 形如 {@code upsert}
     */
    public String code() {
        return code;
    }

    /**
     * 中文名。
     *
     * @return 形如 {@code 追加或更新}
     */
    public String label() {
        return label;
    }

    /**
     * 是否更新命中行。
     *
     * @return true=更新
     */
    public boolean updateExisting() {
        return updateExisting;
    }

    /**
     * 是否插入未命中行。
     *
     * @return true=插入
     */
    public boolean insertMissing() {
        return insertMissing;
    }

    /**
     * 是否跳过命中行（既不更新也不插入）。
     *
     * @return true=跳过
     */
    public boolean skipExisting() {
        return skipExisting;
    }

    /**
     * 是否需要在执行前清空目标表。
     *
     * @return true=复制模式
     */
    public boolean truncateFirst() {
        return this == COPY;
    }

    /**
     * 是否只删不写。
     *
     * @return true=删除模式
     */
    public boolean deleteOnly() {
        return this == DELETE;
    }

    /**
     * 按协议码解析；未知值与空值回落到 {@link #APPEND}。
     *
     * <p>回落到「追加」而不是抛错：前端可能在旧版本里发了 {@code skip}/{@code replace}
     * （改造前的取值），对一个「无脑插入」的语义做静默兼容，比让用户看到
     * 「未知导入模式」而无法继续要好。</p>
     *
     * @param value 协议码
     * @return 导入模式
     */
    public static ImportMode of(String value) {
        if (value == null || value.isBlank()) {
            return APPEND;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        // 旧协议兼容：改造前的冲突策略只有 skip / replace
        if ("skip".equals(normalized)) {
            return INSERT_IGNORE;
        }
        if ("replace".equals(normalized)) {
            return UPSERT;
        }
        for (ImportMode mode : values()) {
            if (mode.code.equals(normalized)) {
                return mode;
            }
        }
        return APPEND;
    }
}
