package com.chua.starter.monitor.support.model;

import java.util.List;
import java.util.Objects;

/**
 * nginx 指令元数据（面向前端）。
 *
 * <p>前端据此决定用哪种控件渲染：{@code flag} 出开关、{@code enum} 出下拉、
 * {@code number}/{@code size}/{@code duration} 出数字框并带单位后缀、
 * {@code path} 出带目录提示的输入框、{@code text}/{@code list} 出文本域。</p>
 *
 * @param name       指令名
 * @param argType    参数类型
 * @param enumValues 枚举取值
 * @param multiple   同一区块内是否可重复
 * @param required   是否必填
 * @param desc       中文说明
 * @param example    示例值
 * @param scopes     允许的作用域
 * @author CH
 * @since 4.0.0.42
 */
public record NginxDirectiveMetaVO(
        String name,
        String argType,
        List<String> enumValues,
        boolean multiple,
        boolean required,
        String desc,
        String example,
        List<String> scopes
) {

    /**
     * 规范构造器：集合组件做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。</p>
     *
     * @param name       指令名
     * @param argType    参数类型
     * @param enumValues 枚举取值
     * @param multiple   同一区块内是否可重复
     * @param required   是否必填
     * @param desc       中文说明
     * @param example    示例值
     * @param scopes     允许的作用域
     */
    public NginxDirectiveMetaVO {
        enumValues = List.copyOf(Objects.requireNonNull(enumValues, "enumValues 不能为 null"));
        scopes = List.copyOf(Objects.requireNonNull(scopes, "scopes 不能为 null"));
    }
}
