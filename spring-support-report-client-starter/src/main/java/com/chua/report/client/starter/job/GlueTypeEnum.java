package com.chua.report.client.starter.job;

import lombok.Getter;

/**
 * 调度任务脚本类型枚举。
 *
 * <p>描述任务执行方式（Bean 或 GLUE 脚本），脚本类型记录执行命令与文件后缀。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Getter
public enum GlueTypeEnum {

    BEAN("BEAN", false, null, null),
    GLUE_GROOVY("GLUE(Java)", false, null, null),
    GLUE_SHELL("GLUE(Shell)", true, "bash", ".sh"),
    GLUE_PYTHON("GLUE(Python)", true, "python", ".py"),
    GLUE_PHP("GLUE(PHP)", true, "php", ".php"),
    GLUE_NODEJS("GLUE(Nodejs)", true, "node", ".js"),
    GLUE_POWERSHELL("GLUE(PowerShell)", true, "powershell", ".ps1");

    /** Desc */
    private final String desc;
    /** ISscript */
    private final boolean isScript;
    /** CMD */
    private final String cmd;
    /** Suffix */
    private final String suffix;

    GlueTypeEnum(String desc, boolean isScript, String cmd, String suffix) {
        this.desc = desc;
        this.isScript = isScript;
        this.cmd = cmd;
        this.suffix = suffix;
    }

    public static GlueTypeEnum match(String name){
        for (GlueTypeEnum item: GlueTypeEnum.values()) {
            if (item.name().equals(name)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 判断是否为脚本类型
     *
     * @return 是否为脚本类型
     */
    public boolean isScript() {
        return isScript;
    }

    /**
     * 获取执行命令
     *
     * @return 执行命令
     */
    public String getCmd() {
        return cmd;
    }

    /**
     * 获取文件后缀
     *
     * @return 文件后缀
     */
    public String getSuffix() {
        return suffix;
    }

    /**
     * 获取描述
     *
     * @return 描述
     */
    public String getDesc() {
        return desc;
    }

}

