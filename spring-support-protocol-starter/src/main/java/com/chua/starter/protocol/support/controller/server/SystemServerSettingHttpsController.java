package com.chua.starter.protocol.support.controller.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.protocol.support.entity.SystemServerSetting;
import com.chua.starter.protocol.support.service.server.SystemServerSettingService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 协议管理-HTTPS配置控制器。
 */
@RestController
@RequestMapping("/system/server/setting/https")
@Api(tags = "协议管理-HTTPS配置")
@Tag(name = "协议管理-HTTPS配置")
@RequiredArgsConstructor
public class SystemServerSettingHttpsController {

    private final SystemServerSettingService systemServerSettingService;

    @GetMapping("/{serverId}")
    @ApiOperation("查询HTTPS配置")
    public ReturnResult<SystemServerSetting> get(@ApiParam("服务ID") @PathVariable Integer serverId) {
        return systemServerSettingService.getHttpsConfigByServerId(serverId);
    }

    @PostMapping("/save")
    @ApiOperation("保存HTTPS配置")
    public ReturnResult<Boolean> save(
            @ApiParam("服务ID") @RequestParam Integer serverId,
            @ApiParam("是否启用HTTPS") @RequestParam(required = false, defaultValue = "false") Boolean enabled,
            @ApiParam("证书类型: PEM/PFX/JKS") @RequestParam SystemServerSetting.HttpsCertType certType,
            @ApiParam("PEM证书文件") @RequestPart(required = false, value = "pemCert") MultipartFile pemCert,
            @ApiParam("PEM私钥文件") @RequestPart(required = false, value = "pemKey") MultipartFile pemKey,
            @ApiParam("PEM私钥密码") @RequestParam(required = false) String keyPassword,
            @ApiParam("Keystore文件(PFX/JKS)") @RequestPart(required = false, value = "keystore") MultipartFile keystore,
            @ApiParam("Keystore密码") @RequestParam(required = false) String keystorePassword
    ) throws Exception {
        byte[] pemCertBytes = pemCert != null && !pemCert.isEmpty() ? pemCert.getBytes() : null;
        byte[] pemKeyBytes = pemKey != null && !pemKey.isEmpty() ? pemKey.getBytes() : null;
        byte[] keystoreBytes = keystore != null && !keystore.isEmpty() ? keystore.getBytes() : null;
        return systemServerSettingService.saveHttpsConfig(
                serverId, enabled, certType, pemCertBytes, pemKeyBytes, keyPassword, keystoreBytes, keystorePassword
        );
    }

    @DeleteMapping("/{serverId}")
    @ApiOperation("删除HTTPS配置")
    public ReturnResult<Boolean> remove(@ApiParam("服务ID") @PathVariable Integer serverId) {
        return systemServerSettingService.saveHttpsConfig(serverId, false, null, null, null, null, null, null);
    }
}

