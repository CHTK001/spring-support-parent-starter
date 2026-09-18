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

    /**
    * Bean。
    * @param "BEAN" "Bean"
    * @param false false
    * @param null 空
    * @param null 空
    */
    BEAN("BEAN", false, null, null),
    /**
    * 胶水Groovy。
    * @param "GLUE(Java "胶水(Java
    */
    GLUE_GROOVY("GLUE(Java)", false, null, null),
    /**
    * 胶水Shell。
    * @param "GLUE(Shell "胶水(Shell
    */
    GLUE_SHELL("GLUE(Shell)", true, "bash", ".sh"),
    /**
    * 胶水Python。
    * @param "GLUE(Python "胶水(Python
    */
    GLUE_PYTHON("GLUE(Python)", true, "python", ".py"),
    /**
    * 胶水PHP。
    * @param "GLUE(PHP "胶水(PHP
    */
    GLUE_PHP("GLUE(PHP)", true, "php", ".php"),
    /**
    * 胶水nodejs。
    * @param "GLUE(Nodejs "胶水(Nodejs
    */
    GLUE_NODEJS("GLUE(Nodejs)", true, "node", ".js"),
    /**
    * 胶水PowerShell。
    * @param "GLUE(PowerShell "胶水(PowerShell
    */
    GLUE_POWERSHELL("GLUE(PowerShell)", true, "powershell", ".ps1");

    /** Desc */
    private final String desc;
    /** isscript */
    private final boolean isScript;
    /** CMD */
    private final String cmd;
    /** 后缀 */
    private final String suffix;

    /**
    * 胶水类型enum。
    * @param desc desc
    * @param isScript 是否script
    * @param cmd CMD
    * @param suffix 后缀
    */
    GlueTypeEnum(String desc, boolean isScript, String cmd, String suffix) {
        this.desc = desc;
        this.isScript = isScript;
        this.cmd = cmd;
        this.suffix = suffix;
    }

    /**
    * 匹配
    *
    * @param name 名称
    * @return 匹配的结果
    */
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

