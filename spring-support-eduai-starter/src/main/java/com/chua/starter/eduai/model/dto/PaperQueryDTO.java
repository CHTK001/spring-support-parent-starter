package com.chua.starter.eduai.model.dto;

import lombok.Data;

/**
 * 试卷查询DTO
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class PaperQueryDTO {

    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String subject;
    private String gradeLevel;
    private Integer status;
}