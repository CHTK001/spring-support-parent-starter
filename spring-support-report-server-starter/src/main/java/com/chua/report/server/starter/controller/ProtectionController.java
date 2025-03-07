package com.chua.report.server.starter.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.utils.StringUtils;
import com.chua.report.server.starter.entity.MonitorProtection;
import com.chua.report.server.starter.service.MonitorProtectionService;
import com.chua.starter.mybatis.controller.AbstractSwaggerQueryController;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 守护进程
 *
 * @author CH
 */
@RestController
@SuppressWarnings("ALL")
@Tag(name = "守护进程")
@Slf4j
@RequiredArgsConstructor
@RequestMapping("v1/protection")
public class ProtectionController extends AbstractSwaggerQueryController<MonitorProtectionService, MonitorProtection> {

    private final MonitorProtectionService monitorProtectionService;


    @Override
    public Wrapper<MonitorProtection> createWrapper(MonitorProtection entity) {
        return Wrappers.<MonitorProtection>lambdaQuery()
                .likeRight(StringUtils.isNotEmpty(entity.getMonitorProtectionName()), MonitorProtection::getMonitorProtectionName, entity.getMonitorProtectionName())
                .eq(null != entity.getMonitorProtectionStatus(), MonitorProtection::getMonitorProtectionStatus, entity.getMonitorProtectionStatus())
                .orderByDesc(MonitorProtection::getCreateTime);
    }

    @Override
    public MonitorProtectionService getService() {
        return monitorProtectionService;
    }
}
