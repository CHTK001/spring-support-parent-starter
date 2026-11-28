package com.chua.starter.server.support.acme.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.common.support.lang.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.server.support.acme.entity.MonitorAcmeAccount;
import com.chua.starter.server.support.acme.entity.MonitorAcmeCertificate;
import com.chua.starter.server.support.acme.entity.MonitorAcmeOrder;
import com.chua.starter.server.support.acme.model.AcmeApplyCertRequest;
import com.chua.starter.server.support.acme.model.AcmeCertificateDetailVo;
import com.chua.starter.server.support.acme.model.CertStats;
import com.chua.starter.server.support.acme.service.AcmeCertificateService;
import com.chua.starter.server.support.acme.service.MonitorAcmeAccountService;
import com.chua.starter.server.support.acme.service.MonitorAcmeCertificateService;
import com.chua.starter.server.support.acme.service.MonitorAcmeOrderService;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.PageResultUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * ACME 证书管理接口，提供证书申请、续签、吊销、下载、重新验证等完整的证书生命周期管理功能。
 *
 * @since 2025-10-17
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v1/cert")
@Api(tags = "ACME证书管理")
@Tag(name = "ACME证书管理")
@RequiredArgsConstructor
public class AcmeCertificateController {

    /**
     * 通配符域名前缀
     */
    private static final String WILDCARD_PREFIX = "*";

    /**
     * 通配符域名在文件名中的替换文案
     */
    private static final String WILDCARD_REPLACEMENT = "wildcard";

    /**
     * 文件类型：私钥
     */
    private static final String FILE_TYPE_KEY = "key";

    /**
     * 文件类型：证书链
     */
    private static final String FILE_TYPE_CHAIN = "chain";

    /**
     * 文件类型：证书（默认下载类型）
     */
    private static final String FILE_TYPE_CERT = "cert";

    /**
     * 证书下载响应的 内容-类型：二进制流
     */
    private static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";

    /**
     * 证书下载响应的 内容-Disposition 响应头名称
     */
    private static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";

    /**
     * ACME 证书服务接口，提供申请、续签、吊销、重新验证等能力
     */
    private final AcmeCertificateService acmeCertificateService;

    /**
     * ACME 账户服务接口
     */
    private final MonitorAcmeAccountService accountService;

    /**
     * ACME 证书服务接口
     */
    private final MonitorAcmeCertificateService certificateService;

    /**
     * ACME 订单服务接口
     */
    private final MonitorAcmeOrderService orderService;

    // ======================== 账户管理 ========================

    /**
     * 分页查询 ACME 账户列表 forcert
     *
     * @param page   分页参数
     * @param entity 查询条件
     * @return 账户分页列表
     */
    @GetMapping("/account/page")
    @Operation(summary = "分页查询ACME账户列表 ForCert")
    public ReturnPageResult<MonitorAcmeAccount> pageAccountForCert(Query<MonitorAcmeAccount> page, MonitorAcmeAccount entity) {
        return PageResultUtils.ok(accountService.page(page.createPage(),
                Wrappers.<MonitorAcmeAccount>lambdaQuery()
                        .like(StringUtils.isNotEmpty(entity.getAcmeAccountEmail()), MonitorAcmeAccount::getAcmeAccountEmail, entity.getAcmeAccountEmail())
                        .eq(StringUtils.isNotEmpty(entity.getAcmeAccountStatus()), MonitorAcmeAccount::getAcmeAccountStatus, entity.getAcmeAccountStatus())
                        .orderByDesc(MonitorAcmeAccount::getUpdateTime)));
    }

    /**
     * 查询所有 ACME 账户列表 forcert
     *
     * @return 账户列表
     */
    @GetMapping("/account/list")
    @Operation(summary = "查询所有ACME账户列表 ForCert")
    public ReturnResult<List<MonitorAcmeAccount>> listAccountForCert() {
        return ReturnResult.ok(accountService.list(
                Wrappers.<MonitorAcmeAccount>lambdaQuery()
                        .orderByDesc(MonitorAcmeAccount::getUpdateTime)));
    }

    /**
     * 保存/更新 ACME 账户 forcert
     *
     * @param account 账户
     * @return 账户ID
     */
    @PostMapping("/account/save")
    @ApiOperation("保存/更新ACME账户 ForCert")
    public ReturnResult<Integer> saveAccountForCert(@RequestBody MonitorAcmeAccount account) {
        return acmeCertificateService.saveAccountForCert(account);
    }

    /**
     * 获取 ACME 账户详情 forcert
     *
     * @param accountId 账户标识
     * @return 账户详情
     * @param @ApiParam("账户ID" @ApiParam("账户ID"
     */
    @GetMapping("/account/{accountId}")
    @Operation(summary = "获取ACME账户详情 ForCert")
    public ReturnResult<MonitorAcmeAccount> getAccountForCert(@ApiParam("账户ID") @PathVariable Integer accountId) {
        return ReturnResult.ok(accountService.getById(accountId));
    }

    /**
     * 删除 ACME 账户 forcert
     *
     * @param accountId 账户标识
     * @return 是否成功
     * @param @ApiParam("账户ID" @ApiParam("账户ID"
     */
    @DeleteMapping("/account/{accountId}")
    @Operation(summary = "删除ACME账户 ForCert")
    public ReturnResult<Boolean> deleteAccountForCert(@ApiParam("账户ID") @PathVariable Integer accountId) {
        log.info("[ACME] 删除账户: accountId={}", accountId);
        // 检查是否有关联的证书
        long certCount = certificateService.count(
                Wrappers.<MonitorAcmeCertificate>lambdaQuery()
                        .eq(MonitorAcmeCertificate::getAcmeAccountId, accountId));
        if (certCount > 0) {
            return ReturnResult.illegal("该账户下存在证书，无法删除");
        }
        return ReturnResult.ok(accountService.removeById(accountId));
    }

    // ======================== 证书管理 ========================

    /**
     * 分页查询证书列表 forcert
     *
     * @param page   分页参数
     * @param entity 查询条件
     * @return 证书分页列表
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询证书列表 ForCert")
    public ReturnPageResult<MonitorAcmeCertificate> pageCertForCert(Query<MonitorAcmeCertificate> page, MonitorAcmeCertificate entity) {
        return PageResultUtils.ok(certificateService.page(page.createPage(),
                Wrappers.<MonitorAcmeCertificate>lambdaQuery()
                        .eq(entity.getAcmeAccountId() != null, MonitorAcmeCertificate::getAcmeAccountId, entity.getAcmeAccountId())
                        .like(StringUtils.isNotEmpty(entity.getAcmeCertPrimaryDomain()),
                            MonitorAcmeCertificate::getAcmeCertPrimaryDomain, entity.getAcmeCertPrimaryDomain())
                        .eq(StringUtils.isNotEmpty(entity.getAcmeCertStatus()), MonitorAcmeCertificate::getAcmeCertStatus, entity.getAcmeCertStatus())
                        .eq(StringUtils.isNotEmpty(entity.getAcmeCertChallengeType()), MonitorAcmeCertificate::getAcmeCertChallengeType,
                            entity.getAcmeCertChallengeType())
                        .orderByDesc(MonitorAcmeCertificate::getUpdateTime)));
    }

    /**
     * 申请证书 forcert
     *
     * @param request 请求参数
     * @return 证书ID
     */
    @PostMapping("/apply")
    @ApiOperation("申请证书 ForCert")
    public ReturnResult<Integer> applyForCert(@RequestBody AcmeApplyCertRequest request) {
        return acmeCertificateService.applyCertificateForCert(request);
    }

    /**
     * 续签证书 forcert
     *
     * @param certId 证书标识
     * @return 是否成功
     * @param @ApiParam("证书ID" @ApiParam("证书ID"
     */
    @PostMapping("/{certId}/renew")
    @ApiOperation("续签证书 ForCert")
    public ReturnResult<Boolean> renewForCert(@ApiParam("证书ID") @PathVariable Integer certId) {
        return acmeCertificateService.renewCertificateForCert(certId);
    }

    /**
     * 吊销证书 forcert
     *
     * @param certId 证书标识
     * @return 是否成功
     * @param @ApiParam("证书ID" @ApiParam("证书ID"
     */
    @PostMapping("/{certId}/revoke")
    @ApiOperation("吊销证书 ForCert")
    public ReturnResult<Boolean> revokeForCert(@ApiParam("证书ID") @PathVariable Integer certId) {
        return acmeCertificateService.revokeCertificateForCert(certId);
    }

    /**
     * 获取证书详情 forcert
     *
     * @param certId 证书标识
     * @return 证书详情（含账户信息和订单信息）
     * @param @ApiParam("证书ID" @ApiParam("证书ID"
     */
    @GetMapping("/{certId}")
    @ApiOperation("获取证书详情 ForCert")
    public ReturnResult<AcmeCertificateDetailVo> getForCert(@ApiParam("证书ID") @PathVariable Integer certId) {
        return acmeCertificateService.getCertificateDetailForCert(certId);
    }

    /**
     * 删除证书 forcert
     *
     * @param certId 证书标识
     * @return 是否成功
     * @param @ApiParam("证书ID" @ApiParam("证书ID"
     */
    @DeleteMapping("/{certId}")
    @Operation(summary = "删除证书 ForCert")
    public ReturnResult<Boolean> deleteCertForCert(@ApiParam("证书ID") @PathVariable Integer certId) {
        log.info("[ACME] 删除证书: certId={}", certId);
        // 同时删除关联订单
        orderService.remove(Wrappers.<MonitorAcmeOrder>lambdaQuery()
                .eq(MonitorAcmeOrder::getAcmeCertId, certId));
        return ReturnResult.ok(certificateService.removeById(certId));
    }

    /**
     * 下载证书文件 forcert
     *
     * @param certId   证书标识
     * @param fileType 文件类型（cert/键/chain）
     * @param response HTTP响应
     */
    @GetMapping("/{certId}/download")
    @Operation(summary = "下载证书文件 ForCert")
    public void downloadCertForCert(
            @ApiParam("证书ID") @PathVariable Integer certId,
            @ApiParam("文件类型: cert/key/chain") @RequestParam(defaultValue = FILE_TYPE_CERT) String fileType,
            HttpServletResponse response) {
        MonitorAcmeCertificate cert = certificateService.getById(certId);
        if (cert == null) {
            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "证书不存在");
            } catch (IOException e) {
                log.error("[ACME] 下载证书响应错误: certId={}", certId, e);
            }
            return;
        }

        // 生成文件基础名：通配符域名替换为 wildcard，避免文件名非法字符
        String content;
        String fileName;
        String domain = cert.getAcmeCertPrimaryDomain().replace(WILDCARD_PREFIX, WILDCARD_REPLACEMENT);

        switch (fileType.toLowerCase()) {
            case FILE_TYPE_KEY -> {
                content = cert.getAcmeCertKeyPem();
                fileName = domain + ".key";
            }
            case FILE_TYPE_CHAIN -> {
                content = cert.getAcmeCertChainPem();
                fileName = domain + "_chain.crt";
            }
            default -> {
                content = cert.getAcmeCertChainPem();
                fileName = domain + ".crt";
            }
        }

        if (StringUtils.isEmpty(content)) {
            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "证书内容为空");
            } catch (IOException e) {
                log.error("[ACME] 下载证书响应错误: certId={}", certId, e);
            }
            return;
        }

        try {
            response.setContentType(CONTENT_TYPE_OCTET_STREAM);
            response.setHeader(HEADER_CONTENT_DISPOSITION, "attachment; filename=" + fileName);
            response.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
            response.getOutputStream().flush();
            log.info("[ACME] 下载证书成功: certId={}, fileType={}", certId, fileType);
        } catch (IOException e) {
            log.error("[ACME] 下载证书失败: certId={}", certId, e);
        }
    }

    /**
     * 重新验证证书 forcert（用户手动部署完成后调用）
     *
     * @param certId 证书标识
     * @return 是否提交成功
     * @param @ApiParam("证书ID" @ApiParam("证书ID"
     */
    @PostMapping("/{certId}/retry-validation")
    @Operation(summary = "重新验证证书 ForCert")
    public ReturnResult<Boolean> retryValidationForCert(@ApiParam("证书ID") @PathVariable Integer certId) {
        return acmeCertificateService.retryValidationForCert(certId);
    }

    /**
     * 手动触发续签检查 forcert
     *
     * @return 续签结果
     */
    @PostMapping("/renew-check")
    @Operation(summary = "手动触发续签检查 ForCert")
    public ReturnResult<String> triggerRenewCheckForCert() {
        log.info("[ACME] 手动触发续签检查");
        acmeCertificateService.checkAndRenewSoonExpired();
        return ReturnResult.ok("续签检查已触发");
    }

    // ======================== 订单管理 ========================

    /**
     * 查询证书关联的订单列表 forcert
     *
     * @param certId 证书标识
     * @return 订单列表
     * @param @ApiParam("证书ID" @ApiParam("证书ID"
     */
    @GetMapping("/{certId}/orders")
    @Operation(summary = "查询证书关联的订单列表 ForCert")
    public ReturnResult<List<MonitorAcmeOrder>> listOrdersForCert(@ApiParam("证书ID") @PathVariable Integer certId) {
        return ReturnResult.ok(orderService.list(
                Wrappers.<MonitorAcmeOrder>lambdaQuery()
                        .eq(MonitorAcmeOrder::getAcmeCertId, certId)
                        .orderByDesc(MonitorAcmeOrder::getUpdateTime)));
    }

    /**
     * 获取证书统计信息 forcert
     *
     * @return 统计信息
     */
    @GetMapping("/stats")
    @Operation(summary = "获取证书统计信息 ForCert")
    public ReturnResult<CertStats> statsForCert() {
        return acmeCertificateService.getCertStatsForCert();
    }
}
