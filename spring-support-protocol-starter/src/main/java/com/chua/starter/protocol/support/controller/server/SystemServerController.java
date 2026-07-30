package com.chua.starter.protocol.support.controller.server;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.utils.group.AddGroup;
import com.chua.common.support.utils.group.UpdateGroup;
import com.chua.common.support.spi.SpiOption;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.protocol.support.entity.SystemServer;
import com.chua.starter.protocol.support.service.server.SystemServerService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 协议管理-系统服务控制器。
 */
@Slf4j
@RestController
@RequestMapping("/system/server")
@RequiredArgsConstructor
@Api(tags = "协议管理-系统服务")
@Tag(name = "协议管理-系统服务", description = "系统服务生命周期、分页与统计")
public class SystemServerController {

    private final SystemServerService systemServerService;

    @GetMapping("/page")
    @ApiOperation("分页查询服务列表")
    public ReturnResult<IPage<SystemServer>> page(
            @ApiParam("当前页") @RequestParam(defaultValue = "1") Integer current,
            @ApiParam("页大小") @RequestParam(defaultValue = "10") Integer size,
            @ApiParam("服务名称") @RequestParam(required = false) String serverName,
            @ApiParam("服务类型") @RequestParam(required = false) String serverType,
            @ApiParam("服务状态") @RequestParam(required = false) String status
    ) {
        try {
            Page<SystemServer> page = new Page<>(current, size);
            SystemServer entity = new SystemServer();
            entity.setSystemServerName(serverName);
            entity.setSystemServerType(serverType);
            if (StringUtils.isNotBlank(status)) {
                entity.setSystemServerStatus(SystemServer.SystemServerStatus.valueOf(status));
            }
            return ReturnResult.ok(systemServerService.pageFor(page, entity));
        } catch (Exception e) {
            log.error("分页查询服务失败", e);
            return ReturnResult.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @ApiOperation("查询服务详情")
    public ReturnResult<SystemServer> getById(@ApiParam("服务ID") @PathVariable Integer id) {
        SystemServer server = systemServerService.getById(id);
        if (server == null) {
            return ReturnResult.error("服务不存在");
        }
        return ReturnResult.ok(server);
    }

    @PostMapping
    @ApiOperation("新增服务")
    public ReturnResult<SystemServer> add(@ApiParam("服务信息") @RequestBody @Validated(AddGroup.class) SystemServer server) {
        try {
            ReturnResult<Boolean> portCheck = systemServerService.checkPortAvailable(server.getSystemServerPort(), null);
            if (!portCheck.isOk() || !Boolean.TRUE.equals(portCheck.getData())) {
                return ReturnResult.error("端口 " + server.getSystemServerPort() + " 不可用");
            }
            server.setSystemServerStatus(SystemServer.SystemServerStatus.STOPPED);
            return systemServerService.save(server) ? ReturnResult.ok(server) : ReturnResult.error("新增失败");
        } catch (Exception e) {
            log.error("新增服务失败", e);
            return ReturnResult.error("新增失败: " + e.getMessage());
        }
    }

    @PutMapping
    @ApiOperation("更新服务")
    public ReturnResult<SystemServer> update(@ApiParam("服务信息") @RequestBody @Validated(UpdateGroup.class) SystemServer server) {
        try {
            ReturnResult<Boolean> portCheck = systemServerService.checkPortAvailable(server.getSystemServerPort(), server.getSystemServerId());
            if (!portCheck.isOk() || !Boolean.TRUE.equals(portCheck.getData())) {
                return ReturnResult.error("端口 " + server.getSystemServerPort() + " 不可用");
            }
            boolean result = systemServerService.updateById(server);
            if (result && SystemServer.SystemServerStatus.RUNNING.equals(server.getSystemServerStatus())) {
                systemServerService.applyConfigChanges(server.getSystemServerId());
            }
            return result ? ReturnResult.ok(server) : ReturnResult.error("更新失败");
        } catch (Exception e) {
            log.error("更新服务失败", e);
            return ReturnResult.error("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除服务")
    public ReturnResult<Boolean> delete(@ApiParam("服务ID") @PathVariable Integer id) {
        try {
            SystemServer server = systemServerService.getById(id);
            if (server == null) {
                return ReturnResult.error("服务不存在");
            }
            if (SystemServer.SystemServerStatus.RUNNING.equals(server.getSystemServerStatus())) {
                ReturnResult<Boolean> stopResult = systemServerService.stopServer(id);
                if (!stopResult.isOk()) {
                    return ReturnResult.error("服务正在运行且停止失败，无法删除");
                }
            }
            return ReturnResult.ok(systemServerService.removeById(id));
        } catch (Exception e) {
            log.error("删除服务失败: id={}", id, e);
            return ReturnResult.error("删除失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/start")
    @ApiOperation("启动服务")
    public ReturnResult<Boolean> start(@ApiParam("服务ID") @PathVariable Integer id) {
        return systemServerService.startServer(id);
    }

    @PostMapping("/{id}/stop")
    @ApiOperation("停止服务")
    public ReturnResult<Boolean> stop(@ApiParam("服务ID") @PathVariable Integer id) {
        return systemServerService.stopServer(id);
    }

    @PostMapping("/{id}/restart")
    @ApiOperation("重启服务")
    public ReturnResult<Boolean> restart(@ApiParam("服务ID") @PathVariable Integer id) {
        return systemServerService.restartServer(id);
    }

    @GetMapping("/{id}/status")
    @ApiOperation("获取服务状态")
    public ReturnResult<String> getStatus(@ApiParam("服务ID") @PathVariable Integer id) {
        return systemServerService.getServerStatus(id);
    }

    @GetMapping("/types")
    @ApiOperation("获取可用服务类型")
    public ReturnResult<List<SpiOption>> getAvailableServerTypes() {
        return systemServerService.getAvailableServerTypes();
    }

    @GetMapping("/statistics")
    @ApiOperation("获取统计信息")
    public ReturnResult<Map<String, Object>> getStatistics() {
        return systemServerService.getStatistics();
    }

    @GetMapping("/check-port")
    @ApiOperation("检查端口可用性")
    public ReturnResult<Boolean> checkPortAvailable(
            @ApiParam("端口") @RequestParam Integer port,
            @ApiParam("排除服务ID") @RequestParam(required = false) Integer serverId
    ) {
        return systemServerService.checkPortAvailable(port, serverId);
    }

    @PostMapping("/{id}/clone")
    @ApiOperation("克隆服务")
    public ReturnResult<SystemServer> clone(
            @ApiParam("源服务ID") @PathVariable Integer id,
            @ApiParam("新服务名称") @RequestParam(required = false) String newServerName,
            @ApiParam("新服务名称(兼容参数)") @RequestParam(required = false) String newName,
            @ApiParam("新端口") @RequestParam Integer newPort
    ) {
        String finalName = StringUtils.isNotBlank(newServerName) ? newServerName : newName;
        if (StringUtils.isBlank(finalName)) {
            return ReturnResult.error("新服务名称不能为空");
        }
        return systemServerService.cloneServer(id, finalName, newPort);
    }

    @PostMapping("/{id}/apply-config")
    @ApiOperation("热应用配置")
    public ReturnResult<Boolean> applyConfigChanges(@ApiParam("服务ID") @PathVariable Integer id) {
        return systemServerService.applyConfigChanges(id);
    }

    @PostMapping("/batch")
    @ApiOperation("批量操作服务(start/stop/restart/delete)")
    public ReturnResult<Boolean> batchOperation(
            @ApiParam("服务ID列表，逗号分隔") @RequestParam(required = false) String serverIds,
            @ApiParam("服务ID列表") @RequestParam(required = false) List<Integer> idList,
            @ApiParam("操作类型") @RequestParam String operation
    ) {
        List<Integer> ids = resolveIds(serverIds, idList);
        if (ids.isEmpty()) {
            return ReturnResult.error("服务ID不能为空");
        }
        for (Integer id : ids) {
            ReturnResult<Boolean> result = executeOperation(id, operation);
            if (!result.isOk()) {
                return ReturnResult.error("批量操作失败，id=" + id + "，原因: " + result.getMsg());
            }
        }
        return ReturnResult.ok(true);
    }

    @PostMapping("/auto-start")
    @ApiOperation("自动启动所有配置了自动启动的服务")
    public ReturnResult<Boolean> autoStartSystemServers() {
        List<SystemServer> autoStartServers = systemServerService.list(
                new LambdaQueryWrapper<SystemServer>()
                        .eq(SystemServer::getSystemServerAutoStart, true)
        );
        for (SystemServer server : autoStartServers) {
            if (!Objects.equals(server.getSystemServerStatus(), SystemServer.SystemServerStatus.RUNNING)) {
                ReturnResult<Boolean> result = systemServerService.startServer(server.getSystemServerId());
                if (!result.isOk()) {
                    return ReturnResult.error("自动启动失败，id=" + server.getSystemServerId() + "，原因: " + result.getMsg());
                }
            }
        }
        return ReturnResult.ok(true);
    }

    @PostMapping("/stop-all")
    @ApiOperation("停止所有运行中的服务")
    public ReturnResult<Boolean> stopAllSystemServers() {
        List<SystemServer> runningServers = systemServerService.list(
                new LambdaQueryWrapper<SystemServer>()
                        .eq(SystemServer::getSystemServerStatus, SystemServer.SystemServerStatus.RUNNING)
        );
        for (SystemServer server : runningServers) {
            ReturnResult<Boolean> result = systemServerService.stopServer(server.getSystemServerId());
            if (!result.isOk()) {
                return ReturnResult.error("停止失败，id=" + server.getSystemServerId() + "，原因: " + result.getMsg());
            }
        }
        return ReturnResult.ok(true);
    }

    private ReturnResult<Boolean> executeOperation(Integer id, String operation) {
        String op = operation == null ? "" : operation.trim().toLowerCase();
        return switch (op) {
            case "start" -> systemServerService.startServer(id);
            case "stop" -> systemServerService.stopServer(id);
            case "restart" -> systemServerService.restartServer(id);
            case "delete" -> {
                ReturnResult<Boolean> result = delete(id);
                yield result.isOk() ? ReturnResult.ok(true) : ReturnResult.error(result.getMsg());
            }
            default -> ReturnResult.error("不支持的操作类型: " + operation);
        };
    }

    private List<Integer> resolveIds(String serverIds, List<Integer> idList) {
        List<Integer> result = new ArrayList<>();
        if (idList != null) {
            result.addAll(idList.stream().filter(Objects::nonNull).toList());
        }
        if (StringUtils.isNotBlank(serverIds)) {
            Arrays.stream(serverIds.split(","))
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .map(Integer::valueOf)
                    .forEach(result::add);
        }
        return result.stream().distinct().toList();
    }
}

