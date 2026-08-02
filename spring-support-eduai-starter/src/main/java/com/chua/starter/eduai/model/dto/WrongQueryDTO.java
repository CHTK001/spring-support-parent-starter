package com.chua.starter.eduai.model.dto;

import lombok.Data;

/**
 * 错题查询DTO
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class WrongQueryDTO {

    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String studentId;
    private String subject;
    private Integer masteryLevel;
}