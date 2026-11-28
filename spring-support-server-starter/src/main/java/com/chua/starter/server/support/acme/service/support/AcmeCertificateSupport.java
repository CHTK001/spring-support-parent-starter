// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.server.support.acme.service.support;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.network.ssl.AcmeCertificateResult;
import com.chua.common.support.network.ssl.AcmeValidationInfo;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.server.support.acme.entity.MonitorAcmeCertificate;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.chua.common.support.utils.CollectionUtils;

/**
 * ACME 证书公共支持类，集中提供域名列表构建、错误信息截断与签发结果回填等公共逻辑，
 * 供 {@code AcmeCertificateServiceImpl} 与 {@code AcmeCertificateAsyncService} 复用，
 * 避免重复实现。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public final class AcmeCertificateSupport {

    /**
     * 挑战类型：HTTP-01
     */
    public static final String CHALLENGE_HTTP_01 = "HTTP-01";

    /**
     * 挑战类型：DNS-01
     */
    public static final String CHALLENGE_DNS_01 = "DNS-01";

    /**
     * 证书状态：签发失败
     */
    public static final String STATUS_FAILED = "failed";

    /**
     * 证书状态：待申请（初始状态，异步申请尚未产生任何进展）
     */
    public static final String STATUS_PENDING = "pending";

    /**
     * 部署模式：本机自动
     */
    public static final String DEPLOY_MODE_LOCAL = "local";

    /**
     * 部署模式：SSH 远程
     */
    public static final String DEPLOY_MODE_SSH = "ssh";

    /**
     * 部署模式：手动
     */
    public static final String DEPLOY_MODE_MANUAL = "manual";

    /**
     * 订单/证书状态：待验证
     */
    public static final String STATUS_VALIDATING = "validating";

    /**
     * ACME 网络请求默认超时（秒）：acme4j 默认 10s 在公网 CA 慢握手时申请/续签会超时，
     * 账户未配置超时时使用该默认值（60s）。
     */
    public static final int DEFAULT_TIMEOUT_SECONDS = 60;

    /**
     * 订单状态：已完成
     */
    public static final String ORDER_STATUS_VALID = "valid";

    /**
     * 订单状态：已失效（历史订单不可恢复，如授权过期/订单作废，需重新下单）
     */
    public static final String ORDER_STATUS_INVALID = "invalid";

    /**
     * 证书状态：已签发
     */
    private static final String STATUS_VALID = "valid";

    /**
     * 证书服务商类型：ACME 协议
     */
    public static final String PROVIDER_TYPE_ACME = "acme";

    /**
     * 证书服务商类型：阿里云 CAS
     */
    public static final String PROVIDER_TYPE_CAS = "cas";

    /**
     * 自动续签窗口下限天数，防止配置值过小导致高频无效续签
     */
    private static final int MIN_RENEW_DAYS = 7;

    /**
     * 错误信息最大长度，避免超出数据库字段长度
     */
    private static final int MAX_ERROR_LENGTH = 512;

    /**
     * PEM 内容识别标识
     */
    private static final String PEM_BEGIN_MARKER = "-----BEGIN";

    /**
     * acme4j 提供者的 SPI 扩展名
     */
    private static final String PROVIDER_NAME_ACME4J = "acme4j";

    /**
     * 时间格式化器，与证书表时间字段格式保持一致
     */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 私有构造方法，禁止实例化
     */
    private AcmeCertificateSupport() {
    }

    /**
     * 根据账户证书服务商类型解析 SPI 提供者扩展名。
     * <p>
     * acme 类型对应 acme4j 实现，CAS 类型对应阿里云 CAS 实现，其余值回退到 acme4j。
     *
     * @param providerType 服务商类型（acme/CAS），可空
     * @return SPI 扩展名（acme4j/CAS）
     */
    public static String providerName(String providerType) {
        if (PROVIDER_TYPE_CAS.equalsIgnoreCase(providerType)) {
            return PROVIDER_TYPE_CAS;
        }
        return PROVIDER_NAME_ACME4J;
    }

    /**
     * 判断已保存的账户私钥是否为空或历史占位值（非 PEM 文本）。
     *
     * <p>命中时说明提供者本次新生成了真实密钥，调用方应将返回的 PEM 持久化，
     * 否则每次连接都会生成新密钥并向 CA 注册重复账户。</p>
     *
     * @param savedPrivateKeyPem 已持久化的账户私钥 PEM
     * @return 需要重新持久化返回 true
     */
    public static boolean needPersistAccountKey(String savedPrivateKeyPem) {
        return savedPrivateKeyPem == null || !savedPrivateKeyPem.contains(PEM_BEGIN_MARKER);
    }

    /**
     * 构建域名列表（主域名 + SAN）。
     *
     * @param cert 证书实体
     * @return 域名列表
     */
    public static List<String> buildDomains(MonitorAcmeCertificate cert) {
        List<String> domains = new ArrayList<>();
        domains.add(cert.getAcmeCertPrimaryDomain());
        if (StringUtils.isNotEmpty(cert.getAcmeCertSan())) {
            for (String s : cert.getAcmeCertSan().split(",")) {
                if (StringUtils.isNotEmpty(s)) {
                    domains.add(s.trim());
                }
            }
        }
        return domains;
    }

    /**
     * 提取简短异常信息，限制长度避免超出数据库字段。
     *
     * @param e 异常
     * @return 简短信息
     */
    public static String shortMsg(Throwable e) {
        String msg = e.getMessage();
        return shortMsg(msg);
    }

    /**
     * 限制错误信息长度，避免超出数据库字段。
     *
     * @param error 原始错误信息
     * @return 限制长度后的信息
     */
    public static String shortMsg(String error) {
        if (error == null) {
            return "";
        }
        return error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
    }

    /**
     * 成功签发后回填证书信息到实体。
     *
     * @param target         目标证书实体
     * @param src            证书申请结果
     * @param renewDaysBefore 续签提前天数（配置值）
     */
    public static void fillCertificateSuccess(MonitorAcmeCertificate target, AcmeCertificateResult src, int renewDaysBefore) {
        target.setAcmeCertStatus(STATUS_VALID);
        if (src.getNotBefore() != null) {
            target.setAcmeCertNotBefore(src.getNotBefore().format(DATE));
        }
        if (src.getNotAfter() != null) {
            target.setAcmeCertNotAfter(src.getNotAfter().format(DATE));
 // 计划续签日期：到期前 N 天（下限 最小_RENEW_DAYS 天）
            LocalDateTime renewAt = src.getNotAfter().minusDays(Math.max(renewDaysBefore, MIN_RENEW_DAYS));
            target.setAcmeCertRenewAt(renewAt.format(DATE));
        }
        target.setAcmeCertChainPem(src.getCertificatePem());
        target.setAcmeCertKeyPem(src.getPrivateKeyPem());
        target.setAcmeCertLastError(null);
    }

    /**
     * 将 ACME 验证信息序列化为订单表存储的 JSON 结构。
     *
     * @param validationInfos 验证信息列表
     * @return JSON 字符串，入参为空时返回空列表 JSON
     */
    public static String toValidationJson(List<AcmeValidationInfo> validationInfos) {
        List<Map<String, Object>> validations = new ArrayList<>();
        if (validationInfos != null) {
            for (AcmeValidationInfo info : validationInfos) {
                Map<String, Object> item = new HashMap<>(10);
                item.put("orderUrl", info.orderUrl());
                item.put("domain", info.domain());
                item.put("type", info.challengeType());
                item.put("token", info.token());
                item.put("keyAuthorization", info.httpContent());
                item.put("httpPath", info.httpPath());
                item.put("httpContent", info.httpContent());
                item.put("dnsName", info.dnsName());
                item.put("dnsValue", info.dnsValue());
                validations.add(item);
            }
        }
        return Json.toJson(validations);
    }

    /**
     * 从验证信息列表中提取 ACME 订单地址。
     *
     * @param validationInfos 验证信息列表
     * @return 首个非空订单地址，不存在时返回 {@code null}
     */
    public static String resolveOrderUrl(List<AcmeValidationInfo> validationInfos) {
        if (CollectionUtils.isEmpty(validationInfos)) {
            return null;
        }
        for (AcmeValidationInfo info : validationInfos) {
            if (StringUtils.isNotEmpty(info.orderUrl())) {
                return info.orderUrl();
            }
        }
        return null;
    }

    /**
     * 将 HTTP-01 验证文件写入本机 webroot 目录。
     *
     * <p>写入路径为 {@code ${webrootPath}/.well-known/acme-challenge/${token}}，
     * 用于本机静态目录与内置验证端点并存的部署方式；webroot 未配置时跳过。</p>
     *
     * @param webrootPath     webroot 根目录
     * @param validationInfos 验证信息列表
     * @return 全部写入成功返回 true，未配置路径或存在写入失败返回 false
     */
    public static boolean writeHttpChallengeFiles(String webrootPath, List<AcmeValidationInfo> validationInfos) {
        if (StringUtils.isEmpty(webrootPath)) {
            return false;
        }
        if (CollectionUtils.isEmpty(validationInfos)) {
            return true;
        }
        boolean allSuccess = true;
        try {
            Path challengeDir = Paths.get(webrootPath, ".well-known", "acme-challenge");
            Files.createDirectories(challengeDir);
            for (AcmeValidationInfo info : validationInfos) {
                if (!CHALLENGE_HTTP_01.equalsIgnoreCase(info.challengeType())
                        || StringUtils.isEmpty(info.token())) {
                    continue;
                }
                try {
                    Path tokenFile = challengeDir.resolve(info.token());
                    Files.writeString(tokenFile, info.httpContent(), StandardCharsets.UTF_8);
                    log.info("[ACME] 已写入本机验证文件: {}", tokenFile);
                } catch (IOException e) {
                    allSuccess = false;
                    log.error("[ACME] 写入验证文件失败: token={}, error={}", info.token(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("[ACME] 创建验证目录失败: webroot={}, error={}", webrootPath, e.getMessage());
            return false;
        }
        return allSuccess;
    }}
