package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.support.entity.DeviceProviderCredential;
import com.chua.starter.device.support.service.DeviceProviderCredentialService;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 提供商接入凭据控制器
 * <p>管理厂商自定义的 AK/SK 等接入参数；同一厂商可登记多套凭据。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
// 说明：厂商 AK/SK 等接入参数的登记与维护（本模块未引入 swagger 注解）
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/device/provider-credential")
public class ProviderCredentialController {

    /** 提供商接入凭据服务 */
    private final DeviceProviderCredentialService credentialService;

    /** 设备服务（凭据连通性测试） */
    private final DeviceService deviceService;

    @GetMapping("/list")
    public ReturnResult<List<DeviceProviderCredential>> list(@RequestParam(required = false) String vendor) {
        return ReturnResult.ok(credentialService.list(vendor));
    }
    @GetMapping("/{id}")
    public ReturnResult<DeviceProviderCredential> detail(@PathVariable String id) {
        return ReturnResult.ok(credentialService.get(id));
    }
    @PostMapping
    public ReturnResult<DeviceProviderCredential> create(@RequestBody DeviceProviderCredential credential) {
        return ReturnResult.ok(credentialService.create(credential));
    }
    @PutMapping("/{id}")
    public ReturnResult<DeviceProviderCredential> update(@PathVariable String id,
                                                         @RequestBody DeviceProviderCredential credential) {
        return ReturnResult.ok(credentialService.update(id, credential));
    }
    @DeleteMapping("/{id}")
    public ReturnResult<Void> delete(@PathVariable String id) {
        credentialService.delete(id);
        return ReturnResult.ok();
    }

    /**
     * 测试凭据连通性
     *
     * <p>只验证平台登录/令牌，不需要具体设备；SDK 直连凭据没有 IP，始终返回 false。</p>
     *
     * @param id 凭据 ID
     * @return true 表示登录成功
     */
    @PostMapping("/{id}/test")
    public ReturnResult<Boolean> test(@PathVariable String id) {
        DeviceProviderCredential credential = credentialService.resolveById(id);
        if (credential == null) {
            throw new IllegalArgumentException("凭据不存在: " + id);
        }
        DeviceConnectInfo info = new DeviceConnectInfo(
                credential.getDeviceCredentialVendor(),
                "PLATFORM",
                null,
                0,
                null,
                null,
                credential.getDeviceCredentialPlatformType(),
                credential.getDeviceCredentialAppKey(),
                credential.getDeviceCredentialAppSecret(),
                null,
                null);
        return ReturnResult.ok(deviceService.testConnection(info));
    }
}