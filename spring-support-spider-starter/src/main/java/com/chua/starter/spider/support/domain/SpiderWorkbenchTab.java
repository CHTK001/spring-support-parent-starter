package com.chua.starter.spider.support.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工作台 Tab 定义（前端 Tab 状态持久化）。
 *
 * @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("spider_workbench_tab")
public class SpiderWorkbenchTab {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** Tab 类型：HOME / TASK */
    private String tabType;

    /** 关联任务 ID（TASK 类型时有值） */
    private Long taskId;

    /** Tab 标题 */
    private String title;

    /** 是否可关闭 */
    private Boolean closeable;

    /** 排序序号 */
    private Integer sortOrder;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
