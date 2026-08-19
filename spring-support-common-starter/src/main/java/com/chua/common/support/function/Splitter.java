package com.chua.common.support.function;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 字符串拆分器
 * <p>
 * 用于将字符串按指定分隔符拆分为多个部分
 * </p>
 *
 * @author CH
 */
public class Splitter {

    /** Separator */
    private final String separator;
    /** Trimresults */
    private boolean trimResults;
    /** Omit是否为空strings */
    private boolean omitEmptyStrings;

    /**
     * 构造拆分器
     *
     * @param separator 分隔符
     */
    public Splitter(String separator) {
        this.separator = separator;
    }

    /**
     * 使用指定分隔符构造拆分器
     *
     * @param separator 分隔符
     * @return Splitter 实例
     */
    public static Splitter on(String separator) {
        return new Splitter(separator);
    }

    /**
     * 设置去除结果中的空白字符
     *
     * @return 当前 Splitter 实例
     */
    public Splitter trimResults() {
        this.trimResults = true;
        return this;
    }

    /**
     * 设置忽略空字符串
     *
     * @return 当前 Splitter 实例
     */
    public Splitter omitEmptyStrings() {
        this.omitEmptyStrings = true;
        return this;
    }

    /**
     * 将字符串拆分为列表
     *
     * @param input 输入字符串
     * @return 拆分后的列表
     */
    public List<String> splitToList(String input) {
        List<String> result = new ArrayList<>();
        if (input == null || input.isEmpty()) {
            return result;
        }
        for (String part : input.split(separator)) {
            String processed = trimResults ? part.trim() : part;
            if (omitEmptyStrings && processed.isEmpty()) {
                continue;
            }
            result.add(processed);
        }
        return result;
    }

    /**
     * 将字符串拆分为 Set
     *
     * @param input 输入字符串
     * @return 拆分后的 Set
     */
    public Set<String> splitToSet(String input) {
        return new HashSet<>(splitToList(input));
    }
}