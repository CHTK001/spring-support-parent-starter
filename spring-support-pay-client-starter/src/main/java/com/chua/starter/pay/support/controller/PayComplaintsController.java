package com.chua.starter.pay.support.controller;

import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.chua.common.support.core.utils.ServiceProvider;
import com.chua.common.support.base.validator.group.AddGroup;
import com.chua.common.support.base.validator.group.SelectGroup;
import com.chua.starter.pay.support.complaints.ComplaintsAdaptor;
import com.chua.starter.pay.support.dto.SearchComplaintsResponse;
import com.chua.starter.pay.support.pojo.SearchComplaintsV2Request;
import com.chua.starter.pay.support.pojo.SearchComplaintsV2Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 投诉单接口
 * @author CH
 * @since 2025/10/15 15:48
 */
@RestController
@RequestMapping("/v2/pay/complaints")
@Tag(name = "投诉单接口")
@RequiredArgsConstructor
public class PayComplaintsController {


    /**
     * 搜索投诉单
     * @param request 搜索投诉单参数
     * @return 投诉单信息
     */
    @PostMapping("page")
    @Operation(summary = "搜索投诉单")
    public ReturnPageResult<SearchComplaintsV2Response> search(@Validated(SelectGroup.class) @RequestBody SearchComplaintsV2Request request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return PageResultUtils.error(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return ServiceProvider.of(ComplaintsAdaptor.class).getExtension(request.getTradeType()).search(request);
    }
}
