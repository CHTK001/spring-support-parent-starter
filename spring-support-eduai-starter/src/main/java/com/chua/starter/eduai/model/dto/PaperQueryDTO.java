package com.chua.starter.eduai.model.dto;

import lombok.Data;

/**
 * 试卷查询DTO
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class PaperQueryDTO {

    /** 页NUM */
    private Integer pageNum = 1;
    /** 每页大小 */
    private Integer pageSize = 10;
    /** 主题 */
    private String subject;
    /** Grade级别 */
    private String gradeLevel;
    /** 状态 */
    private Integer status;
}