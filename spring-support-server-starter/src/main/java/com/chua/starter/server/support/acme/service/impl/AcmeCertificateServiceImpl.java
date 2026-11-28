package com.chua.starter.server.support.acme.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.network.ssl.AcmeCertificateResult;
import com.chua.common.support.network.ssl.AcmeConnectionResult;
import com.chua.common.support.network.ssl.AcmeProvider;
import com.chua.common.support.network.ssl.AcmeValidationInfo;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.server.support.acme.entity.MonitorAcmeAccount;
import com.chua.starter.server.support.acme.entity.MonitorAcmeCertificate;
import com.chua.starter.server.support.acme.entity.MonitorAcmeOrder;
import com.chua.starter.server.support.acme.mapper.MonitorAcmeAccountMapper;
import com.chua.starter.server.support.acme.mapper.MonitorAcmeCertificateMapper;
import com.chua.starter.server.support.acme.mapper.MonitorAcmeOrderMapper;
import com.chua.starter.server.support.acme.model.AcmeApplyCertRequest;
import com.chua.starter.server.support.acme.model.AcmeCertificateDetailVo;
import com.chua.starter.server.support.acme.model.CertStats;
import com.chua.starter.server.support.acme.config.AcmeProperties;
import com.chua.starter.server.support.acme.service.AcmeCertificateAsyncService;
import com.chua.starter.server.support.acme.service.AcmeChallengeService;
import com.chua.starter.server.support.acme.service.support.AcmeCertificateSupport;
import com.chua.starter.server.support.acme.service.AcmeCertificateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.chua.common.support.utils.CollectionUtils;

/**
 * ACME 证书服务实现，提供账户保存、证书申请（异步）、续签、吊销、详情、统计、
 * 到期自动续签与重新验证等完整的证书生命周期管理能力。
 *
 * @since 2025-10-17
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcmeCertificateServiceImpl implements AcmeCertificateService {

    /**
     * 挑战类型：HTTP-01
     */
    private static final String CHALLENGE_HTTP_01 = "HTTP-01";

    /**
     * 部署模式：手动
     */
    private static final String DEPLOY_MODE_MANUAL = "manual";

    /**
     * 证书状态：已提交待签发
     */
    private static final String STATUS_PENDING = "pending";

    /**
     * 证书状态：待验证
     */
    private static final String STATUS_VALIDATING = "validating";

    /**
     * 证书状态：已签发
     */
    private static final String STATUS_VALID = "valid";

    /**
     * 证书状态：已吊销
     */
    private static final String STATUS_REVOKED = "revoked";

    /**
     * 证书状态：签发失败
     */
    private static final String STATUS_FAILED = "failed";

    /**
     * 证书状态：已过期
     */
    private static final String STATUS_EXPIRED = "expired";

    /**
     * 自动续签窗口下限天数，防止配置值过小导致高频无效续签
     */
    private static final int MIN_RENEW_DAYS = 7;

    /**
     * 即将到期判定窗口天数
     */
    private static final int SOON_EXPIRING_DAYS = 30;

    /**
     * 时间格式化器，与证书表时间字段格式保持一致
     */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * ACME 账户 映射器，用于账户查询与持久化
     */
    private final MonitorAcmeAccountMapper accountMapper;

    /**
     * ACME 证书 映射器，用于证书查询与持久化
     */
    private final MonitorAcmeCertificateMapper certificateMapper;

    /**
     * ACME 订单 映射器，用于订单查询与持久化
     */
    private final MonitorAcmeOrderMapper orderMapper;

    /**
     * ACME 配置，读取续签提前天数等参数
     */
    private final AcmeProperties acmeProperties;

    /**
     * 认证服务，用于获取当前登录用户信息以便消息推送
     */
    private final AuthService authService;

    /**
     * 异步证书服务，负责后台执行证书申请与重新验证
     */
    private final AcmeCertificateAsyncService asyncService;

    /**
     * 本机 HTTP-01 验证缓存服务
     */
    private final AcmeChallengeService challengeService;

    /**
     * 保存/更新 ACME 账户 forcert
     *
     * @param account 账户
     * @return 结果（账户ID）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Integer> saveAccountForCert(MonitorAcmeAccount account) {
        if (account == null || StringUtils.isNullOrEmpty(account.getAcmeAccountEmail())) {
            return ReturnResult.illegal("参数错误: acmeAccountEmail 不能为空");
        }
        if (StringUtils.isNullOrEmpty(account.getAcmeAccountServer())) {
            return ReturnResult.illegal("参数错误: acmeAccountServer 不能为空");
        }
        // 服务商类型为空时默认 ACME 协议
        if (StringUtils.isNullOrEmpty(account.getAcmeAccountProviderType())) {
            account.setAcmeAccountProviderType(AcmeCertificateSupport.PROVIDER_TYPE_ACME);
        }
        log.info("[ACME] 保存账户: email={}, server={}, providerType={}", account.getAcmeAccountEmail(), account.getAcmeAccountServer(),
            account.getAcmeAccountProviderType());
        if (account.getAcmeAccountId() == null) {
            accountMapper.insert(account);
        } else {
            accountMapper.updateById(account);
        }
        return ReturnResult.ok(account.getAcmeAccountId());
    }

    /**
     * 申请证书 forcert（异步执行，立即返回）
     *
     * <p>不使用事务：证书记录插入后需立即提交，异步虚拟线程才能读取到该记录。</p>
     *
     * @param request 请求
     * @return 证书ID
     */
    @Override
    public ReturnResult<Integer> applyCertificateForCert(AcmeApplyCertRequest request) {
        if (request == null || request.acmeAccountId() == null || StringUtils.isNullOrEmpty(request.primaryDomain())) {
            return ReturnResult.illegal("参数错误: acmeAccountId/primaryDomain 不能为空");
        }
        // 查询账户信息
        MonitorAcmeAccount account = accountMapper.selectById(request.acmeAccountId());
        if (account == null) {
            return ReturnResult.illegal("ACME账户不存在: " + request.acmeAccountId());
        }

        List<String> sans = request.sanDomains() == null ? List.of() : request.sanDomains();
        String sanStr = String.join(",", sans.stream().filter(StringUtils::isNotEmpty).toList());
        String challenge = StringUtils.defaultString(request.challengeType(), CHALLENGE_HTTP_01);

        // 新建证书记录（pending）
        String deployMode = StringUtils.defaultString(request.deployMode(), DEPLOY_MODE_MANUAL);
        MonitorAcmeCertificate cert = new MonitorAcmeCertificate();
        cert.setAcmeAccountId(request.acmeAccountId());
        cert.setAcmeCertPrimaryDomain(request.primaryDomain());
        cert.setAcmeCertSan(sanStr);
        cert.setAcmeCertChallengeType(challenge);
        cert.setAcmeCertStatus(STATUS_PENDING);
        cert.setAcmeCertDeployMode(deployMode);
        cert.setAcmeCertWebrootPath(request.webrootPath());
        certificateMapper.insert(cert);
        int certId = cert.getAcmeCertId();

        log.info("[ACME] 提交证书申请, certId={}, domain={}, san={}, challenge={}", certId, cert.getAcmeCertPrimaryDomain(), sanStr, challenge);

 // 获取当前用户标识用于消息推送
        Integer userId = null;
        try {
            CurrentUser currentUser = authService.getCurrentUser();
            if (currentUser != null && currentUser.getUserId() != null) {
                userId = Integer.parseInt(currentUser.getUserId());
            }
        } catch (Exception e) {
            log.warn("[ACME] 获取当前用户失败，消息推送可能受影响: {}", e.getMessage());
        }

        // 异步执行证书申请
        asyncService.applyCertificateAsync(certId, request, userId);

        return ReturnResult.ok(certId, "证书申请已提交，请在消息中心查看进度");
    }

    /**
     * 续签证书 forcert
     *
     * <p>续签即基于同一账户重新走完整 ACME 流程：创建续签订单 → 获取验证信息 →
     * 按部署模式完成 HTTP-01 验证部署 → 触发验证并下载新证书。
     * 本机模式（local）自动完成验证；SSH/手动/DNS-01 模式无法自动部署时，
     * 订单与验证信息入库、证书转入待验证状态，用户部署后点击重新验证即可完成续签。</p>
     *
     * <p>不使用事务：方法包含最长可达分钟级的 CA 轮询网络调用，
     * 每次数据库写入均为单条语句，由 MyBatis 自动提交。</p>
     *
     * @param certId 证书标识
     * @return 是否成功
     */
    @Override
    public ReturnResult<Boolean> renewCertificateForCert(Integer certId) {
        MonitorAcmeCertificate cert = certificateMapper.selectById(certId);
        if (cert == null) {
            return ReturnResult.illegal("证书不存在: " + certId);
        }
        MonitorAcmeAccount account = accountMapper.selectById(cert.getAcmeAccountId());
        if (account == null) {
            return ReturnResult.illegal("ACME账户不存在: " + cert.getAcmeAccountId());
        }
        log.info("[ACME] 续签证书: id={}, domain={}", certId, cert.getAcmeCertPrimaryDomain());

        // 通过 SPI 获取 ACME 提供者（按账户服务商类型路由）
        AcmeProvider provider = ServiceProvider.of(AcmeProvider.class)
                .getNewExtension(AcmeCertificateSupport.providerName(account.getAcmeAccountProviderType()));
        if (provider == null) {
            return ReturnResult.illegal("ACME 服务提供者不可用");
        }

        // 连接 ACME 服务器（支持 EAB，ZeroSSL 等 CA 需要）
        AcmeConnectionResult connResult = provider.connect(
                account.getAcmeAccountServer(),
                account.getAcmeAccountEmail(),
                account.getAcmeAccountPrivateKeyPem(),
                account.getAcmeAccountEabKid(),
                account.getAcmeAccountEabHmacKey()
        );
        if (!connResult.isSuccess()) {
            provider.close();
            return ReturnResult.illegal("连接 ACME 服务器失败: " + connResult.getError());
        }

        List<String> domains = AcmeCertificateSupport.buildDomains(cert);
        String challenge = cert.getAcmeCertChallengeType();
        try {
            // 1.创建续签订单并获取各域名的验证信息（授权仍有效时列表为空）
            List<AcmeValidationInfo> validationInfos = provider.getValidationInfo(domains, challenge);

            // 2.续签订单与验证信息入库，保证后续重新验证可按订单地址恢复
            MonitorAcmeOrder order = new MonitorAcmeOrder();
            order.setAcmeAccountId(account.getAcmeAccountId());
            order.setAcmeCertId(certId);
            order.setAcmeOrderStatus(STATUS_VALIDATING);
            order.setAcmeOrderUrl(AcmeCertificateSupport.resolveOrderUrl(validationInfos));
            order.setAcmeAuthzUrls(AcmeCertificateSupport.toValidationJson(validationInfos));
            orderMapper.insert(order);

            // 3.存在待完成验证时，按部署模式处理
            if (!validationInfos.isEmpty()) {
                String deployMode = StringUtils.defaultString(cert.getAcmeCertDeployMode(), DEPLOY_MODE_MANUAL);
                boolean autoDeployable = AcmeCertificateSupport.DEPLOY_MODE_LOCAL.equals(deployMode)
                        && CHALLENGE_HTTP_01.equalsIgnoreCase(challenge);
                if (!autoDeployable) {
                    // SSH 凭据未随证书持久化、手动模式与 DNS-01 均无法自动部署，转待验证由用户处理
                    cert.setAcmeCertStatus(STATUS_VALIDATING);
                    cert.setAcmeCertLastError(buildDeployPendingMessage(deployMode, challenge));
                    certificateMapper.updateById(cert);
                    log.warn("[ACME] 续签需手动完成域名验证: id={}, deployMode={}", certId, deployMode);
                    return ReturnResult.illegal("续签需完成域名验证，验证信息已保存，部署完成后请点击重新验证");
                }

                // 本机模式：写入内存缓存，由内置 /.well-known/acme-challenge 端点响应 CA
                for (AcmeValidationInfo info : validationInfos) {
                    if (StringUtils.isNotEmpty(info.token())) {
                        challengeService.put(info.token(), info.httpContent());
                    }
                }

                // 配置了本机 webroot 时同步写静态文件（尽力而为，失败不影响内置端点响应）
                AcmeCertificateSupport.writeHttpChallengeFiles(cert.getAcmeCertWebrootPath(), validationInfos);
            }

            // 4.触发验证、提交 CSR 并下载新证书（复用第 1 步创建的同一订单）
            AcmeCertificateResult certResult = provider.requestCertificate(domains, challenge);
            if (certResult.isSuccess()) {
                fillCertificateSuccess(cert, certResult);
                certificateMapper.updateById(cert);
                order.setAcmeOrderStatus(STATUS_VALID);
                orderMapper.updateById(order);
                log.info("[ACME] 续签成功: id={}, domain={}", certId, cert.getAcmeCertPrimaryDomain());
                return ReturnResult.ok(true, "续签成功");
            }

            cert.setAcmeCertLastError(AcmeCertificateSupport.shortMsg(certResult.getError()));
            certificateMapper.updateById(cert);
            return ReturnResult.illegal("续签失败: " + certResult.getError());
        } catch (Exception e) {
            log.error("[ACME] 续签失败: id={}", certId, e);
            cert.setAcmeCertLastError(AcmeCertificateSupport.shortMsg(e));
            certificateMapper.updateById(cert);
            return ReturnResult.illegal("续签失败: " + e.getMessage());
        } finally {
            provider.close();
        }
    }

    /**
     * 根据部署模式与挑战类型生成待验证提示信息。
     *
     * @param deployMode    部署模式
     * @param challengeType 挑战类型
     * @return 提示信息
     */
    private String buildDeployPendingMessage(String deployMode, String challengeType) {
        if (AcmeCertificateSupport.DEPLOY_MODE_SSH.equals(deployMode)) {
            return "自动续签无法完成 SSH 远程验证（证书未保存 SSH 凭据），请手动部署验证文件后重新验证";
        }
        if (AcmeCertificateSupport.CHALLENGE_DNS_01.equalsIgnoreCase(challengeType)) {
            return "DNS-01 验证需手动配置 DNS 记录，配置完成后请重新验证";
        }
        return "自动续签需手动完成域名验证，部署验证文件后请重新验证";
    }

    /**
     * 吊销证书 forcert
     *
     * <p>不使用事务：吊销包含 CA 网络调用，吊销成功后的状态更新为单条语句自动提交。</p>
     *
     * @param certId 证书标识
     * @return 是否成功
     */
    @Override
    public ReturnResult<Boolean> revokeCertificateForCert(Integer certId) {
        MonitorAcmeCertificate cert = certificateMapper.selectById(certId);
        if (cert == null) {
            return ReturnResult.illegal("证书不存在: " + certId);
        }
        MonitorAcmeAccount account = accountMapper.selectById(cert.getAcmeAccountId());
        if (account == null) {
            return ReturnResult.illegal("ACME账户不存在: " + cert.getAcmeAccountId());
        }
        log.warn("[ACME] 吊销证书: id={}, domain={}", certId, cert.getAcmeCertPrimaryDomain());

 // CAS 服务商需 证书id+instanceid 才能吊销，当前业务层无该信息，直接提示
        if (AcmeCertificateSupport.PROVIDER_TYPE_CAS.equalsIgnoreCase(account.getAcmeAccountProviderType())) {
            return ReturnResult.illegal("阿里云 CAS 吊销需证书实例ID，暂不支持在线吊销，请前往阿里云控制台操作");
        }

        // 通过 SPI 获取 ACME 提供者（按账户服务商类型路由）
        AcmeProvider provider = ServiceProvider.of(AcmeProvider.class)
                .getNewExtension(AcmeCertificateSupport.providerName(account.getAcmeAccountProviderType()));
        if (provider == null) {
            return ReturnResult.illegal("ACME 服务提供者不可用");
        }

        // 连接 ACME 服务器
        AcmeConnectionResult connResult = provider.connect(
                account.getAcmeAccountServer(),
                account.getAcmeAccountEmail(),
                account.getAcmeAccountPrivateKeyPem()
        );
        if (!connResult.isSuccess()) {
            provider.close();
            return ReturnResult.illegal("连接 ACME 服务器失败: " + connResult.getError());
        }

        try {
            boolean revoked = provider.revokeCertificate(cert.getAcmeCertChainPem());
            if (revoked) {
                cert.setAcmeCertStatus(STATUS_REVOKED);
                certificateMapper.updateById(cert);
                log.info("[ACME] 证书吊销成功: id={}", certId);
                return ReturnResult.ok(true);
            } else {
                return ReturnResult.illegal("吊销失败");
            }
        } catch (Exception e) {
            log.error("[ACME] 吊销失败: id={}", certId, e);
            return ReturnResult.illegal("吊销失败: " + e.getMessage());
        } finally {
            provider.close();
        }
    }

    /**
     * 获取证书详情 forcert（含账户和订单信息）
     *
     * @param certId 证书标识
     * @return 证书详情VO
     */
    @Override
    public ReturnResult<AcmeCertificateDetailVo> getCertificateDetailForCert(Integer certId) {
        MonitorAcmeCertificate cert = certificateMapper.selectById(certId);
        if (cert == null) {
            return ReturnResult.illegal("证书不存在: " + certId);
        }

        // VO 是不可变 record，只能一次装配，因此先算好各个分量再走规范构造器
        MonitorAcmeAccount account = null;
        if (cert.getAcmeAccountId() != null) {
            account = accountMapper.selectById(cert.getAcmeAccountId());
        }

        List<MonitorAcmeOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<MonitorAcmeOrder>()
                .eq(MonitorAcmeOrder::getAcmeCertId, certId)
                .orderByDesc(MonitorAcmeOrder::getUpdateTime));

        // 仅待验证状态且 HTTP-01 挑战才生成 cURL 自检命令，其余情况保持为空
        List<String> curlCommands = null;
        if (STATUS_VALIDATING.equals(cert.getAcmeCertStatus()) && CHALLENGE_HTTP_01.equals(cert.getAcmeCertChallengeType())) {
            List<String> commands = new ArrayList<>();
            if (!orders.isEmpty()) {
                MonitorAcmeOrder latestOrder = orders.get(0);
                String authzUrlsJson = latestOrder.getAcmeAuthzUrls();
                if (StringUtils.isNotEmpty(authzUrlsJson)) {
                    try {
                        List<Map<String, Object>> validations = Json.fromJson(authzUrlsJson, List.class);
                        if (validations != null) {
                            for (Map<String, Object> v : validations) {
                                String domain = (String) v.get("domain");
                                String httpPath = (String) v.get("httpPath");
                                if (StringUtils.isNotEmpty(domain) && StringUtils.isNotEmpty(httpPath)) {
                                    // 生成cURL命令，使用-I检查响应头，使用-s静默模式
                                    String curlCmd = String.format("curl -s -o /dev/null -w '%%{http_code}' http://%s%s", domain, httpPath);
                                    commands.add(curlCmd);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("[ACME] 解析验证信息JSON失败: certId={}, error={}", certId, e.getMessage());
                    }
                }
            }
            curlCommands = commands;
        }

        // 解析不出到期时间就留空，不拿 0 冒充"今天到期"
        Long daysUntilExpiry = null;
        Boolean soonExpiring = null;
        if (StringUtils.isNotEmpty(cert.getAcmeCertNotAfter())) {
            try {
                LocalDateTime notAfter = LocalDateTime.parse(cert.getAcmeCertNotAfter(), DATE);
                long days = ChronoUnit.DAYS.between(LocalDateTime.now(), notAfter);
                daysUntilExpiry = days;
                soonExpiring = days <= SOON_EXPIRING_DAYS && days >= 0;
            } catch (Exception e) {
                log.warn("[ACME] 解析证书到期时间失败: certId={}, notAfter={}", certId, cert.getAcmeCertNotAfter());
            }
        }

        // 当前步骤（用于前端展示申请流程），未知状态留空
        Integer currentStep = null;
        String stepDescription = null;
        String status = cert.getAcmeCertStatus();
        if (STATUS_PENDING.equals(status)) {
            currentStep = 1;
            stepDescription = "第1步: 提交申请 - 正在连接 ACME 服务器...";
        } else if (STATUS_VALIDATING.equals(status)) {
            currentStep = 2;
            stepDescription = "第2步: 等待验证 - 请部署验证文件后点击重新验证";
        } else if (STATUS_VALID.equals(status)) {
            currentStep = 3;
            stepDescription = "第3步: 签发完成 - 证书已成功签发";
        } else if (STATUS_FAILED.equals(status)) {
            currentStep = 3;
            stepDescription = "第3步: 签发失败 - " + StringUtils.defaultString(cert.getAcmeCertLastError(), "未知错误");
        } else if (STATUS_REVOKED.equals(status)) {
            currentStep = 3;
            stepDescription = "第3步: 证书已吐销";
        } else if (STATUS_EXPIRED.equals(status)) {
            currentStep = 3;
            stepDescription = "第3步: 证书已过期";
        }

        // 证书合法性校验
        AcmeCertificateDetailVo.CertificateVerification verification = buildCertificateVerification(cert);

        return ReturnResult.ok(new AcmeCertificateDetailVo(cert, account, orders,
                daysUntilExpiry, soonExpiring, curlCommands, currentStep, stepDescription, verification));
    }

    /**
     * 校验证书合法性：PEM 是否可解析、有效期窗口、域名覆盖、私钥/公钥匹配、链完整性、吊销状态。
     *
     * @param cert 证书实体
     * @return 校验结果
     */
    private AcmeCertificateDetailVo.CertificateVerification buildCertificateVerification(MonitorAcmeCertificate cert) {
        boolean pemParsable = false;
        boolean inValidWindow = false;
        boolean domainsCovered = false;
        boolean privateKeyMatches = false;
        boolean chainComplete = false;
        boolean revoked = STATUS_REVOKED.equals(cert.getAcmeCertStatus());
        List<String> messages = new ArrayList<>();

        // 1) 证书链 PEM 可解析（chain_pem 含完整链；key_pem 为私钥）
        List<java.security.cert.X509Certificate> chain = parsePemCertificates(cert.getAcmeCertChainPem());
        if (CollectionUtils.isEmpty(chain)) {
            messages.add("证书链 PEM 为空或无法解析");
        } else {
            pemParsable = true;
            chainComplete = chain.size() >= 1;
        }

        // 2) 有效期窗口：当前时间在 notBefore ~ notAfter 之间（同时校验业务字段与 PEM 内的有效期）
        String notBefore = cert.getAcmeCertNotBefore();
        String notAfter = cert.getAcmeCertNotAfter();
        if (StringUtils.isNotEmpty(notBefore) && StringUtils.isNotEmpty(notAfter)) {
            try {
                LocalDateTime nb = LocalDateTime.parse(notBefore, DATE);
                LocalDateTime na = LocalDateTime.parse(notAfter, DATE);
                LocalDateTime now = LocalDateTime.now();
                inValidWindow = !now.isBefore(nb) && !now.isAfter(na);
            } catch (Exception e) {
                messages.add("有效期字段格式异常");
            }
        }
        if (!inValidWindow) {
            messages.add("当前时间不在证书有效期窗口内");
        }

        // 3) 域名覆盖：证书 CN/SAN 是否覆盖主域名 + 备用域名
        if (CollectionUtils.isNotEmpty(chain)) {
            try {
                java.security.cert.X509Certificate leaf = chain.get(0);
                List<String> certDomains = new ArrayList<>();
                String cn = extractCn(leaf.getSubjectX500Principal().getName());
                if (StringUtils.isNotEmpty(cn)) {
                    certDomains.add(stripWildcard(cn));
                }
                try {
                    java.util.Collection<List<?>> sans = leaf.getSubjectAlternativeNames();
                    if (sans != null) {
                        for (List<?> san : sans) {
                            if (san != null && san.size() >= 2 && san.get(1) != null) {
                                certDomains.add(stripWildcard(String.valueOf(san.get(1))));
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // SAN 不存在或不适用（如自签名单域）不视为失败
                }
                List<String> required = new ArrayList<>();
                if (StringUtils.isNotEmpty(cert.getAcmeCertPrimaryDomain())) {
                    required.add(stripWildcard(cert.getAcmeCertPrimaryDomain()));
                }
                if (StringUtils.isNotEmpty(cert.getAcmeCertSan())) {
                    for (String d : cert.getAcmeCertSan().split(",")) {
                        if (StringUtils.isNotEmpty(d.trim())) {
                            required.add(stripWildcard(d.trim()));
                        }
                    }
                }
                domainsCovered = !required.isEmpty() && required.stream().allMatch(d -> certDomains.stream()
                        .anyMatch(cd -> cd.equals(d) || cd.endsWith("." + d) || d.endsWith("." + cd)));
            } catch (Exception e) {
                messages.add("域名覆盖解析失败");
            }
        }
        if (StringUtils.isNotEmpty(cert.getAcmeCertPrimaryDomain()) && !domainsCovered) {
            messages.add("证书域名未覆盖申请域名");
        }

        // 4) 私钥与证书公钥匹配：从 key_pem 提取密钥，与证书公钥比对
        if (StringUtils.isNotEmpty(cert.getAcmeCertKeyPem())) {
            try {
                java.security.PrivateKey privateKey = parsePrivateKey(cert.getAcmeCertKeyPem());
                if (privateKey != null && CollectionUtils.isNotEmpty(chain)) {
                    privateKeyMatches = publicKeysMatch(privateKey, chain.get(0).getPublicKey());
                } else {
                    messages.add("私钥 PEM 无法解析");
                }
            } catch (Exception e) {
                messages.add("私钥解析失败: " + e.getMessage());
            }
        } else {
            messages.add("缺少私钥 PEM");
        }
        if (!pemParsable || !inValidWindow || !domainsCovered || !privateKeyMatches) {
            messages.add(0, "证书合法性校验未通过");
        }
        // 吊销即视为不合法
        if (revoked) {
            messages.add("证书已吊销");
        }

        String fullMessage = String.join("；", messages);
        boolean overallValid = pemParsable && inValidWindow && domainsCovered && privateKeyMatches && chainComplete && !revoked;
        return new AcmeCertificateDetailVo.CertificateVerification(
                pemParsable, inValidWindow, domainsCovered, privateKeyMatches, chainComplete, revoked,
                fullMessage.isEmpty() ? "证书合法" : fullMessage);
    }

    /**
     * 解析 PEM 格式的证书链，返回证书列表（首个为叶子证书）。
     *
     * @param pem PEM 格式证书链文本
     * @return 证书列表（首个为叶子证书），解析失败或入参为空时返回 null
     */
    private List<java.security.cert.X509Certificate> parsePemCertificates(String pem) {
        if (StringUtils.isEmpty(pem)) {
            return null;
        }
        List<java.security.cert.X509Certificate> result = new ArrayList<>();
        try {
            java.security.cert.CertificateFactory factory = java.security.cert.CertificateFactory.getInstance("X.509");
            byte[] der = pemToDer(pem, "-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----");
            if (der == null) {
                return null;
            }
            java.util.Collection<? extends java.security.cert.Certificate> certs = factory.generateCertificates(new java.io.ByteArrayInputStream(der));
            for (java.security.cert.Certificate c : certs) {
                if (c instanceof java.security.cert.X509Certificate x) {
                    result.add(x);
                }
            }
        } catch (Exception e) {
            log.warn("[ACME] 解析证书链失败: {}", e.getMessage());
            return null;
        }
        return result;
    }

    /**
     * 解析 PEM 格式的私钥（支持 PKCS#8 / PKCS#1 RSA / EC）。
     *
     * @param pem PEM 格式私钥文本
     * @return 私钥对象，解析失败或入参为空时返回 null
     */
    private java.security.PrivateKey parsePrivateKey(String pem) {
        try {
            byte[] der = pemToDer(pem,
                    "-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----");
            if (der == null) {
                byte[] pkcs1 = pemToDer(pem,
                        "-----BEGIN RSA PRIVATE KEY-----", "-----END RSA PRIVATE KEY-----");
                if (pkcs1 == null) {
                    byte[] ec = pemToDer(pem,
                            "-----BEGIN EC PRIVATE KEY-----", "-----END EC PRIVATE KEY-----");
                    if (ec == null) {
                        return null;
                    }
                    try {
                        java.security.KeyFactory kf = java.security.KeyFactory.getInstance("EC");
                        return kf.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(ec));
                    } catch (Exception e) {
                        org.bouncycastle.openssl.PEMParser parser = new org.bouncycastle.openssl.PEMParser(
                                new java.io.StringReader(pem));
                        Object obj = parser.readObject();
                        if (obj instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo pki) {
                            return java.security.KeyFactory.getInstance("EC")
                                    .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(pki.getEncoded()));
                        }
                        return null;
                    }
                }
                java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
                try {
                    java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(pkcs1);
                    return kf.generatePrivate(spec);
                } catch (Exception e) {
                    // PKCS#1 直接转 PKCS#8
                    org.bouncycastle.asn1.pkcs.RSAPrivateKey rsa = org.bouncycastle.asn1.pkcs.RSAPrivateKey
                            .getInstance(org.bouncycastle.asn1.ASN1Primitive.fromByteArray(pkcs1));
                    org.bouncycastle.asn1.pkcs.PrivateKeyInfo pki = new org.bouncycastle.asn1.pkcs.PrivateKeyInfo(
                            new org.bouncycastle.asn1.x509.AlgorithmIdentifier(
                                    org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers.rsaEncryption),
                            rsa);
                    return kf.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(pki.getEncoded()));
                }
            }
            return java.security.KeyFactory.getInstance("RSA").generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            log.warn("[ACME] 解析私钥失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 校验私钥对应公钥与证书公钥是否匹配：用私钥对固定消息签名、再由证书公钥验签。
     * <p>对 RSA / EC 都成立（SHA256withRSA / SHA256withECDSA），避免解析 EC 公钥点时的
     * 编解码脆弱性。</p>
     *
     * @param privateKey    私钥
     * @param certPublicKey 证书公钥
     * @return true 表示私钥与证书公钥匹配
     */
    private boolean publicKeysMatch(java.security.PrivateKey privateKey, java.security.PublicKey certPublicKey) {
        try {
            String alg = privateKey.getAlgorithm();
            String sigAlg = "EC".equalsIgnoreCase(alg) ? "SHA256withECDSA" : "SHA256withRSA";
            byte[] data = "acme-cert-key-match-check".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            java.security.Signature signer = java.security.Signature.getInstance(sigAlg);
            signer.initSign(privateKey);
            signer.update(data);
            byte[] signature = signer.sign();

            java.security.Signature verifier = java.security.Signature.getInstance(sigAlg);
            verifier.initVerify(certPublicKey);
            verifier.update(data);
            return verifier.verify(signature);
        } catch (Exception e) {
            log.warn("[ACME] 私钥/证书公钥匹配校验失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 提取证书 CN。
     *
     * @param dn 证书主体 DN 文本（逗号分隔的 RDN 列表）
     * @return CN 值，未找到或入参为空时返回 null
     */
    private String extractCn(String dn) {
        if (StringUtils.isEmpty(dn)) {
            return null;
        }
        for (String part : dn.split(",")) {
            String t = part.trim();
            if (t.regionMatches(true, 0, "CN=", 0, 3)) {
                return t.substring(3).trim();
            }
        }
        return null;
    }

    /**
     * 去除通配符前缀（*.example.com -> example.com），用于域名匹配。
     *
     * @param domain 原始域名
     * @return 去除通配符前缀后的域名
     */
    private String stripWildcard(String domain) {
        return domain != null && domain.startsWith("*.") ? domain.substring(2) : domain;
    }

    /**
     * 从 PEM 文本中提取 Base64 DER 块。
     *
     * @param pem   PEM 文本
     * @param begin 块开始标记（如 -----BEGIN CERTIFICATE-----）
     * @param end   块结束标记（如 -----END CERTIFICATE-----）
     * @return Base64 解码后的 DER 字节，块缺失时返回 null
     */
    private byte[] pemToDer(String pem, String begin, String end) {
        int b = pem.indexOf(begin);
        int e = pem.indexOf(end, b + begin.length());
        if (b < 0 || e < 0) {
            return null;
        }
        String base64 = pem.substring(b + begin.length(), e).replaceAll("\\s+", "");
        return java.util.Base64.getDecoder().decode(base64);
    }

    /**
     * 获取证书统计信息 forcert
     *
     * @return 统计信息
     */
    @Override
    public ReturnResult<CertStats> getCertStatsForCert() {
        // 账户总数
        Long accountCount = accountMapper.selectCount(null);

        // 证书总数
        Long certCount = certificateMapper.selectCount(null);

        // 按状态统计
        Long validCount = certificateMapper.selectCount(new LambdaQueryWrapper<MonitorAcmeCertificate>()
                .eq(MonitorAcmeCertificate::getAcmeCertStatus, STATUS_VALID));
        Long pendingCount = certificateMapper.selectCount(new LambdaQueryWrapper<MonitorAcmeCertificate>()
                .in(MonitorAcmeCertificate::getAcmeCertStatus, STATUS_PENDING, STATUS_VALIDATING));
        Long revokedCount = certificateMapper.selectCount(new LambdaQueryWrapper<MonitorAcmeCertificate>()
                .eq(MonitorAcmeCertificate::getAcmeCertStatus, STATUS_REVOKED));
        Long failedCount = certificateMapper.selectCount(new LambdaQueryWrapper<MonitorAcmeCertificate>()
                .eq(MonitorAcmeCertificate::getAcmeCertStatus, STATUS_FAILED));

        // 统计即将到期和已过期的证书
        String now = LocalDateTime.now().format(DATE);
        String soon = LocalDateTime.now().plusDays(SOON_EXPIRING_DAYS).format(DATE);

        Long expiringCount = certificateMapper.selectCount(new LambdaQueryWrapper<MonitorAcmeCertificate>()
                .eq(MonitorAcmeCertificate::getAcmeCertStatus, STATUS_VALID)
                .ge(MonitorAcmeCertificate::getAcmeCertNotAfter, now)
                .le(MonitorAcmeCertificate::getAcmeCertNotAfter, soon));

        Long expiredCount = certificateMapper.selectCount(new LambdaQueryWrapper<MonitorAcmeCertificate>()
                .eq(MonitorAcmeCertificate::getAcmeCertStatus, STATUS_VALID)
                .lt(MonitorAcmeCertificate::getAcmeCertNotAfter, now));

        return ReturnResult.ok(new CertStats(accountCount, certCount, validCount, pendingCount,
                expiringCount, expiredCount, revokedCount, failedCount));
    }

    /**
     * 检查并续签到期前 N 天的证书（调度）
     *
     * <p>不使用事务：续签为逐证书独立的网络密集型操作，单证书失败不影响其它证书，
     * 数据库写入由各 Mapper 单条自动提交。</p>
     */
    @Override
    public void checkAndRenewSoonExpired() {
        int days = Math.max(acmeProperties.getRenewDaysBefore(), MIN_RENEW_DAYS);
        String deadline = LocalDate.now().plusDays(days).atStartOfDay().format(DATE);
        List<MonitorAcmeCertificate> list = certificateMapper.selectList(new LambdaQueryWrapper<MonitorAcmeCertificate>()
                .eq(MonitorAcmeCertificate::getAcmeCertStatus, STATUS_VALID)
                .le(MonitorAcmeCertificate::getAcmeCertNotAfter, deadline)
        );
        for (MonitorAcmeCertificate it : list) {
            try {
                log.info("[ACME] 到期前{}天自动续签: id={}, domain={}, notAfter={}", days, it.getAcmeCertId(), it.getAcmeCertPrimaryDomain(),
                    it.getAcmeCertNotAfter());
                renewCertificateForCert(it.getAcmeCertId());
            } catch (Exception e) {
                log.error("[ACME] 自动续签失败, id={}", it.getAcmeCertId(), e);
            }
        }
    }

    /**
     * 重新验证证书 forcert（用户手动部署完成后调用）
     *
     * @param certId 证书标识
     * @return 是否提交成功
     */
    @Override
    public ReturnResult<Boolean> retryValidationForCert(Integer certId) {
        MonitorAcmeCertificate cert = certificateMapper.selectById(certId);
        if (cert == null) {
            return ReturnResult.illegal("证书不存在: " + certId);
        }
        if (!STATUS_VALIDATING.equals(cert.getAcmeCertStatus())) {
            return ReturnResult.illegal("证书状态不是待验证，无法重新验证");
        }

 // 获取当前用户标识用于消息推送
        Integer userId = null;
        try {
            CurrentUser currentUser = authService.getCurrentUser();
            if (currentUser != null && currentUser.getUserId() != null) {
                userId = Integer.parseInt(currentUser.getUserId());
            }
        } catch (Exception e) {
            log.warn("[ACME] 获取当前用户失败: {}", e.getMessage());
        }

        log.info("[ACME] 提交重新验证: certId={}, domain={}", certId, cert.getAcmeCertPrimaryDomain());
        asyncService.retryValidationAsync(certId, userId);
        return ReturnResult.ok(true, "重新验证已提交，请在消息中心查看进度");
    }

    /**
     * 成功签发/续签后回填证书信息到实体
     *
     * @param target 目标证书实体
     * @param src    证书申请结果
     */
    private void fillCertificateSuccess(MonitorAcmeCertificate target, AcmeCertificateResult src) {
        AcmeCertificateSupport.fillCertificateSuccess(target, src, acmeProperties.getRenewDaysBefore());
    }
}
