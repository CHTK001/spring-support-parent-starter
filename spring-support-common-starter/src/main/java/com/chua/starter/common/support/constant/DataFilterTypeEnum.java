package com.chua.starter.common.support.constant;

/**
 * 权限
 * @author CH
 */
public enum DataFilterTypeEnum {
    /**全部可见*/
    ALL(1, "全部可见"),
    /**本人可见*/
    SELF(2, "本人可见"),
    /**所在部门可见*/
    DEPT(3, "所在部门可见"),
    /**所在部门及子级可见*/
    DEPT_AND_SUB(4, "所在部门及子级可见"),
    /**选择的部门可见*/
    DEPT_SETS(5, "选择的部门可见"),
    /**自定义*/
    CUSTOM(6, "自定义");
    /**
     * 构造函数
     *
     * @param code Integer
     * @param label String
     */
    DataFilterTypeEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }


    private final Integer code;
    private final String label;

    /**
     * 根据code获取枚举
     *
     * @param code code
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
     * 获取 code
     *
     * @return code
     */
    public Integer getCode() {
        return code;
    }

    /**
     * 获取 label
     *
     * @return label
     */
    public String getLabel() {
        return label;
    }
}
