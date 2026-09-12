package com.chua.starter.common.support.constant;

/**
 * 权限
 * @author CH
 * @since 4.0.0
 */
public enum DataFilterTypeEnum {
    /**
     * 全部可见
     *
     * @param 1 1
     * @param "全部可见" "全部可见"
     * @return 全部的结果
     */
    ALL(1, "全部可见"),
    /**
     * 本人可见
     *
     * @param 2 2
     * @param "本人可见" "本人可见"
     * @return SELF的结果
     */
    SELF(2, "本人可见"),
    /**
     * 所在部门可见
     *
     * @param 3 3
     * @param "所在部门可见" "所在部门可见"
     * @return DEPT的结果
     */
    DEPT(3, "所在部门可见"),
    /**
     * 所在部门及子级可见
     *
     * @param 4 4
     * @param "所在部门及子级可见" "所在部门及子级可见"
     * @return DEPT_AND_SUB的结果
     */
    DEPT_AND_SUB(4, "所在部门及子级可见"),
    /**
     * 选择的部门可见
     *
     * @param 5 5
     * @param "选择的部门可见" "选择的部门可见"
     * @return dept设置的结果
     */
    DEPT_SETS(5, "选择的部门可见"),
    /**
     * 自定义
     *
     * @param 6 6
     * @param "自定义" "自定义"
     * @return 习俗的结果
     */
    CUSTOM(6, "自定义");
    /**
     * 构造函数
     *
     * @param code Integer
     * @param label 字符串
     */
    DataFilterTypeEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }


    /** 代码 */
    private final Integer code;
    /** 标签 */
    private final String label;

    /**
      * 根据编码获取枚举
     *
     * @param code 编码
     * @return DataFilterTypeEnum
     */
    public static DataFilterTypeEnum getByCode(Integer code) {
        for (DataFilterTypeEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }
    /**
      * 获取 编码
     *
     * @return code
     */
    public Integer getCode() {
        return code;
    }

    /**
      * 获取 标签
     *
     * @return label
     */
    public String getLabel() {
        return label;
    }
}
