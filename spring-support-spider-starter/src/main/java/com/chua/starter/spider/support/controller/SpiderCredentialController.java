package com.chua.starter.spider.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.spider.support.domain.SpiderCredential;
import com.chua.starter.spider.support.repository.SpiderCredentialRepository;
import com.chua.starter.spider.support.security.CredentialEncryptionService;
import com.chua.starter.spider.support.security.CredentialSafetyChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 凭证池 REST 接口。
 *
 * <p>提供凭证的查询、新增、删除功能。凭证内容在存储前使用 AES 加密，
 * 查询时不返回加密数据字段，保证凭证安全。</p>
 *
 * @author CH
 */
@RestController
@RequestMapping("/v1/spider/credentials")
@RequiredArgsConstructor
public class SpiderCredentialController {

    private final SpiderCredentialRepository credentialRepository;
    private final CredentialEncryptionService encryptionService;
    private final CredentialSafetyChecker safetyChecker = new CredentialSafetyChecker();

    /**
     * GET /v1/spider/credentials — 查询凭证列表（不返回加密数据）
     */
    @GetMapping
    public ReturnResult<List<Map<String, Object>>> list() {
        List<SpiderCredential> credentials = credentialRepository.findAll();
        List<Map<String, Object>> result = credentials.stream()
                .map(c -> Map.<String, Object>of(
                        "id", c.getId(),
                        "credentialName", c.getCredentialName() != null ? c.getCredentialName() : "",
                        "credentialType", c.getCredentialType() != null ? c.getCredentialType() : "",
                        "domain", c.getDomain() != null ? c.getDomain() : "",
                        "description", c.getDescription() != null ? c.getDescription() : ""
                ))
                .collect(Collectors.toList());
        return ReturnResult.ok(result);
    }

    /**
     * POST /v1/spider/credentials — 新增凭证（后端 AES 加密存储）
     */
    @PostMapping
    public ReturnResult<?> create(@RequestBody CreateCredentialRequest request) {
        if (request.credentialName() == null || request.credentialName().isBlank()) {
            return ReturnResult.illegal("凭证名称不能为空");
        }
        if (request.plainData() == null || request.plainData().isBlank()) {
            return ReturnResult.illegal("凭证内容不能为空");
        }

        // 安全检查：检测明文密码
        List<String> warnings = safetyChecker.checkMap(
                Map.of("plainData", request.plainData()),
                "credential"
        );
        if (!warnings.isEmpty()) {
            return ReturnResult.illegal("安全警告：" + warnings.get(0));
        }

        // 加密凭证内容
        String encryptedData = encryptionService.encrypt(request.plainData());

        SpiderCredential credential = SpiderCredential.builder()
                .credentialName(request.credentialName())
                .credentialType(request.credentialType())
                .domain(request.domain())
                .description(request.description())
                .encryptedData(encryptedData)
                .build();

        credentialRepository.save(credential);
        return ReturnResult.ok(Map.of("id", credential.getId()));
    }

    /**
     * DELETE /v1/spider/credentials/{id} — 删除凭证
     */
    @DeleteMapping("/{id}")
    public ReturnResult<?> delete(@PathVariable Long id) {
        if (credentialRepository.findById(id).isEmpty()) {
            return ReturnResult.illegal("凭证 [" + id + "] 不存在");
        }
        credentialRepository.deleteById(id);
        return ReturnResult.ok();
    }

    /**
     * 新增凭证请求体。
     *
     * @param credentialName 凭证显示名称
     * @param credentialType 凭证类型（BASIC/COOKIE/TOKEN/SMS_CODE）
     * @param domain         适用域名
     * @param description    备注说明
     * @param plainData      明文凭证内容（后端加密后存储）
     */
    record CreateCredentialRequest(
            String credentialName,
            String credentialType,
            String domain,
            String description,
            String plainData
    ) {}
}
