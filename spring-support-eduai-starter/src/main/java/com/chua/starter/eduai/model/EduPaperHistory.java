package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 试卷历史实体（防篡改留痕：每次改动落一条不可变快照）。
 *
 * <p>本表不含 SysBase 的通用审计列（create_name / create_by / create_time 等），
 * 留痕时间与操作人使用 paper_history_create_time / paper_history_create_by，
 * 因此不继承 {@link com.chua.starter.mybatis.pojo.SysBase}。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("edu_paper_history")
public class EduPaperHistory implements Serializable {

    /**
     * 串行版本UID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 历史标识
     */
    @TableId(type = IdType.AUTO)
    private Long paperHistoryId;

    /**
     * 归属用户 ID（服务端按登录态写入，客户端不可指定）
     */
    @TableField("paper_history_owner_user_id")
    private String ownerUserId;

    /**
     * 试卷标识
     */
    @TableField("paper_history_paper_id")
    private Long paperId;

    /**
     * 试卷唯一编号
     */
    @TableField("paper_history_paper_no")
    private String paperNo;

    /**
     * 版本号（与试卷 paper_version 对应）
     */
    @TableField("paper_history_version")
    private Integer version;

    /**
     * 动作: CREATE/UPDATE/ADD_QUESTION/REMOVE_QUESTION/REORDER/PUBLISH/EVEN
     */
    @TableField("paper_history_action")
    private String action;

    /**
     * 试卷快照（JSON：卷头 + 题项列表，含顺序与分值）
     */
    @TableField("paper_history_snapshot")
    private String snapshot;

    /**
     * 快照 SHA-256 摘要（十六进制小写，用于比对历史是否被改写）
     */
    @TableField("paper_history_hash")
    private String hash;

    /**
     * 留痕时间
     */
    @TableField("paper_history_create_time")
    private LocalDateTime createTime;

    /**
     * 操作人
     */
    @TableField("paper_history_create_by")
    private String createBy;
}
