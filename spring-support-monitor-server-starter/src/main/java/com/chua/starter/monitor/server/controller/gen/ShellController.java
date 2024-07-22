package com.chua.starter.monitor.server.controller.gen;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.session.Session;
import com.chua.common.support.session.query.ExecuteQuery;
import com.chua.common.support.session.result.SessionResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.monitor.server.entity.MonitorSysGen;
import com.chua.starter.monitor.server.entity.MonitorSysGenShell;
import com.chua.starter.monitor.server.service.MonitorSysGenService;
import com.chua.starter.monitor.server.service.MonitorSysGenShellService;
import com.chua.starter.mybatis.controller.AbstractSwaggerController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * 终端接口
 *
 */
@RestController
@SuppressWarnings("ALL")
@Tag(name = "终端接口")
@Slf4j
@RequestMapping("v1/shell")
@RequiredArgsConstructor
public class ShellController extends AbstractSwaggerController<MonitorSysGenShellService, MonitorSysGenShell> {

    private final MonitorSysGenService sysGenService;
    @Getter
    private final MonitorSysGenShellService service;
    private final SocketSessionTemplate socketSessionTemplate;
    /**
     * 关闭
     *
     * @param shellId 外壳id
     * @return {@link ReturnResult}<{@link Boolean}>
     */
    @Operation(summary = "关闭日志日志")
    @PutMapping({"log/close"})
    public ReturnResult<Boolean> close(@RequestBody ExecuteQuery executeQuery) {
        if (null == executeQuery.getGenId() || null == executeQuery.getDataId()) {
            return ReturnResult.error("未配置生成器类型");
        }
        MonitorSysGen sysGen = sysGenService.getById(executeQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        MonitorSysGenShell sysGenShell = service.getById(executeQuery.getDataId());
        ServiceProvider.of(Session.class).closeKeepExtension(sysGen.getGenId() + "_log");
        try {
            return ReturnResult.ok();
        } catch (Exception e) {
            return ReturnResult.error("开启失败");
        }
    }
    /**
     * 开始
     *
     * @param shellId 外壳id
     * @return {@link ReturnResult}<{@link Boolean}>
     */
    @Operation(summary = "开启日志")
    @PutMapping({"log/open"})
    public ReturnResult<Boolean> open(@RequestBody ExecuteQuery executeQuery) {
        if (null == executeQuery.getGenId() || null == executeQuery.getDataId()) {
            return ReturnResult.error("未配置生成器类型");
        }
        MonitorSysGen sysGen = sysGenService.getById(executeQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        MonitorSysGenShell sysGenShell = service.getById(executeQuery.getDataId());
        Session session = ServiceProvider.of(Session.class).getKeepExtension(sysGen.getGenId() + "_log", sysGen.getGenType(), sysGen.newDatabaseOptions());
        if(!session.isConnect()) {
            ServiceProvider.of(Session.class).closeKeepExtension(sysGen.getGenId() + "");
            return ReturnResult.illegal("当前服务器不可达");
        }
        session.setListener(message -> {
            socketSessionTemplate.send(executeQuery.getDataId(), message);
        });
        try {
            session.executeQuery("tail -f " + sysGenShell.getOutName() + " \r", new ExecuteQuery());
            return ReturnResult.ok();
        } catch (Exception e) {
            return ReturnResult.error("开启失败");
        }
    }
    /**
     * 开始
     *
     * @param shellId 外壳id
     * @return {@link ReturnResult}<{@link Boolean}>
     */
    @Operation(summary = "启停Shell")
    @PutMapping({"start", "stop"})
    public ReturnResult<SessionResult> start(@RequestBody ExecuteQuery executeQuery) {
        if (null == executeQuery.getGenId() || null == executeQuery.getDataId()) {
            return ReturnResult.error("未配置生成器类型");
        }
        MonitorSysGen sysGen = sysGenService.getById(executeQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }
        String dataId = executeQuery.getDataId();
        StringBuilder stringBuffer = new StringBuilder();
        long startTime = System.nanoTime();
        Session session = ServiceProvider.of(Session.class).getKeepExtension(sysGen.getGenId() + "", sysGen.getGenType(), sysGen.newDatabaseOptions());
        if(!session.isConnect()) {
            ServiceProvider.of(Session.class).closeKeepExtension(sysGen.getGenId() + "");
            return ReturnResult.illegal("当前服务器不可达");
        }
        long toMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        stringBuffer.append("\r\n").append("耗时: ").append(toMillis);
        stringBuffer.append(" ms");
        SessionResult sessionResult = new SessionResult();
        sessionResult.setMessage(stringBuffer.toString());
        sessionResult.setCost(toMillis);
        return ReturnResult.ok(sessionResult);
    }
}
