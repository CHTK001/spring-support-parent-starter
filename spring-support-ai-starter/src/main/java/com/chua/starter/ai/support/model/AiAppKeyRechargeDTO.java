package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * appKey 充值/扣减请求 DTO（前端表单一次性携带金额+备注）。
 *
 * @since 2026-08-03
 * @author CH
 */
@Data
@Schema(name = "AppKey 充值/扣减请求 DTO")
public class AiAppKeyRechargeDTO implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 充值/扣减金额（正数）
     */
    @Schema(description = "充值/扣减金额（正数）")
    private BigDecimal amount;

    /**
     * 备注
     */
    @Schema(description = "备注")
    private String remark;
}
