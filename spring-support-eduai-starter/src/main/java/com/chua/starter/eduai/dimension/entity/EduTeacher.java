package com.chua.starter.eduai.dimension.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教师档案。
 *
 * <p>与 {@link EduStudent} 类似：基础用户信息来自 spring-support 用户体系，
 * 本表只保存教学域扩展属性（工号/学科/班级）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("edu_teacher")
public class EduTeacher {

    @TableId(type = IdType.AUTO)
    /**
     * teacherid
    */
    private Long teacherId;

    /**
     * 系统用户 用户标识
     */
    private Long teacherUserId;

    /**
     * 绑定的登录账号
     *
     * <p>同 EduStudent.studentAccount：登录态 userId 是字符串，
     * 与 BIGINT 的 teacherUserId 类型不一致，绑定单独占用此列。</p>
     */
    private String teacherAccount;

    /**
     * 工号
     */
    private String teacherNo;

    /**
     * 教师姓名（冗余展示）
     */
    private String teacherName;

    /**
     * 任教学科编码（关联 edu_主题.主题_编码）
     */
    private String teacherSubjectCode;

    /**
     * 状态：0 离职 / 1 在职
     */
    private Integer teacherStatus;

    /**
     * 登录口令摘要 {@code hmacSha256(明文, 盐)}，非明文。
     *
     * <p>用于「工号即登录名」的教师登录（{@code TeacherCredentialProvider}），
     * 与 {@link EduStudent#getStudentPassword()} 同一套算法与语义：
     * 未设置口令的教师不能用工号登录，须由管理员在「学生管理 → 教师」重置口令。</p>
     *
     * <p>{@code @JsonIgnore} 是必须的：本实体由列表接口
     * （{@code GET /v2/edu/dimension/teachers}）直接返回，
     * 不加这个注解会把口令摘要与盐一起吐给前端。</p>
     */
    @JsonIgnore
    private String teacherPassword;

    /**
     * 口令盐（随机十六进制，与 {@link #teacherPassword} 一一对应）
     *
     * <p>同样 {@code @JsonIgnore}，理由见 {@link #teacherPassword}。</p>
     */
    @JsonIgnore
    private String teacherPwdSalt;

    /**
     * 表单提交的登录口令明文（仅入参，不落库）。
     *
     * <p>与 {@code EduStudent.studentPwdInput} 同形：管理端表单里填的明文在这一层
     * 转成摘要加盐写回 {@link #teacherPassword}，实体本身不持有明文列。</p>
     */
    @TableField(exist = false)
    private String teacherPwdInput;

    @TableField(value = "create_time", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    /**
     * 创建时间
    */
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    /**
     * 更新时间
    */
    private LocalDateTime updateTime;

    /**
     * 归属用户 ID（服务端按登录态写入，客户端不可指定）
     */
    @TableField("teacher_owner_user_id")
    private String ownerUserId;
}
