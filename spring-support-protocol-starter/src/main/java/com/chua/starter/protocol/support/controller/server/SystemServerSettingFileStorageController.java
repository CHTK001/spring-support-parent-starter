package com.chua.starter.protocol.support.controller.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.protocol.support.entity.SystemServerSettingFileStorage;
import com.chua.starter.protocol.support.service.server.SystemServerSettingFileStorageService;
import com.chua.starter.protocol.support.service.server.SystemServerSettingService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * 协议管理-文件存储配置控制器。
 */
@Slf4j
@RestController
@RequestMapping("/system/server/setting/file-storage")
@Api(tags = "协议管理-文件存储配置")
@Tag(name = "协议管理-文件存储配置")
@RequiredArgsConstructor
public class SystemServerSettingFileStorageController {

    private final SystemServerSettingFileStorageService fileStorageService;
    private final SystemServerSettingService systemServerSettingService;

    @GetMapping("/{serverId}")
    @ApiOperation("查询服务文件存储配置")
    public ReturnResult<List<SystemServerSettingFileStorage>> listByServerId(@ApiParam("服务ID") @PathVariable Integer serverId) {
        List<SystemServerSettingFileStorage> list = fileStorageService.listByServerId(serverId);
        return ReturnResult.ok(list == null ? Collections.emptyList() : list);
    }

    @PostMapping("/save")
    @ApiOperation("保存文件存储配置")
    public ReturnResult<Boolean> save(@ApiParam("配置") @RequestBody SystemServerSettingFileStorage config) {
        return fileStorageService.saveOne(config);
    }

    @PutMapping("/update")
    @ApiOperation("更新文件存储配置")
    public ReturnResult<Boolean> update(@ApiParam("配置") @RequestBody SystemServerSettingFileStorage config) {
        return fileStorageService.saveOne(config);
    }

    @DeleteMapping("/{serverId}")
    @ApiOperation("删除服务全部文件存储配置")
    public ReturnResult<Boolean> removeByServerId(@ApiParam("服务ID") @PathVariable Integer serverId) {
        return fileStorageService.replaceAllForServer(serverId, Collections.emptyList());
    }

    @DeleteMapping("/item/{id}")
    @ApiOperation("删除单条文件存储配置")
    public ReturnResult<Boolean> removeById(@ApiParam("配置ID") @PathVariable Integer id) {
        SystemServerSettingFileStorage storage = fileStorageService.getById(id);
        if (storage == null) {
            return ReturnResult.error("文件存储配置不存在");
        }
        Integer serverId = storage.getFileStorageServerId();
        boolean removed = fileStorageService.removeById(id);
        if (removed && serverId != null) {
            systemServerSettingService.applyConfigToRunningServer(serverId);
        }
        return ReturnResult.ok(removed);
    }
}

