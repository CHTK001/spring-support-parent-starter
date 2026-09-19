package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsServerIdentity;
import com.chua.starter.server.support.entity.MsServerKey;
import com.chua.starter.server.support.service.ServerIdentityService;
import com.chua.starter.server.support.service.ServerKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * 凭据接口：密钥库与登录身份
 * <p>
 * 两者均脱敏返回：私钥、口令永不出现在响应体中。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "服务器资产-凭据", description = "SSH 密钥库与登录身份")
@RestController
@RequestMapping(ServerConstants.API_PREFIX)
@RequiredArgsConstructor
public class ServerCredentialController {

    /** 密钥服务 */
    private final ServerKeyService keyService;

    /** 身份服务 */
    private final ServerIdentityService identityService;

    /**
     * 新增密钥
     *
     * @param key 密钥
     * @return 结果（脱敏）
     */
    @Operation(summary = "新增密钥")
    @PostMapping("/key")
    public ReturnResult<MsServerKey> createKey(@RequestBody MsServerKey key) {
        return keyService.create(key);
    }

    /**
     * 更新密钥
     *
     * @param id  主键
     * @param key 密钥
     * @return 结果（脱敏）
     */
    @Operation(summary = "更新密钥")
    @PutMapping("/key/{id}")
    public ReturnResult<MsServerKey> updateKey(@PathVariable Long id, @RequestBody MsServerKey key) {
        return keyService.update(id, key);
    }

    /**
     * 删除密钥
     *
     * @param id 主键
     * @return 结果
     */
    @Operation(summary = "删除密钥")
    @DeleteMapping("/key/{id}")
    public ReturnResult<Void> deleteKey(@PathVariable Long id) {
        return keyService.delete(id);
    }

    /**
     * 密钥列表
     *
     * @return 脱敏列表
     */
    @Operation(summary = "密钥列表")
    @GetMapping("/key")
    public ReturnResult<List<MsServerKey>> listKeys() {
        return keyService.list();
    }

    /**
     * 生成密钥对并保存到密钥库
     *
     * @param name     密钥名称
     * @param type     密钥类型（RSA / ED25519 / ECDSA，默认 RSA）
     * @param password 私钥口令（可空）
     * @param remark   备注（可空）
     * @return 生成结果（脱敏：不含私钥明文）
     */
    @Operation(summary = "生成密钥对")
    @PostMapping("/key/generate")
    public ReturnResult<MsServerKey> generateKey(
            @RequestParam String name,
            @RequestParam(defaultValue = "RSA") String type,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) String remark) {
        return keyService.generate(name, type, password, remark);
    }

    /**
     * 新增身份
     *
     * @param identity 身份
     * @return 结果（脱敏）
     */
    @Operation(summary = "新增登录身份")
    @PostMapping("/identity")
    public ReturnResult<MsServerIdentity> createIdentity(@RequestBody MsServerIdentity identity) {
        return identityService.create(identity);
    }

    /**
     * 更新身份
     *
     * @param id       主键
     * @param identity 身份
     * @return 结果（脱敏）
     */
    @Operation(summary = "更新登录身份")
    @PutMapping("/identity/{id}")
    public ReturnResult<MsServerIdentity> updateIdentity(@PathVariable Long id,
                                                         @RequestBody MsServerIdentity identity) {
        return identityService.update(id, identity);
    }

    /**
     * 删除身份
     *
     * @param id 主键
     * @return 结果
     */
    @Operation(summary = "删除登录身份")
    @DeleteMapping("/identity/{id}")
    public ReturnResult<Void> deleteIdentity(@PathVariable Long id) {
        return identityService.delete(id);
    }

    /**
     * 身份列表
     *
     * @return 脱敏列表
     */
    @Operation(summary = "身份列表")
    @GetMapping("/identity")
    public ReturnResult<List<MsServerIdentity>> listIdentities() {
        return identityService.list();
    }
}
