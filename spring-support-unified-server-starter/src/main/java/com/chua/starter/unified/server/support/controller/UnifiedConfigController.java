package com.chua.starter.unified.server.support.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.common.support.result.ResultData;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.mybatis.entity.DelegatePage;
import com.chua.starter.unified.server.support.entity.UnifiedConfig;
import com.chua.starter.unified.server.support.service.UnifiedConfigService;
import lombok.AllArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import static com.chua.starter.common.support.result.ReturnCode.PARAM_ERROR;

/**
 * 配置中心接口
 *
 * @author CH
 */
@RestController
@RequestMapping("v1/config")
@AllArgsConstructor
public class UnifiedConfigController {

    private final UnifiedConfigService unifiedConfigService;

    /**
     * 分页查询数据
     *
     * @param page   页码
     * @param entity 结果
     * @return 分页结果
     */
    @GetMapping("page")
    @ResponseBody
    public ReturnPageResult<Page<UnifiedConfig>> page(DelegatePage<UnifiedConfig> page, @Valid UnifiedConfig entity, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnPageResult.illegal(PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return ReturnPageResult.ok(unifiedConfigService.page(page.createPage(), Wrappers.<UnifiedConfig>lambdaQuery()
                .eq(StringUtils.isNotBlank(entity.getUnifiedConfigProfile()), UnifiedConfig::getUnifiedConfigProfile, entity.getUnifiedConfigProfile())
        ));
    }

    /**
     * 根据主键删除数据
     *
     * @param id 页码
     * @return 分页结果
     */
    @ResponseBody
    @GetMapping("delete")
    public ResultData<Boolean> delete(String id) {
        if (null == id) {
            return ResultData.failure(PARAM_ERROR, "主键不能为空");
        }
        return ResultData.success(unifiedConfigService.removeById(id));
    }

    /**
     * 根据主键更新数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @PostMapping("update")
    @ResponseBody
    public ResultData<Boolean> updateById(@Valid @RequestBody UnifiedConfig t, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResultData.failure(PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return ResultData.success(unifiedConfigService.updateById(t));
    }

    /**
     * 添加数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @PostMapping("save")
    @ResponseBody
    public ResultData<Boolean> save(@Valid @RequestBody UnifiedConfig t, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResultData.failure(PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        if(null != t.getUnifiedConfigId()) {
            return ResultData.success(unifiedConfigService.updateById(t));
        }
        return ResultData.success(unifiedConfigService.save(t));
    }
}
