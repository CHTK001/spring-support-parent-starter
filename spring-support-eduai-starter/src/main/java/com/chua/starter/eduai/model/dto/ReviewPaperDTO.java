package com.chua.starter.eduai.model.dto;

import lombok.Data;

/**
 * 复习卷生成DTO
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class ReviewPaperDTO {

    private String studentId;
    private Integer count;
    private String subject;
}