package com.chua.starter.unified.server.support.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.result.ResultData;
import com.chua.starter.mybatis.entity.DelegatePage;
import com.chua.starter.unified.server.support.entity.UnifiedLimit;
import com.chua.starter.unified.server.support.entity.UnifiedLimitLog;
import com.chua.starter.unified.server.support.service.UnifiedLimitLogService;
import com.chua.starter.unified.server.support.service.UnifiedLimitService;
import lombok.AllArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Date;

import static com.chua.common.support.lang.code.ReturnCode.REQUEST_PARAM_ERROR;


/**
 * 配置中心接口
 *
 * @author CH
 */
@RestController
@RequestMapping("v1/limit")
@AllArgsConstructor
public class UnifiedLimiterController {

    private final UnifiedLimitService unifiedLimitService;
    private final UnifiedLimitLogService unifiedLimitLogService;

    /**
     * 分页查询数据
     *
     * @param page   页码
     * @param entity 结果
     * @return 分页结果
     */
    @GetMapping("page")
    @ResponseBody
    public ReturnPageResult<Page<UnifiedLimit>> page(DelegatePage<UnifiedLimit> page, @Valid UnifiedLimit entity, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnPageResult.illegal(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return ReturnPageResult.ok(unifiedLimitService.page(page.createPage(), Wrappers.<UnifiedLimit>lambdaQuery()
                .eq(StringUtils.isNotBlank(entity.getUnifiedLimitProfile()), UnifiedLimit::getUnifiedLimitProfile, entity.getUnifiedLimitProfile())
        ));
    }
    /**
     * 分页查询数据
     *
     * @param page   页码
     * @return 分页结果
     */
    @GetMapping("log/page")
    @ResponseBody
    public ReturnPageResult<Page<UnifiedLimitLog>> logPage(DelegatePage<UnifiedLimitLog> page,
                                                           @RequestParam(required = false) Date startTime,
                                                           @RequestParam(required = false) Date endTime
                                                           ) {
        return ReturnPageResult.ok(unifiedLimitLogService.page(page.createPage(),
                Wrappers.<UnifiedLimitLog>lambdaQuery()
                        .ge(null != startTime, UnifiedLimitLog::getCreateTime, startTime)
                        .le(null != endTime, UnifiedLimitLog::getCreateTime, endTime)
                        .orderByDesc(UnifiedLimitLog::getCreateTime)));
    }

    /**
     * 根据主键删除数据
     *
     * @param id 页码
     * @return 分页结果
     */
    @ResponseBody
    @DeleteMapping("delete")
    public ResultData<Boolean> delete(String id) {
        if (null == id) {
            return ResultData.failure(REQUEST_PARAM_ERROR, "主键不能为空");
        }
        return ResultData.success(unifiedLimitService.removeBatchByIds(Splitter.on(",").trimResults().omitEmptyStrings().splitToSet(id)));
    }

    /**
     * 根据主键更新数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @PostMapping("update")
    @ResponseBody
    public ReturnResult<Boolean> updateById(@Valid @RequestBody UnifiedLimit t, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        t.setUpdateTime(new Date());
        boolean b = unifiedLimitService.updateById(t);
        if(!b) {
            return ReturnResult.illegal("更新失败, 请稍后重试");
        }
        if(t.getUnifiedLimitStatus().equals(1)) {
            unifiedLimitService.notifyConfig(t);
        }
        return ReturnResult.ok(true);
    }
    /**
     * 手动下发
     *
     * @param id id
     * @return 分页结果
     */
    @GetMapping("notify")
    @ResponseBody
    public ReturnResult<Boolean> notifyConfig(String id) {
        if (null == id) {
            return ReturnResult.illegal("信息不存在");
        }
        UnifiedLimit unifiedLimit = unifiedLimitService.getById(id);
        if(null == unifiedLimit) {
            return ReturnResult.illegal("信息不存在");
        }
        if(unifiedLimit.getUnifiedLimitStatus().equals(1)) {
            unifiedLimitService.notifyConfig(unifiedLimit);
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
    @ResponseBody
    public ResultData<Boolean> save(@Valid @RequestBody UnifiedLimit t, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResultData.failure(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        return unifiedLimitService.saveOrUpdateConfig(t);
    }
}
