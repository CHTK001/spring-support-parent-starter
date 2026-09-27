package com.chua.starter.monitor.support.server.model;

import java.util.List;

/**
 * 插件参数 schema —— 前端据此渲染配置表单，新增插件无需改前端。
 *
 * @param name         参数名
 * @param defaultValue 默认值
 * @param desc         参数说明
 * @param type         控件类型：boolean / string / number / enum
 * @param options      枚举候选值，仅 {@code type=enum} 时下发
 * @author CH
 * @since 4.0.0.42
 */
public record ServerPluginParamVO(
        String name,
        String defaultValue,
        String desc,
        String type,
        List<String> options
) {

    /**
     * 规范构造器：非空时对枚举候选值做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。非枚举参数由
     * {@code ServerManageServiceImpl#param(...)} 与 {@code paramsOf(...)} 显式传 null，
     * 此处保留 null 语义，只在非空时拷贝。</p>
     *
     * @param options 枚举候选值，可为 null
     */
    public ServerPluginParamVO {
        options = options == null ? null : List.copyOf(options);
    }
}
