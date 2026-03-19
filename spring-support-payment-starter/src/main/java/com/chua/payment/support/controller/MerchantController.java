package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.common.PageResult;
import com.chua.payment.support.common.Result;
import com.chua.payment.support.dto.MerchantDTO;
import com.chua.payment.support.service.MerchantService;
import com.chua.payment.support.vo.MerchantVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 商户管理控制器
 *
 * @author CH
 * @since 2026-03-18
 */
@Tag(name = "商户管理", description = "商户管理相关接口")
@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    @Operation(summary = "创建商户")
    @PostMapping
    public Result<MerchantVO> createMerchant(@RequestBody MerchantDTO dto) {
        MerchantVO vo = merchantService.createMerchant(dto);
        return Result.success(vo);
    }

    @Operation(summary = "更新商户")
    @PutMapping("/{id}")
    public Result<MerchantVO> updateMerchant(@PathVariable Long id, @RequestBody MerchantDTO dto) {
        MerchantVO vo = merchantService.updateMerchant(id, dto);
        return Result.success(vo);
    }

    @Operation(summary = "删除商户")
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteMerchant(@PathVariable Long id) {
        boolean success = merchantService.deleteMerchant(id);
        return Result.success(success);
    }

    @Operation(summary = "查询商户")
    @GetMapping("/{id}")
    public Result<MerchantVO> getMerchant(@PathVariable Long id) {
        MerchantVO vo = merchantService.getMerchant(id);
        return Result.success(vo);
    }

    @Operation(summary = "分页查询商户列表")
    @GetMapping("/list")
    public Result<PageResult<MerchantVO>> listMerchants(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String merchantName,
            @RequestParam(required = false) Integer status) {
        Page<MerchantVO> pageResult = merchantService.listMerchants(page, size, merchantName, status);
        PageResult<MerchantVO> result = new PageResult<>(
                pageResult.getRecords(),
                pageResult.getTotal(),
                pageResult.getCurrent(),
                pageResult.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "激活商户")
    @PutMapping("/{id}/activate")
    public Result<Boolean> activateMerchant(@PathVariable Long id) {
        boolean success = merchantService.activateMerchant(id);
        return Result.success(success);
    }

    @Operation(summary = "停用商户")
    @PutMapping("/{id}/deactivate")
    public Result<Boolean> deactivateMerchant(@PathVariable Long id) {
        boolean success = merchantService.deactivateMerchant(id);
        return Result.success(success);
    }
}
