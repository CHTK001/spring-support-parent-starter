package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsServerConfig;
import com.chua.starter.server.support.service.ServerConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
* 服务器系统配置接口
* @author CH
* @since 4.0.0
 */
@Tag(name = "服务器资产-系统配置", description = "在离线/采集/Agent/远控等系统设置")
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/config")
@RequiredArgsConstructor
public class ServerConfigController {

    /** 配置服务 */
    private final ServerConfigService configService;

    /**
    * 查询全部配置
    * @return 列表的结果
    */
    @Operation(summary = "查询全部配置")
    @GetMapping
    public ReturnResult<List<MsServerConfig>> list() {
        return configService.listAll();
    }

    /**
    * 读取配置为扁平 映射（含默认值）
    * @return 映射的结果
    */
    @Operation(summary = "读取配置Map")
    @GetMapping("/map")
    public ReturnResult<Map<String, Object>> map() {
        return configService.getConfigMap();
    }

    /**
    * 保存配置
    * @param body 主体
    * @return 保存的结果
    */
    @Operation(summary = "保存配置")
    @PostMapping
    public ReturnResult<Void> save(@RequestBody Map<String, Object> body) {
        return configService.saveConfig(body);
    }
}
