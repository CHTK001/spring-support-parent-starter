package com.chua.starter.monitor.server.controller;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.entity.MonitorApp;
import com.chua.starter.monitor.server.factory.MonitorServerFactory;
import com.chua.starter.monitor.server.service.MonitorAppService;
import com.chua.starter.mybatis.controller.AbstractSwaggerUpdateController;
import com.chua.starter.mybatis.entity.PageRequest;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.github.xiaoymin.knife4j.annotations.Ignore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 监控应用控制器
 */
@RestController
@RequestMapping("v1/app")
@Tag(name = "上报数据接口")
@RequiredArgsConstructor
public class MonitorAppController extends AbstractSwaggerUpdateController<MonitorAppService, MonitorApp> {

    @Getter
    private final MonitorAppService service;

    private final MonitorServerFactory monitorServerFactory;


    /**
     * 分页查询数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "分页查询基础数据")
    @GetMapping("page")
    public ReturnPageResult<MonitorApp> page(PageRequest<MonitorApp> page, @Valid MonitorApp entity, @Ignore BindingResult bindingResult) {
        Page<MonitorApp> page1 = getService().page(page.createPage(), Wrappers.lambdaQuery(entity));
        mergePage(page1);
        return PageResultUtils.ok(page1);
    }

    /**
     * 分页查询数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "查询基础数据")
    @GetMapping("list")
    public ReturnResult<List<MonitorApp>> list(PageRequest<MonitorApp> page, @Valid MonitorApp entity, @Ignore BindingResult bindingResult) {
        List<MonitorApp> page1 = getService().list(Wrappers.lambdaQuery(entity));
        mergePage(page1);
        return ReturnResult.ok(page1);
    }

    private void mergePage(Page<MonitorApp> page1) {
        mergePage(page1.getRecords());
    }
    private void mergePage(List<MonitorApp> page1) {
        Map<String, List<MonitorRequest>> appHeart = monitorServerFactory.getHeart();
        for (MonitorApp record : page1) {
            record.setMonitorRequests(appHeart.get(record.getMonitorAppname()));
        }
    }


}
