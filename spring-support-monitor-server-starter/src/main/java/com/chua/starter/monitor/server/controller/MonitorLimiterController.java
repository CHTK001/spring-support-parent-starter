package com.chua.starter.monitor.server.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.protocol.boot.CommandType;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.starter.common.support.result.ResultData;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.entity.MonitorLimit;
import com.chua.starter.monitor.server.factory.MonitorServerFactory;
import com.chua.starter.monitor.server.service.MonitorAppService;
import com.chua.starter.monitor.server.service.MonitorLimitService;
import com.chua.starter.mybatis.entity.DelegatePage;
import io.swagger.v3.oas.annotations.Operation;
import lombok.AllArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

import static com.chua.common.support.lang.code.ReturnCode.REQUEST_PARAM_ERROR;


/**
 * 配置中心接口
 *
 * @author CH
 */
@RestController
@RequestMapping("v1/limit")
@AllArgsConstructor
public class MonitorLimiterController {

    private final MonitorLimitService monitorLimitService;
    private final MonitorAppService monitorAppService;
    private final MonitorServerFactory monitorServerFactory;

    /**
     * 分页查询数据
     *
     * @param page   页码
     * @param entity 结果
     * @return 分页结果
     */
    @GetMapping("page")
    @ResponseBody
    public ReturnPageResult<Page<MonitorLimit>> page(DelegatePage<MonitorLimit> page, @Valid MonitorLimit entity, BindingResult bindingResult) {
        return ReturnPageResult.ok(monitorLimitService.page(page.createPage(), Wrappers.<MonitorLimit>lambdaQuery()
                .eq(StringUtils.isNotBlank(entity.getLimitProfile()), MonitorLimit::getLimitProfile, entity.getLimitProfile())
        ));
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
        return ResultData.success(monitorLimitService.removeBatchByIds(Splitter.on(",").trimResults().omitEmptyStrings().splitToSet(id)));
    }

    /**
     * 根据主键更新数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @PostMapping("update")
    @ResponseBody
    public ReturnResult<Boolean> updateById(@Valid @RequestBody MonitorLimit t, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        MonitorLimit monitorLimit = monitorLimitService.getById(t.getLimitId());
        if(null == monitorLimit) {
            return ReturnResult.illegal("信息不存在");
        }
        boolean b = monitorLimitService.updateById(t);
        if(!b) {
            return ReturnResult.illegal("更新失败, 请稍后重试");
        }
        if(t.getLimitStatus().equals(1)) {
            upload(monitorLimit);
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
        MonitorLimit unifiedLimit = monitorLimitService.getById(id);
        if(null == unifiedLimit) {
            return ReturnResult.illegal("信息不存在");
        }
        if(unifiedLimit.getLimitStatus().equals(1)) {
            upload(unifiedLimit);
        }
        return ReturnResult.ok(true);
    }
    /**
     * 添加数据
     *
     * @param monitorLimit 实体
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "下发配置")
    @PostMapping("upload")
    public ReturnResult<Boolean> upload( @RequestBody MonitorLimit monitorLimit) {
        MonitorLimit config = monitorLimitService.getById(monitorLimit.getLimitId());
        if(null == config) {
            return ReturnResult.illegal("数据不存在");
        }

        if(config.getLimitStatus() == 0) {
            return ReturnResult.illegal("配置已禁用");
        }


        List<MonitorRequest> heart = monitorServerFactory.getHeart(monitorLimit.getLimitApp());
        if(ObjectUtils.isEmpty(heart)) {
            return ReturnResult.illegal("应用不存在");
        }

        for (MonitorRequest monitorRequest : heart) {
            monitorAppService.upload(null, monitorRequest, Json.toJSONString(config), "LIMIT", CommandType.REQUEST);
        }
        return ReturnResult.success();
    }
    /**
     * 添加数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @PostMapping("save")
    @ResponseBody
    public ResultData<Boolean> save(@Validated({AddGroup.class, AddGroup.class}) @RequestBody MonitorLimit t, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResultData.failure(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }


        return ResultData.success(monitorLimitService.saveOrUpdate(t));
    }
}
