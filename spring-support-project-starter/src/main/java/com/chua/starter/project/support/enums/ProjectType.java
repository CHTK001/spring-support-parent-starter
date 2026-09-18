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

    /**
    * SpringBoot 单体 jar
    *
    * @param "springboot单体jar" "springboot单体jar"
    * @return SPRINGBOOT_MONO的结果
    */
    SPRINGBOOT_MONO("springboot单体jar"),

    /**
    * SpringBoot war 包
    *
    * @param war包" war包"
    * @return SPRINGBOOT_WAR的结果
    */
    SPRINGBOOT_WAR("springboot war包"),

    /**
    * 前后端分离部署（后端+前端目录）
    *
    * @param "前后端分离" "前后端分离"
    * @return springboot分割的结果
    */
    SPRINGBOOT_SPLIT("前后端分离"),

    /**
    * 节点.js 服务
    *
    * @param "nodejs" "nodejs"
    * @return NODEJS的结果
    */
    NODEJS("nodejs"),

    /**
    * 静态站点（dist 等纯静态目录）
    *
    * @param "静态资源" "静态资源"
    * @return 静态的结果
    */
    STATIC("静态资源"),

    /**
    * 自定义类型（脚本完全由用户维护）
    *
    * @param "自定义" "自定义"
    * @return 习俗的结果
    */
    CUSTOM("自定义");

    /** 描述 */
    private final String desc;

    /**
    * project类型。
    * @param desc desc
    */
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
