package com.chua.starter.project.support.enums;

/**
 * 项目类型
 * <p>
 * 决定默认启动脚本模板与解压/编排策略。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public enum ProjectType {

    /** SpringBoot 单体 jar */
    SPRINGBOOT_MONO("springboot单体jar"),

    /** SpringBoot war 包 */
    SPRINGBOOT_WAR("springboot war包"),

    /** 前后端分离部署（后端+前端目录） */
    SPRINGBOOT_SPLIT("前后端分离"),

    /** Node.js 服务 */
    NODEJS("nodejs"),

    /** 静态站点（dist 等纯静态目录） */
    STATIC("静态资源"),

    /** 自定义类型（脚本完全由用户维护） */
    CUSTOM("自定义");

    /** 描述 */
    private final String desc;

    ProjectType(String desc) {
        this.desc = desc;
    }

    /**
     * 描述
     *
     * @return 描述
     */
    public String desc() {
        return desc;
    }
}