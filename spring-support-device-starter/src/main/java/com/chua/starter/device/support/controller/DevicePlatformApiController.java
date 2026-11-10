package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.provider.sdk.DevicePlatformSdk;
import com.chua.starter.device.provider.sdk.DevicePlatformSdks;
import com.chua.starter.device.provider.sdk.PlatformApiResult;
import com.chua.starter.device.provider.sdk.PlatformApiSpec;
import com.chua.starter.device.support.entity.DeviceProviderCredential;
import com.chua.starter.device.support.service.DeviceProviderCredentialService;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 平台能力开放接口（按凭据直接调厂商官方 API）。
 *
 * <p><b>为什么需要它</b>：设备模块原先每个厂商只接了「连通性 + 设备目录 + 少量操作」，
 * 平台其余上百个官方接口都用不上。这里把 {@link DevicePlatformSdk#apis()} 的接口目录
 * 直接开放给前端/业务：查目录 → 按 {@code code} 调用，无需为每个接口新增 Java 方法。</p>
 *
 * <p>凭据由路径参数给出，AK/SK 与服务端补全的产品/项目参数都由服务端取，
 * 前端不接触密钥。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/api/device/platform")
@RequiredArgsConstructor
public class DevicePlatformApiController {

    /** 凭据服务 */
    private final DeviceProviderCredentialService credentialService;

    /**
     * 查询某套凭据对应平台的接口目录（含版本与认证方式）。
     *
     * @param credentialId 凭据 ID
     * @param domain       能力域过滤：device / monitor / org / person / access / alarm / video，可空
     * @return 接口目录
     */
    @GetMapping("/{credentialId}/apis")
    public ReturnResult<Map<String, Object>> apis(@PathVariable String credentialId,
                                                  @RequestParam(required = false) String domain) {
        DevicePlatformSdk sdk = resolveSdk(credentialId);
        List<PlatformApiSpec> all = sdk.apis();
        List<PlatformApiSpec> filtered = StringUtils.hasText(domain)
                ? all.stream().filter(item -> domain.equalsIgnoreCase(item.domain())).toList()
                : all;
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("platform", sdk.platform());
        view.put("officialSdk", sdk.officialSdk());
        view.put("apiVersion", sdk.apiVersion().version());
        view.put("verified", sdk.apiVersion().verified());
        view.put("docUrl", sdk.apiVersion().docUrl());
        view.put("total", all.size());
        view.put("apis", filtered);
        // 按能力域分组，前端可直接渲染分组入口
        view.put("domains", all.stream()
                .collect(Collectors.groupingBy(PlatformApiSpec::domain,
                        LinkedHashMap::new, Collectors.counting())));
        return ReturnResult.ok(view);
    }

    /**
     * 按接口编码调用平台官方接口。
     *
     * @param credentialId 凭据 ID
     * @param code         接口编码（见目录）
     * @param params       请求参数；POST 走 JSON body
     * @return 调用结果
     */
    @PostMapping("/{credentialId}/invoke/{code}")
    public ReturnResult<Map<String, Object>> invoke(@PathVariable String credentialId,
                                                    @PathVariable String code,
                                                    @RequestBody(required = false) Map<String, Object> params) {
        DevicePlatformSdk sdk = resolveSdk(credentialId);
        PlatformApiResult result = sdk.invoke(code, params == null ? Map.of() : params);
        Map<String, Object> view = new LinkedHashMap<>(result.toMap());
        // 明确区分「失败」与「接口通但没有数据」，避免把空列表当成凭据/权限问题
        view.put("empty", result.empty());
        return ReturnResult.ok(view);
    }

    /**
     * 批量探测一批接口是否可调用（只读接口，避免误触发写操作）。
     *
     * @param credentialId 凭据 ID
     * @param domain       能力域，可空表示全部只读接口
     * @return 每条接口的可用性
     */
    @GetMapping("/{credentialId}/probe")
    public ReturnResult<List<Map<String, Object>>> probe(@PathVariable String credentialId,
                                                         @RequestParam(required = false) String domain) {
        DevicePlatformSdk sdk = resolveSdk(credentialId);
        List<PlatformApiSpec> targets = sdk.apis().stream()
                .filter(item -> !StringUtils.hasText(domain) || domain.equalsIgnoreCase(item.domain()))
                .filter(DevicePlatformApiController::readOnly)
                .toList();
        List<Map<String, Object>> results = new ArrayList<>();
        for (PlatformApiSpec spec : targets) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("pageNo", 1);
            body.put("pageSize", 1);
            PlatformApiResult result = sdk.invoke(spec.code(), body);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", spec.code());
            row.put("name", spec.name());
            row.put("domain", spec.domain());
            row.put("version", spec.version());
            row.put("ok", result.ok());
            row.put("empty", result.empty());
            row.put("message", result.message());
            row.put("elapsedMs", result.elapsedMs());
            results.add(row);
        }
        return ReturnResult.ok(results);
    }

    /**
     * 是否只读接口（批量探测只跑这些，避免误触发删除/升级等写操作）。
     *
     * @param spec 接口声明
     * @return true 表示只读
     */
    private static boolean readOnly(PlatformApiSpec spec) {
        String code = spec.code();
        return code.contains(".page") || code.contains(".list") || code.contains(".search")
                || code.contains(".info") || code.contains(".status") || code.contains(".get")
                || code.contains(".query") || code.contains(".online") || code.contains(".count")
                || code.contains(".stat");
    }

    /**
     * 按凭据解析平台 SDK 实现。
     *
     * @param credentialId 凭据 ID
     * @return SDK 实现
     */
    private DevicePlatformSdk resolveSdk(String credentialId) {
        DeviceProviderCredential credential = credentialService.resolveById(credentialId);
        if (credential == null) {
            throw new IllegalArgumentException("凭据不存在: " + credentialId);
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        if (StringUtils.hasText(credential.getDeviceCredentialBaseUrl())) {
            extra.put("baseUrl", credential.getDeviceCredentialBaseUrl());
        }
        if (StringUtils.hasText(credential.getDeviceCredentialProductCode())) {
            extra.put("productCode", credential.getDeviceCredentialProductCode());
        }
        if (StringUtils.hasText(credential.getDeviceCredentialProjectId())) {
            extra.put("projectId", credential.getDeviceCredentialProjectId());
        }
        extra.put("credentialId", credential.getDeviceCredentialId());
        DeviceConnectInfo info = new DeviceConnectInfo(
                credential.getDeviceCredentialVendor(), "PLATFORM", null, 0, null, null,
                credential.getDeviceCredentialPlatformType(),
                credential.getDeviceCredentialAppKey(),
                credential.getDeviceCredentialAppSecret(),
                null, extra);
        DevicePlatformSdk sdk = DevicePlatformSdks.of(info);
        if (sdk == null) {
            throw new IllegalStateException("平台 " + credential.getDeviceCredentialVendor()
                    + "/" + credential.getDeviceCredentialPlatformType() + " 暂无 SDK 门面实现");
        }
        return sdk;
    }
}
