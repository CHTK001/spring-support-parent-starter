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
    public Result<MerchantVO> createMerchant(@RequestBody MerchantDTO dto) {
        MerchantVO vo = merchantService.createMerchant(dto);
        return Result.success(vo);
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
    public Result<MerchantVO> updateMerchant(@PathVariable Long id, @RequestBody MerchantDTO dto) {
        MerchantVO vo = merchantService.updateMerchant(id, dto);
        return Result.success(vo);
    }

    @Operation(summary = "删除商户")
    @DeleteMapping("/{id}")
    /**
     * 删除商户
     *
     * @param id 标识
     * @return 删除商户的结果
     */
    public Result<Boolean> deleteMerchant(@PathVariable Long id) {
        boolean success = merchantService.deleteMerchant(id);
        return Result.success(success);
    }

    @Operation(summary = "查询商户")
    @GetMapping("/{id}")
    /**
     * 获取商户
     *
     * @param id 标识
     * @return 获取商户的结果
     */
    public Result<MerchantVO> getMerchant(@PathVariable Long id) {
        MerchantVO vo = merchantService.getMerchant(id);
        return Result.success(vo);
    }

    @Operation(summary = "分页查询商户列表")
    @GetMapping("/list")
    /**
     * 商户列表
     * @param page page
     * @param size 大小
     * @param merchantName 商户名称
     * @param status 状态
     */
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
    /**
     * 激活商户
     *
     * @param id 标识
     */
    public Result<Boolean> activateMerchant(@PathVariable Long id) {
        boolean success = merchantService.activateMerchant(id);
        return Result.success(success);
    }

    @Operation(summary = "停用商户")
    @PutMapping("/{id}/deactivate")
    /**
     * 停用商户
     *
     * @param id 标识
     */
    public Result<Boolean> deactivateMerchant(@PathVariable Long id) {
        boolean success = merchantService.deactivateMerchant(id);
        return Result.success(success);
    }
}
