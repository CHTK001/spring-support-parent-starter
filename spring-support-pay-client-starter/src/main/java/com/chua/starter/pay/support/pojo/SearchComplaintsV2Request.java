package com.chua.starter.pay.support.pojo;

import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.SelectGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.pay.support.dto.SearchComplaintsResponse;
import com.chua.starter.pay.support.enums.PayTradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 搜索投诉响应数据
 *
 * @author CH
 * @since 2025/10/15 15:38
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "搜索投诉请求数据")
public class SearchComplaintsV2Request extends Query<SearchComplaintsResponse> {

    /**
     * 投诉开始时间
     */
    @Schema(description = "投诉开始时间")
    @NotNull(message = "投诉开始时间不能为空", groups = {SelectGroup.class})
    private LocalDate startDate;

    /**
     * 投诉结束时间
     */
    @Schema(description = "投诉结束时间")
    @NotNull(message = "投诉结束时间不能为空", groups = {SelectGroup.class})
    private LocalDate endDate;


    /**
     * 交易类型
     */
    @Schema(description = "交易类型")
    @NotNull(message = "交易类型不能为空", groups = {SelectGroup.class})
    private PayTradeType tradeType;
    /**
     * 商家ID
     */
    @Schema(description = "商家ID")
    @NotNull(message = "商家ID不能为空", groups = {SelectGroup.class})
    private Integer merchantId;
}
