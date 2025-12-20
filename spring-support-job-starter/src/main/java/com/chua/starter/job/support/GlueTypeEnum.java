package com.chua.starter.job.support;

import lombok.Getter;

/**
 * Glue类型枚举
 * <p>
 * 定义任务执行器支持的各种运行模式类型，包括：
 * <ul>
 *     <li><b>BEAN模式</b> - 直接调用Spring Bean中的方法执行任务</li>
 *     <li><b>GLUE模式</b> - 运行时动态编译执行代码，支持多种脚本语言</li>
 * </ul>
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 根据名称获取类型
 * GlueTypeEnum type = GlueTypeEnum.match("BEAN");
 *
 * // 判断是否为脚本类型
 * if (type.isScript()) {
 *     String cmd = type.getCmd();    // 获取执行命令，如 "bash", "python"
 *     String suffix = type.getSuffix(); // 获取文件后缀，如 ".sh", ".py"
 * }
 * }</pre>
 *
 * <h3>支持的脚本类型</h3>
 * <pre>
 * | 类型            | 命令       | 后缀   | 说明              |
 * |----------------|-----------|-------|------------------|
 * | GLUE_SHELL     | bash      | .sh   | Shell脚本        |
 * | GLUE_PYTHON    | python    | .py   | Python脚本       |
 * | GLUE_PHP       | php       | .php  | PHP脚本          |
 * | GLUE_NODEJS    | node      | .js   | NodeJS脚本       |
 * | GLUE_POWERSHELL| powershell| .ps1  | PowerShell脚本   |
 * </pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 * @see com.chua.starter.job.support.glue.GlueFactory
 * @see com.chua.starter.job.support.handler.GlueJobHandler
 * @see com.chua.starter.job.support.handler.ScriptJobHandler
 */
@Getter
public enum GlueTypeEnum {

    /**
     * Bean模式
     * <p>
     * 通过Spring Bean名称调用执行方法，最常用的任务执行模式。
     * 开发者只需在业务Bean中定义带有@Job注解的方法即可。
     * </p>
     */
    BEAN("BEAN", false, null, null),

    /**
     * Groovy/Java脚本模式
     * <p>
     * 支持在线编写Java或Groovy代码，运行时动态编译执行。
     * 适用于需要频繁修改逻辑但不想重启应用的场景。
     * </p>
     */
    GLUE_GROOVY("GLUE(Java)", false, null, null),

    /**
     * Shell脚本模式
     * <p>
     * 执行Shell脚本，适用于Linux/Unix系统命令操作。
     * 使用 bash 命令执行，文件后缀 .sh
     * </p>
     */
    GLUE_SHELL("GLUE(Shell)", true, "bash", ".sh"),

    /**
     * Python脚本模式
     * <p>
     * 执行Python脚本，适用于数据处理、机器学习等场景。
     * 使用 python 命令执行，文件后缀 .py
     * </p>
     */
    GLUE_PYTHON("GLUE(Python)", true, "python", ".py"),

    /**
     * PHP脚本模式
     * <p>
     * 执行PHP脚本，适用于Web相关任务处理。
     * 使用 php 命令执行，文件后缀 .php
     * </p>
     */
    GLUE_PHP("GLUE(PHP)", true, "php", ".php"),

    /**
     * NodeJS脚本模式
     * <p>
     * 执行NodeJS脚本，适用于IO密集型任务和前端相关操作。
     * 使用 node 命令执行，文件后缀 .js
     * </p>
     */
    GLUE_NODEJS("GLUE(Nodejs)", true, "node", ".js"),

    /**
     * PowerShell脚本模式
     * <p>
     * 执行PowerShell脚本，适用于Windows系统管理任务。
     * 使用 powershell 命令执行，文件后缀 .ps1
     * </p>
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
