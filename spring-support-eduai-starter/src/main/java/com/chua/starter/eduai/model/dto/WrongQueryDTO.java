package com.chua.starter.eduai.model.dto;

import lombok.Data;

/**
 * 错题查询DTO
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class WrongQueryDTO {

    /** 页NUM */
    private Integer pageNum = 1;
    /** 每页大小 */
    private Integer pageSize = 10;
    /** StudentID */
    private String studentId;
    /** Subject */
    private String subject;
    /** Mastery级别 */
    private Integer masteryLevel;
}