package com.chua.report.client.starter.job;

import lombok.Getter;

@Getter
public enum GlueTypeEnum {

    BEAN("BEAN", false, null, null),
    GLUE_GROOVY("GLUE(Java)", false, null, null),
    GLUE_SHELL("GLUE(Shell)", true, "bash", ".sh"),
    GLUE_PYTHON("GLUE(Python)", true, "python", ".py"),
    GLUE_PHP("GLUE(PHP)", true, "php", ".php"),
    GLUE_NODEJS("GLUE(Nodejs)", true, "node", ".js"),
    GLUE_POWERSHELL("GLUE(PowerShell)", true, "powershell", ".ps1");

    private final String desc;
    private final boolean isScript;
    private final String cmd;
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
