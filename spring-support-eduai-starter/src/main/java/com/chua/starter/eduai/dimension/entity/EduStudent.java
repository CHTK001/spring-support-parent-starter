package com.chua.starter.eduai.dimension.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生档案。
 *
 * <p>学生的基础信息存于 {@code spring-support-common-starter} 的用户体系；
 * 本表只存储教育域扩展属性（学号/班级/入学年份/年级）。
 * {@code studentUserId} 是与系统用户的关联键。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("edu_student")
public class EduStudent {

    @TableId(type = IdType.AUTO)
    /**
     * studentid
    */
    private Long studentId;

    /**
     * 系统用户 用户标识（关联 spring-支持 用户体系，可为空表示待注册）
     */
    private Long studentUserId;

    /**
     * 绑定的登录账号
     *
     * <p>与 {@code studentUserId} 分开存：登录态里的 userId 是字符串
     * （如 admin），而 studentUserId 是 BIGINT，两者类型对不上。
     * 学生端「进入即绑定」占用的就是这个列，唯一索引保证一个账号只绑一个学生。</p>
     */
    private String studentAccount;

    /**
     * 学号（业务唯一）
     */
    private String studentNo;

    /**
     * 学生姓名（冗余展示）
     */
    private String studentName;

    /**
     * 班级 标识（关联 edu_类.类_标识）
     */
    private Long studentClassId;

    /**
     * 学段
     */
    private String studentGradeLevel;

    /**
     * 年级
     */
    private String studentGrade;

    /**
     * 入学年份
     */
    private Integer studentEnrollYear;

    /**
     * 家长手机号
     *
     * <p>学生端登录的第二个入口：账号可用「学号」或「家长手机号」。
     * 刻意不加唯一约束 —— 同一号码可能带多个孩子（二孩、隔代抚养），
     * 唯一化会把第二个孩子挡在门外；查询侧按号反查会返回多行，
     * 由服务层按「当前选中的孩子」消歧。</p>
     */
    private String studentParentPhone;

    /**
     * 登录口令摘要
     *
     * <p>存的是 {@code hmacSha256(明文, 盐)} 的十六进制，不是明文，也不是裸摘要：
     * 有了每个学生独立的盐，同一密码在不同学生处的摘要互不相同，
     * 拿到整表也无法用一张彩虹表批量比对。</p>
     *
     * <p>没有 BCrypt 可用（本仓未引入 spring-security-crypto），因此采用带密钥的
     * HMAC-SHA256 而非裸 SHA256：HMAC 不受长度扩展攻击影响，
     * 且盐由服务端随机生成、不与学号绑定。</p>
     *
     * <p>{@code @JsonIgnore} 是必须的：本实体由列表接口直接返回，
     * 不加会让摘要随学生列表一起下发到前端。</p>
     */
    @JsonIgnore
    private String studentPassword;

    /**
     * 口令盐（随机十六进制串，与 {@link #studentPassword} 一一对应）
     */
    @JsonIgnore
    private String studentPwdSalt;

    /**
     * 口令明文（只进不出，不落库）
     *
     * <p>管理员在「新增学生 / 重置密码」时填写的原始口令。
     * {@code exist = false} 保证它永远不会被 MyBatis 当成列写入；
     * 服务层读取它后立刻转成摘要 + 盐写进 {@link #studentPassword}/{@link #studentPwdSalt}。</p>
     */
    @TableField(exist = false)
    private String studentPwdInput;

    /**
     * 状态：0 离校 / 1 在读
     */
    private Integer studentStatus;

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
    @TableField("student_owner_user_id")
    private String ownerUserId;
}
