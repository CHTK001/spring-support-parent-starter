package com.chua.starter.task.support.params;

import com.baomidou.mybatisplus.annotation.TableId;
import com.chua.common.support.database.annotation.Column;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统日志
 *
 * @author CH
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SysTaskLogger extends SysBase {

    @Column(comment = "表ID")
    @TableId
    private Integer taskLoggerId;
    /**
     * 任务ID
     */
    @Column(comment = "任务ID")
    private String taskId;
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
    @Column(comment = "任务名称")
    private String taskName;
    /**
     * 业务ID
     */
    @Column(comment = "业务ID")
    private String taskBusiness;
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
    @Column(comment = "是否完成; 0: 未完成; 1:完成;2: 暂停; 3: 进行中")
    private Integer taskStatus;


}
