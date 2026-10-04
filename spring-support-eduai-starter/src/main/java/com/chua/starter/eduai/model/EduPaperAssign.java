package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 班级试卷布置：老师把一张试卷布置给一个班级，班内学生即可看到。
 *
 * <p>为什么单独一张表，而不是给 {@code edu_paper} 加一个 {@code class_id} 列：
 * 同一张试卷常要布置给多个班（甚至跨年级复用），加列会把「试卷」和「布置」两种
 * 生命周期绑死 —— 撤掉一个班的布置就得复制一份试卷。这里按
 * {@code (paper_id, class_id)} 唯一建行，撤布置只是把状态置 0。</p>
 *
 * <p>归属：{@code teacher_account} 与 {@code edu_class.class_owner_user_id} 同口径
 * （都是登录账号），因此「我布置的」可直接过滤，不必再回查班级表。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_paper_assign")
public class EduPaperAssign extends SysBase {

    /**
     * 布置标识
     */
    @TableId(type = IdType.AUTO)
    private Long assignId;

    /**
     * 试卷标识
     */
    private Long paperId;

    /**
     * 班级标识
     */
    private Long classId;

    /**
     * 布置人账号（老师登录名，与 edu_class.class_owner_user_id 同口径）
     */
    private String teacherAccount;

    /**
     * 状态：0 已撤回 / 1 已布置
     */
    private Integer assignStatus;

    /**
     * 备注（如截止时间、说明）
     */
    private String assignRemark;

    /**
     * 布置时间
     */
    private LocalDateTime assignTime;

    /**
     * 归属用户 ID（服务端按登录态写入，客户端不可指定）
     */
    @TableField("paper_assign_owner_user_id")
    private String ownerUserId;
}
