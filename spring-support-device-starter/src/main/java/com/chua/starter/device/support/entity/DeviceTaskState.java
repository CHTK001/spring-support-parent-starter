package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台任务状态实体（Trigger 模式）
 * <p>用于控制任务是否运行，避免并发执行。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("device_task_state")
public class DeviceTaskState {

    /**
     * 主键 标识
     */
    @TableId(value = "device_task_state_id", type = IdType.ASSIGN_UUID)
    /** Device任务状态标识 */
    private String deviceTaskStateId;

    /**
    * 任务名称（唯一）
    */
    @TableField("device_task_state_name")
    /** Device任务状态名称 */
    private String deviceTaskStateName;

    /**
    * 是否正在运行：0 否、1 是
    */
    @TableField("device_task_state_running")
    /** Device任务状态running */
    private Boolean deviceTaskStateRunning;

    /**
    * 最后运行时间
    */
    @TableField("device_task_state_last_run_at")
    /** Device任务状态最后RUNAT */
    private LocalDateTime deviceTaskStateLastRunAt;

    /**
    * 运行间隔（秒）
    */
    @TableField("device_task_state_interval")
    /** Device任务状态间隔 */
    private Integer deviceTaskStateInterval;

    /**
    * 任务描述
    */
    @TableField("device_task_state_description")
    /** Device任务状态描述 */
    private String deviceTaskStateDescription;
}
