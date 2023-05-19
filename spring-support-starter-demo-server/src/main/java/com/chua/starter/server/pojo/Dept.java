package com.chua.starter.server.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 机构
 * </p>
 *
 * @author yzj
 * @since 2021-12-08
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class Dept implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;

    private String deptName;

    private Integer createdBy;

    private String createdByName;

    private LocalDateTime createdTime;

    private Integer updatedBy;

    private String updatedByName;

    private LocalDateTime updatedTime;


}
