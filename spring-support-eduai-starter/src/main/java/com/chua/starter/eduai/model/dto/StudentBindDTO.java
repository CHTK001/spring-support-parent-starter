package com.chua.starter.eduai.model.dto;

/**
 * 学生绑定 DTO
 *
 * <p>学生端首次进入时提交，把当前登录账号与一条学生档案绑定起来；
 * 学号不存在则新建档案，已存在则占用（已被别的账号占用时报错）。</p>
 *
 * @param studentNo   学号（绑定键；为空时按「姓名 + 学段 + 年级」查找）
 * @param studentName 姓名
 * @param gradeLevel  学段：PRIMARY / JUNIOR / SENIOR（也接受 小学/初中/高中）
 * @param grade       年级，如 一年级 / 初二 / 高三
 * @param className   班级名称，如 三年二班；不存在时会自动建
 * @param schoolName  学校名称（可选，仅记录）
 * @since 4.0.0.42
 * @author CH
 */
public record StudentBindDTO(
        String studentNo,
        String studentName,
        String gradeLevel,
        String grade,
        String className,
        String schoolName) {
}
