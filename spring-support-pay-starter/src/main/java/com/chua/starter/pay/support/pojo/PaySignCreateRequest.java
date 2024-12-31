package com.chua.starter.pay.support.pojo;

import com.chua.common.support.validator.group.AddGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 签名创建
 * @author CH
 * @since 2024/12/31
 */
@Data
@Schema(title = "签名创建")
public class PaySignCreateRequest {

    /**
     * 交易类型
     */
    private String tradeType;
    /**
     * 商户编码
     */
    @Schema(description = "商户编码")
    @NotBlank(message = "商户编码不能为空", groups = {AddGroup.class})
    private String merchantCode;
    /**
     * 签名
     */
    @Schema(title = "prepay_id=xxx这种格式")
    private String packageStr;
}
