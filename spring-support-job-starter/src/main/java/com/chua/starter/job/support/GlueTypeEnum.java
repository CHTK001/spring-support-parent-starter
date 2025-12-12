package com.chua.starter.job.support;

import lombok.Getter;

/**
 * Glue类型枚举
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
@Getter
public enum GlueTypeEnum {

    /**
     * Bean模式
     */
    BEAN("BEAN", false, null, null),

    /**
     * Groovy脚本
     */
    GLUE_GROOVY("GLUE(Java)", false, null, null),

    /**
     * Shell脚本
     */
    GLUE_SHELL("GLUE(Shell)", true, "bash", ".sh"),

    /**
     * Python脚本
     */
    GLUE_PYTHON("GLUE(Python)", true, "python", ".py"),

    /**
     * PHP脚本
     */
    GLUE_PHP("GLUE(PHP)", true, "php", ".php"),

    /**
     * NodeJS脚本
     */
    GLUE_NODEJS("GLUE(Nodejs)", true, "node", ".js"),

    /**
     * PowerShell脚本
     */
    GLUE_POWERSHELL("GLUE(PowerShell)", true, "powershell", ".ps1");

    /**
     * 描述
     */
    private final String desc;

    /**
     * 是否脚本类型
     */
    private final boolean isScript;

    /**
     * 命令
     */
    private final String cmd;

    /**
     * 后缀
     */
    private final String suffix;

    GlueTypeEnum(String desc, boolean isScript, String cmd, String suffix) {
        this.desc = desc;
        this.isScript = isScript;
        this.cmd = cmd;
        this.suffix = suffix;
    }

    /**
     * 根据名称匹配枚举
     *
     * @param name 名称
     * @return 枚举值
     */
    public static GlueTypeEnum match(String name) {
        for (GlueTypeEnum item : GlueTypeEnum.values()) {
            if (item.name().equals(name)) {
                return item;
            }
        }
        return null;
    }
}
