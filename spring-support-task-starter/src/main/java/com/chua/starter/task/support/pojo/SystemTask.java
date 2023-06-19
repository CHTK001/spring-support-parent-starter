package com.chua.starter.task.support.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.chua.common.support.database.annotation.Column;
import lombok.Data;

/**
 * 系统任务
 * @author CH
 */
@Data
public class SystemTask {

    @Column(comment = "表ID")
    @TableId
    private Integer taskId;
    /**
     * 任务ID
     */
    @Column(comment = "任务ID")
    private String taskTid;
    /**
     * 任务类型
     */
    @Column(comment = "任务类型")
    private String taskType;
    /**
     * 任务分片ID
     */
    @Column(comment = "任务分片ID")
    private String taskCid;
    /**
     * 任务名称
     */
    @Column(comment = "任务ID")
    private String taskName;
    /**
     * 游标
     */
    @Column(comment = "游标")
    private Integer taskOffset;
    /**
     * 总量
     */
    @Column(comment = "总量")
    private Integer taskTotal;

    /**
     * 总量
     */
    @Column(comment = "处理量")
    private Integer taskCurrent;
    /**
     * 是否完成
     */
    @Column(comment = "是否完成; 0: 未完成")
    private Integer taskStatus;
}
