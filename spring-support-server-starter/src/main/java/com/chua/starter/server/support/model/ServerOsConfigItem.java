package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OS 配置项视图，含当前值、可设值、适用场景说明。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerOsConfigItem {
    /** 唯一标识 */
    private String key;
    /** 显示名称 */
    private String name;
    /** 分类：RESOURCE / SECURITY / NETWORK / KERNEL / OTHER */
    private String category;
    /** 简短描述 */
    private String description;
    /** 当前值 */
    private String currentValue;
    /** 默认值 */
    private String defaultValue;
    /** 可选值列表（开关型给出 allow/deny、enforcing/permissive/disabled 等） */
    private List<String> possibleValues;
    /** 应用值所需 root 命令 */
    private String applyCommand;
    /** 何时使用、解决什么问题的详细说明（前端 tooltip 展示） */
    private String tooltip;
    /** 当前 OS 是否支持此项配置 */
    private boolean supported;
}
