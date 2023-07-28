package com.chua.starter.task.support.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;
import com.chua.common.support.database.annotation.Column;
import com.chua.starter.mybatis.pojo.SysBase;
import com.chua.starter.task.support.task.Task;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统任务
 *
 * @author CH
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JsonIgnoreProperties("key")
public class SysTask extends SysBase implements Comparable<SysTask> {

    @Column(comment = "表ID")
    @TableId(type = IdType.AUTO)
    private Integer taskId;
    /**
     * 任务ID
     */
    @Column(comment = "任务ID(系统生成的唯一标识)")
    private String taskTid;
    /**
     * 任务执行的超时时间(s)
     */
    @Column(comment = "任务执行的超时时间(s)", defaultValue = "86400")
    private Integer taskExpire;
    /**
     * 任务过期记录是否删除
     */
    @Column(comment = "任务过期记录是否删除; 1: 删除; 0: 不删除", defaultValue = "1")
    private Integer taskDelete;
    @Column(comment = "任务删除列表多个,分割", length = 1000)
    private String taskDeleteList;
    /**
     * 任务类型
     */
    @Column(comment = "任务类型")
    private String taskType;
    /**
     * 任务分片ID
     */
    @Column(comment = "任务的实现Bean")
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
    @Column(comment = "总量", defaultValue = "0")
    private Long taskTotal;

    /**
     * 总量
     */
    @Column(comment = "处理量", defaultValue = "0")
    private Long taskCurrent;
    /**
     * 是否完成
     */
    @Column(comment = "是否完成; 0: 未完成; 1:完成;2: 暂停; 3: 进行中", defaultValue = "0")
    private Integer taskStatus;
    /**
     * 是否完成
     */
    @Column(comment = "通知类型;导出: export, 信息: report", defaultValue = "'export'")
    private String taskNoticeType;
    /**
     * 版本
     */
    @Version
    @Column(comment = "版本")
    private Integer taskVersion;


    @Column(comment = "任务参数", length = 2000)
    private String taskParams;

    @Column(comment = "任务耗时", defaultValue = "0")
    private Long taskCost;

    /**
     * 任务相同是否覆盖
     */
    @TableField(exist = false)
    private Integer taskOver;


    /**
     * 任务相同时,当文件存在返回文件路径
     */
    @TableField(exist = false)
    private String taskFinishFile;


    @Override
    public int compareTo(SysTask o) {
        return (taskType + taskTid + taskBusiness + taskCid).compareTo(o.taskType + o.taskTid + o.taskBusiness + o.taskCid);
    }

    public String getKey() {
        return taskTid;
    }

    /**
     * 文件key
     * @param userId 用户ID
     * @return 文件key
     */
    public String getFileKey(String userId) {
        return Task.SUBSCRIBE + ":LIST:" + userId +":" + taskTid;
    }
}
