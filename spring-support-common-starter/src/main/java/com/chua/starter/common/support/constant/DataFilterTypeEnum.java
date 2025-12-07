package com.chua.starter.common.support.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 权限
 * @author CH
 */
@Getter
@AllArgsConstructor
public enum DataFilterTypeEnum {
    /**全部可见*/
    ALL(1, "全部可见"),
    /**本人可见*/
    SELF(2, "本人可见"),
    /**所在部门可�?/
    DEPT(3, "所在部门可�?),
    /**所在部门及子级可见*/
    DEPT_AND_SUB(4, "所在部门及子级可见"),
    /**选择的部门可�?/
    DEPT_SETS(5, "选择的部门可�?),
    /**自定�?/
    CUSTOM(6, "自定�?);


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
}

