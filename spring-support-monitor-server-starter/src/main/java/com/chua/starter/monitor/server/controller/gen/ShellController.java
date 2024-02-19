package com.chua.starter.monitor.server.controller.gen;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.session.Session;
import com.chua.common.support.session.query.ExecuteQuery;
import com.chua.common.support.session.result.SessionResult;
import com.chua.common.support.session.result.SessionResultSet;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.chua.common.support.constant.NameConstant.SYMBOL_EXCEPTION;

@RestController
@SuppressWarnings("ALL")
@Tag(name = "脚本接口")
@Slf4j
@RequestMapping("v1/shell")
public class ShellController extends AbstractSwaggerController<MonitorSysGenShellService, MonitorSysGenShell> {

    @Resource
    private MonitorSysGenService sysGenService;

    @Getter
    @Resource
    private MonitorSysGenShellService service;
    @Resource
    private SocketSessionTemplate socketSessionTemplate;
    /**
     * 开始
     *
     * @param shellId 外壳id
     * @return {@link ReturnResult}<{@link Boolean}>
     */
    @Operation(summary = "启动脚本")
    @PutMapping({"start", "stop"})
    public ReturnResult<SessionResult> start(@RequestBody ExecuteQuery executeQuery) {
        if (null == executeQuery.getGenId() || null == executeQuery.getDataId()) {
            return ReturnResult.error("未配置生成器类型");
        }
        MonitorSysGen sysGen = sysGenService.getById(executeQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        MonitorSysGenShell sysGenShell = service.getById(executeQuery.getDataId());
        StringBuilder stringBuffer = new StringBuilder();
        long startTime = System.nanoTime();
        Session session = ServiceProvider.of(Session.class).getKeepExtension(sysGen.getGenId() + "", sysGen.getGenType(), sysGen.newDatabaseOptions());
        SessionResultSet sessionResultSet = null;
        session.setListener(message -> {
            socketSessionTemplate.send(executeQuery.getDataId(), message);
        });
        try {
            sessionResultSet = session.executeQuery("nohup " + sysGenShell.getShellScriptPath() + " 2>1& &\r", new ExecuteQuery());
            if(StringUtils.isNotEmpty(sysGenShell.getShellScriptPath())) {
                stringBuffer.append(sysGenShell.getShellScriptPath());
            }
        } catch (Exception e) {
            String localizedMessage = e.getLocalizedMessage();
            if(null != localizedMessage) {
                int i = localizedMessage.indexOf(SYMBOL_EXCEPTION);
                while (i > -1) {
                    localizedMessage = localizedMessage.substring(SYMBOL_EXCEPTION.length() + i + 1);
                    i = localizedMessage.indexOf(SYMBOL_EXCEPTION);
                }
                stringBuffer.append(localizedMessage);
            }

            return ReturnResult.illegal(stringBuffer.toString());
        }
        long toMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        stringBuffer.append("\r\n").append("耗时: ").append(toMillis);
        stringBuffer.append(" ms");
        sysGenShell.setShellStatus(sysGenShell.getShellStatus() == 0 ? 1 : 0);
        service.updateById(sysGenShell);
        SessionResult sessionResult = new SessionResult();
        sessionResult.setData(sessionResultSet.toData());
        sessionResult.setFields(sessionResultSet.toFields());;
        sessionResult.setMessage(stringBuffer.toString());
        sessionResult.setCost(toMillis);
        return ReturnResult.ok(sessionResult);
    }
}
