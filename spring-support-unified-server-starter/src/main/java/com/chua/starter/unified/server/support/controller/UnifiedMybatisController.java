package com.chua.starter.unified.server.support.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.result.ResultData;
import com.chua.starter.mybatis.entity.DelegatePage;
import com.chua.starter.unified.server.support.entity.UnifiedMybatis;
import com.chua.starter.unified.server.support.service.UnifiedMybatisService;
import lombok.AllArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Date;

import static com.chua.common.support.lang.code.ReturnCode.PARAM_ERROR;

/**
 * 配置中心接口
 *
 * @author CH
 */
@RestController
@RequestMapping("v1/mybatis")
@AllArgsConstructor
public class UnifiedMybatisController {

    private final UnifiedMybatisService unifiedMybatisService;

    /**
     * 分页查询数据
     *
     * @param page   页码
     * @param entity 结果
     * @return 分页结果
     */
    @GetMapping("page")
    public ReturnPageResult<Page<UnifiedMybatis>> page(DelegatePage<UnifiedMybatis> page, @Valid UnifiedMybatis entity, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnPageResult.illegal(PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return ReturnPageResult.ok(unifiedMybatisService.page(page.createPage(), Wrappers.<UnifiedMybatis>lambdaQuery()
                .eq(StringUtils.isNotBlank(entity.getUnifiedMybatisProfile()), UnifiedMybatis::getUnifiedMybatisProfile, entity.getUnifiedMybatisProfile())
        ));
    }

    /**
     * 根据主键删除数据
     *
     * @param id 页码
     * @return 分页结果
     */
    @DeleteMapping("delete")
    public ResultData<Boolean> delete(String id) {
        if (null == id) {
            return ResultData.failure(PARAM_ERROR, "主键不能为空");
        }
        return ResultData.success(unifiedMybatisService.removeBatchByIds(Splitter.on(",").trimResults().omitEmptyStrings().splitToSet(id)));
    }

    /**
     * 根据主键更新数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @PostMapping("update")
    public ReturnResult<Boolean> updateById(@Valid @RequestBody UnifiedMybatis t, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        t.setUpdateTime(new Date());
        boolean b = unifiedMybatisService.updateById(t);
        if(!b) {
            return ReturnResult.illegal("更新失败, 请稍后重试");
        }
        return ReturnResult.ok(true);
    }

    /**
     * 添加数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @PostMapping("save")
    public ResultData<Boolean> save(@Valid @RequestBody UnifiedMybatis t, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResultData.failure(PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        unifiedMybatisService.saveOrUpdate(t);
        return ResultData.success(true);
    }

    /**
     * 通知
     *
     * @param id id
     * @return 分页结果
     */
    @GetMapping("notify")
    public ResultData<Boolean> notify(String id) {
        if (null == id) {
            return ResultData.failure(PARAM_ERROR, "主键不能为空");
        }
        UnifiedMybatis unifiedMybatis = unifiedMybatisService.getById(id);
        if (null == unifiedMybatis) {
            return ResultData.failure(PARAM_ERROR, "信息不存在");
        }

        unifiedMybatisService.notifyConfig(unifiedMybatis);
        return ResultData.success(true);
    }
}
