package com.chua.starter.monitor.server.controller.gen;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.redis.support.search.SearchResultItem;
import com.chua.starter.monitor.server.entity.MonitorSysGen;
import com.chua.starter.monitor.server.properties.GenProperties;
import com.chua.starter.monitor.server.query.LogTimeQuery;
import com.chua.starter.monitor.server.service.MonitorGenBackupService;
import com.chua.starter.monitor.server.service.MonitorSysGenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日志控制器
 *
 * @author CH
 */
@RestController
@SuppressWarnings("ALL")
@Tag(name = "数据库日志接口")
@Slf4j
@RequiredArgsConstructor
@RequestMapping("v1/log")
public class LogController {

    private final MonitorSysGenService sysGenService;
    private final MonitorGenBackupService monitorGenBackupService;
    private final GenProperties genProperties;
    private final ApplicationContext applicationContext;
    private static final String MYSQL = "mysql";

    @Operation(summary = "查询日志")
    @GetMapping("query")
    public ReturnResult<SearchResultItem> query(LogTimeQuery timeQuery, MonitorSysGen sysGen) {
        if(null == sysGen.getGenId()) {
            return ReturnResult.illegal("genId 不能为空");
        }
        MonitorSysGen monitorSysGen = sysGenService.getById(sysGen);
        return monitorGenBackupService.queryForLog(timeQuery, monitorSysGen);
    }

}
