package com.chua.starter.proxy.support.controller.server;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.core.spi.SpiOption;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.common.support.base.validator.group.AddGroup;
import com.chua.common.support.base.validator.group.UpdateGroup;
import com.chua.starter.proxy.support.entity.SystemServer;
import com.chua.starter.proxy.support.service.server.SystemServerService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统服务器配置控制器
 *
 * @author CH
 * @since 2025/01/07
 */
@Slf4j
@RestController
@RequestMapping("/proxy/server")
@RequiredArgsConstructor
@Api(tags = "系统服务器管理")
@Tag(name = "系统服务器管理", description = "系统服务器配置的增删改查和控制操作")
public class SystemServerController {

    private final SystemServerService systemServerService;

    /**
     * 分页查询服务器列表
     */
    @GetMapping("/page")
    @ApiOperation("分页查询服务器列表")
    public ReturnResult<IPage<SystemServer>> page(
            @ApiParam("当前页") @RequestParam(defaultValue = "1") Integer current,
            @ApiParam("页大小") @RequestParam(defaultValue = "10") Integer size,
            @ApiParam("服务器名称") @RequestParam(required = false) String serverName,
            @ApiParam("服务器类型") @RequestParam(required = false) String serverType,
            @ApiParam("服务器状态") @RequestParam(required = false) String status) {

        try {
            Page<SystemServer> page = new Page<>(current, size);
            SystemServer entity = new SystemServer();
            entity.setSystemServerName(serverName);
            entity.setSystemServerType(serverType);
            if (StringUtils.isNotBlank(status)) {
                entity.setSystemServerStatus(SystemServer.SystemServerStatus.valueOf(status));
            }

            IPage<SystemServer> result = systemServerService.pageFor(page, entity);
            return ReturnResult.ok(result);
        } catch (Exception e) {
            log.error("分页查询服务器列表失败", e);
            return ReturnResult.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询服务器详情
     */
    @GetMapping("/{id}")
    @ApiOperation("根据ID查询服务器详情")
    public ReturnResult<SystemServer> getById(@ApiParam("服务器ID") @PathVariable Integer id) {
        try {
            SystemServer server = systemServerService.getById(id);
            if (server == null) {
                return ReturnResult.error("服务器不存在");
            }
            return ReturnResult.ok(server);
        } catch (Exception e) {
            log.error("查询服务器详情失败: id={}", id, e);
            return ReturnResult.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 新增服务器
     */
    @PostMapping
    @ApiOperation("新增服务器")
    public ReturnResult<SystemServer> add(@ApiParam("服务器信息") @RequestBody @Validated(AddGroup.class) SystemServer server) {
        try {
            // 检查端口是否可用
            ReturnResult<Boolean> portCheck = systemServerService.checkPortAvailable(server.getSystemServerPort(), null);
            if (!portCheck.isOk() || !portCheck.getData()) {
                return ReturnResult.error("端口 " + server.getSystemServerPort() + " 不可用");
            }

            // 设置默认状态
            server.setSystemServerStatus(SystemServer.SystemServerStatus.STOPPED);

            boolean result = systemServerService.save(server);
            if (result) {
                return ReturnResult.ok(server);
            } else {
                return ReturnResult.error("新增服务器失败");
            }
        } catch (Exception e) {
            log.error("新增服务器失败", e);
            return ReturnResult.error("新增失败: " + e.getMessage());
        }
    }

    /**
     * 更新服务器
     */
    @PutMapping
    @ApiOperation("更新服务器")
    public ReturnResult<SystemServer> update(@ApiParam("服务器信息") @RequestBody @Validated(UpdateGroup.class) SystemServer server) {
        try {
            // 检查端口是否可用（排除自己）
            ReturnResult<Boolean> portCheck = systemServerService.checkPortAvailable(server.getSystemServerPort(), server.getSystemServerId());
            if (!portCheck.isOk() || !portCheck.getData()) {
                return ReturnResult.error("端口 " + server.getSystemServerPort() + " 不可用");
            }

            boolean result = systemServerService.updateById(server);
            if (result) {
                // 如果服务器正在运行，应用配置更改
                if (SystemServer.SystemServerStatus.RUNNING.equals(server.getSystemServerStatus())) {
                    systemServerService.applyConfigChanges(server.getSystemServerId());
                }
                return ReturnResult.ok(server);
            } else {
                return ReturnResult.error("更新服务器失败");
            }
        } catch (Exception e) {
            log.error("更新服务器失败", e);
            return ReturnResult.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除服务器
     */
    @DeleteMapping("/{id}")
    @ApiOperation("删除服务器")
    public ReturnResult<Boolean> delete(@ApiParam("服务器ID") @PathVariable Integer id) {
        try {
            SystemServer server = systemServerService.getById(id);
            if (server == null) {
                return ReturnResult.error("服务器不存在");
            }

            // 如果服务器正在运行，先停止
            if (SystemServer.SystemServerStatus.RUNNING.equals(server.getSystemServerStatus())) {
                ReturnResult<Boolean> stopResult = systemServerService.stopServer(id);
                if (!stopResult.isOk()) {
                    return ReturnResult.error("停止服务器失败，无法删除");
                }
            }

            boolean result = systemServerService.removeById(id);
            return ReturnResult.ok(result);
        } catch (Exception e) {
            log.error("删除服务器失败: id={}", id, e);
            return ReturnResult.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 启动服务器
     */
    @PostMapping("/{id}/start")
    @ApiOperation("启动服务器")
    public ReturnResult<Boolean> start(@ApiParam("服务器ID") @PathVariable Integer id) {
        return systemServerService.startServer(id);
    }

    /**
     * 停止服务器
     */
    @PostMapping("/{id}/stop")
    @ApiOperation("停止服务器")
    public ReturnResult<Boolean> stop(@ApiParam("服务器ID") @PathVariable Integer id) {
        return systemServerService.stopServer(id);
    }

    /**
     * 重启服务器
     */
    @PostMapping("/{id}/restart")
    @ApiOperation("重启服务器")
    public ReturnResult<Boolean> restart(@ApiParam("服务器ID") @PathVariable Integer id) {
        return systemServerService.restartServer(id);
    }

    /**
     * 获取服务器状态
     */
    @GetMapping("/{id}/status")
    @ApiOperation("获取服务器状态")
    public ReturnResult<String> getStatus(@ApiParam("服务器ID") @PathVariable Integer id) {
        return systemServerService.getServerStatus(id);
    }

    /**
     * 获取可用的服务器类型
     */
    @GetMapping("/types")
    @ApiOperation("获取可用的服务器类型")
    public ReturnResult<List<SpiOption>> getAvailableServerTypes() {
        return systemServerService.getAvailableServerTypes();
    }

    /**
     * 获取服务器统计信息
     */
    @GetMapping("/statistics")
    @ApiOperation("获取服务器统计信息")
    public ReturnResult<Map<String, Object>> getStatistics() {
        return systemServerService.getStatistics();
    }

    /**
     * 检查端口可用性
     */
    @GetMapping("/check-port")
    @ApiOperation("检查端口可用性")
    public ReturnResult<Boolean> checkPortAvailable(
            @ApiParam("端口号") @RequestParam Integer port,
            @ApiParam("排除的服务器ID") @RequestParam(required = false) Integer serverId) {
        return systemServerService.checkPortAvailable(port, serverId);
    }

    /**
     * 克隆服务器
     */
    @PostMapping("/{id}/clone")
    @ApiOperation("克隆服务器")
    public ReturnResult<SystemServer> clone(
            @ApiParam("源服务器ID") @PathVariable Integer id,
            @ApiParam("新服务器名称") @RequestParam String newServerName,
            @ApiParam("新端口号") @RequestParam Integer newPort) {
        return systemServerService.cloneServer(id, newServerName, newPort);
    }

    /**
     * 应用配置更改
     */
    @PostMapping("/{id}/apply-config")
    @ApiOperation("应用配置更改")
    public ReturnResult<Boolean> applyConfigChanges(@ApiParam("服务器ID") @PathVariable Integer id) {
        return systemServerService.applyConfigChanges(id);
    }
}




