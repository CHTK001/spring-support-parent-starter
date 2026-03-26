package com.chua.starter.proxy.support.controller.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.proxy.support.entity.SystemServerSettingFileStorage;
import com.chua.starter.proxy.support.service.server.SystemServerSettingFileStorageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/proxy/server/setting/file-storage")
@Api(tags = "文件存储配置管理")
@Tag(name = "文件存储配置管理")
@RequiredArgsConstructor
public class SystemServerSettingFileStorageController {

    private final SystemServerSettingFileStorageService fileStorageService;

    @GetMapping("/{serverId}")
    @ApiOperation("获取服务器的文件存储配置列表")
    public ReturnResult<List<SystemServerSettingFileStorage>> listByServerId(@ApiParam("服务器ID") @PathVariable Integer serverId) {
        List<SystemServerSettingFileStorage> list = fileStorageService.listByServerId(serverId);
        return ReturnResult.ok(list == null ? Collections.emptyList() : list);
    }

    @PostMapping("/save")
    @ApiOperation("保存或更新单个文件存储配置(保存即热应用)")
    public ReturnResult<Boolean> save(@ApiParam("配置") @RequestBody SystemServerSettingFileStorage config) {
        return fileStorageService.saveOne(config);
    }
    @PutMapping("/update")
    @ApiOperation("保存或更新单个文件存储配置(保存即热应用)")
    public ReturnResult<Boolean> update(@ApiParam("配置") @RequestBody SystemServerSettingFileStorage config) {
        return fileStorageService.saveOne(config);
    }

    // 预留：一次性覆盖保存全部（前端未使用，如需可打开）
//    @PostMapping("/save-all")
//    @ApiOperation("覆盖保存服务器的文件存储配置列表(保存即热应用)")
//    public ReturnResult<Boolean> saveAll(@ApiParam("服务器ID") @RequestParam Integer serverId,
//                                      @ApiParam("配置列表") @RequestBody List<SystemServerSettingFileStorage> configs) {
//        return fileStorageService.replaceAllForServer(serverId, configs);
//    }

    @DeleteMapping("/{serverId}")
    @ApiOperation("删除服务器的所有文件存储配置(删除即热应用)")
    public ReturnResult<Boolean> remove(@ApiParam("服务器ID") @PathVariable Integer serverId) {
        return fileStorageService.replaceAllForServer(serverId, Collections.emptyList());
    }
}






