package com.chua.starter.eduai.model.dto;

import lombok.Data;

/**
 * 采集任务查询条件DTO
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class CollectTaskQueryDTO {

    /**
     * 当前页码
     */
    private Integer pageNum = 1;

    /**
     * 每页大小
     */
    private Integer pageSize = 10;

    /**
     * 任务名称（模糊）
     */
    private String taskName;

    /**
     * 来源类型
     */
    private String sourceType;

    /**
     * 学科
     */
    private String subject;

    /**
     * 状态
     */
    private Integer status;
}
