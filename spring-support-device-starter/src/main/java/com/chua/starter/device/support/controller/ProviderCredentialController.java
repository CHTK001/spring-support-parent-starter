package com.chua.starter.device.support.controller;

import com.chua.starter.device.support.entity.DeviceProviderCredential;
import com.chua.starter.device.support.service.DeviceProviderCredentialService;
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
    @GetMapping("/list")
    public List<DeviceProviderCredential> list(@RequestParam(required = false) String vendor) {
        return credentialService.list(vendor);
    }
    @GetMapping("/{id}")
    public DeviceProviderCredential detail(@PathVariable String id) {
        return credentialService.get(id);
    }
    @PostMapping
    public DeviceProviderCredential create(@RequestBody DeviceProviderCredential credential) {
        return credentialService.create(credential);
    }
    @PutMapping("/{id}")
    public DeviceProviderCredential update(@PathVariable String id,
                                          @RequestBody DeviceProviderCredential credential) {
        return credentialService.update(id, credential);
    }
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        credentialService.delete(id);
    }
}