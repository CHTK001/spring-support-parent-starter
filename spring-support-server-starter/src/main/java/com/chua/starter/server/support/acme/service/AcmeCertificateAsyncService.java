// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.server.support.acme.service;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.network.ssl.AcmeCertificateResult;
import com.chua.common.support.network.ssl.AcmeConnectionResult;
import com.chua.common.support.network.ssl.AcmeProvider;
import com.chua.common.support.network.ssl.AcmeValidationInfo;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.socket.io.constant.SocketTopics;
import com.chua.ssh.support.client.SshClient;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.acme.entity.MonitorAcmeAccount;
import com.chua.starter.server.support.acme.entity.MonitorAcmeCertificate;
import com.chua.starter.server.support.acme.entity.MonitorAcmeOrder;
import com.chua.starter.server.support.acme.mapper.MonitorAcmeAccountMapper;
import com.chua.starter.server.support.acme.mapper.MonitorAcmeCertificateMapper;
import com.chua.starter.server.support.acme.mapper.MonitorAcmeOrderMapper;
import com.chua.starter.server.support.acme.model.AcmeApplyCertRequest;
import com.chua.starter.server.support.acme.config.AcmeProperties;
import com.chua.starter.server.support.acme.service.support.AcmeCertificateSupport;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.service.ServerHostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ACME 证书异步申请服务
 * 通过 @异步 在后台线程执行证书申请，并通过 WebSocket 推送结果到消息中心
 *
 * @author CH
 * @since 2025-12-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnClass(name = "com.chua.socket.support.session.SocketSessionTemplate")
public class AcmeCertificateAsyncService {

    /**
     * 账户映射器
     */
    private final MonitorAcmeAccountMapper accountMapper;

    /**
     * 证书映射器
     */
    private final MonitorAcmeCertificateMapper certificateMapper;

    /**
     * 订单映射器
     */
    private final MonitorAcmeOrderMapper orderMapper;

    /**
     * ACME 配置属性
     */
    private final AcmeProperties acmeProperties;

    /**
     * 消息推送会话模板
     */
    private final SocketSessionTemplate socketSessionTemplate;

    /**
     * 本机 HTTP-01 验证缓存服务
     */
    private final AcmeChallengeService challengeService;

    /**
     * 主机资产服务：SSH 部署时按 sshHostId 解析主机凭据（地址/端口/用户名/密码或密钥），
     * 并按 id 取主机行补充回调里缺失的地址与端口
     */
    private final ServerHostService serverHostService;

    /**
     * 时间格式化器
     */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 异步申请证书
     * 立即返回，实际申请在虚拟线程中执行，完成后通过消息推送通知用户
     *
     * @param certId  预创建的证书标识
     * @param request 申请请求
     * @param userId  用户标识（用于消息推送）
     */
    public void applyCertificateAsync(Integer certId, AcmeApplyCertRequest request, Integer userId) {
        // 兜底：内层只捕获 Exception，Error（如缺运行时依赖的 NoClassDefFoundError）或异常处理
        // 链路上再抛出的异常会逃出工作线程——挂 uncaughtExceptionHandler 等价于把整个任务体
        // 包进外层 catch (Throwable)，防止线程静默死亡导致证书永远停留在「待申请」状态
        Thread worker = ThreadUtils.ofVirtual()
                .name("acme-async-" + certId)
                .uncaughtExceptionHandler((thread, throwable) -> {
                    log.error("[ACME-ASYNC] 证书申请线程异常终止: certId={}", certId, throwable);
                    failCertIfNotTerminal(certId, userId, "证书申请", AcmeCertificateSupport.shortMsg(throwable));
                })
                .start(() -> {
                    log.info("[ACME-ASYNC] 开始异步申请证书: certId={}, domain={}, userId={}",
                            certId, request.primaryDomain(), userId);

                    MonitorAcmeCertificate cert = certificateMapper.selectById(certId);
                    if (cert == null) {
                        pushMessage(userId, "证书申请失败", "证书记录不存在", "error", certId, request.primaryDomain());
                        return;
                    }

                    // 查询账户信息
                    MonitorAcmeAccount account = accountMapper.selectById(request.acmeAccountId());
                    if (account == null) {
                        updateCertStatus(cert, AcmeCertificateSupport.STATUS_FAILED, "ACME账户不存在");
                        pushMessage(userId, "证书申请失败", "ACME账户不存在", "error", certId, request.primaryDomain());
                        return;
                    }

                    // 推送开始消息
                    pushMessage(userId, "证书申请中", "正在连接 ACME 服务器...", "info", certId, request.primaryDomain());

                    // 组装域名列表（主域名 + SAN），与持久化的证书记录保持同一来源
                    List<String> domains = AcmeCertificateSupport.buildDomains(cert);
                    String challenge = StringUtils.defaultString(request.challengeType(), AcmeCertificateSupport.CHALLENGE_HTTP_01);

                    // 通过 SPI 获取 ACME 提供者（按账户服务商类型路由）
                    AcmeProvider provider = ServiceProvider.of(AcmeProvider.class)
                            .getNewExtension(AcmeCertificateSupport.providerName(account.getAcmeAccountProviderType()));
                    if (provider == null) {
                        updateCertStatus(cert, AcmeCertificateSupport.STATUS_FAILED, "ACME 服务提供者不可用");
                        pushMessage(userId, "证书申请失败", "ACME 服务提供者不可用，请检查系统配置", "error", certId, request.primaryDomain());
                        return;
                    }

                    // 应用账户级网络请求超时（默认 60s），避免 acme4j 默认 10s 在公网 CA 慢握手时超时
                    provider.setTimeout(java.time.Duration.ofSeconds(
                            account.getAcmeAccountTimeoutSeconds() == null
                                    ? AcmeCertificateSupport.DEFAULT_TIMEOUT_SECONDS
                                    : account.getAcmeAccountTimeoutSeconds()));

                    try {
                        // 连接 ACME 服务器
                        AcmeConnectionResult connResult = provider.connect(
                                account.getAcmeAccountServer(),
                                account.getAcmeAccountEmail(),
                                account.getAcmeAccountPrivateKeyPem(),
                                account.getAcmeAccountEabKid(),
                                account.getAcmeAccountEabHmacKey()
                        );

                        if (!connResult.isSuccess()) {
                            updateCertStatus(cert, AcmeCertificateSupport.STATUS_FAILED, connResult.getError());
                            pushMessage(userId, "证书申请失败", "连接 ACME 服务器失败: " + connResult.getError(), "error", certId, request.primaryDomain());
                            return;
                        }

                        // 已保存私钥为空或为历史占位值时，持久化本次生成的真实私钥，避免重复注册账户
                        if (AcmeCertificateSupport.needPersistAccountKey(account.getAcmeAccountPrivateKeyPem())
                                && connResult.getPrivateKeyPem() != null) {
                            account.setAcmeAccountPrivateKeyPem(connResult.getPrivateKeyPem());
                            account.setAcmeAccountStatus("valid");
                            accountMapper.updateById(account);
                        }

                        // 推送验证中消息
                        pushMessage(userId, "证书申请中", "正在进行域名验证...", "info", certId, request.primaryDomain());

                        // 获取验证信息（同一订单地址随验证信息返回，供跨请求恢复）
                        List<AcmeValidationInfo> validationInfos = provider.getValidationInfo(domains, challenge);

                        // 写入订单记录（订单地址 + 验证信息）
                        MonitorAcmeOrder order = new MonitorAcmeOrder();
                        order.setAcmeAccountId(account.getAcmeAccountId());
                        order.setAcmeCertId(certId);
                        order.setAcmeOrderStatus(AcmeCertificateSupport.STATUS_VALIDATING);
                        order.setAcmeOrderUrl(AcmeCertificateSupport.resolveOrderUrl(validationInfos));
                        order.setAcmeAuthzUrls(AcmeCertificateSupport.toValidationJson(validationInfos));
                        orderMapper.insert(order);

                        // 根据部署模式处理
                        String deployMode = StringUtils.defaultString(cert.getAcmeCertDeployMode(), AcmeCertificateSupport.DEPLOY_MODE_MANUAL);

                        if (AcmeCertificateSupport.DEPLOY_MODE_LOCAL.equals(deployMode)
                                && AcmeCertificateSupport.CHALLENGE_HTTP_01.equals(challenge)) {
                            // 本机模式：通过内置 HTTP 端点响应验证请求
                            for (AcmeValidationInfo info : validationInfos) {
                                if (AcmeCertificateSupport.CHALLENGE_HTTP_01.equals(info.challengeType())
                                        && StringUtils.isNotEmpty(info.token())) {
                                    challengeService.put(info.token(), info.httpContent());
                                }
                            }
                            // 配置了 webroot 时同步写静态文件（尽力而为，失败不影响内置端点响应）
                            AcmeCertificateSupport.writeHttpChallengeFiles(request.webrootPath(), validationInfos);
                            log.info("[ACME-ASYNC] 本机模式，验证信息已存储到缓存");
                        } else if (AcmeCertificateSupport.DEPLOY_MODE_SSH.equals(deployMode)
                                && AcmeCertificateSupport.CHALLENGE_HTTP_01.equals(challenge)) {
                            // SSH远程模式：通过SSH创建验证文件
                            boolean deployed = deploySshChallengeFiles(request, validationInfos);
                            if (!deployed) {
                                cert.setAcmeCertStatus(AcmeCertificateSupport.STATUS_VALIDATING);
                                cert.setAcmeCertLastError("SSH部署验证文件失败，请检查SSH连接信息");
                                certificateMapper.updateById(cert);
                                pushMessage(userId, "证书申请待验证",
                                        String.format("域名 %s SSH部署验证文件失败，请检查SSH连接信息后重新验证", request.primaryDomain()),
                                        "warning", certId, request.primaryDomain());
                                return;
                            }
                            log.info("[ACME-ASYNC] SSH模式，验证文件已部署到远程服务器: {}", request.sshHost());
                        } else if (AcmeCertificateSupport.DEPLOY_MODE_MANUAL.equals(deployMode)) {
                            cert.setAcmeCertStatus(AcmeCertificateSupport.STATUS_VALIDATING);
                            cert.setAcmeCertLastError(null);
                            certificateMapper.updateById(cert);
                            log.info("[ACME-ASYNC] 手动模式，等待用户部署验证文件: certId={}", certId);
                            pushMessage(userId, "证书申请待验证",
                                    String.format("域名 %s 需要完成 %s 验证，请查看证书详情获取验证信息并手动部署",
                                            request.primaryDomain(), challenge),
                                    "warning", certId, request.primaryDomain());
                            return;
                        }

                        // 尝试申请证书
                        AcmeCertificateResult certResult = provider.requestCertificate(domains, challenge);

                        if (certResult.isSuccess()) {
                            // 成功签发
                            AcmeCertificateSupport.fillCertificateSuccess(cert, certResult, acmeProperties.getRenewDaysBefore());
                            certificateMapper.updateById(cert);
                            order.setAcmeOrderStatus(AcmeCertificateSupport.ORDER_STATUS_VALID);
                            orderMapper.updateById(order);

                            log.info("[ACME-ASYNC] 证书签发成功: certId={}, domain={}", certId, request.primaryDomain());
                            pushMessage(userId, "证书签发成功",
                                    String.format("域名 %s 的 SSL 证书已成功签发", request.primaryDomain()),
                                    "success", certId, request.primaryDomain());
                        } else {
                            // 需要手动验证
                            cert.setAcmeCertStatus(AcmeCertificateSupport.STATUS_VALIDATING);
                            cert.setAcmeCertLastError(AcmeCertificateSupport.shortMsg(certResult.getError()));
                            certificateMapper.updateById(cert);

                            log.warn("[ACME-ASYNC] 证书申请需要验证: certId={}, err={}", certId, certResult.getError());
                            pushMessage(userId, "证书申请待验证",
                                    String.format("域名 %s 需要完成 %s 验证，请查看证书详情获取验证信息",
                                            request.primaryDomain(), challenge),
                                    "warning", certId, request.primaryDomain());
                        }

                    } catch (Exception e) {
                        log.error("[ACME-ASYNC] 证书申请异常: certId={}", certId, e);
                        updateCertStatus(cert, AcmeCertificateSupport.STATUS_VALIDATING, AcmeCertificateSupport.shortMsg(e));
                        pushMessage(userId, "证书申请待验证",
                                String.format("域名 %s 需要手动验证: %s", request.primaryDomain(), AcmeCertificateSupport.shortMsg(e)),
                                "warning", certId, request.primaryDomain());
                    } finally {
                        provider.close();
                    }
                });
        startApplyWatchdog(worker, certId, userId, "证书申请");
    }

    /**
     * 推送消息到用户的消息中心
     * @param userId 用户id
     * @param title title
     * @param content 内容
     * @param level 级别
     * @param certId certId
     * @param domain domain
     */
    private void pushMessage(Integer userId, String title, String content, String level, Integer certId, String domain) {
        try {
            Map<String, Object> data = new HashMap<>(12);
            data.put("messageId", System.currentTimeMillis());
            data.put("title", title);
            data.put("content", content);
            data.put("type", "acme_cert_apply");
            data.put("level", level);
            data.put("bizType", "acme_certificate");
            data.put("bizId", String.valueOf(certId));
            data.put("url", "/acme");
            data.put("sendTime", LocalDateTime.now().format(DATE));
            data.put("dataId", userId != null ? String.valueOf(userId) : "0");
            data.put("certId", certId);
            data.put("domain", domain);

            String topic = SocketTopics.Service.Message.PUSH + "@" + (userId != null ? userId : 0);
            socketSessionTemplate.send(topic, Json.toJson(data));
            log.debug("[ACME-ASYNC] 消息推送成功: userId={}, title={}", userId, title);
        } catch (Exception e) {
            log.error("[ACME-ASYNC] 消息推送失败: userId={}, title={}", userId, title, e);
        }
    }

    /**
     * 更新证书状态
     * @param cert cert
     * @param status 状态
     * @param error 错误
     */
    private void updateCertStatus(MonitorAcmeCertificate cert, String status, String error) {
        cert.setAcmeCertStatus(status);
        cert.setAcmeCertLastError(error);
        certificateMapper.updateById(cert);
    }

    /**
     * SSH远程模式下通过SSH部署 HTTP-01 验证文件
     *
     * @param request         申请请求（含 SSH 连接信息）
     * @param validationInfos 验证信息列表
     * @return 是否部署成功
     */
    private boolean deploySshChallengeFiles(AcmeApplyCertRequest request, List<AcmeValidationInfo> validationInfos) {
        // 优先按主机资产解析凭据：前端只需选主机，地址/端口/用户名/密码由后端取自主机绑定的登录身份
        SshTarget target = resolveSshTarget(request);
        if (target == null) {
            log.error("[ACME-ASYNC] SSH连接信息不完整: sshHostId={}, sshHost={}, sshUsername={}",
                    request.sshHostId(), request.sshHost(), request.sshUsername());
            return false;
        }
        if (StringUtils.isEmpty(request.webrootPath())) {
            log.error("[ACME-ASYNC] webroot路径未配置");
            return false;
        }

        SshClient sshClient = null;
        try {
            // 创建 SSH 连接
            sshClient = SshClient.builder()
                    .host(target.host())
                    .port(target.port())
                    .username(target.username())
                    .password(target.password())
                    .connectTimeout(30)
                    .build()
                    .connect();
            log.info("[ACME-ASYNC] SSH连接成功: {}@{}{}", target.username(), target.host(),
                    request.sshHostId() != null ? " (主机 #" + request.sshHostId() + ")" : "");

            String webroot = request.webrootPath();
            // 创建验证目录
            String mkdirCmd = String.format("mkdir -p %s/.well-known/acme-challenge", webroot);
            sshClient.exec().command(mkdirCmd).execute();
            log.info("[ACME-ASYNC] 创建验证目录: {}", mkdirCmd);

            // 写入验证文件
            for (AcmeValidationInfo info : validationInfos) {
                if (AcmeCertificateSupport.CHALLENGE_HTTP_01.equals(info.challengeType())
                        && StringUtils.isNotEmpty(info.token())) {
                    String writeCmd = String.format("echo '%s' > %s/.well-known/acme-challenge/%s",
                            info.httpContent(), webroot, info.token());
                    sshClient.exec().command(writeCmd).execute();
                    log.info("[ACME-ASYNC] SSH写入验证文件: {}", info.token());
                }
            }
            return true;
        } catch (Exception e) {
            log.error("[ACME-ASYNC] SSH部署验证文件失败: {}", e.getMessage(), e);
            return false;
        } finally {
            if (sshClient != null) {
                try {
                    sshClient.close();
                } catch (Exception e) {
                    log.warn("[ACME-ASYNC] 关闭SSH连接失败: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 解析 SSH 部署目标。
     *
     * <p>优先按 {@code sshHostId}（主机资产）取凭据：主机记录里已经绑定了登录身份
     * （用户名 + 密码/密钥）与端口，前端选了主机就不该再让用户重复填写一遍，
     * 更不该把密码明文塞进请求体。仅当未传主机 ID、或主机凭据解析失败时，
     * 才回落到请求里显式给出的字段（兼容旧调用方与「手填一台临时机器」的场景）。</p>
     *
     * @param request 申请请求
     * @return 可用于建连的目标；信息不足返回 null
     */
    private SshTarget resolveSshTarget(AcmeApplyCertRequest request) {
        Long hostId = request.sshHostId();
        if (hostId != null) {
            try {
                ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, null);
                if (resolved != null && resolved.isOk() && resolved.getData() != null) {
                    SshCredential credential = resolved.getData();
                    MsServerHost host = serverHostService.findById(hostId);
                    String address = request.sshHost();
                    if (StringUtils.isEmpty(address) && host != null) {
                        address = host.getServerHostAddress();
                    }
                    Integer port = request.sshPort();
                    if (port == null && host != null && host.getServerHostPort() != null) {
                        port = host.getServerHostPort();
                    }
                    String username = StringUtils.isNotEmpty(credential.username())
                            ? credential.username() : request.sshUsername();
                    String password = StringUtils.isNotEmpty(credential.password())
                            ? credential.password() : request.sshPassword();
                    if (StringUtils.isEmpty(password) && credential.hasKey()) {
                        // 当前 SSH 客户端仅支持口令认证，密钥身份无法在此通道使用，提前给出明确原因
                        log.error("[ACME-ASYNC] 主机 #{} 使用密钥认证，SSH 部署验证文件仅支持口令身份", hostId);
                        return null;
                    }
                    if (StringUtils.isNotEmpty(address) && StringUtils.isNotEmpty(username)) {
                        return new SshTarget(address, port == null ? 22 : port, username, password);
                    }
                    log.warn("[ACME-ASYNC] 主机 #{} 凭据解析结果不完整: address={}, username={}", hostId, address, username);
                } else if (resolved != null) {
                    log.warn("[ACME-ASYNC] 解析主机 #{} 凭据失败: {}", hostId, resolved.getMsg());
                }
            } catch (Exception e) {
                log.warn("[ACME-ASYNC] 解析主机 #{} 凭据异常: {}", hostId, e.getMessage());
            }
        }
        // 回落到请求里显式给出的 SSH 字段
        if (StringUtils.isNotEmpty(request.sshHost()) && StringUtils.isNotEmpty(request.sshUsername())) {
            return new SshTarget(request.sshHost(),
                    request.sshPort() == null ? 22 : request.sshPort(),
                    request.sshUsername(),
                    request.sshPassword());
        }
        return null;
    }

    /**
     * 解析后的 SSH 部署目标。
     *
     * @param host     主机地址
     * @param port     端口
     * @param username 用户名
     * @param password 口令
     */
    private record SshTarget(String host, int port, String username, String password) {
    }

    /**
     * 重新验证证书（用户手动部署完成后调用）
     *
     * @param certId 证书标识
     * @param userId 用户标识
     */
    public void retryValidationAsync(Integer certId, Integer userId) {
        // 兜底：Error 或内层未接住的异常逃出工作线程时，不能让证书永远停留在「验证中」状态
        Thread worker = ThreadUtils.ofVirtual()
                .name("acme-retry-" + certId)
                .uncaughtExceptionHandler((thread, throwable) -> {
                    log.error("[ACME-ASYNC] 重新验证线程异常终止: certId={}", certId, throwable);
                    failCertIfNotTerminal(certId, userId, "重新验证", AcmeCertificateSupport.shortMsg(throwable));
                })
                .start(() -> {
                    log.info("[ACME-ASYNC] 开始重新验证证书: certId={}, userId={}", certId, userId);

                    MonitorAcmeCertificate cert = certificateMapper.selectById(certId);
                    if (cert == null) {
                        pushMessage(userId, "重新验证失败", "证书记录不存在", "error", certId, null);
                        return;
                    }

                    MonitorAcmeAccount account = accountMapper.selectById(cert.getAcmeAccountId());
                    if (account == null) {
                        updateCertStatus(cert, AcmeCertificateSupport.STATUS_FAILED, "ACME账户不存在");
                        pushMessage(userId, "重新验证失败", "ACME账户不存在", "error", certId, cert.getAcmeCertPrimaryDomain());
                        return;
                    }

                    pushMessage(userId, "正在重新验证", "正在连接 ACME 服务器...", "info", certId, cert.getAcmeCertPrimaryDomain());

                    // 查询最近一笔订单，存在订单地址时跨请求恢复该订单，避免重新下单导致已部署验证失效
                    MonitorAcmeOrder latestOrder = orderMapper.selectOne(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MonitorAcmeOrder>()
                                    .eq(MonitorAcmeOrder::getAcmeCertId, certId)
                                    .orderByDesc(MonitorAcmeOrder::getUpdateTime)
                                    .last("LIMIT 1"));

                    // 组装域名列表（主域名 + SAN）
                    List<String> domains = AcmeCertificateSupport.buildDomains(cert);
                    String challenge = cert.getAcmeCertChallengeType();

                    // 通过 SPI 获取 ACME 提供者（按账户服务商类型路由）
                    AcmeProvider provider = ServiceProvider.of(AcmeProvider.class)
                            .getNewExtension(AcmeCertificateSupport.providerName(account.getAcmeAccountProviderType()));
                    if (provider == null) {
                        updateCertStatus(cert, AcmeCertificateSupport.STATUS_FAILED, "ACME 服务提供者不可用");
                        pushMessage(userId, "重新验证失败", "ACME 服务提供者不可用", "error", certId, cert.getAcmeCertPrimaryDomain());
                        return;
                    }

                    try {
                        AcmeConnectionResult connResult = provider.connect(
                                account.getAcmeAccountServer(),
                                account.getAcmeAccountEmail(),
                                account.getAcmeAccountPrivateKeyPem(),
                                account.getAcmeAccountEabKid(),
                                account.getAcmeAccountEabHmacKey()
                        );

                        if (!connResult.isSuccess()) {
                            updateCertStatus(cert, AcmeCertificateSupport.STATUS_FAILED, connResult.getError());
                            pushMessage(userId, "重新验证失败", "连接 ACME 服务器失败: " + connResult.getError(), "error", certId, cert.getAcmeCertPrimaryDomain());
                            return;
                        }

                        pushMessage(userId, "正在重新验证", "正在进行域名验证...", "info", certId, cert.getAcmeCertPrimaryDomain());

                        // 优先恢复历史订单；历史数据无订单地址时回退为重新下单
                        AcmeCertificateResult certResult;
                        if (latestOrder != null && StringUtils.isNotEmpty(latestOrder.getAcmeOrderUrl())) {
                            certResult = provider.resumeCertificate(latestOrder.getAcmeOrderUrl(), domains, challenge);
                            // 历史订单已失效（如授权过期 Authorization expired、订单已作废），恢复必然失败，
                            // 改为重新下单，否则订单永远无法推进，证书会一直卡在「验证中」
                            if (!certResult.isSuccess() && isOrderUnrecoverable(certResult.getError())) {
                                log.warn("[ACME-ASYNC] 历史订单不可恢复，改为重新下单: certId={}, orderUrl={}, err={}",
                                        certId, latestOrder.getAcmeOrderUrl(), certResult.getError());
                                pushMessage(userId, "正在重新验证", "历史订单已失效，正在重新下单...", "info", certId, cert.getAcmeCertPrimaryDomain());

                                // 旧订单标记失效，保留历史记录
                                latestOrder.setAcmeOrderStatus(AcmeCertificateSupport.ORDER_STATUS_INVALID);
                                orderMapper.updateById(latestOrder);

                                // 重新下单：先取验证信息（会创建新订单），落库新订单后再提交
                                List<AcmeValidationInfo> validationInfos = provider.getValidationInfo(domains, challenge);
                                MonitorAcmeOrder newOrder = new MonitorAcmeOrder();
                                newOrder.setAcmeAccountId(account.getAcmeAccountId());
                                newOrder.setAcmeCertId(certId);
                                newOrder.setAcmeOrderStatus(AcmeCertificateSupport.STATUS_VALIDATING);
                                newOrder.setAcmeOrderUrl(AcmeCertificateSupport.resolveOrderUrl(validationInfos));
                                newOrder.setAcmeAuthzUrls(AcmeCertificateSupport.toValidationJson(validationInfos));
                                orderMapper.insert(newOrder);
                                latestOrder = newOrder;

                                // 本机模式下重新下单的 token 需重新写入缓存，否则 CA 回查验证文件会 404
                                String deployMode = StringUtils.defaultString(cert.getAcmeCertDeployMode(), AcmeCertificateSupport.DEPLOY_MODE_MANUAL);
                                if (AcmeCertificateSupport.DEPLOY_MODE_LOCAL.equals(deployMode)) {
                                    for (AcmeValidationInfo info : validationInfos) {
                                        if (AcmeCertificateSupport.CHALLENGE_HTTP_01.equals(info.challengeType())
                                                && StringUtils.isNotEmpty(info.token())) {
                                            challengeService.put(info.token(), info.httpContent());
                                        }
                                    }
                                }
                                certResult = provider.requestCertificate(domains, challenge);
                            }
                        } else {
                            log.warn("[ACME-ASYNC] 未找到历史订单地址，回退为重新下单: certId={}", certId);
                            certResult = provider.requestCertificate(domains, challenge);
                        }

                        if (certResult.isSuccess()) {
                            AcmeCertificateSupport.fillCertificateSuccess(cert, certResult, acmeProperties.getRenewDaysBefore());
                            certificateMapper.updateById(cert);

                            // 更新订单状态
                            if (latestOrder != null) {
                                latestOrder.setAcmeOrderStatus(AcmeCertificateSupport.ORDER_STATUS_VALID);
                                orderMapper.updateById(latestOrder);
                            }

                            log.info("[ACME-ASYNC] 重新验证成功: certId={}", certId);
                            pushMessage(userId, "证书签发成功",
                                    String.format("域名 %s 的 SSL 证书已成功签发", cert.getAcmeCertPrimaryDomain()),
                                    "success", certId, cert.getAcmeCertPrimaryDomain());
                        } else {
                            // 无法完成时置为失败，避免停留在「验证中」造成永远等待的假象
                            cert.setAcmeCertStatus(AcmeCertificateSupport.STATUS_FAILED);
                            cert.setAcmeCertLastError(AcmeCertificateSupport.shortMsg(certResult.getError()));
                            certificateMapper.updateById(cert);

                            if (latestOrder != null && AcmeCertificateSupport.STATUS_VALIDATING.equals(latestOrder.getAcmeOrderStatus())) {
                                latestOrder.setAcmeOrderStatus(AcmeCertificateSupport.ORDER_STATUS_INVALID);
                                orderMapper.updateById(latestOrder);
                            }

                            log.warn("[ACME-ASYNC] 重新验证失败: certId={}, err={}", certId, certResult.getError());
                            pushMessage(userId, "重新验证失败",
                                    String.format("域名验证失败: %s，请检查验证文件是否正确部署", certResult.getError()),
                                    "error", certId, cert.getAcmeCertPrimaryDomain());
                        }
                    } catch (Exception e) {
                        log.error("[ACME-ASYNC] 重新验证异常: certId={}", certId, e);
                        updateCertStatus(cert, AcmeCertificateSupport.STATUS_FAILED, AcmeCertificateSupport.shortMsg(e));
                        pushMessage(userId, "重新验证失败",
                                String.format("验证异常: %s", AcmeCertificateSupport.shortMsg(e)),
                                "error", certId, cert.getAcmeCertPrimaryDomain());
                    } finally {
                        provider.close();
                    }
                });
        startApplyWatchdog(worker, certId, userId, "重新验证");
    }

    /**
     * 启动证书异步任务的超时看门狗：工作线程超过配置时限
     * （{@code monitor.acme.applyTimeoutMinutes}，默认 15 分钟）仍未结束时，
     * 若证书仍停留在「待申请/验证中」，兜底标记为失败并推送消息，
     * 避免任务阻塞导致证书状态永远不推进。配置为 ≤0 时关闭看门狗。
     *
     * @param worker 任务工作线程
     * @param certId 证书标识
     * @param userId 用户标识（用于消息推送）
     * @param stage  业务阶段名（证书申请/重新验证），用于日志与消息文案
     */
    private void startApplyWatchdog(Thread worker, Integer certId, Integer userId, String stage) {
        long timeoutMinutes = acmeProperties.getApplyTimeoutMinutes();
        if (timeoutMinutes <= 0) {
            return;
        }
        ThreadUtils.ofVirtual()
                .name("acme-watchdog-" + certId)
                .start(() -> {
                    try {
                        worker.join(timeoutMinutes * 60_000L);
                        if (worker.isAlive()) {
                            log.error("[ACME-ASYNC] {}超时（超过 {} 分钟仍未结束），兜底标记失败: certId={}",
                                    stage, timeoutMinutes, certId);
                            failCertIfNotTerminal(certId, userId, stage,
                                    stage + "超过 " + timeoutMinutes + " 分钟仍未完成，已超时终止");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Throwable t) {
                        log.error("[ACME-ASYNC] {}超时看门狗异常: certId={}", stage, certId, t);
                    }
                });
    }

    /**
     * 异步任务兜底失败标记：仅当证书仍处于「待申请/验证中」等未终态时，
     * 才降级为失败并推送消息；已是终态（valid/failed 等）时不覆盖既有结果。
     *
     * @param certId 证书标识
     * @param userId 用户标识（用于消息推送）
     * @param stage  业务阶段名（证书申请/重新验证），用于日志与消息文案
     * @param reason 失败原因
     */
    private void failCertIfNotTerminal(Integer certId, Integer userId, String stage, String reason) {
        try {
            MonitorAcmeCertificate cert = certificateMapper.selectById(certId);
            if (cert == null) {
                return;
            }
            String status = cert.getAcmeCertStatus();
            if (!AcmeCertificateSupport.STATUS_PENDING.equals(status)
                    && !AcmeCertificateSupport.STATUS_VALIDATING.equals(status)) {
                return;
            }
            String msg = AcmeCertificateSupport.shortMsg(reason);
            updateCertStatus(cert, AcmeCertificateSupport.STATUS_FAILED, msg);
            pushMessage(userId, stage + "失败",
                    String.format("%s异常终止（域名 %s）: %s", stage, cert.getAcmeCertPrimaryDomain(), msg),
                    "error", certId, cert.getAcmeCertPrimaryDomain());
        } catch (Throwable t) {
            // 兜底逻辑自身不能再向线程抛出异常
            log.error("[ACME-ASYNC] {}兜底标记失败异常: certId={}", stage, certId, t);
        }
    }

    /**
     * 判断历史订单错误是否属于「订单已不可恢复」——此类错误继续恢复同一订单永远不会成功，
     * 必须重新下单才能推进流程。
     *
     * @param error 恢复订单返回的错误信息
     * @return true 表示订单已失效，需要重新下单
     */
    private boolean isOrderUnrecoverable(String error) {
        if (StringUtils.isEmpty(error)) {
            return false;
        }
        String msg = error.toLowerCase();
        return msg.contains("authorization expired")
                || msg.contains("order not found")
                || msg.contains("no such order")
                || msg.contains("expired")
                || msg.contains("already final")
                || msg.contains("order is not pending")
                || msg.contains("malformed");
    }
}
