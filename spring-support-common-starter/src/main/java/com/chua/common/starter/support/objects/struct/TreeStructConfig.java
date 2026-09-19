package com.chua.common.starter.support.objects.struct;

import java.util.function.Function;

/**
 * 树形结构配置
 *
 * @author CH
 * @since 4.0.0
 */
public class TreeStructConfig<T> {

    /**
     * 标识提取器
    */
    private Function<T, ?> idExtractor;

    /**
     * 父标识提取器
    */
    private Function<T, ?> parentIdExtractor;

    /**
     * 名称提取器
    */
    private Function<T, String> nameExtractor;

    /**
     * 数值提取器
    */
    private Function<T, Number> valueExtractor;

    /**
     * 创建 树struct配置 实例
     * @param idExtractor idextractor
     * @param parentIdExtractor 父idextractor
     * @param nameExtractor 名称extractor
     * @param valueExtractor 值extractor
     */
    public TreeStructConfig(Function<T, ?> idExtractor,
                            Function<T, ?> parentIdExtractor,
                            Function<T, String> nameExtractor,
                            Function<T, Number> valueExtractor) {
        this.idExtractor = idExtractor;
        this.parentIdExtractor = parentIdExtractor;
        this.nameExtractor = nameExtractor;
        this.valueExtractor = valueExtractor;
    }

    /**
     * 获取idextractor。
     * @return 获取idextractor的结果
     */
    public Function<T, ?> getIdExtractor() {
        return idExtractor;
    }

    /**
     * 设置idextractor
     *
     * @param idExtractor idextractor
     */
    public void setIdExtractor(Function<T, ?> idExtractor) {
        this.idExtractor = idExtractor;
    }

    /**
     * 获取父idextractor。
     * @return 获取父idextractor的结果
     */
    public Function<T, ?> getParentIdExtractor() {
        return parentIdExtractor;
    }

    /**
     * 设置父idextractor
     *
     * @param parentIdExtractor 父idextractor
     */
    public void setParentIdExtractor(Function<T, ?> parentIdExtractor) {
        this.parentIdExtractor = parentIdExtractor;
    }

    /**
     * 获取名称extractor
     *
     * @return 获取名称extractor的结果
     */
    public Function<T, String> getNameExtractor() {
        return nameExtractor;
    }

    /**
     * 设置名称extractor
     *
     * @param nameExtractor 名称extractor
     */
    public void setNameExtractor(Function<T, String> nameExtractor) {
        this.nameExtractor = nameExtractor;
    }

    /**
     * 获取值extractor
     *
     * @return 获取值extractor的结果
     */
    public Function<T, Number> getValueExtractor() {
        return valueExtractor;
    }

    /**
     * 设置值extractor
     *
     * @param valueExtractor 值extractor
     */
    public void setValueExtractor(Function<T, Number> valueExtractor) {
        this.valueExtractor = valueExtractor;
    }
}
