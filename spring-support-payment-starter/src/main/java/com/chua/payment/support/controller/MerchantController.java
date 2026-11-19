package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.payment.support.dto.MerchantDTO;
import com.chua.payment.support.service.MerchantService;
import com.chua.payment.support.vo.MerchantVO;
import com.chua.starter.common.support.lang.ReturnPageResult;
import com.chua.starter.mybatis.utils.PageResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商户管理控制器
 *
 * @since 2026-03-18
 * @author CH
 */
@Tag(name = "商户管理", description = "商户管理相关接口")
@RestController
@RequestMapping("/merchant")
@RequiredArgsConstructor
public class MerchantController {

    /**
     * 商户服务
     */
    private final MerchantService merchantService;

    @Operation(summary = "创建商户")
    @PostMapping
    /**
     * 创建商户
     *
     * @param dto dto
     * @return 创建商户的结果
     */
    public ReturnResult<MerchantVO> createMerchant(@RequestBody MerchantDTO dto) {
        MerchantVO vo = merchantService.createMerchant(dto);
        return ReturnResult.ok(vo);
    }

    @Operation(summary = "更新商户")
    @PutMapping("/{id}")
    /**
     * 更新商户
     *
     * @param id 标识
     * @param dto dto
     * @return 更新商户的结果
     */
    public ReturnResult<MerchantVO> updateMerchant(@PathVariable Long id, @RequestBody MerchantDTO dto) {
        MerchantVO vo = merchantService.updateMerchant(id, dto);
        return ReturnResult.ok(vo);
    }

    @Operation(summary = "删除商户")
    @DeleteMapping("/{id}")
    /**
     * 删除商户
     *
     * @param id 标识
     * @return 删除商户的结果
     */
    public ReturnResult<Boolean> deleteMerchant(@PathVariable Long id) {
        boolean success = merchantService.deleteMerchant(id);
        return ReturnResult.ok(success);
    }

    @Operation(summary = "查询商户")
    @GetMapping("/{id}")
    /**
     * 获取商户
     *
     * @param id 标识
     * @return 获取商户的结果
     */
    public ReturnResult<MerchantVO> getMerchant(@PathVariable Long id) {
        MerchantVO vo = merchantService.getMerchant(id);
        return ReturnResult.ok(vo);
    }

    @Operation(summary = "分页查询商户列表")
    @GetMapping("/list")
    /**
     * 商户列表
     *
     * @param page 页码
     * @param size 大小
     * @param merchantName 商户名称
     * @param status 状态
     * @return 商户分页结果
     */
    public ReturnPageResult<MerchantVO> listMerchants(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String merchantName,
            @RequestParam(required = false) Integer status) {
        Page<MerchantVO> pageResult = merchantService.listMerchants(page, size, merchantName, status);
        return PageResultUtils.ok(pageResult);
    }

    @Operation(summary = "激活商户")
    @PutMapping("/{id}/activate")
    /**
     * 激活商户
     * @param id 标识
     * @return 返回值
    */
    public ReturnResult<Boolean> activateMerchant(@PathVariable Long id) {
        boolean success = merchantService.activateMerchant(id);
        return ReturnResult.ok(success);
    }

    @Operation(summary = "停用商户")
    @PutMapping("/{id}/deactivate")
    /**
     * 停用商户
     * @param id 标识
     * @return 返回值
    */
    public ReturnResult<Boolean> deactivateMerchant(@PathVariable Long id) {
        boolean success = merchantService.deactivateMerchant(id);
        return ReturnResult.ok(success);
    }
}
