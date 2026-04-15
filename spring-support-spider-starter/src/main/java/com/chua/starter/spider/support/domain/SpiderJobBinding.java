package com.chua.starter.spider.support.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务与 job-starter 调度任务的绑定关系
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("spider_job_binding")
public class SpiderJobBinding {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联的爬虫任务 ID */
    private Long taskId;

    /** job-starter 返回的调度绑定 ID */
    @TableField("job_binding_id")
    private String jobBindingId;

    /** 调度通道 */
    @TableField("job_channel")
    private String jobChannel;

    /** 是否有效 */
    private Boolean active;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
