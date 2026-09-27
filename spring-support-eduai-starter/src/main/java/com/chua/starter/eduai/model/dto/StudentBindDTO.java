package com.chua.starter.eduai.model.dto;

import lombok.Data;

/**
 * 学生绑定 DTO
 *
 * <p>学生端首次进入时提交，把当前登录账号与一条学生档案绑定起来；
 * 学号不存在则新建档案，已存在则占用（已被别的账号占用时报错）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class StudentBindDTO {

    /**
     * 学号（绑定键；为空时按「姓名 + 学段 + 年级」查找）
     */
    private String studentNo;

    /**
     * 姓名
     */
    private String studentName;

    /**
     * 学段：PRIMARY / JUNIOR / SENIOR（也接受 小学/初中/高中）
     */
    private String gradeLevel;

    /**
     * 年级，如 一年级 / 初二 / 高三
     */
    private String grade;

    /**
     * 班级名称，如 三年二班；不存在时会自动建
     */
    private String className;

    /**
     * 学校名称（可选，仅记录）
     */
    private String schoolName;
}
