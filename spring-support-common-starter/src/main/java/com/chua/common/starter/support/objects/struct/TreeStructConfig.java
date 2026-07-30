package com.chua.common.starter.support.objects.struct;

import java.util.function.Function;

/**
 * 树形结构配置
 *
 * @author CH
 */
public class TreeStructConfig<T> {

    /** 标识提取器 */
    private Function<T, ?> idExtractor;

    /** 父标识提取器 */
    private Function<T, ?> parentIdExtractor;

    /** 名称提取器 */
    private Function<T, String> nameExtractor;

    /** 数值提取器 */
    private Function<T, Number> valueExtractor;

    public TreeStructConfig(Function<T, ?> idExtractor,
                            Function<T, ?> parentIdExtractor,
                            Function<T, String> nameExtractor,
                            Function<T, Number> valueExtractor) {
        this.idExtractor = idExtractor;
        this.parentIdExtractor = parentIdExtractor;
        this.nameExtractor = nameExtractor;
        this.valueExtractor = valueExtractor;
    }

    public Function<T, ?> getIdExtractor() {
        return idExtractor;
    }

    public void setIdExtractor(Function<T, ?> idExtractor) {
        this.idExtractor = idExtractor;
    }

    public Function<T, ?> getParentIdExtractor() {
        return parentIdExtractor;
    }

    public void setParentIdExtractor(Function<T, ?> parentIdExtractor) {
        this.parentIdExtractor = parentIdExtractor;
    }

    public Function<T, String> getNameExtractor() {
        return nameExtractor;
    }

    public void setNameExtractor(Function<T, String> nameExtractor) {
        this.nameExtractor = nameExtractor;
    }

    public Function<T, Number> getValueExtractor() {
        return valueExtractor;
    }

    public void setValueExtractor(Function<T, Number> valueExtractor) {
        this.valueExtractor = valueExtractor;
    }
}
