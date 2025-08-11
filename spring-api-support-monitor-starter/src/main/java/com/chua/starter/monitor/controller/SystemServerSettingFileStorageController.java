package com.chua.starter.monitor.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.entity.SystemServerSettingFileStorage;
import com.chua.starter.monitor.service.SystemServerSettingFileStorageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system/server/setting/file-storage")
@Api(tags = "文件存储配置管理")
@Tag(name = "文件存储配置管理")
@RequiredArgsConstructor
public class SystemServerSettingFileStorageController {

    private final SystemServerSettingFileStorageService fileStorageService;

    @GetMapping("/{serverId}")
    @ApiOperation("获取服务器的文件存储配置")
    public ReturnResult<List<SystemServerSettingFileStorage>> list(@ApiParam("服务器ID") @PathVariable Integer serverId) {
        return ReturnResult.ok(fileStorageService.listByServerId(serverId));
    }

    @PostMapping("/save")
    @ApiOperation("保存或更新文件存储配置(保存即热应用)")
    public ReturnResult<SystemServerSettingFileStorage> save(@RequestBody SystemServerSettingFileStorage config) {
        return fileStorageService.saveOrUpdate(config);
    }

    @DeleteMapping("/{serverId}")
    @ApiOperation("删除服务器的文件存储配置(删除即热应用)")
    public ReturnResult<Boolean> delete(@ApiParam("服务器ID") @PathVariable Integer serverId) {
        return fileStorageService.deleteByServerId(serverId);
    }
}

