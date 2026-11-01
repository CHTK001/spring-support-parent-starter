package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * appKey 套餐/透支/单价 请求 DTO（按主键的设置变更）。
 *
 * @since 2026-08-03
 * @author CH
 */
@Data
@Schema(name = "AppKey 设置变更请求 DTO")
public class AiAppKeyPropertyDTO implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 套餐 ID（0= 无套餐）
     */
    @Schema(description = "套餐 ID（0= 无套餐）")
    private Long planId;

    /**
     * 是否允许透支
     */
    @Schema(description = "是否允许透支")
    private Boolean overdraftEnabled;

    /**
     * 每分钟单价
     */
    @Schema(description = "每分钟单价")
    private BigDecimal minuteRate;
}
