package com.chua.starter.eduai.model.dto;

import lombok.Data;

/**
 * 复习卷生成DTO
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class ReviewPaperDTO {

    /** studentid */
    private String studentId;
    /** 数量 */
    private Integer count;
    /** 主题 */
    private String subject;
}