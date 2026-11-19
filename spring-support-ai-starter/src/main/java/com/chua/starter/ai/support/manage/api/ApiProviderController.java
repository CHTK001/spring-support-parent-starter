// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.ai.chat.AuthHeaders;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientIndicator;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.ai.chat.indicator.ChatClientIndicators;
import com.chua.common.support.ai.chat.indicator.HttpChatClientIndicator;
import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.channel.KeylessMediaChannel;
import com.chua.starter.entity.service.SysMessage;
import com.chua.starter.system.service.SysMessageService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.UUID;
import com.chua.starter.ai.support.entity.SysAiGlobalSetting;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.service.ModelMetricsSnapshotService;
import com.chua.starter.ai.support.manage.service.OnnxModelTaxonomy;
import com.chua.starter.ai.support.manage.service.SysAiModelMetricsService;
import com.chua.starter.ai.support.manage.store.SysAiModelMetrics;
import com.chua.starter.ai.support.manage.service.ProviderCreditAlertLogService;
import com.chua.starter.ai.support.manage.store.SysAiProviderCredit;
import com.chua.starter.ai.support.manage.store.SysAiProviderCreditAlertLog;
import com.chua.starter.ai.support.manage.store.SysAiProviderCreditHistory;
import com.chua.starter.account.channel.store.SysAccountChannel;
import com.chua.starter.account.channel.store.SysAccountChannelConnection;
import com.chua.starter.account.channel.store.SysAccountChannelModel;
import com.chua.starter.ai.support.chat.MagpieRouteSource;
import com.chua.starter.ai.support.schedule.CreditRefreshScheduler;
import com.chua.starter.ai.support.util.AiModelMetricKeys;
import com.chua.starter.ai.support.util.ProviderRouteTargets;
import com.chua.starter.datasearch.provider.limit.ProviderLimitCollector;
import com.chua.starter.datasearch.provider.limit.ProviderLimitContext;
import com.chua.starter.datasearch.provider.support.ProviderModelMetrics;
import com.chua.starter.datasearch.provider.support.ProviderRegistries;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditMapping;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.chua.starter.datasearch.credit.spi.impl.HostCreditQueryProvider;
import com.chua.starter.datasearch.credit.support.CreditQueries;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 前端 提供者 / 模型 / 协议管理控制器（对接 nomicore 契约）。
 *
 * <p>替代原 Rust 后端的厂商管理能力，提供 providers、provider-models、provider 连接、
 * 托管免费模型服务以及模型协议清单五类纯 Spring 接口。数据通过 MyBatis-Plus 持久化到
 * {@code sys_nomifun_provider} / {@code sys_nomifun_provider_model} /
 * {@code sys_nomifun_provider_connection} 三张表。</p>
 *
 * <p>凭证字段（credentials）写入后不回显，响应仅以 {@code has_credentials} 布尔标识是否存在。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ApiProviderController {

    /**
     * 渠道生命周期业务（账号模块）：渠道列表、判重、extra 落库
     */
    private final com.chua.starter.account.channel.service.AccountChannelService accountChannelService;

    /**
     * 渠道模型业务（账号模块）：模型清单与元数据写入
     */
    private final com.chua.starter.account.channel.service.AccountChannelModelService accountChannelModelService;

    /**
     * 渠道连接业务（账号模块）：归属隔离的连接查询
     */
    private final com.chua.starter.account.channel.service.AccountChannelConnectionService accountChannelConnectionService;

    /**
     * Magpie 网关路由源：免费渠道的模型清单要并入网关当前动态模型
     */
    private final MagpieRouteSource magpieRouteSource;

    /**
     * 模型价格/指标快照服务（读取 Redis 缓存，含价格/延迟/速度/智能等指标）
     */
    private final ModelMetricsSnapshotService metricsSnapshotService;

    /**
     * Agent 模型注册中心（动态注入 Agent 虚拟 提供者）
     */
    private final com.chua.starter.ai.support.registry.ChatClientRegistry chatClientRegistry;

    /**
     * 全局设置映射器（持久化 ONNX 外部服务器目录，重启后恢复）
     */
    private final com.chua.starter.ai.support.mapper.SysAiGlobalSettingMapper globalSettingMapper;

    /**
     * JSON 序列化（读写 ONNX 外部目录清单）
     */
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * 本地模型门面（ONNX 提供商的模型目录来源）
     */
    private final com.chua.starter.ai.support.service.AiModelCatalogService aiModelCatalogService;

    /**
     * 模型指标库服务（sync-pricing 同步入库）
     */
    private final com.chua.starter.ai.support.manage.service.SysAiModelMetricsService sysAiModelMetricsService;

    /**
     * 余额快照持久化服务：按 Key 落库 sys_ai_provider_credit，重启不丢
     */
    private final com.chua.starter.ai.support.manage.service.ProviderCreditSnapshotService providerCreditSnapshotService;

    /**
     * 余额告警推送留痕服务：投递成功后写一行 sys_ai_provider_credit_alert_log，
     * 供「上次推送时间」展示与事后核对
     */
    private final ProviderCreditAlertLogService providerCreditAlertLogService;

    /**
     * 模型目录同步（models.dev 说明 / 能力位 / 计费）
     */
    private final com.chua.starter.ai.support.manage.service.ProviderCatalogSyncService providerCatalogSyncService;

    /**
     * 站内消息服务：余额低量告警的投递出口。
     *
     * <p>依赖在 pom 里声明为 optional，宿主没接 common-system-starter 时容器里
     * 没有该 Bean，直接注入会启动失败，所以用 ObjectProvider 承接——
     * 拿不到就跳过告警，不影响余额查询主链路。</p>
     */
    private final ObjectProvider<SysMessageService> messageServiceProvider;

    /**
     * 余额定时刷新任务（可观测状态出口）。
     *
     * <p>该任务构造器依赖本控制器，直接互注会成 Bean 创建环；
     * 用 ObjectProvider 懒解析打破循环。</p>
     */
    private final ObjectProvider<CreditRefreshScheduler> creditRefreshScheduler;

    /**
     * AK/SK 签名型余额查询的占位 Key：供应商没有余额令牌（extra.credit_key 为空）
     * 但配齐了 extra.credit_access_key / credit_secret_key 时，用这个占位串走
     * AK/SK 签名链路（火山 Billing / 百度财务 / 七牛 / 阿里云 BSS 等）。
     */
    private static final String CREDIT_AKSK_KEY = "__aksk__";

    /**
     * AK/SK 占位 Key 在页面与快照里的展示掩码（不脱敏成 ****，让用户知道走的是签名链路）。
     */
    private static final String CREDIT_AKSK_MASK = "AK/SK";

    /**
     * 余额低量告警的进程内冷却窗口：同一供应商两次推送的最小间隔（6 小时）。
     *
     * <p>余额没充值就会一直低于阈值，而告警挂在实时回源路径上——不冷却的话，
     * 管理员每次打开余额面板（5 分钟缓存过期后）都会收到一条重复消息。</p>
     */
    private static final long CREDIT_WARN_COOLDOWN_MS = 6L * 60L * 60L * 1000L;

    /** 余额告警冷却登记：providerId -> 上次成功推送时间（epoch 毫秒） */
    private final Map<String, Long> creditWarnLastSent = new ConcurrentHashMap<>();

    /**
     * 余额低量告警去抖轮数：连续多少轮实时回源都低于阈值才推送（单轮波动不告警）。
     *
     * <p>「轮」= 一次实时回源评估（面板强制刷新 / 5 分钟缓存过期后打开）；
     * 缓存命中与快照回放不计轮。读数在阈值附近抖动时，连续达标才认定真低，
     * 避免「刚好卡线的单次读数」触发一条告警。</p>
     */
    private static final int CREDIT_WARN_STREAK = 2;

    /** 余额告警去抖登记：providerId -> 连续低于阈值的轮数 */
    private final Map<String, Integer> creditWarnLowStreak = new ConcurrentHashMap<>();

    /** 已推送低量告警且尚未推送恢复通知的供应商：回升过阈值后据此补一条恢复通知 */
    private final Set<String> creditWarnAlertOpen = ConcurrentHashMap.newKeySet();

    /**
     * ONNX 供应商物化服务：ONNX 供应商与模型镜像同步
     */
    private final com.chua.starter.ai.support.manage.service.OnnxProviderMaterializer onnxProviderMaterializer;

    /**
     * ONNX 外部模型目录服务：目录选取（对接后台文件系统接口）与登记、注销、启动恢复。
     */
    private final com.chua.starter.ai.support.manage.service.OnnxExternalDirectoryService onnxExternalDirectoryService;

    /**
     * 路由组业务：成员表（来自已添加的模型库）读写、顺序调整、成员删除级联
     */
    private final com.chua.starter.ai.support.chat.RouterGroupService routerGroupService;

    /**
     * 模型 → 客户端解析器（路由组配置变更后让缓存立即失效）
     */
    private final com.chua.starter.ai.support.chat.ModelClientResolver modelClientResolver;

    /**
     * 服务访问日志映射器：路由拓扑页的「入站流量」按 AppKey 聚合
     */
    private final com.chua.starter.ai.support.mapper.SysAiServiceAccessLogMapper accessLogMapper;

    /**
     * AppKey 映射器：拓扑页入站节点回填名称 / 余额 / 配额（网关鉴权身份）
     */
    private final com.chua.starter.ai.support.mapper.SysAiAppKeyMapper appKeyMapper;

    /**
     * ONNX 供应商标识：与其余提供商一样是 {@code sys_account_channel} 表里的真实一行，
     * 由<strong>用户自己添加</strong>（落库时统一用该标识），
     * {@link com.chua.starter.ai.support.manage.service.OnnxProviderMaterializer}
     * 只在该行存在时同步模型镜像，不再自动建行。
     */
    private static final String ONNX_PROVIDER_ID =
            com.chua.starter.ai.support.manage.service.OnnxProviderMaterializer.ONNX_PROVIDER_ID;

    /**
     * 渠道管理为「内置模型渠道」物化出来的 provider 行前缀。
     *
     * <p>渠道标识形如 {@code chat-duckduckgo}，落库时统一大写成
     * {@code CHAT-DUCKDUCKGO}；对应的 ChatClient SPI 扩展名是去掉前缀后的
     * {@code duckduckgo}。模型拉取与运行时解析都按同一口径还原。</p>
     */
    private static final String CHAT_CHANNEL_PREFIX = "chat-";

    /**
     * 渠道管理为各类「免登录内置渠道」物化的 provider 行的平台前缀。
     *
     * <p>渠道 SPI 按能力分四类，渠道标识统一为 {@code <种类>-<名称>}：
     * 对话 {@code chat-}、文生图 {@code image-}、文生视频 {@code video-}、图生 3D {@code model3d-}。
     * 它们经「渠道标识大写」惰性物化出的 provider 行（如 {@code CHAT-DUCKDUCKGO} /
     * {@code IMAGE-POLLINATIONS}）都承载模型行，模型管理列表必须同样保留。</p>
     */
    private static final List<String> KEYLESS_CHANNEL_PREFIXES =
            List.of("chat-", "image-", "video-", "model3d-");

    /**
     * 判断平台标识是否为渠道管理物化的「免登录内置渠道」行。
     *
     * @param platform 平台标识
     * @return true=免登录内置渠道行
     */
    private static boolean isKeylessChannelPlatform(String platform) {
        if (platform == null) {
            return false;
        }
        String value = platform.trim().toLowerCase(Locale.ROOT);
        for (String prefix : KEYLESS_CHANNEL_PREFIXES) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断供应商标识是否为 ONNX 本地推理供应商。
     *
     * @param providerId 供应商标识
     * @return true=ONNX 供应商
     */
    private static boolean isOnnxProvider(String providerId) {
        return providerId != null && ONNX_PROVIDER_ID.equalsIgnoreCase(providerId.trim());
    }

    /**
     * 判断平台标识是否属于 ONNX 本地推理。
     *
     * <p>前端选「ONNX 本地推理」模板后落的 platform 就是 onnx；这里用包含判断，
     * 与 {@link #isOnnxRow} / 模型拉取的平台分支同口径。</p>
     *
     * @param platform 平台标识
     * @return true=ONNX 平台
     */
    private static boolean isOnnxPlatform(String platform) {
        return platform != null && platform.toLowerCase(Locale.ROOT).contains("onnx");
    }

    /**
     * 判断供应商行是否为 ONNX 供应商（按标识或平台判定）。
     *
     * @param provider 供应商行
     * @return true=ONNX 供应商
     */
    private static boolean isOnnxRow(SysAccountChannel provider) {
        if (provider == null) {
            return false;
        }
        return isOnnxProvider(provider.getProviderId()) || isOnnxPlatform(provider.getPlatform());
    }

    /**
     * 渠道管理页 OAuth 行（auth_scheme='oauth'）：由渠道管理为每个已注册渠道镀铬创建，
     * 没有模型 / 凭证，模型管理的供应商列表不暴露这些行。
     *
     * @param provider 供应商
     * @return true=渠道 OAuth provider
     */
    private static boolean isOAuthChannelRow(SysAccountChannel provider) {
        if (provider == null) {
            return false;
        }
        return "oauth".equalsIgnoreCase(provider.getAuthScheme());
    }

    /**
     * 模型管理列表可见的「免登录渠道行」：免费渠道（platform='free-channel'/'free'），
     * 以及渠道管理为免登录内置渠道物化的 {@code chat-<渠道>} / {@code image-<渠道>} 等
     * 内置渠道行（如 {@code CHAT-DUCKDUCKGO} / {@code IMAGE-POLLINATIONS}）。
     *
     * <p>{@code chat-*} / {@code image-*} 等内置渠道行由账号模块惰性物化，
     * {@code auth_scheme=oauth}，会被 {@link #isOAuthChannelRow(SysAccountChannel)} 命中；
     * 但它们是承载模型行的行，会话 / 生成经免登录客户端直连上游，必须与免费渠道同样
     * 保留在模型管理列表，否则整行（含其模型）被镀铬过滤剔除，表现为非网关渠道
     * （如 duckduckgo / pollinations）在模型页选不到。</p>
     *
     * @param provider 供应商
     * @return true=免登录渠道 provider
     */
    private static boolean isFreeChannelRow(SysAccountChannel provider) {
        if (provider == null) {
            return false;
        }
        if (ProviderRouteTargets.isFreeChannel(provider.getPlatform())) {
            return true;
        }
        return isKeylessChannelPlatform(provider.getPlatform());
    }

    /**
     * 新增 / 克隆供应商前的唯一性校验。
     *
     * <p>两条口径：</p>
     * <ul>
     *   <li>ONNX <strong>和其它供应商一样由用户自己添加</strong>（2026-11-14 用户口径：
     *       自动出现一行是错的），但仍只保留一行 —— 本地推理同一时刻只对应一套模型
     *       目录与推理设备，两行会互相覆盖。已有 ONNX 行时不允许再新增或克隆；</li>
     *   <li>其余供应商按「同平台 + 同名称（去空格、忽略大小写）」判重，
     *       避免列表里出现两个都叫 DeepSeek 的条目导致默认模型选择歧义。</li>
     * </ul>
     *
     * @param platform         平台标识
     * @param name             供应商名称
     * @param excludeProviderId 需要跳过的供应商标识（编辑 / 克隆时为源供应商，可为 null）
     */
    private void requireNotDuplicateProvider(String platform, String name, String excludeProviderId) {
        String platformKey = platform == null ? "" : platform.trim().toLowerCase(Locale.ROOT);
        boolean wantsOnnx = platformKey.contains("onnx");
        // 只有本地推理供应商能叫 onnx：云 API 行取这个名字只会让页面显示成「本地」。
        if (!wantsOnnx && isOnnxProvider(name)) {
            throw new ApiException(ApiErrorCode.CONFLICT,
                    "「onnx」是本地推理供应商专用名称，云 API 供应商请改用其它名称");
        }
        if (wantsOnnx) {
            // 排除自身：编辑已有 ONNX 行（改名称、改凭证）是合法操作，
            // 只有「库里已有一行 ONNX 时再落一行」才需要拦。ONNX 是 AI 侧的本地推理概念，
            // 因此这条单行规则留在 AI；渠道重名规则由账号模块统一维护。
            for (SysAccountChannel candidate : accountChannelService.listAll()) {
                boolean onnxRow = ONNX_PROVIDER_ID.equals(candidate.getProviderId())
                        || (candidate.getPlatform() != null
                        && candidate.getPlatform().contains(ONNX_PROVIDER_ID));
                if (!onnxRow) {
                    continue;
                }
                if (excludeProviderId != null && excludeProviderId.equals(candidate.getProviderId())) {
                    continue;
                }
                throw new ApiException(ApiErrorCode.CONFLICT,
                        "已有 ONNX 供应商，只允许一行；如需调整请直接编辑该供应商");
            }
        }
        accountChannelService.requireNotDuplicate(platform, name, excludeProviderId);
    }

    /**
     * 拉取模型列表使用的 HTTP 客户端（连接池复用）
     */
    private final HttpClient upstreamHttpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 上游模型列表接口超时时间（秒）
     */
    private static final Duration UPSTREAM_TIMEOUT = Duration.ofSeconds(15);

    /**
     * 最近一次上游 {@code /models} 响应头（供「同步」里的限制探测复用同一请求）。
     *
     * <p>请求线程内使用，取走后立即移除，避免线程池复用导致串数据。</p>
     */
    private final ThreadLocal<Map<String, List<String>>> lastUpstreamHeaders = new ThreadLocal<>();

    /**
     * uuidv7 格式正则：xxxxxxxx-xxxx-7xxx-xxxx-xxxxxxxxxxxx
     */
    private static final java.util.regex.Pattern UUID_V7_PATTERN = 
        java.util.regex.Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    /**
     * 指标索引构建时间戳（毫秒）
     */
    private volatile long metricsIndexLoadedAt = 0;

    /**
     * 指标索引（提供者标识::模型 → 快照行），供查询接口从 Redis 缓存组装指标
     */
    private volatile Map<String, Map<String, Object>> metricsIndex = Map.of();

    /**
     * 指标索引重建标记（单飞）：冷启动并发请求只放一个线程去建，其余直接用旧值返回。
     */
    private final java.util.concurrent.atomic.AtomicBoolean metricsIndexRefreshing =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * 指标索引构建失败后的退避截止时间（毫秒）。
     *
     * <p>失败时既不能把可用索引覆盖成空表，也不能让后续每个请求都重查一次快照
     * （Redis 不可用时每次构建都要打两条全表 SQL，跨网络就是秒级）。</p>
     */
    private volatile long metricsIndexRetryAt = 0;

    /**
     * 指标索引 TTL（毫秒）：30 秒
     */
    private static final long METRICS_INDEX_TTL_MILLIS = 30_000;

    /**
     * 指标索引构建失败后的退避时长（毫秒）：3 秒
     */
    private static final long METRICS_INDEX_RETRY_BACKOFF_MILLIS = 3_000;

    /**
     * 本地指标库索引构建时间戳（毫秒）
     */
    private volatile long metricsLibraryLoadedAt = 0;

    /**
     * 本地指标库索引（归一化模型键 → 指标行）。
     *
     * <p>一次全量查询替代「每个模型查一次」：跨网络/慢库环境下逐模型查询会把
     * 一次列表请求放大成 N 次往返（实测远端 185 个模型耗时数分钟）。</p>
     */
    private volatile Map<String, SysAiModelMetrics> metricsLibraryIndex = Map.of();

    /**
     * 本地指标库索引重建标记（单飞）
     */
    private final java.util.concurrent.atomic.AtomicBoolean metricsLibraryRefreshing =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * 本地指标库索引构建失败后的退避截止时间（毫秒）
     */
    private volatile long metricsLibraryRetryAt = 0;

    /**
     * 本地指标库索引 TTL（毫秒）：60 秒
     */
    private static final long METRICS_LIBRARY_TTL_MILLIS = 60_000;

    /**
     * 本地指标库索引构建失败后的退避时长（毫秒）：5 秒
     */
    private static final long METRICS_LIBRARY_RETRY_BACKOFF_MILLIS = 5_000;

    /**
     * 价格指标键（每百万 token 美元 + 单任务成本）。
     *
     * <p>上游 {@code /models} 的 pricing 由
     * {@code HttpChatClientIndicator#parseModelMetrics} 归一成这些键；
     * 采集时同时写指标库与模型行 metrics，模型卡片才会显示价格与「免费」。</p>
     */
    private static final String[] PRICE_METRIC_KEYS = {
            "price_input_per_musd", "price_output_per_musd",
            "price_cache_hit_per_musd", "price_cache_write_per_musd",
            "cost_per_task",
    };

    /**
     * 需要合并进模型行 指标 的快照指标键。
     */
    private static final String[] METRIC_KEYS = {
            "price_input_per_musd", "price_output_per_musd",
            "price_cache_hit_per_musd", "price_cache_write_per_musd",
            "intelligence_index", "output_speed_tps", "latency_first_token_seconds",
            "probe_latency_ms", "icon_url", "reasoning_model", "web_search",
            "image_input", "function_calling", "streaming", "async", "context_window_tokens",
            "hle_score", "omniscience_index",
            "gpqa_score",
            "aime25_score",
            "livecodebench_score",
            "scicode_score",
            "ifbench_score",
            "critpt_score",
            "mmmu_pro_score",
            "tau2_score",
            "tau_banking_score",
            "lcr_score",
            "terminalbench_hard_score",
            "terminal_bench21_score",
            "terminal_bench40_score",
            "automation_bench_score",
            "gdpval_normalized_score",
            "briefcase_score",
            "harvey_lab_score",
            "apex_agents_score",
            "itbench_sre_score",
            "omniscience_accuracy",
            "omniscience_non_hallucination",
            "elo",
            "cost_per_task",
            "json_mode",
            "openai_compatible",
            "intelligence_index_estimated"
    };

    /**
     * 免费通道模型名后缀（如 deepseek-v4.1-flash-free）：指标库里存的是去掉后缀的
     * 上游模型名，精确键取不到时按此后缀回退一次。
     */
    private static final String FREE_MODEL_SUFFIX = "-free";

    /**
     * 首字延迟按秒采信的上限：指标库该列混有历史毫秒数据，超过该值一律丢弃。
     */
    private static final long TRUSTED_FIRST_TOKEN_MAX_SECONDS = 30L;

    /**
     * 余额自动回落时依次尝试的通用中转站实现（厂商官方与 OpenAI 官方由品牌/地址单独前置）。
     *
     * <p>只保留各站点普遍沿用的 OpenAI dashboard 计费接口（one-api / new-api / voapi 系通用）。
     * new-api 的 {@code /api/usage/token} 与 sub2api 的订阅接口不再无差别自动探测：
     * 自定义供应商先按站点指纹自动适配网关（见 {@link #relayCreditKinds}），认出后才把
     * 专属实现排到它前面；登记了平台名（platform=sub2api）或显式配置 credit_kind 的行不受此限。</p>
     *
     * <p>只在「上游不是厂商官方站点」时才进候选：官方站点上没有这个接口，
     * 拿官方 Key 去打只会 404。判定见 {@link #isOfficialSite(SysAccountChannel)}。</p>
     */
    private static final List<String> AUTO_CREDIT_KINDS = List.of("billing");

    /**
     * 有官方余额口径的厂商（Bearer 类），由 {@code HostCreditQueryProvider} 自述，
     * 避免两处清单各改各的。
     */
    private static final Set<String> CREDIT_HOST_BRANDS = HostCreditQueryProvider.bearerBrands();

    /**
     * 余额查询失败原因的回传长度上限（字符），避免多家错误拼成一大段。
     */
    private static final int CREDIT_ERROR_LIMIT = 400;

    /**
     * 自定义供应商的网关指纹缓存：站点根地址 → 认出的中转网关（空列表 = 未认出）。
     *
     * <p>指纹探测要发真实请求，结论按站点缓存一段时间，避免每次查余额都白探一轮；
     * 站点换软件（罕见）最迟一个有效期后自动纠正。</p>
     */
    private final ConcurrentHashMap<String, RelayFingerprint> relayFingerprints = new ConcurrentHashMap<>();

    /**
     * 网关指纹缓存有效期（毫秒）：认出与未认出都缓存，到期重探
     */
    private static final long RELAY_FINGERPRINT_TTL_MILLIS = 10L * 60 * 1000;

    /**
     * 网关指纹探测超时：比余额查询短，站点无响应时尽快放弃、回落通用探测
     */
    private static final Duration RELAY_PROBE_TIMEOUT = Duration.ofSeconds(6);

    /**
     * 站点根地址可能带的接口前缀（长前缀优先）
     */
    private static final String[] RELAY_API_PREFIXES = {"/api/usage", "/api/v1", "/v1", "/api"};

    // ==================== 提供者 ====================

    /**
     * 获取 提供者 凭证明文（仅限本地可信客户端调用）。
     *
     * @param providerId 提供者 标识
     * @return 凭证映射，包含密钥明文
     */
    @Permission("admin")
    @GetMapping("/providers/{provider_id}/credentials")
    public ApiResult<Map<String, Object>> getProviderCredentials(@PathVariable("provider_id") String providerId) {
        SysAccountChannel entity = requireProvider(providerId);
        Map<String, Object> credentials = entity.getCredentials();
        if (credentials == null) {
            credentials = new java.util.LinkedHashMap<>();
        }
        return ApiResult.ok(credentials);
    }

    /**
     * 列举全部 提供者（按 排序_订单 升序）。
     *
     * <p>ONNX 供应商与其余供应商同构（同样是 {@code sys_account_channel} 表里的一行），
     * 差别只有一条：仅管理员可见，非管理员的返回列表里不出现该条目。</p>
     *
     * <p>模型清单的口径由账号模块决定：缺省只带已开启的模型行 —— 会话选择、路由解析
     * 等消费方不用带参数，拿到的就是「对外供给的模型」；模型管理页要看到停用行才能
     * 重新开启，须显式传 {@code enabled_only=0} 取全量。
     * 本接口不做 enabled 判定，避免同一条规则在两处实现。</p>
     *
     * @param enabledOnly 是否只返回已开启的模型行（缺省 true）
     * @return 提供者响应列表
     */
    @GetMapping("/providers")
    public ApiResult<List<Map<String, Object>>> listProviders(
            @RequestParam(value = "enabled_only", required = false, defaultValue = "true") boolean enabledOnly) {
        boolean onnxVisible = com.chua.starter.ai.support.util.AiUserContext.isAdmin();
        List<SysAccountChannel> providers = accountChannelService.listAll();

        List<Map<String, Object>> items = new ArrayList<>(8); // 提供者数量不可预估
        for (SysAccountChannel p : providers) {
            if (isOnnxRow(p) && !onnxVisible) {
                continue;
            }
            // 渠道管理页为每个已注册渠道镀铬的 OAuth provider 行（auth_scheme='oauth'，无模型 / 无凭证），
            // 模型管理（供应商列表 / 模型面板）与渠道解耦，不暴露这些行。
            // 免登录渠道行（platform='free-channel'/'free'，或渠道管理物化的 chat-<渠道> 行）
            // 承载模型、由 FreeChatClient 免登录直连，保留在列表。
            if (isOAuthChannelRow(p) && !isFreeChannelRow(p)) {
                continue;
            }
            // 模型清单口径由账号模块决定：消费方（会话选择 / 路由）只要已开启的行，
            // 管理页保留全量，否则停用的模型在界面上消失后就再也打不开了。
            List<SysAccountChannelModel> models = enabledOnly
                    ? accountChannelModelService.listEnabled(p.getProviderId())
                    : accountChannelModelService.listAll(p.getProviderId());
            // 连接按账号隔离：provider 行是全局的，挂在下面的账号凭证不是。
            // 注：toProviderResponse 目前并未把 connections 回显到响应里（页面上的
            // 账号列表走 /api/channels），这里的过滤是防御性的 —— 一旦有人把连接
            // 加进响应，隔离条件已经在这里，不会裸奔。
            List<SysAccountChannelConnection> connections =
                    accountChannelConnectionService.list(p.getProviderId());
            Map<String, Object> item = toProviderResponse(p, models, connections);
            if (routerGroupService.isRouterGroup(p)) {
                // 路由组没有自己的模型行：成员全在模型库里。但会话的模型面板读的就是
                // /api/providers 里每行的 models，这里补一条虚拟模型，让「选中这个组」
                // 在会话里可选；它解析到的客户端是 RouterGroupChatClient（按组内策略选成员）。
                item.put("models", List.of(routerGroupVirtualModel(p)));
            }
            items.add(item);
        }

        // 注入已注册的智能体模型作为虚拟提供者
        List<String> registeredAgentModels = new ArrayList<>(chatClientRegistry.getRegisteredAgentModels());
        if (!registeredAgentModels.isEmpty()) {
            Map<String, Object> agentProvider = buildAgentProvider(registeredAgentModels);
            items.add(0, agentProvider);
        }

        return ApiResult.ok(items);
    }

    /**
     * 获取单个 提供者 详情。
     *
     * <p>ONNX 供应商仅管理员可见：非管理员按「不存在」处理，避免通过详情接口探到本地推理配置。</p>
     *
     * @param providerId 提供者 标识
     * @return 提供者详情
     */
    @GetMapping("/providers/{provider_id}")
    public ApiResult<Map<String, Object>> getProvider(@PathVariable("provider_id") String providerId) {
        SysAccountChannel entity = requireVisibleProvider(providerId);
        List<SysAccountChannelModel> models = accountChannelModelService.listAll(providerId);
        // 连接按账号隔离：详情页同样只能看到自己的账号（管理员另见 NULL 系统池）
        List<SysAccountChannelConnection> connections = accountChannelConnectionService.list(providerId);
        return ApiResult.ok(toProviderResponse(entity, models, connections));
    }

    /**
     * 创建 提供者（原子包含 initial_模型 与可选 connections）。
     *
     * @param body 创建请求
     * @return 创建后的 提供者响应
     */
    @Permission("admin")
    @PostMapping("/providers")
    @Transactional
    public ApiResult<Map<String, Object>> createProvider(@RequestBody Map<String, Object> body) {
        String platform = requireStr(body, "platform");
        String name = requireStr(body, "name");
        String providerId = str(body, "provider_id");
        // ONNX 行统一落成固定标识 'onnx'：本地模型目录回显、模型镜像同步、本地模型拉取
        // 兜底等多处都按该标识定位，生成时间戳 id 会让这些能力全部失配。
        if (isOnnxPlatform(platform)) {
            providerId = ONNX_PROVIDER_ID;
        }
        if (providerId == null || providerId.isBlank()) {
            providerId = String.valueOf(System.currentTimeMillis() + (int)(Math.random() * 1000));
        }
        if (accountChannelService.exists(providerId)) {
            throw new ApiException(ApiErrorCode.CONFLICT, "provider_id 已存在: " + providerId);
        }
        // 路由组供应商：模型来自「已添加的模型库」，没有上游地址可填，
        // 因此不校验同平台重名（同一平台可以建多个不同策略的路由组）。
        boolean allowDuplicateName = ProviderRouteTargets.isRouterGroup(platform);
        if (!allowDuplicateName) {
            // ONNX 只允许一行本地推理；其余供应商按「平台+名称」判重
            requireNotDuplicateProvider(platform, name, null);
        }
        long now = System.currentTimeMillis();

        // 创建提供者实体
        SysAccountChannel entity = new SysAccountChannel();
        entity.setProviderId(providerId);
        entity.setPlatform(platform);
        entity.setName(name);
        // 路由组没有真实上游：地址与鉴权方式留空占位，不做必填校验
        // （成员各自带着自己的地址与凭证，由 ModelClientResolver 按成员构造）。
        if (ProviderRouteTargets.isRouterGroup(platform)) {
            // 库列 NOT NULL：空串占位（不做「必填」校验，也不校验地址格式）
            String routerBaseUrl = str(body, "base_url");
            entity.setBaseUrl(routerBaseUrl == null ? "" : routerBaseUrl);
            entity.setAuthScheme(str(body, "auth_scheme") != null && !str(body, "auth_scheme").isBlank()
                ? str(body, "auth_scheme") : "none");
        } else if (isOnnxPlatform(platform)) {
            // ONNX 是本地推理：前端不采集地址，base_url 留空占位，由同步服务按
            // ai.onnx.base-url 回填；认证方式与其它提供商统一为 api_key。
            String onnxBaseUrl = str(body, "base_url");
            entity.setBaseUrl(onnxBaseUrl == null ? "" : onnxBaseUrl);
            String onnxAuth = str(body, "auth_scheme");
            entity.setAuthScheme(onnxAuth == null || onnxAuth.isBlank() ? "api_key" : onnxAuth);
        } else {
            String authScheme = str(body, "auth_scheme");
            // 免费渠道是渠道管理的统一入口：模型与上游地址/凭据都由渠道管理维护，
            // 本行只做路由聚合，地址允许留空（不再填 free.nomifun.com 之类的占位地址）。
            if (ProviderRouteTargets.isFreeChannel(platform)) {
                String freeBaseUrl = str(body, "base_url");
                entity.setBaseUrl(freeBaseUrl == null ? "" : freeBaseUrl);
                entity.setAuthScheme(authScheme == null || authScheme.isBlank() ? "none" : authScheme);
            } else if (authScheme != null && "none".equalsIgnoreCase(authScheme.trim())) {
                String keylessBaseUrl = str(body, "base_url");
                entity.setBaseUrl(keylessBaseUrl == null ? "" : keylessBaseUrl);
                entity.setAuthScheme("none");
            } else {
                entity.setBaseUrl(requireStr(body, "base_url"));
                entity.setAuthScheme(requireStr(body, "auth_scheme"));
            }
        }
        entity.setCredentials(uncheckedCast(body.get("credentials")));
        entity.setEnabled(bool(body, "enabled", true));
        entity.setBedrockConfig(uncheckedCast(body.get("bedrock_config")));
        entity.setProxy(str(body, "proxy"));
        entity.setExtra(uncheckedCast(body.get("extra")));
        entity.setSortOrder(intv(body, "sort_order", accountChannelService.nextSortOrder()));

        // 初始模型（可选，用户可以后续手动添加）
        Map<String, Object> initialModel = uncheckedCast(body.get("initial_model"));
        SysAccountChannelModel initialModelEntity = initialModel == null
                ? null : toModelEntity(providerId, initialModel, now);

        // 初始连接按连接角色保存
        List<SysAccountChannelConnection> connEntities = new ArrayList<>(4);
        for (Map<String, Object> conn : listOfMap(body, "connections")) {
            connEntities.add(toConnectionEntity(providerId, conn, now));
        }
        // 渠道、初始模型、初始连接的落库与判重统一由账号模块的渠道业务负责
        accountChannelService.create(entity, initialModelEntity, connEntities, allowDuplicateName);

        // ONNX 刚由用户添加：立刻把本地模型镜像与推理设备同步过来，
        // 否则页面上要等到下次启动才看得到模型（用户加完却是空列表）。
        if (isOnnxRow(entity)) {
            try {
                onnxProviderMaterializer.syncModels(providerId);
                onnxProviderMaterializer.syncProviderDevice(providerId);
            } catch (Exception e) {
                log.warn("[OnnxProvider] 新增后同步模型失败（不影响供应商行）: {}", e.getMessage());
            }
        }

        // 查询完整数据返回
        List<SysAccountChannelModel> models = accountChannelService.modelsOf(providerId);
        List<SysAccountChannelConnection> conns = accountChannelService.connectionsOf(providerId);
        return ApiResult.ok(toProviderResponse(entity, models, conns));
    }

    /**
     * 更新 提供者（凭证缺省时保留既有凭证）。
     *
     * @param providerId 提供者 标识
     * @param body       更新请求
     * @return 更新后的 提供者响应
     */
    @Permission("admin")
    @PutMapping("/providers/{provider_id}")
    @Transactional
    public ApiResult<Map<String, Object>> updateProvider(@PathVariable("provider_id") String providerId,
                                                         @RequestBody Map<String, Object> body) {
        SysAccountChannel entity = requireProvider(providerId);
        if (body.containsKey("name")) {
            entity.setName(str(body, "name"));
        }
        if (body.containsKey("base_url")) {
            entity.setBaseUrl(str(body, "base_url"));
        }
        if (body.containsKey("auth_scheme")) {
            entity.setAuthScheme(str(body, "auth_scheme"));
        }
        if (body.containsKey("credentials")) {
            Map<String, Object> credentials = uncheckedCast(body.get("credentials"));
            // 「删光 Key」时前端会显式提交 {api_key: ""}：把空白 api_key 摘掉再落库，
            // has_credentials / api_key_count 才不会误报「已配置」。注意不能把 credentials
            // 置 null —— updateById 默认跳过 null 字段，置 null 等于旧 Key 清不掉。
            if (credentials != null) {
                Object apiKey = credentials.get("api_key");
                if (apiKey != null && apiKey.toString().trim().isEmpty()) {
                    credentials.remove("api_key");
                }
            }
            entity.setCredentials(credentials == null ? new LinkedHashMap<>() : credentials);
        }
        if (body.containsKey("enabled")) {
            entity.setEnabled(bool(body, "enabled", true));
        }
        if (body.containsKey("bedrock_config")) {
            entity.setBedrockConfig(uncheckedCast(body.get("bedrock_config")));
        }
        if (body.containsKey("proxy")) {
            // 表单把代理做成「直连 / 自定义」两态：选直连时前端会显式提交空串。
            // str() 把空串归成 null，而 updateById 默认跳过 null 字段 —— 于是
            // 「改回直连」永远清不掉旧代理。这里把空串落成 '' 才能真的写入。
            String proxy = str(body, "proxy");
            entity.setProxy(proxy == null ? "" : proxy);
        }
        if (body.containsKey("extra")) {
            entity.setExtra(uncheckedCast(body.get("extra")));
        }
        if (body.containsKey("platform")) {
            entity.setPlatform(str(body, "platform"));
        }
        // 管理员可改接入协议：协议落在每个模型 capability 上，需同步到该供应商全部模型
        String protocol = body.containsKey("protocol") ? str(body, "protocol") : null;
        if (body.containsKey("sort_order")) {
            entity.setSortOrder(intv(body, "sort_order", entity.getSortOrder()));
        }
        // 编辑同样要守唯一性：否则可以把 A 改名为 B 的名字，绕过新增/克隆的判重造出重复项。
        // 排除自身（excludeProviderId = 当前被编辑的供应商），只拦与「别人」撞名的情况。
        boolean checkDuplicate = body.containsKey("name") || body.containsKey("platform");
        // 判重、协议整渠道同步、落库与事件广播统一由账号模块的渠道业务负责
        accountChannelService.save(entity, checkDuplicate, protocol);

        // ONNX 供应商的推理设备取自 provider.extra.device，更新后立即桥接到运行时，
        // 覆盖原模型参数接口的 device 读取，避免需重启才生效。
        refreshOnnxDevice(entity);

        List<SysAccountChannelModel> models = accountChannelService.modelsOf(providerId);
        List<SysAccountChannelConnection> conns = accountChannelService.connectionsOf(providerId);
        return ApiResult.ok(toProviderResponse(entity, models, conns));
    }

    /**
     * 删除 提供者（级联删除模型和连接）。
     *
     * <p>单条入口，复用 {@link #doBatchDeleteProviders(List)} 的整批拦截与清理逻辑。</p>
     *
     * @param providerId 提供者 标识
     * @return 成功
     */
    @Permission("admin")
    @DeleteMapping("/providers/{provider_id}")
    @Transactional
    public ApiResult<Void> deleteProvider(@PathVariable("provider_id") String providerId) {
        deleteProviderRows(providerId);
        return ApiResult.ok();
    }

    /**
     * 解析批量删除的供应商 id 列表，并在删除前拦截非法请求。
     *
     * <p>整批定位先于任何写操作：空 id、不存在的 id 都会直接拦截整批，
     * 不允许部分删除。ONNX 供应商由用户自己添加，因此和普通供应商一样可删
     * （删除后模型镜像与运行时设备覆盖一并清理）。</p>
     *
     * @param body 请求体，须含 {@code ids} 数组（provider_id 字符串）
     * @return 待删除的供应商 id 列表
     */
    private List<String> parseBatchProviderIds(Map<String, ?> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = body == null ? null : (List<String>) body.get("ids");
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "ids 不能为空");
        }
        // 空 id 在进入渠道业务前就拦掉；「是否存在」由账号模块的整批定位负责，
        // 任何一条不存在都会拦截整批，不出现部分删除。
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "id 不能为空");
            }
        }
        return ids;
    }

    /**
     * 批量删除 提供者 行（级联删除其模型与连接）。
     *
     * <p>供应商行与模型行不同，id 就是 {@code provider_id}（不带 {@code :} 模型名的复合标识）。
     * 整批鉴权与清理逻辑与 {@link #deleteProvider} 公用。</p>
     *
     * @param body 请求体，须含 {@code ids} 数组（provider_id 字符串）
     * @return 包含 success 与 deletedCount 的结果
     */
    @Permission("admin")
    @DeleteMapping("/providers/batch")
    @Transactional
    public ApiResult<Map<String, Object>> batchDeleteProviders(@RequestBody Map<String, ?> body) {
        List<String> ids = parseBatchProviderIds(body);
        int deleted = doBatchDeleteProviders(ids);
        Map<String, Object> result = new HashMap<>(2);
        result.put("success", true);
        result.put("deletedCount", deleted);
        return ApiResult.ok(result);
    }

    /**
     * 删除渠道及其 AI 侧连带数据。
     *
     * <p>渠道行、模型行、连接行由账号模块的渠道业务删除；本方法只处理 AI 自己的账：
     * 全局默认模型里指向已删渠道/模型的悬空取值、ONNX 推理设备覆盖。</p>
     *
     * @param providerId 渠道标识
     */
    private void deleteProviderRows(String providerId) {
        // 全局设置里的「默认模型」按 "providerId:model" 存值，
        // 必须在删模型行之前收集，否则无法判断哪些配置项指向了本渠道
        List<String> ownedModels = accountChannelService.modelsOf(providerId).stream()
                .map(SysAccountChannelModel::getModel)
                .filter(s -> s != null && !s.trim().isEmpty())
                .map(String::trim)
                .toList();
        SysAccountChannel target = requireProvider(providerId);
        accountChannelService.delete(List.of(providerId));
        clearGlobalSettingsReferencing(providerId, ownedModels);
        if (isOnnxRow(target)) {
            onnxProviderMaterializer.syncProviderDevice(providerId);
        }
    }

    /**
     * 整批删除渠道。
     *
     * @param ids 已定位的渠道标识列表
     * @return 删除的渠道数
     */
    private int doBatchDeleteProviders(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int deleted = 0;
        for (String providerId : ids) {
            if (!accountChannelService.exists(providerId)) {
                continue;
            }
            deleteProviderRows(providerId);
            deleted++;
        }
        return deleted;
    }

    /**
     * 清理全局设置中指向指定渠道的模型取值（{@code sys_ai_global_setting} 属于 AI 侧）。
     *
     * <p>「默认模型」类字段的取值形如 {@code providerId:model}。渠道被删除后若不同步清理，
     * 配置项会残留悬空引用：界面仍显示已不存在的模型，运行期解析失败且无提示。</p>
     *
     * @param providerId  被删除的渠道标识
     * @param ownedModels 该渠道下的模型标识
     * @return 清理条数
     */
    private int clearGlobalSettingsReferencing(String providerId, List<String> ownedModels) {
        if (providerId == null || providerId.trim().isEmpty()) {
            return 0;
        }
        int cleared = 0;
        String prefix = providerId + ":";
        for (SysAiGlobalSetting setting : globalSettingMapper.selectList(
                new LambdaQueryWrapper<SysAiGlobalSetting>()
                        .likeRight(SysAiGlobalSetting::getValue, prefix))) {
            if (setting.getValue() != null && setting.getValue().startsWith(prefix)) {
                cleared += globalSettingMapper.update(null, new LambdaUpdateWrapper<SysAiGlobalSetting>()
                        .eq(SysAiGlobalSetting::getId, setting.getId())
                        .set(SysAiGlobalSetting::getValue, ""));
            }
        }
        // 裸模型名：仅当该模型名未被其它渠道占用时才可安全清空，避免误伤同名模型
        for (String model : ownedModels) {
            if (model == null || model.trim().isEmpty() || model.contains(":")) {
                continue;
            }
            if (accountChannelModelService.countByModel(model) > 0) {
                continue;
            }
            cleared += globalSettingMapper.update(null, new LambdaUpdateWrapper<SysAiGlobalSetting>()
                    .eq(SysAiGlobalSetting::getValue, model)
                    .set(SysAiGlobalSetting::getValue, ""));
        }
        return cleared;
    }

    /**
     * 模型行删除后，把它从所有路由组成员表里摘掉。
     *
     * <p>路由组的成员就是模型库里的行；成员行被删后如果仍留在组里，那个成员会在
     * 每次路由时尝试构造客户端并失败。这里在同一个事务里一并清理，让「删模型」与
     * 「清成员」同进同退。</p>
     *
     * @param providerId 被删模型所属渠道
     * @param model      被删模型
     */
    private void pruneRouterGroupMembers(String providerId, String model) {
        try {
            List<SysAccountChannel> all = accountChannelService.listAll().stream()
                    .filter(routerGroupService::isRouterGroup)
                    .toList();
            for (com.chua.starter.ai.support.chat.RouterGroupService.PrunedGroup pruned
                    : routerGroupService.pruneMemberDeletions(all, providerId, model)) {
                // 渠道行的写入仍走账号模块业务，AI 只提供「新的路由组配置」这份内容
                SysAccountChannel update = accountChannelService.require(pruned.providerId());
                update.setExtra(pruned.extra());
                accountChannelService.save(update, false, null);
                modelClientResolver.invalidateRouterGroup(pruned.providerId());
            }
        } catch (Exception e) {
            log.warn("清理路由组成员失败 {}:{}: {}", providerId, model, e.getMessage());
        }
    }

    /**
     * 克隆 提供者（连同其模型能力与连接一并复制）。
     *
     * @param providerId 提供者 标识
     * @param body       可选 { 名称 }
     * @return 克隆后的 提供者响应
     */
    @Permission("admin")
    @PostMapping("/providers/{provider_id}/clone")
    @Transactional
    public ApiResult<Map<String, Object>> cloneProvider(@PathVariable("provider_id") String providerId,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        SysAccountChannel source = requireProvider(providerId);
        String cloneName = body != null && body.get("name") != null
                ? str(body, "name") : source.getName() + " 副本";
        // 克隆同样受唯一性约束：ONNX 不可克隆出第二份，其余供应商不能与既有条目重名
        requireNotDuplicateProvider(source.getPlatform(), cloneName, source.getProviderId());
        // 深拷贝渠道行、模型清单与当前账号可见的连接，统一由账号模块的渠道业务完成
        SysAccountChannel clone = accountChannelService.clone(providerId, cloneName);
        List<SysAccountChannelModel> models = accountChannelService.modelsOf(clone.getProviderId());
        List<SysAccountChannelConnection> conns = accountChannelService.connectionsOf(clone.getProviderId());
        return ApiResult.ok(toProviderResponse(clone, models, conns));
    }

    /**
     * 拉取 提供者 远端模型清单（需 提供者 已存在）。
     *
     * <p>使用 Provider 持久化的 base_url 与凭证真实调用上游 {@code GET {base_url}/models}
     * 接口，返回实时模型目录；不使用任何写死的兜底数据。</p>
     *
     * @param providerId 提供者 标识
     * @param body       可选 { 尝试_fix }
     * @return 上游模型列表响应
     */
    @Permission("admin")
    @PostMapping("/providers/{provider_id}/models")
    public ApiResult<Map<String, Object>> fetchProviderModels(@PathVariable("provider_id") String providerId,
                                                              @RequestBody(required = false) Map<String, Object> body) {
        SysAccountChannel entity = requireProvider(providerId);
        boolean tryFix = body != null && Boolean.TRUE.equals(body.get("try_fix"));
        return ApiResult.ok(fetchModelsFromUpstream(entity.getPlatform(), entity.getBaseUrl(),
                entity.getCredentials(), UpstreamAuth.of(entity.getAuthScheme(), entity.getExtra()),
                entity.getProxy(), tryFix, entity.getExtra(), entity.getProviderId()));
    }

    /**
     * 一键拉取并**批量录入** 提供者 的全部远端模型。
     *
     * <p>「添加模型」要的是一次到位：用 提供者 已保存的地址与凭证真实调用上游
     * {@code GET {base_url}/models}，把返回的模型目录整批写进
     * {@code sys_account_channel_model}，用户不必再在弹窗里逐条点「选择」。</p>
     *
     * <p><b>幂等</b>：模型名已存在的直接跳过，**不覆盖**用户改过的别名/参数，
     * 所以重复点击只会补录新出现的模型。缺模型名的上游条目计入 skipped 而非报错
     * ——一个坏条目不能让整批失败。</p>
     *
     * <p><b>元数据同步</b>：上游 {@code /models} 真实声明的上下文窗口、输出上限、
     * family、能力开关、模态会随录入一起落库；已存在的模型行也会补空列（不改别名、
     * 启用状态与排序），因此重复点击同时起到「刷新元数据」的作用，
     * 计数见响应里的 {@code enriched}。</p>
     *
     * @param providerId 提供者 标识
     * @param body       可选 { try_fix, models }（try_fix: 404 时补全 /v1 重试；models: 只录入勾选的模型名，空 = 全部）
     * @return { total, imported, skipped, enriched, models }
     */
    @SuppressWarnings("unchecked")
    @Permission("admin")
    @PostMapping("/providers/{provider_id}/models/import-all")
    @Transactional
    public ApiResult<Map<String, Object>> importAllProviderModels(@PathVariable("provider_id") String providerId,
                                                                  @RequestBody(required = false) Map<String, Object> body) {
        SysAccountChannel entity = requireProvider(providerId);
        boolean tryFix = body != null && Boolean.TRUE.equals(body.get("try_fix"));
        Map<String, Object> upstream = fetchModelsFromUpstream(entity.getPlatform(), entity.getBaseUrl(),
                entity.getCredentials(), UpstreamAuth.of(entity.getAuthScheme(), entity.getExtra()),
                entity.getProxy(), tryFix, entity.getExtra(), entity.getProviderId());
        Object rawModels = upstream.get("models");
        List<Map<String, Object>> upstreamModels = rawModels instanceof List<?> list ? (List<Map<String, Object>>) list
                : new ArrayList<>(0);
        // 只录入用户勾选的模型：body.models 为空表示「全部录入」（保持原语义）。
        // 放在落库前过滤，仍复用同一套元数据回填 / 去重逻辑。
        if (body != null && body.get("models") instanceof List<?> only) {
            Set<String> wanted = new HashSet<>(16);
            for (Object item : only) {
                if (item != null) {
                    wanted.add(String.valueOf(item));
                }
            }
            if (!wanted.isEmpty()) {
                List<Map<String, Object>> filtered = new ArrayList<>(wanted.size());
                for (Map<String, Object> m : upstreamModels) {
                    if (wanted.contains(batchModelName(m))) {
                        filtered.add(m);
                    }
                }
                upstreamModels = filtered;
            }
        }

        // 已存在的模型名一次取齐：既是幂等判据，也省掉每条一次 select；
        // 顺带算出最大排序号，新模型排在既有行之后
        Set<String> seen = new HashSet<>(16);
        Map<String, SysAccountChannelModel> existing = new HashMap<>(16);
        int maxSort = -1;
        for (SysAccountChannelModel row : accountChannelModelService.listAll(providerId)) {
            if (row.getModel() != null) {
                existing.put(row.getModel(), row);
            }
            if (row.getSortOrder() != null && row.getSortOrder() > maxSort) {
                maxSort = row.getSortOrder();
            }
        }
        // 按上游下标直接落库会和既有行撞出并列序号（并列时显示顺序不稳定），
        // 统一平移到既有行之后
        int sortBase = maxSort + 1;

        long now = System.currentTimeMillis();
        int imported;
        int skipped = 0;
        int enriched = 0;
        List<Map<String, Object>> importedRows = new ArrayList<>(8);
        List<SysAccountChannelModel> candidates = new ArrayList<>(8);
        for (int i = 0; i < upstreamModels.size(); i++) {
            Map<String, Object> m = upstreamModels.get(i);
            String name = batchModelName(m);
            // 上游缺模型名的坏条目计入 skipped
            if (name == null) {
                skipped++;
                continue;
            }
            SysAccountChannelModel exist = existing.get(name);
            if (exist != null) {
                // 已存在：不覆盖用户配置，只补上游真实元数据（仅空列 + metrics 合并）
                if (mergeUpstreamMetadata(entity, exist, m, now)) {
                    enriched++;
                }
                skipped++;
                continue;
            }
            // Set#add：上游内部重名也跳过，避免同批插入两条
            if (!seen.add(name)) {
                skipped++;
                continue;
            }
            SysAccountChannelModel row = toModelEntity(providerId, batchModelInput(m, name, sortBase + i), now);
            enrichFromMetrics(entity, row);
            // 落库交给账号模块的模型业务：幂等补录 + 统一追排序号
            candidates.add(row);
        }
        imported = accountChannelModelService.createMissing(providerId, candidates);
        for (SysAccountChannelModel row : candidates) {
            importedRows.add(toProviderModelResponse(entity, row));
        }

        Map<String, Object> out = new LinkedHashMap<>(8);
        out.put("total", upstreamModels.size());
        out.put("imported", imported);
        out.put("skipped", skipped);
        out.put("enriched", enriched);
        out.put("models", importedRows);
        // 批量录入改变的是「可路由模型集合」：索引里 5 分钟路由缓存必须立刻失效，
        // 否则刚拉进来的模型在免费渠道里要等一个 TTL 才出现。
        invalidateRouteCache("provider-models-imported");
        return ApiResult.ok(out);
    }

    /**
     * 用上游 {@code /models} 条目回填已存在模型行的元数据。
     *
     * <p>只走「空列回填」语义（{@link SysAiModelMetricsService#enrichRowFromMetrics}）：
     * 别名、启用状态、排序、手填的上下文窗口 / 输出上限与能力开关一律保留；
     * 上游未声明的键也不会写成空。无实际变化时不产生一次「全表写」。</p>
     *
     * @param provider 服务商行（用于记录来源地址）
     * @param row      已存在的模型行（就地修改并在有变化时落库）
     * @param upstream 上游目录条目
     * @param now      本次采集时间
     * @return 是否写入了新元数据
     */
    @SuppressWarnings("unchecked")
    private boolean mergeUpstreamMetadata(SysAccountChannel provider, SysAccountChannelModel row,
                                          Map<String, Object> upstream, long now) {
        Object rawMetrics = upstream.get("metrics");
        if (!(rawMetrics instanceof Map<?, ?>)) {
            return false;
        }
        Map<String, Object> metrics = (Map<String, Object>) rawMetrics;
        if (metrics.isEmpty()) {
            return false;
        }
        Map<String, Object> beforeMetrics = row.getMetrics() == null
                ? new LinkedHashMap<>(8)
                : new LinkedHashMap<>(row.getMetrics());
        beforeMetrics.remove("metrics_captured_at");
        Map<String, Object> beforeColumns = entityColumns(row);
        Map<String, Object> current = row.getMetrics() == null
                ? new LinkedHashMap<>(24)
                : new LinkedHashMap<>(row.getMetrics());
        sysAiModelMetricsService.enrichRowFromMetrics(row, current, metrics,
                "upstream:/models", provider.getBaseUrl(), now);
        // 上游 /models 自带 pricing 时也要落到行 metrics：模型卡片的「价格 / 免费」
        // 读的是行 metrics，只写指标库的话卡片上仍然看不到价格。
        // 只补行里还没有的价，已有价格（官方指标同步写入的）优先。
        for (String priceKey : PRICE_METRIC_KEYS) {
            Object price = metrics.get(priceKey);
            if (price != null && current.get(priceKey) == null) {
                current.put(priceKey, price);
            }
        }
        Map<String, Object> afterMetrics = new LinkedHashMap<>(current);
        afterMetrics.remove("metrics_captured_at");
        // 采集时间每次都会变，不参与「是否有变化」判断，否则每次点击都全表写
        if (afterMetrics.equals(beforeMetrics) && entityColumns(row).equals(beforeColumns)) {
            return false;
        }
        row.setUpdatedAt(now);
        // 模型行的写入统一走账号模块的模型业务（复合主键局部更新）
        accountChannelModelService.updateByKey(row);
        return true;
    }

    /**
     * 快照 {@link SysAiModelMetricsService#enrichRowFromMetrics} 会回填的实体列。
     *
     * <p>用于判断一次合并是否真的带来新值，避免「元数据没变也写库」。</p>
     *
     * @param row 模型行
     * @return 列名 → 值
     */
    private static Map<String, Object> entityColumns(SysAccountChannelModel row) {
        Map<String, Object> columns = new LinkedHashMap<>(16);
        columns.put("context_window", row.getContextWindow());
        columns.put("max_tokens", row.getMaxTokens());
        columns.put("family", row.getFamily());
        columns.put("knowledge", row.getKnowledge());
        columns.put("release_date", row.getReleaseDate());
        columns.put("last_updated", row.getLastUpdated());
        columns.put("open_weights", row.getOpenWeights());
        columns.put("attachment", row.getAttachment());
        columns.put("reasoning", row.getReasoning());
        columns.put("tool_call", row.getToolCall());
        columns.put("temperature_supported", row.getTemperatureSupported());
        columns.put("modalities", row.getModalities());
        return columns;
    }

    /**
     * 按模型名从服务商指标端点拉取指标快照，供模型新增/编辑页「自动获取」按钮回填表单。
     *
     * <p>返回可直接回填的字段集（Models.dev 等 indicator 的标准键，口径与价格同步一致）：</p>
     * <ul>
     *   <li>实体列键：family / knowledge / release_date / last_updated / modalities /
     *       open_weights / attachment / reasoning / tool_call / temperature_supported</li>
     *   <li>思考档位：reasoning_efforts（Models.dev reasoning_options 解析，如
     *       claude-opus-5 → [low,medium,high,xhigh,max]）</li>
     *   <li>常规：context_window_tokens / max_output_tokens / 价格</li>
     * </ul>
     *
     * <p>未收录该模型时 {@code found=false}，前端提示「该模型未被指标源收录」并保持表单不动。</p>
     *
     * @param providerId 提供者标识
     * @param model      模型名（query 参数）
     * @return 指标快照
     */
    @Permission("admin")
    @GetMapping("/providers/{provider_id}/models/metrics")
    public ApiResult<Map<String, Object>> recommendModelMetrics(
            @PathVariable("provider_id") String providerId,
            @RequestParam(value = "model", required = false) String model) {
        SysAccountChannel entity = requireProvider(providerId);
        Map<String, Object> out = new LinkedHashMap<>(8);
        if (model == null || model.isBlank()) {
            out.put("found", false);
            out.put("message", "模型名不能为空");
            return ApiResult.ok(out);
        }
        Map<String, Object> hit = findIndicatorMetrics(entity, model.trim());
        if (hit == null || hit.isEmpty()) {
            out.put("found", false);
            out.put("message", "该模型未被指标源收录，可手动填写配置");
            return ApiResult.ok(out);
        }
        out.put("found", true);
        out.put("model", model.trim());
        // 只回传前端表单能消费的字段，避免整包指标泄漏
        for (String key : new String[]{
                "family", "knowledge", "release_date", "last_updated",
                "modalities", "open_weights", "attachment", "reasoning",
                "tool_call", "temperature_supported",
        }) {
            String indicatorKey = switch (key) {
                case "knowledge" -> "knowledge_cutoff";
                case "family" -> "model_family";
                case "tool_call" -> "function_calling";
                case "temperature_supported" -> "temperature";
                default -> key;
            };
            Object value = hit.get(indicatorKey);
            if (value != null) {
                out.put(key, value);
            }
        }
        out.put("reasoning_efforts", hit.get("reasoning_efforts"));
        out.put("context_window_tokens", hit.get("context_window_tokens"));
        out.put("max_output_tokens", hit.get("max_output_tokens"));
        out.put("price_input_per_musd", hit.get("price_input_per_musd"));
        out.put("price_output_per_musd", hit.get("price_output_per_musd"));
        return ApiResult.ok(out);
    }

    /**
     * 从服务商官方指标源同步价格 / 能力指标。
     *
     * <p>来源解析统一委托给
     * {@link com.chua.common.support.ai.chat.indicator.ChatClientIndicators}：
     * 优先使用服务商 {@code extra.metrics_url} 配置的端点；否则按平台路由到
     * OpenRouter / Pollinations / Vercel AI Gateway 官方目录或 Models.dev 聚合
     * （见各厂商的 {@code ChatClientIndicator} 实现）。同步结果同时写入
     * {@code sys_ai_model_metrics}（计费取价）与该服务商的
     * {@code sys_account_channel_model.metrics}（前端模型卡片立即刷新）。</p>
     *
     * @param providerId 提供者标识
     * @return 同步统计；无可用来源时 {@code supported=false}
     */
    @Permission("admin")
    @PostMapping("/providers/{provider_id}/models/sync-pricing")
    @Transactional
    public ApiResult<Map<String, Object>> syncPricing(@PathVariable("provider_id") String providerId) {
        SysAccountChannel entity = requireProvider(providerId);
        ChatClientIndicator indicator = resolveIndicator(entity);
        if (indicator == null) {
            log.info("[SyncPricing] provider={} 无可用指标端点，跳过", providerId);
            Map<String, Object> empty = new LinkedHashMap<>(8);
            empty.put("supported", false);
            empty.put("updated", 0);
            empty.put("inserted", 0);
            empty.put("provider_models", 0);
            empty.put("source", null);
            empty.put("source_url", null);
            empty.put("pricing_url", ChatClientIndicators.officialPricingUrl(entity.getPlatform()));
            empty.put("models", 0);
            return ApiResult.ok(empty);
        }
        int modelsCount = indicator.modelMetrics() == null ? 0 : indicator.modelMetrics().size();
        SysAiModelMetricsService.SyncResult result =
                sysAiModelMetricsService.syncFromIndicator(indicator, providerId);
        Map<String, Object> out = new LinkedHashMap<>(12);
        out.put("supported", true);
        out.put("updated", result.getUpdated());
        out.put("inserted", result.getInserted());
        out.put("provider_models", result.getProviderModels());
        out.put("source", indicator.source());
        out.put("source_url", indicator.sourceUrl());
        out.put("pricing_url", ChatClientIndicators.officialPricingUrl(entity.getPlatform()));
        out.put("models", modelsCount);
        out.put("captured_at", indicator.capturedAt());
        return ApiResult.ok(out);
    }

    /**
     * 按服务商平台解析对应的 {@link ChatClientIndicator}。
     *
     * <p>使用统一的
     * {@link com.chua.common.support.ai.chat.indicator.ChatClientIndicators} 解析，
     * 与 {@code ChatClient.indicator()} 走同一套映射，避免前后端登记不一致。</p>
     *
     * @param entity 供应商行
     * @return indicator 实现或 空
     */
    private ChatClientIndicator resolveIndicator(SysAccountChannel entity) {
        ChatClientSetting setting = buildSettingForIndicator(entity);
        ChatClientIndicator indicator = ChatClientIndicators.of(setting);
        if (indicator != null) {
            return indicator;
        }
        String metricsUrl = extraString(entity.getExtra(), "metrics_url");
        if (com.chua.common.support.utils.StringUtils.isNotBlank(metricsUrl)) {
            return new HttpChatClientIndicator(setting);
        }
        return null;
    }

    /**
     * 组装供 indicator 使用的最小 {@link ChatClientSetting}。
     * @param entity 参数
     * @return 返回值
    */
    private ChatClientSetting buildSettingForIndicator(SysAccountChannel entity) {
        Map<String, String> extraHeaders = extractExtraHeaders(entity.getExtra());
        String platform = entity.getPlatform();
        if (platform == null || platform.isBlank()) {
            platform = entity.getProviderId();
        }
        return ChatClientSetting.builder()
                .provider(platform)
                .appKey(firstApiKey(entity))
                .baseUrl(entity.getBaseUrl())
                .extraHeaders(extraHeaders)
                .authScheme(entity.getAuthScheme())
                .authHeader(extraString(entity.getExtra(), "auth_header"))
                .authPrefix(extraString(entity.getExtra(), "auth_prefix"))
                .metricsUrl(extraString(entity.getExtra(), "metrics_url"))
                .proxy(entity.getProxy())
                .build();
    }

    /**
     * 取服务商自身凭证中的第一个 API Key（无则返回 {@code null}）。 <p>复用 {@link #extractApiKey(Map)} 的既有逻辑。</p>
     * @param entity 参数
     * @return 返回值
    */
    private String firstApiKey(SysAccountChannel entity) {
        return extractApiKey(entity.getCredentials());
    }

    /**
     * 从 extra 中读取字符串值（extra 为 {@code null} 或键不存在时返回 {@code null}）。
     * @param Map<String 参数
     * @param extra 参数
     * @param key 字符串
     * @return 返回值
    */
    private static String extraString(Map<String, Object> extra, String key) {
        if (extra == null) {
            return null;
        }
        Object value = extra.get(key);
        return value == null ? null : value.toString();
    }

    /**
    /**
     * 从 extra 的 {@code custom_headers} 字段提取附加请求头 Map；无则返回 {@code null}。
     * @param Map<String 参数
     * @param extra 参数
     * @return 返回值
    */
    private static Map<String, String> extractExtraHeaders(Map<String, Object> extra) {
        if (extra == null) {
            return null;
        }
        Object raw = extra.get("custom_headers");
        if (raw instanceof Map<?, ?> m) {
            Map<String, String> result = new LinkedHashMap<>((Map<String, String>) m);
            return result.isEmpty() ? null : result;
        }
        return null;
    }

    /**
     * 取上游模型条目的模型名（{@code name} 优先，缺 name 回退 {@code id}）。
     *
     * @param m 上游条目
     * @return 模型名；条目缺失或两个字段都为空返回 null
     */
    private String batchModelName(Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        if (m.get("name") instanceof String s && !s.isBlank()) {
            return s;
        }
        if (m.get("id") instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    /**
     * 上游条目 → 「保存提供者模型」输入（{@link #toModelEntity} 认识的形态）。
     *
     * <p>上游 OpenAI 兼容接口只给 {@code {id,name,tasks,traits}}。这里把它映射成
     * {@code capabilities[].task / traits} 结构，前端的模型类型标签与能力图标才能与
     * 单条保存路径一致 —— 不落 capabilities 的话批量录入出来的卡片会全退化成
     * 「文本」类型，图像/视频模型在模型选择器里也会被分错组。</p>
     *
     * <p>上游目录携带的元数据（上下文窗口、输出上限、family、能力开关、模态）也原样
     * 带过去，{@link #toModelEntity} 直接落实体列；<b>上游没给的键不写入</b>，
     * 保持「未指定」，由指标库快照或用户手填补上。</p>
     *
     * @param m     上游条目
     * @param name  模型名（已判空）
     * @param sortOrder 排序号（既有行之后的序号，保证不与已有行并列）
     * @return 模型输入
     */
    private Map<String, Object> batchModelInput(Map<String, Object> m, String name, int sortOrder) {
        Map<String, Object> input = new LinkedHashMap<>(24);
        input.put("model", name);
        // 开关取自上游行，不预设常量：上游没声明就按「关闭」入库，由管理员在模型管理里开启。
        // 之前写死 true 会把「导入」变成「整批启用」，模型数直接虚高。
        input.put("enabled", bool(m, "enabled", false));
        input.put("sort_order", sortOrder);
        if (m.get("description") instanceof String s && !s.isBlank()) {
            input.put("description", s);
        }
        if (m.get("metrics") instanceof Map<?, ?>) {
            input.put("metrics", m.get("metrics"));
        }
        // 上游目录直接给出的实体列元数据：与前端「保存模型」同一套字段名
        for (String key : new String[]{"context_window", "max_tokens", "family", "knowledge",
                "release_date", "last_updated", "modalities", "open_weights", "attachment",
                "reasoning", "tool_call", "temperature_supported"}) {
            Object value = m.get(key);
            if (value != null) {
                input.put(key, value);
            }
        }

        List<String> traits = new ArrayList<>(4);
        if (m.get("traits") instanceof List<?> list) {
            for (Object t : list) {
                if (t instanceof String s && !s.isBlank()) {
                    traits.add(s);
                }
            }
        }
        List<Map<String, Object>> caps = new ArrayList<>(2);
        if (m.get("tasks") instanceof List<?> tasks) {
            for (Object t : tasks) {
                if (t instanceof String s && !s.isBlank()) {
                    Map<String, Object> cap = new LinkedHashMap<>(4);
                    cap.put("task", s);
                    cap.put("traits", traits);
                    // 上游条目如果有细分类型（capabilities[].subtype），批量录入时原样保留
                    String subType = str(m, "subtype");
                    if (subType == null && m.get("capabilities") instanceof List<?> rawCaps) {
                        for (Object rc : rawCaps) {
                            if (rc instanceof Map<?, ?> rm && rm.get("subtype") instanceof String sub && !sub.isBlank()) {
                                subType = sub;
                                break;
                            }
                        }
                    }
                    if (subType != null) {
                        cap.put("subtype", subType);
                    }
                    caps.add(cap);
                }
            }
        }
        if (!caps.isEmpty()) {
            input.put("capabilities", caps);
        }
        return input;
    }

    /**
     * 从提供商远程获取模型列表（GET接口，兼容前端调用）。
     *
     * <p>前端guada版本调用的是 GET /providers/{id}/remote_models，
     * 这里提供兼容的GET接口，内部调用同样的逻辑，
     * 并将返回格式转换成前端期望的 items[].modelName 格式。</p>
     *
     * @param providerId 提供者 标识
     * @return 上游模型列表响应
     */
    @Permission("admin")
    @GetMapping("/providers/{provider_id}/remote_models")
    public ApiResult<?> fetchRemoteModels(@PathVariable("provider_id") String providerId) {
        try {
            // ONNX 行由用户自己添加；万一库中没有该行（已删除 / 标识未落成 onnx），
            // 本地模型仍可直接从模型门面取，避免 requireProvider() 报「provider 不存在」
            if (isVirtualLocalProvider(providerId)) {
                Map<String, Object> local = fetchLocalModelsFromFacade();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> models = (List<Map<String, Object>>) local.get("models");
                List<Map<String, Object>> items = new ArrayList<>();
                if (models != null) {
                    for (Map<String, Object> model : models) {
                        Map<String, Object> item = new LinkedHashMap<>(model);
                        item.put("modelName", model.get("name") != null ? model.get("name") : model.get("id"));
                        items.add(item);
                    }
                }
                // 与云端分支保持一致：同时给出 models 与 items[].modelName
                local.put("items", items);
                return ApiResult.ok(local);
            }
            SysAccountChannel entity = requireProvider(providerId);
            Map<String, Object> upstreamResult = fetchModelsFromUpstream(entity.getPlatform(), entity.getBaseUrl(),
                    entity.getCredentials(), UpstreamAuth.of(entity.getAuthScheme(), entity.getExtra()),
                    entity.getProxy(), false, entity.getExtra(), entity.getProviderId());

            // 转换格式：data.models[].name → data.items[].modelName
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> models = (List<Map<String, Object>>) upstreamResult.get("models");
            List<Map<String, Object>> items = new ArrayList<>();
            if (models != null) {
                for (Map<String, Object> m : models) {
                    Map<String, Object> item = new LinkedHashMap<>(m);
                    item.put("modelName", m.get("name") != null ? m.get("name") : m.get("id"));
                    // 价格 / 能力已随 metrics 解析出来：这里补挂到行顶层，
                    // 让「从提供商获取」列表能直接标注价格与「免费」，不必再钻 metrics
                    applyUpstreamMetrics(item, uncheckedCast(m.get("metrics")));
                    items.add(item);
                }
            }
            upstreamResult.put("items", items);
            return ApiResult.ok(upstreamResult);
        } catch (ApiException e) {
            // 上游供应商的 Key/网络错误属于业务错误：以 200 + 错误信封返回，
            // 避免被前端误判为「本应用会话过期」而强制登出。
            ApiErrorCode code = e.getErrorCode();
            // 认证/限流是用户可操作、需要明确告知的业务状态，保留错误信封；
            // 「连不上 / 地址没配 / 超时」这类拿不到目录的情况按用户要求直接回空列表，
            // 不再把 null 或网络异常堆到界面上。
            if (code == ApiErrorCode.UNAUTHORIZED || code == ApiErrorCode.FORBIDDEN
                    || code == ApiErrorCode.RATE_LIMITED) {
                return ApiResult.error(code.getCode(), e.getMessage());
            }
            Map<String, Object> empty = new LinkedHashMap<>(4);
            empty.put("models", new ArrayList<>());
            empty.put("items", new ArrayList<>());
            empty.put("hint", "未能从供应商获取模型列表，请检查 API 地址与网络连接");
            return ApiResult.ok(empty);
        }
    }

    /**
     * 匿名预拉取模型清单（创建前预览，不需要 提供者 行）。
     *
     * <p>直接使用请求体中的 base_url 与凭证真实调用上游 {@code GET {base_url}/models}
     * 接口，用于「添加模型」弹窗在下拉框中实时预览供应商可用模型。</p>
     *
     * @param body 获取模型anonymous请求
     * @return 上游模型列表响应
     */
    @Permission("admin")
    @PostMapping("/providers/fetch-models")
    public ApiResult<Map<String, Object>> fetchModelsAnonymous(@RequestBody Map<String, Object> body) {
        String platform = requireStr(body, "platform");
        // 在线神经网络推理模型的基础地址保存模型文件路径，创建前预览阶段允许为空
        String baseUrl = str(body, "base_url");
        if ((baseUrl == null || baseUrl.isBlank()) && !(platform != null && platform.toLowerCase().contains("onnx"))) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "base_url 不能为空");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> credentials = body.get("credentials") instanceof Map
                ? (Map<String, Object>) body.get("credentials") : null;
        String proxy = str(body, "proxy");
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyExtra = body.get("extra") instanceof Map
                ? (Map<String, Object>) body.get("extra") : null;
        return ApiResult.ok(fetchModelsFromUpstream(platform, baseUrl, credentials,
                UpstreamAuth.ofBody(body), proxy, false, bodyExtra, null));
    }

    /**
     * 逐个测试 提供者 凭证中的每个 API Key（多 Key 有效标记）。
     *
     * <p>对 {@code credentials.api_keys} 中的每个 key 单独调用上游
     * {@code GET {base_url}/models}，返回每个 key 的连通性结果
     * （{@code ok} / {@code error}），供前端标记「哪个 Key 有效、哪个无效」。
     * 只读，不修改数据库。</p>
     *
     * @param providerId 提供者 标识
     * @return 每个 Key 的测试结果列表 + 汇总
     */
    @Permission("admin")
    @PostMapping("/providers/{provider_id}/test-keys")
    public ApiResult<Map<String, Object>> testProviderKeys(@PathVariable("provider_id") String providerId) {
        SysAccountChannel entity = requireProvider(providerId);
        Map<String, Object> credentials = entity.getCredentials();
        List<String> keys = extractAllKeys(credentials);
        List<Map<String, Object>> results = new ArrayList<>(8); // 密钥数量不可预估
        int okCount = 0;
        for (int i = 0; i < keys.size(); i++) {
            Map<String, Object> r = testSingleKey(entity.getPlatform(), entity.getBaseUrl(),
                    keys.get(i), UpstreamAuth.of(entity.getAuthScheme(), entity.getExtra()), entity.getProxy(), i);
            if (Boolean.TRUE.equals(r.get("ok"))) {
                okCount++;
            }
            results.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("provider_id", providerId);
        out.put("total", keys.size());
        out.put("ok_count", okCount);
        out.put("results", results);
        return ApiResult.ok(out);
    }

    /**
     * 余额读数缓存时长：面板每次打开都会拉一次，逐 Key 出网不便宜，5 分钟内的重复打开直接回缓存
     */
    private static final long CREDIT_CACHE_TTL_MS = 5L * 60L * 1000L;

    /**
     * 缓存条数上限：超过先清过期，仍超就整体清空（供应商数量有限，正常到不了）
     */
    private static final int CREDIT_CACHE_LIMIT = 256;

    /**
     * 余额读数缓存：指纹 → 快照
     */
    private static final Map<String, CreditSnapshot> CREDIT_CACHE = new ConcurrentHashMap<>();

    /**
     * 余额读数的一段明细：多账户 / 多币种子项（label + 金额 + 币种）。
     *
     * @param label    明细名（如「赠送余额」「充值余额」）
     * @param amount   金额
     * @param currency 币种
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CreditPart(String label, BigDecimal amount, String currency) {
    }

    /**
     * 单个 Key 的余额读数（{@code /credit} 的 {@code keys} 行）。
     *
     * <p>失败的行只有 index / mask / ok / error，其余字段为 null 且不输出
     * （{@code NON_NULL} 复刻旧 Map 版「失败行不含 null 键」的 JSON 形态）。</p>
     *
     * @param index    Key 序号
     * @param mask     脱敏 Key
     * @param ok       是否查到读数
     * @param source   命中的实现标识（如 host / openai / moss / sensenova）
     * @param remain   剩余
     * @param used     已用
     * @param total    总量（remain + used）
     * @param currency 币种
     * @param parts    多段明细
     * @param error    失败原因（成功行为 null）
     * @param extra    供应商专属结构化数据（按 source 区分形态，如 sensenova 的
     *                 plan / pools 令牌窗口明细），前端据此按供应商渲染不同余额视图；
     *                 未提供的实现不输出该键
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CreditReading(int index, String mask, boolean ok, String source,
                         BigDecimal remain, BigDecimal used, BigDecimal total,
                         String currency, List<CreditPart> parts, String error,
                         Map<String, Object> extra) {
    }

    /**
     * 汇总余额：同币种各 Key 之和。
     *
     * @param remain 剩余之和
     * @param used   已用之和
     * @param total  总量（remain + used）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CreditTotal(BigDecimal remain, BigDecimal used, BigDecimal total) {
    }

    /**
     * 余额历史点（前端迷你走势图取数；契约字段名保持 snake_case）。
     *
     * @param syncedAt 回源时间（毫秒）
     * @param keyMask  密钥掩码（不落明文）
     * @param ok       本次是否查到余额
     * @param remain   剩余余额
     * @param used     已用余额
     * @param currency 币种
     * @param source   命中的余额实现
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CreditHistoryPoint(@JsonProperty("synced_at") Long syncedAt,
                              @JsonProperty("key_mask") String keyMask,
                              Boolean ok, BigDecimal remain, BigDecimal used,
                              String currency, String source) {
    }

    /**
     * {@code /credit} 完整载荷（前端契约字段名保持 snake_case）。
     *
     * @param providerId 供应商标识
     * @param url        上游地址
     * @param kind       余额口径（显式配置或 auto）
     * @param candidates 候选实现清单
     * @param level      口径分级（official / openai / relay / manual / none / uncovered）
     * @param loginUrl   「账号登录」入口（无可登录实现时为 null）
     * @param ok         是否至少一个 Key 查到读数
     * @param currency   汇总币种（取第一条成功读数）
     * @param total      汇总余额
     * @param keys       逐 Key 读数
     * @param cached     是否命中缓存（缓存命中 / 快照回放为 true，实时查询为 false）
     * @param cachedAt   缓存时间（epoch 毫秒）
     * @param ageSeconds 缓存年龄（秒）
     * @param message    不可查询时的原因说明
     * @param warn       余额低量告警判定（未配置阈值为 null）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CreditPanelPayload(@JsonProperty("provider_id") String providerId, String url, String kind,
                              List<String> candidates, String level,
                              @JsonProperty("login_url") String loginUrl, boolean ok, String currency,
                              CreditTotal total, List<CreditReading> keys, Boolean cached,
                              @JsonProperty("cached_at") Long cachedAt,
                              @JsonProperty("age_seconds") Long ageSeconds, String message,
                              @JsonProperty("warn") CreditWarn warn) {

        /**
         * 14 参便捷构造：warn 缺省 null，兼容存量构造点（告警信息只有实时回源路径会带）。
         */
        CreditPanelPayload(String providerId, String url, String kind, List<String> candidates, String level,
                           String loginUrl, boolean ok, String currency, CreditTotal total, List<CreditReading> keys,
                           Boolean cached, Long cachedAt, Long ageSeconds, String message) {
            this(providerId, url, kind, candidates, level, loginUrl, ok, currency, total, keys,
                    cached, cachedAt, ageSeconds, message, null);
        }

        /**
         * 缓存命中副本：保留原读数，改写缓存三件套（cached / cached_at / age_seconds）。
         *
         * @param at 命中的快照时间（epoch 毫秒）
         * @return 带缓存标记的新载荷
         */
        CreditPanelPayload cachedCopy(long at) {
            long now = System.currentTimeMillis();
            return new CreditPanelPayload(providerId, url, kind, candidates, level, loginUrl, ok,
                    currency, total, keys, true, at, (now - at) / 1000L, message, warn);
        }
    }

    /**
     * 余额低量告警判定结果（随 {@code /credit} 载荷回给前端展示）。
     *
     * @param threshold 告警阈值（extra.credit_warn_threshold）
     * @param remain    本次汇总剩余余额（无读数为 null）
     * @param triggered 余额是否已低于等于阈值
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CreditWarn(BigDecimal threshold, BigDecimal remain, boolean triggered) {
    }

    /**
     * 余额告警推送留痕行（{@code /credit/alerts} 的 alerts 元素）。
     *
     * @param type      推送类型（WARN=低量告警 / RECOVERY=恢复通知）
     * @param remain    推送时的剩余余额
     * @param threshold 推送时的告警阈值
     * @param receivers 接收人用户 ID（逗号分隔）
     * @param title     消息标题
     * @param content   消息摘要
     * @param pushedAt  推送时间（epoch 毫秒）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CreditAlertLogItem(String type, Double remain, Double threshold, String receivers,
                              String title, String content,
                              @JsonProperty("pushed_at") Long pushedAt) {
    }

    /**
     * 余额告警推送留痕载荷（{@code /credit/alerts} 响应体）。
     *
     * @param providerId   供应商标识
     * @param total        返回的留痕条数
     * @param lastPushedAt 最近一次推送时间（epoch 毫秒；无记录为 null）
     * @param alerts       留痕列表（按推送时间倒序）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CreditAlertLogPayload(@JsonProperty("provider_id") String providerId, int total,
                                 @JsonProperty("last_pushed_at") Long lastPushedAt,
                                 List<CreditAlertLogItem> alerts) {
    }

    /**
     * 余额同步报告（「同步」响应的 {@code credit} 分节）。
     *
     * @param keys     参与查询的 Key 数
     * @param ok       是否至少一个 Key 查到读数
     * @param success  查到读数的 Key 数
     * @param failed   失败的 Key 数
     * @param syncedAt 本次同步时间（epoch 毫秒；跳过时为 null）
     * @param message  跳过原因（正常同步时为 null）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreditSyncReport(int keys, boolean ok, long success, long failed,
                                   @JsonProperty("synced_at") Long syncedAt, String message) {

        /**
         * 跳过报告：无 Key / 无余额口径，未出网。
         *
         * @param reason 跳过原因
         * @return 报告
         */
        static CreditSyncReport skipped(String reason) {
            return new CreditSyncReport(0, false, 0, 0, null, reason);
        }
    }

    /**
     * 余额读数快照：缓存时间 + 完整响应载荷
     *
     * @param at      缓存时间（epoch 毫秒）
     * @param payload 完整响应
     */
    private record CreditSnapshot(long at, CreditPanelPayload payload) {
    }

    /**
     * 余额读数的缓存指纹：供应商 + 上游地址 + Key 列表 + 余额口径，任一变化即视为新键。
     *
     * @param providerId     供应商标识
     * @param baseUrl        上游地址
     * @param keys           参与查询的 Key
     * @param configuredKind 显式余额实现（可为 null）
     * @param extra          供应商扩展配置
     * @return 指纹串
     */
    private static String creditFingerprint(String providerId, String baseUrl, List<String> keys,
                                            String configuredKind, Map<String, Object> extra) {
        return providerId + "|" + baseUrl + "|" + keys + "|" + configuredKind
                + "|" + strOf(extra, "credit_url") + "|" + strOf(extra, "credit_expression")
                + "|" + strOf(extra, "credit_mapping");
    }

    /**
     * 写入余额读数缓存：超限时先清过期条目，仍超则整体清空。
     *
     * @param fingerprint 缓存指纹
     * @param payload     完整响应
     */
    private static void cacheCredit(String fingerprint, CreditPanelPayload payload) {
        if (CREDIT_CACHE.size() >= CREDIT_CACHE_LIMIT) {
            long now = System.currentTimeMillis();
            CREDIT_CACHE.values().removeIf(snapshot -> now - snapshot.at() > CREDIT_CACHE_TTL_MS);
            if (CREDIT_CACHE.size() >= CREDIT_CACHE_LIMIT) {
                CREDIT_CACHE.clear();
            }
        }
        CREDIT_CACHE.put(fingerprint, new CreditSnapshot(System.currentTimeMillis(), payload));
    }

    /**
     * 余额密钥哈希：SHA-256 十六进制（落库不存明文）。
     *
     * @param key 余额密钥明文
     * @return 64 位十六进制串
     */
    private static String creditKeyHash(String key) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // JVM 必有 SHA-256；异常环境退化为哈希码，至少不落明文
            return String.format("%064x", key.hashCode());
        }
    }

    /**
     * 把本轮逐 Key 读数落库（先删后增，整批同一 synced_at）。
     *
     * <p>落库失败只记日志：余额面板的主链路（进程内缓存 + 实时查询）不受影响。</p>
     *
     * @param providerId 供应商标识
     * @param keys       参与查询的 Key（与 rows 同序）
     * @param rows       creditOfKey 产出的逐 Key 读数
     */
    private void persistCreditSnapshots(String providerId, List<String> keys, List<CreditReading> rows) {
        try {
            long now = System.currentTimeMillis();
            List<SysAiProviderCredit> snapshots = new ArrayList<>(rows.size());
            for (int i = 0; i < rows.size(); i++) {
                CreditReading row = rows.get(i);
                SysAiProviderCredit snapshot = new SysAiProviderCredit();
                snapshot.setProviderId(providerId);
                snapshot.setKeyHash(creditKeyHash(keys.get(i)));
                // 掩码取读数行自带值：正常 Key 是 maskKey(key)，AK/SK 占位链路是「AK/SK」
                snapshot.setKeyMask(rows.get(i).mask());
                snapshot.setOk(row.ok());
                snapshot.setRemain(row.remain() == null ? null : row.remain().doubleValue());
                snapshot.setUsed(row.used() == null ? null : row.used().doubleValue());
                snapshot.setTotal(row.total() == null ? null : row.total().doubleValue());
                snapshot.setCurrency(row.currency());
                snapshot.setSource(row.source());
                snapshot.setParts(objectMapper.writeValueAsString(row.parts()));
                snapshot.setExtra(row.extra() == null ? null : objectMapper.writeValueAsString(row.extra()));
                snapshot.setError(row.error());
                snapshot.setSyncedAt(now);
                snapshot.setCreateTime(now);
                snapshot.setUpdateTime(now);
                snapshots.add(snapshot);
            }
            providerCreditSnapshotService.replaceAll(snapshots);
            // 追加余额历史（趋势图取数）：与快照同批同参逐 Key 落一行；
            // 历史是增值数据，追加 / 清理失败只记日志，不影响快照主链路
            List<SysAiProviderCreditHistory> history = new ArrayList<>(snapshots.size());
            for (SysAiProviderCredit snapshot : snapshots) {
                SysAiProviderCreditHistory row = new SysAiProviderCreditHistory();
                row.setProviderId(snapshot.getProviderId());
                row.setKeyHash(snapshot.getKeyHash());
                row.setKeyMask(snapshot.getKeyMask());
                row.setOk(snapshot.getOk());
                row.setRemain(snapshot.getRemain());
                row.setUsed(snapshot.getUsed());
                row.setCurrency(snapshot.getCurrency());
                row.setSource(snapshot.getSource());
                row.setSyncedAt(now);
                row.setCreateTime(now);
                history.add(row);
            }
            providerCreditSnapshotService.appendHistory(history);
            providerCreditSnapshotService.pruneHistory(now);
        } catch (Exception e) {
            log.warn("[ProviderCredit] 余额快照落库失败 provider={}: {}", providerId, e.getMessage());
        }
    }

    /**
     * 读最近一次落库的按 Key 快照，拼成 /credit 页面载荷。
     *
     * <p>只在「快照未过期（TTL 内）且 Key 清单与当前完全一致」时可用；
     * 否则返回 null 走实时回源。汇总口径与实时查询一致：只累加与第一条
     * 成功读数同币种的行。</p>
     *
     * @param entity         供应商
     * @param extra          供应商扩展配置
     * @param keys           当前参与查询的 Key
     * @param configuredKind 显式余额实现（可为 null）
     * @return 页面载荷；快照不可用时为 null
     */
    private CreditPanelPayload persistedCreditPayload(SysAccountChannel entity, Map<String, Object> extra,
                                                      List<String> keys, String configuredKind) {
        List<SysAiProviderCredit> snapshots = providerCreditSnapshotService.listByProvider(entity.getProviderId());
        if (snapshots.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        Long syncedAt = snapshots.get(0).getSyncedAt();
        if (syncedAt == null || now - syncedAt > CREDIT_CACHE_TTL_MS) {
            return null;
        }
        Map<String, SysAiProviderCredit> byHash = new HashMap<>(snapshots.size() * 2);
        for (SysAiProviderCredit snapshot : snapshots) {
            byHash.put(snapshot.getKeyHash(), snapshot);
        }
        List<CreditReading> rows = new ArrayList<>(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            SysAiProviderCredit snapshot = byHash.get(creditKeyHash(keys.get(i)));
            if (snapshot == null) {
                // Key 清单已变（增删改密钥）：落库快照与当前口径不一致，回源
                return null;
            }
            rows.add(creditRowOfSnapshot(snapshot, i));
        }
        BigDecimal sumRemain = BigDecimal.ZERO;
        BigDecimal sumUsed = BigDecimal.ZERO;
        boolean anyRemain = false;
        String currency = null;
        for (CreditReading row : rows) {
            if (row.remain() == null) {
                continue;
            }
            anyRemain = true;
            if (currency == null) {
                currency = row.currency();
            }
            if (currency == null || currency.equals(row.currency())) {
                sumRemain = sumRemain.add(row.remain());
                if (row.used() != null) {
                    sumUsed = sumUsed.add(row.used());
                }
            }
        }
        boolean anyOk = rows.stream().anyMatch(CreditReading::ok);
        return new CreditPanelPayload(entity.getProviderId(),
                entity.getBaseUrl() == null ? "" : entity.getBaseUrl().trim(),
                configuredKind == null ? "auto" : configuredKind,
                candidateKinds(entity, extra), creditLevel(entity, extra), findLoginUrl(entity, extra),
                anyOk, currency, creditTotal(anyRemain ? sumRemain : null, sumUsed), rows, true, syncedAt,
                (now - syncedAt) / 1000L, null);
    }

    /**
     * 落库快照 → /credit 逐 Key 行（结构与实时查询的 creditRow 一致）。
     *
     * @param snapshot 快照行
     * @param index    Key 序号
     * @return 页面行
     */
    private CreditReading creditRowOfSnapshot(SysAiProviderCredit snapshot, int index) {
        boolean ok = Boolean.TRUE.equals(snapshot.getOk());
        String error = !ok && snapshot.getError() != null && !snapshot.getError().isBlank()
                ? snapshot.getError() : null;
        return new CreditReading(index, snapshot.getKeyMask(), ok, snapshot.getSource(),
                snapshot.getRemain() == null ? null : BigDecimal.valueOf(snapshot.getRemain()),
                snapshot.getUsed() == null ? null : BigDecimal.valueOf(snapshot.getUsed()),
                snapshot.getTotal() == null ? null : BigDecimal.valueOf(snapshot.getTotal()),
                snapshot.getCurrency(), creditPartsOfSnapshot(snapshot.getParts()), error,
                creditExtraOfSnapshot(snapshot.getExtra()));
    }

    /**
     * 反序列化快照里的多段读数 JSON；损坏时返回空列表（不抛异常）。
     *
     * @param partsJson 快照 JSON
     * @return 多段读数
     */
    private List<CreditPart> creditPartsOfSnapshot(String partsJson) {
        if (partsJson == null || partsJson.isBlank()) {
            return List.of();
        }
        try {
            List<CreditPart> parts = objectMapper.readValue(partsJson, new TypeReference<List<CreditPart>>() {
            });
            return parts == null ? List.of() : parts;
        } catch (Exception ignored) {
            // 快照 JSON 损坏：按无多段读数处理
        }
        return List.of();
    }

    /**
     * 反序列化快照里的供应商专属结构化数据 JSON（sub2api 订阅明细等）；
     * 损坏时返回 null（不抛异常，前端按无 extra 渲染）。
     *
     * @param extraJson 快照 JSON
     * @return 供应商专属结构化数据；无 / 损坏时为 null
     */
    private Map<String, Object> creditExtraOfSnapshot(String extraJson) {
        if (extraJson == null || extraJson.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> extra = objectMapper.readValue(extraJson, new TypeReference<Map<String, Object>>() {
            });
            return extra == null || extra.isEmpty() ? null : extra;
        } catch (Exception ignored) {
            // 快照 JSON 损坏：按无 extra 处理
        }
        return null;
    }

    /**
     * 拆分余额密钥：支持多账号，换行 / 逗号 / 分号（含全角）分隔，保序去重去空。
     *
     * @param raw 页面保存的余额密钥原文
     * @return Key 列表
     */
    private static List<String> splitCreditKeys(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String piece : raw.split("[\\r\\n,;，；]+")) {
            String key = piece.trim();
            if (!key.isBlank()) {
                keys.add(key);
            }
        }
        return new ArrayList<>(keys);
    }

    /**
     * 解析参与余额查询的 Key。
     *
     * <p>只认显式配置的余额密钥（{@code extra.credit_key}），不再静默沿用渠道 API Key：
     * 沿用会让编辑页看不见真正查余额的 Key、无法删除，且同 Key 的新供应商会显示同一份
     * 余额。存量兼容：历史数据没配余额密钥（extra 里无该键）时，首次查询把渠道 API Key
     * 回填为显式余额密钥；页面清空余额密钥保存的是空串，与「历史未配置」可区分，
     * 删除后不会被再次回填。</p>
     *
     * @param entity 供应商行
     * @param extra  供应商扩展配置（回填成功后同步写入该 Map）
     * @return 参与查询的 Key；未配置时为空列表
     */
    private List<String> resolveCreditKeys(SysAccountChannel entity, Map<String, Object> extra) {
        Object configured = extra.get("credit_key");
        if (configured != null) {
            // 显式配置（含清空成空串）：空串表示「不查询余额」，不再回落、不再回填
            return splitCreditKeys(String.valueOf(configured));
        }
        List<String> inherited = extractAllKeys(entity.getCredentials());
        if (inherited.isEmpty()) {
            return List.of();
        }
        promoteCreditKey(entity, extra, inherited);
        return inherited;
    }

    /**
     * 参与余额查询的有效 Key：显式余额密钥优先；没有令牌但配齐了 AK/SK 时用占位 Key
     * （{@link #CREDIT_AKSK_KEY}）走签名链路，让火山 Billing / 百度财务 / 七牛 /
     * 阿里云 BSS 这类「账户级 AK/SK 签名」供应商不填余额密钥也能查余额。
     *
     * @param entity 供应商
     * @param extra  供应商扩展配置
     * @return 参与查询的 Key；两者都没有时为空列表
     */
    private List<String> effectiveCreditKeys(SysAccountChannel entity, Map<String, Object> extra) {
        List<String> keys = resolveCreditKeys(entity, extra);
        if (!keys.isEmpty()) {
            return keys;
        }
        return hasCreditAkSk(extra) ? List.of(CREDIT_AKSK_KEY) : keys;
    }

    /**
     * 是否配齐了 AK/SK 签名密钥（extra.credit_access_key 与 credit_secret_key 均非空）。
     *
     * <p>两者必须同时存在：签名类实现校验 AK 与 SK 成对，只填一个必然签名失败，
     * 不如视为未配置、维持「未配置余额密钥」的引导文案。</p>
     *
     * @param extra 供应商扩展配置
     * @return 是否配齐
     */
    private boolean hasCreditAkSk(Map<String, Object> extra) {
        String accessKey = str(extra, "credit_access_key");
        String secretKey = str(extra, "credit_secret_key");
        return accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }

    /**
     * 替换读数行的展示掩码（AK/SK 占位链路把 **** 换成「AK/SK」标识）。
     *
     * @param row  原读数行
     * @param mask 新掩码
     * @return 仅掩码不同的新读数行
     */
    private static CreditReading withMask(CreditReading row, String mask) {
        return new CreditReading(row.index(), mask, row.ok(), row.source(), row.remain(), row.used(),
                row.total(), row.currency(), row.parts(), row.error(), row.extra());
    }

    /**
     * 存量回填：把渠道 API Key 显式落为余额密钥（extra.credit_key），一次生效。
     *
     * <p>只经 {@code AccountChannelService#updateExtra} 走账号模块的命令接口落库，
     * 失败只记日志：本次查询仍按继承到的 Key 执行，下次访问再试。</p>
     *
     * @param entity 供应商行
     * @param extra  供应商扩展配置
     * @param keys   渠道 API Key 列表
     */
    private void promoteCreditKey(SysAccountChannel entity, Map<String, Object> extra, List<String> keys) {
        String joined = String.join("\n", keys);
        try {
            Map<String, Object> patch = new LinkedHashMap<>(extra);
            patch.put("credit_key", joined);
            accountChannelService.updateExtra(entity.getProviderId(), patch);
            extra.put("credit_key", joined);
            log.info("[credit] 供应商 {} 未配置余额密钥，已把渠道 API Key 回填为余额密钥", entity.getProviderId());
        } catch (Exception e) {
            log.warn("[credit] 供应商 {} 余额密钥回填失败（本次仍按渠道 Key 查询）: {}",
                    entity.getProviderId(), e.getMessage());
        }
    }

    /**
     * 供应商余额与用量（只读）。
     *
     * <p>把一个供应商的每个 Key 当成一次独立的余额读数：Key 即令牌（或
     * {@code extra.credit_key} 余额专用密钥），地址取供应商的上游地址（或
     * {@code extra.credit_url}），查询实现由 {@code extra.credit_kind} 指定；
     * 没指定时按「厂商官方 → OpenAI 官方 → OpenAI 兼容计费 → Moss 非官方」
     * 依次回落，第一条查到数的实现胜出。余额口径完全复用 {@link CreditQueries}
     * （与渠道管理页同一套 SPI），本接口只负责把 N 个 Key 的读数收成一份可直接渲染的
     * 结构，不承载任何厂商特有的字段解析。</p>
     *
     * <p>失败不抛异常：单个 Key 查不到只在该行写 {@code error}，页面照常有数据可看。
     * 汇总余额只累加与第一条成功读数同币种的行，避免 USD 与 CNY 直接相加。</p>
     *
     * <p>余额密钥支持多账号（换行 / 逗号 / 分号分隔），逐 Key 查询后分别展示并汇总；
     * 读数带 5 分钟缓存（{@code refresh=true} 强制回源），命中时响应多出
     * {@code cached / cached_at / age_seconds} 三个字段。</p>
     *
     * @param providerId 供应商标识
     * @param refresh    跳过缓存强制回源
     * @return { provider_id, url, kind, ok, currency, total:{remain,used,total}, keys:[...] }
     */
    @Permission("admin")
    @GetMapping("/providers/{provider_id}/credit")
    public ApiResult<CreditPanelPayload> providerCredit(@PathVariable("provider_id") String providerId,
            @RequestParam(value = "refresh", required = false, defaultValue = "false") boolean refresh) {
        SysAccountChannel entity = requireProvider(providerId);
        Map<String, Object> extra = entity.getExtra() == null ? new HashMap<>(4) : entity.getExtra();
        String baseUrl = entity.getBaseUrl() == null ? "" : entity.getBaseUrl().trim();
        String configuredKind = str(extra, "credit_kind");
        String kind = configuredKind == null ? "auto" : configuredKind;
        // 余额专用密钥（extra.credit_key）：Moss 这类「查询令牌 ≠ 渠道 Key」的上游用它查余额；
        // 支持多账号：换行 / 逗号 / 分号分隔逐个查。不再静默沿用渠道 API Key——沿用会让
        // 编辑页看不见真正查余额的 Key、无法删除，且同 Key 的新供应商会显示同一份余额；
        // 历史数据由 resolveCreditKeys 在首次查询时把渠道 Key 回填为显式余额密钥。
        // 无余额令牌但配齐 AK/SK（火山 Billing / 百度财务 / 七牛 / 阿里云 BSS 等签名型）时，
        // 用占位 Key 走签名链路，同样能查余额
        List<String> keys = effectiveCreditKeys(entity, extra);
        // 是否为 AK/SK 占位链路：读数行统一标「AK/SK」，让用户知道走的不是余额令牌
        boolean akSkOnly = keys.size() == 1 && CREDIT_AKSK_KEY.equals(keys.get(0));
        // 本次按哪条链路查的：自动识别时是「厂商官方 → OpenAI → OpenAI 兼容计费 → Moss 非官方 → 表达式」
        List<String> candidates = candidateKinds(entity, extra);
        // 余额口径分级：official（有官方预设）/ openai（OpenAI 官方）/ uncovered（官方站点但未收录余额接口）/
        // relay（中转站按协议探测）/ manual（页面上指定了唯一实现）/ none（没有上游地址，查不了）
        String level = creditLevel(entity, extra);
        // 「账号登录」入口：候选实现里能给出登录页的（如 Agnes），前端据此展示按钮
        String loginUrl = findLoginUrl(entity, extra);
        CreditPanelPayload blocked = null;
        if (baseUrl.isBlank() && str(extra, "credit_url") == null) {
            blocked = new CreditPanelPayload(providerId, baseUrl, kind, candidates, level, loginUrl,
                    false, null, null, List.of(), null, null, null, "未配置上游地址，无法查询余额");
        } else if ("uncovered".equals(level)) {
            String host = hostOf(baseUrl);
            String vendor = entity.getPlatform() == null || entity.getPlatform().isBlank()
                    ? "该厂商" : entity.getPlatform();
            blocked = new CreditPanelPayload(providerId, baseUrl, kind, candidates, level, loginUrl,
                    false, null, null, List.of(), null, null, null,
                    "上游是 " + vendor + " 官方站点（" + (host == null ? baseUrl : host) + "），"
                            + "本系统未收录它的余额接口；官方站点上没有中转计费协议，不做探测。"
                            + "可在「余额」里配自定义地址与字段表达式。");
        } else if (keys.isEmpty()) {
            blocked = new CreditPanelPayload(providerId, baseUrl, kind, candidates, level, loginUrl,
                    false, null, null, List.of(), null, null, null,
                    "未配置余额密钥，不查询余额；可在「编辑供应商 → 更多设置 → 余额 → 密钥」中填写。");
        }
        if (blocked != null) {
            return ApiResult.ok(blocked);
        }
        // 读数缓存：余额接口逐 Key 出网，面板每次打开都真查一遍太重；同一口径 5 分钟内直接回缓存，
        // 改了地址 / Key / 口径指纹即变，refresh=true 强制回源
        String fingerprint = creditFingerprint(providerId, baseUrl, keys, configuredKind, extra);
        if (!refresh) {
            CreditSnapshot hit = CREDIT_CACHE.get(fingerprint);
            if (hit != null && System.currentTimeMillis() - hit.at() <= CREDIT_CACHE_TTL_MS) {
                return ApiResult.ok(hit.payload().cachedCopy(hit.at()));
            }
            // 进程内缓存未命中（重启 / 过期）：读最近一次落库的按 Key 快照，TTL 内仍当缓存用，
            // 面板不再因为重启就显示「暂无数据」
            CreditPanelPayload persisted = persistedCreditPayload(entity, extra, keys, configuredKind);
            if (persisted != null) {
                CREDIT_CACHE.put(fingerprint, new CreditSnapshot(persisted.cachedAt(), persisted));
                return ApiResult.ok(persisted);
            }
        }
        List<CreditReading> rows = new ArrayList<>(keys.size());
        BigDecimal sumRemain = BigDecimal.ZERO;
        BigDecimal sumUsed = BigDecimal.ZERO;
        boolean anyRemain = false;
        String currency = null;
        for (int i = 0; i < keys.size(); i++) {
            CreditReading row = creditOfKey(entity, extra, keys.get(i), i);
            rows.add(row);
            if (row.remain() == null) {
                continue;
            }
            anyRemain = true;
            if (currency == null) {
                currency = row.currency();
            }
            if (currency == null || currency.equals(row.currency())) {
                sumRemain = sumRemain.add(row.remain());
                if (row.used() != null) {
                    sumUsed = sumUsed.add(row.used());
                }
            }
        }
        // AK/SK 占位链路：唯一读数行标「AK/SK」，页面与快照同口径
        if (akSkOnly && !rows.isEmpty()) {
            rows.set(0, withMask(rows.get(0), CREDIT_AKSK_MASK));
        }
        boolean anyOk = rows.stream().anyMatch(CreditReading::ok);
        CreditTotal total = creditTotal(anyRemain ? sumRemain : null, sumUsed);
        // 低量告警只挂实时回源路径：缓存命中 / 快照回放不重复评估，也不重复推送
        CreditWarn warn = evaluateCreditWarn(entity, extra, anyOk, total);
        CreditPanelPayload payload = new CreditPanelPayload(providerId, baseUrl, kind, candidates, level,
                loginUrl, anyOk, currency, total, rows, false, null, null, null, warn);
        cacheCredit(fingerprint, payload);
        // 按 Key 持久化：重启 / 过期后面板仍能读到最近一次读数，时间轮也复用这张表
        persistCreditSnapshots(providerId, keys, rows);
        return ApiResult.ok(payload);
    }

    /**
     * 余额低量告警：按 extra.credit_warn_threshold 判定，连续
     * {@link #CREDIT_WARN_STREAK} 轮低于阈值且配置了 extra.credit_warn_receivers
     * 时投递站内消息（进程内冷却防重复）；余额回升过阈值后补一条恢复通知。
     *
     * <p>只在实时回源路径调用；判定结果无论是否触发都随载荷返回，
     * 前端据此在余额面板展示「已低于告警阈值」。全部 Key 都没查到读数时
     * 不告警也不计轮——占位空值不代表真实余额，既不算低也不算恢复。</p>
     *
     * @param entity 渠道（供应商标识与名称来源）
     * @param extra  供应商扩展配置（credit_warn_threshold / credit_warn_receivers）
     * @param anyOk  本次是否至少一个 Key 查到读数
     * @param total  汇总余额
     * @return 告警信息（未配置阈值返回 null）
     */
    private CreditWarn evaluateCreditWarn(SysAccountChannel entity, Map<String, Object> extra,
                                          boolean anyOk, CreditTotal total) {
        BigDecimal threshold = decimalOf(extra.get("credit_warn_threshold"));
        String providerId = entity.getProviderId();
        if (threshold == null) {
            // 阈值被移除：清掉去抖与恢复登记，避免残留旧判定
            creditWarnLowStreak.remove(providerId);
            creditWarnAlertOpen.remove(providerId);
            return null;
        }
        BigDecimal remain = anyOk && total != null ? total.remain() : null;
        CreditWarn warn = new CreditWarn(threshold, remain, remain != null && remain.compareTo(threshold) <= 0);
        if (remain == null) {
            // 本轮读数未知：不累计去抖轮数，也不当「已恢复」
            return warn;
        }
        if (remain.compareTo(threshold) <= 0) {
            // 低于阈值：连续达到 CREDIT_WARN_STREAK 轮才推送（单轮波动不告警）
            int streak = creditWarnLowStreak.merge(providerId, 1, Integer::sum);
            if (streak < CREDIT_WARN_STREAK) {
                log.debug("[credit] 供应商 {} 余额 {} 已低于阈值 {}（连续 {} 轮，未达 {} 轮，暂不推送）",
                        providerId, remain.toPlainString(), threshold.toPlainString(), streak, CREDIT_WARN_STREAK);
                return warn;
            }
            List<Integer> receivers = creditWarnReceivers(extra.get("credit_warn_receivers"));
            if (receivers.isEmpty()) {
                log.debug("[credit] 供应商 {} 余额 {} 已低于阈值 {}，未配置告警接收人，跳过推送",
                        providerId, remain.toPlainString(), threshold.toPlainString());
                return warn;
            }
            long now = System.currentTimeMillis();
            Long last = creditWarnLastSent.get(providerId);
            if (last != null && now - last < CREDIT_WARN_COOLDOWN_MS) {
                return warn;
            }
            if (broadcastCreditWarn(entity, remain, threshold, receivers)) {
                creditWarnLastSent.put(providerId, now);
                // 已推送告警且尚未恢复：回升过阈值时补一条恢复通知
                creditWarnAlertOpen.add(providerId);
            }
            return warn;
        }
        // 回升过阈值：若之前推送过低量告警且尚未发过恢复通知，补一条恢复通知收尾
        creditWarnLowStreak.remove(providerId);
        if (creditWarnAlertOpen.remove(providerId)) {
            List<Integer> receivers = creditWarnReceivers(extra.get("credit_warn_receivers"));
            if (receivers.isEmpty()) {
                log.debug("[credit] 供应商 {} 余额 {} 已回升过阈值 {}，未配置告警接收人，跳过恢复通知",
                        providerId, remain.toPlainString(), threshold.toPlainString());
                return warn;
            }
            if (!broadcastCreditRecovery(entity, remain, threshold, receivers)) {
                // 恢复通知投递失败：保留登记，下次实时回源重试
                creditWarnAlertOpen.add(providerId);
            }
        }
        return warn;
    }

    /**
     * 余额低量告警投递：写一条站内消息到消息中心（sys_message），登录后在消息中心可见。
     *
     * <p>复用平台既有消息中心（{@code common-system-starter} 的
     * {@link SysMessageService#broadcast}）而不是另写一套推送。消息中心服务拿不到
     * （宿主未接该依赖）或写库失败都只记日志、不登记冷却——下次实时回源会重试。</p>
     *
     * @return 是否投递成功
     */
    private boolean broadcastCreditWarn(SysAccountChannel entity, BigDecimal remain, BigDecimal threshold,
                                        List<Integer> receivers) {
        SysMessageService service = messageServiceProvider.getIfAvailable();
        if (service == null) {
            log.debug("[credit] 宿主未接入消息中心，余额低量告警跳过: {}", entity.getProviderId());
            return false;
        }
        String title = "供应商余额低量告警";
        String content = "供应商「" + entity.getName() + "」(" + entity.getProviderId()
                + ") 当前余额 " + remain.toPlainString() + "，已低于告警阈值 " + threshold.toPlainString()
                + "，请及时充值或调整阈值。";
        SysMessage message = new SysMessage();
        message.setSysMessageTitle(title);
        message.setSysMessageContent(content);
        message.setSysMessageType("warning");
        message.setSysMessageLevel("high");
        message.setSysMessageChannel("PLATFORM");
        message.setSysMessageSenderId(0);
        message.setSysMessageSenderName("模型管理");
        try {
            ReturnResult<Integer> result = service.broadcast(message, receivers);
            boolean ok = result != null && result.isOk();
            if (ok) {
                log.info("[credit] 余额低量告警已推送 provider={} remain={} threshold={} receivers={}",
                        entity.getProviderId(), remain.toPlainString(), threshold.toPlainString(), receivers.size());
                // 投递成功才留痕：失败不写，留痕只记真实发出去的推送
                recordCreditAlert(entity, SysAiProviderCreditAlertLog.TYPE_WARN,
                        remain, threshold, receivers, title, content);
            } else {
                log.warn("[credit] 余额低量告警写入消息中心失败 provider={} msg={}",
                        entity.getProviderId(), result == null ? "无返回" : result.getMsg());
            }
            return ok;
        } catch (Exception ex) {
            log.warn("[credit] 余额低量告警写入消息中心异常 provider={} err={}",
                    entity.getProviderId(), ex.getMessage());
            return false;
        }
    }

    /**
     * 余额恢复通知投递：余额从「低于阈值」回升到阈值之上时，补一条站内消息收尾，
     * 让接收人知道该供应商余额已恢复、上次告警可以不处理了。
     *
     * <p>与低量告警同走 {@code common-system-starter} 的
     * {@link SysMessageService#broadcast}；类型 info / 级别 normal（好消息不打 high 级）。
     * 消息中心拿不到或写库失败只记日志，由调用方保留登记、下次实时回源重试。</p>
     *
     * @return 是否投递成功
     */
    private boolean broadcastCreditRecovery(SysAccountChannel entity, BigDecimal remain, BigDecimal threshold,
                                            List<Integer> receivers) {
        SysMessageService service = messageServiceProvider.getIfAvailable();
        if (service == null) {
            log.debug("[credit] 宿主未接入消息中心，余额恢复通知跳过: {}", entity.getProviderId());
            return false;
        }
        String title = "供应商余额恢复通知";
        String content = "供应商「" + entity.getName() + "」(" + entity.getProviderId()
                + ") 当前余额 " + remain.toPlainString() + "，已回升至告警阈值 " + threshold.toPlainString()
                + " 之上。";
        SysMessage message = new SysMessage();
        message.setSysMessageTitle(title);
        message.setSysMessageContent(content);
        message.setSysMessageType("info");
        message.setSysMessageLevel("normal");
        message.setSysMessageChannel("PLATFORM");
        message.setSysMessageSenderId(0);
        message.setSysMessageSenderName("模型管理");
        try {
            ReturnResult<Integer> result = service.broadcast(message, receivers);
            boolean ok = result != null && result.isOk();
            if (ok) {
                log.info("[credit] 余额恢复通知已推送 provider={} remain={} threshold={} receivers={}",
                        entity.getProviderId(), remain.toPlainString(), threshold.toPlainString(), receivers.size());
                // 投递成功才留痕：失败不写，留痕只记真实发出去的推送
                recordCreditAlert(entity, SysAiProviderCreditAlertLog.TYPE_RECOVERY,
                        remain, threshold, receivers, title, content);
            } else {
                log.warn("[credit] 余额恢复通知写入消息中心失败 provider={} msg={}",
                        entity.getProviderId(), result == null ? "无返回" : result.getMsg());
            }
            return ok;
        } catch (Exception ex) {
            log.warn("[credit] 余额恢复通知写入消息中心异常 provider={} err={}",
                    entity.getProviderId(), ex.getMessage());
            return false;
        }
    }

    /**
     * 余额告警推送留痕：投递成功后把「谁、什么时候、以什么余额/阈值、
     * 推给了谁、推了什么」落一行到 {@code sys_ai_provider_credit_alert_log}。
     * 留痕失败只记日志：不影响已完成的投递，也不打断告警主链路。
     */
    private void recordCreditAlert(SysAccountChannel entity, String type, BigDecimal remain, BigDecimal threshold,
                                   List<Integer> receivers, String title, String content) {
        try {
            SysAiProviderCreditAlertLog row = new SysAiProviderCreditAlertLog();
            row.setProviderId(entity.getProviderId());
            row.setType(type);
            row.setRemain(remain == null ? null : remain.doubleValue());
            row.setThreshold(threshold == null ? null : threshold.doubleValue());
            row.setReceivers(receivers.stream().map(String::valueOf).collect(Collectors.joining(",")));
            row.setTitle(title);
            row.setContent(content);
            long now = System.currentTimeMillis();
            row.setPushedAt(now);
            row.setCreateTime(now);
            if (providerCreditAlertLogService.record(row)) {
                log.debug("[credit] 余额告警留痕完成 provider={} type={}", entity.getProviderId(), type);
            }
        } catch (Exception ex) {
            log.warn("[credit] 余额告警留痕失败 provider={} type={} err={}",
                    entity.getProviderId(), type, ex.getMessage());
        }
    }

    /**
     * 解析告警接收人为用户 ID 列表：extra.credit_warn_receivers 兼容 JSON 数组
     * （数字 / 字符串）与逗号分隔文本；非正整数项跳过并记日志。
     */
    private static List<Integer> creditWarnReceivers(Object raw) {
        if (raw == null) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        if (raw instanceof Collection<?> values) {
            for (Object item : values) {
                if (item != null) {
                    tokens.add(String.valueOf(item).trim());
                }
            }
        } else {
            for (String part : String.valueOf(raw).split("[,，;；\\s]+")) {
                tokens.add(part.trim());
            }
        }
        List<Integer> ids = new ArrayList<>();
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            try {
                int id = Integer.parseInt(token);
                if (id > 0 && !ids.contains(id)) {
                    ids.add(id);
                }
            } catch (NumberFormatException ex) {
                log.warn("[credit] 余额告警接收人不是用户 ID，已跳过: {}", token);
            }
        }
        return ids;
    }

    /**
     * 宽松转 BigDecimal：数字 / 数字字符串均可，失败返回 null（告警配置不参与严格校验）。
     */
    private static BigDecimal decimalOf(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal value) {
            return value;
        }
        if (raw instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 余额历史（前端迷你走势图取数）。
     *
     * <p>每次余额回源（手动刷新 / 时间轮定时刷新）都会向
     * {@code sys_ai_provider_credit_history} 追加逐 Key 读数；本端点按时间升序
     * 返回最近 {@code days} 天的记录，前端按回源时间聚合后画走势。历史是
     * 展示数据，查不到返回空列表，不报错。</p>
     *
     * @param providerId 供应商标识
     * @param days       拉取最近几天（夹取 1~90，默认 7）
     * @param limit      最多返回行数（夹取 1~2000，默认 500）
     * @return 历史点（时间升序；无记录为空列表）
     */
    @Permission("admin")
    @GetMapping("/providers/{provider_id}/credit/history")
    public ApiResult<List<CreditHistoryPoint>> providerCreditHistory(
            @PathVariable("provider_id") String providerId,
            @RequestParam(value = "days", required = false, defaultValue = "7") int days,
            @RequestParam(value = "limit", required = false, defaultValue = "500") int limit) {
        int window = Math.max(1, Math.min(90, days));
        int cap = Math.max(1, Math.min(2000, limit));
        long since = System.currentTimeMillis() - window * 24L * 60L * 60L * 1000L;
        List<SysAiProviderCreditHistory> rows =
                providerCreditSnapshotService.listHistory(providerId, since, cap);
        List<CreditHistoryPoint> points = new ArrayList<>(rows.size());
        for (SysAiProviderCreditHistory row : rows) {
            points.add(new CreditHistoryPoint(row.getSyncedAt(), row.getKeyMask(), row.getOk(),
                    row.getRemain() == null ? null : BigDecimal.valueOf(row.getRemain()),
                    row.getUsed() == null ? null : BigDecimal.valueOf(row.getUsed()),
                    row.getCurrency(), row.getSource()));
        }
        return ApiResult.ok(points);
    }

    /**
     * 余额告警推送留痕（最近几次推送记录 + 上次推送时间）。
     *
     * <p>低量告警 / 恢复通知经消息中心投递<b>成功</b>后各留一行；本端点按
     * 推送时间倒序返回最近记录，余额面板的告警设置浮层据此展示
     * 「上次推送时间」。没推送过返回空列表与 null 上次时间，不报错。</p>
     *
     * @param providerId 供应商标识
     * @param limit      最多返回条数（夹取 1~100，默认 20）
     * @return 留痕载荷（alerts 按推送时间倒序）
     */
    @Permission("admin")
    @GetMapping("/providers/{provider_id}/credit/alerts")
    public ApiResult<CreditAlertLogPayload> providerCreditAlerts(
            @PathVariable("provider_id") String providerId,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
        requireProvider(providerId);
        int cap = Math.max(1, Math.min(100, limit));
        List<SysAiProviderCreditAlertLog> rows = providerCreditAlertLogService.recent(providerId, cap);
        List<CreditAlertLogItem> items = new ArrayList<>(rows.size());
        Long lastPushedAt = null;
        for (SysAiProviderCreditAlertLog row : rows) {
            if (row.getPushedAt() != null && (lastPushedAt == null || row.getPushedAt() > lastPushedAt)) {
                lastPushedAt = row.getPushedAt();
            }
            items.add(new CreditAlertLogItem(row.getType(), row.getRemain(), row.getThreshold(),
                    row.getReceivers(), row.getTitle(), row.getContent(), row.getPushedAt()));
        }
        return ApiResult.ok(new CreditAlertLogPayload(providerId, items.size(), lastPushedAt, items));
    }

    /**
     * 余额定时采样运行状态（可观测）。
     *
     * <p>暴露 {@link CreditRefreshScheduler} 的开关 / 周期 / 最近一轮执行情况 /
     * 下次计划时间，让「余额走势由定时任务在后台喂」这件事在页面上可确认，
     * 而不是只进日志。</p>
     *
     * @return 状态快照（任务未装配时 enabled=false、其余字段为 null）
     */
    @Permission("admin")
    @GetMapping("/providers/credit/sample-status")
    public ApiResult<Map<String, Object>> providerCreditSampleStatus() {
        Map<String, Object> out = new LinkedHashMap<>(12);
        CreditRefreshScheduler scheduler = creditRefreshScheduler.getIfAvailable();
        if (scheduler == null) {
            out.put("enabled", false);
            out.put("running", false);
            out.put("interval_seconds", null);
            out.put("last_run_at", null);
            out.put("last_duration_ms", null);
            out.put("next_run_at", null);
            out.put("providers", null);
            out.put("touched", null);
            out.put("succeeded", null);
            return ApiResult.ok(out);
        }
        CreditRefreshScheduler.SampleStatus status = scheduler.status();
        boolean ran = status.lastRunAt() > 0;
        out.put("enabled", status.enabled());
        out.put("running", status.running());
        out.put("interval_seconds", status.intervalSeconds());
        out.put("last_run_at", ran ? status.lastRunAt() : null);
        out.put("last_duration_ms", ran ? status.lastDurationMs() : null);
        out.put("next_run_at", status.nextRunAt() > 0 ? status.nextRunAt() : null);
        out.put("providers", ran ? status.providers() : null);
        out.put("touched", ran ? status.touched() : null);
        out.put("succeeded", ran ? status.succeeded() : null);
        return ApiResult.ok(out);
    }

    /**
     * 余额密钥试查（编辑页「测试查询」按钮）。
     *
     * <p>把表单里「即将保存」的余额配置（上游地址 / 查询口径 / 密钥 / AK·SK / UA /
     * 自定义头 / 字段映射）当场跑一遍：每个密钥一行，回成功读数或失败原因，
     * 用户不必先保存再进详情页碰运气。与 {@link #providerCredit} 同一套
     * {@link CreditQueries} 口径，但供应商由请求体临时合成，不落库、不进缓存、
     * 不写按 Key 快照，因此测试结果不影响详情页的缓存与历史读数。</p>
     *
     * <p>失败不抛异常：单个 Key 查不到只在该行写 {@code error}，页面照常渲染。</p>
     *
     * @param request { url, platform, auth_scheme, extra:{credit_* / auth_*}, keys:[...] }
     * @return 与详情页余额面板同构的载荷（provider_id 固定为 test）
     */
    @Permission("admin")
    @PostMapping("/providers/credit-test")
    public ApiResult<CreditPanelPayload> providerCreditTest(@RequestBody CreditTestRequest request) {
        SysAccountChannel entity = new SysAccountChannel();
        entity.setBaseUrl(request.url() == null ? "" : request.url().trim());
        entity.setPlatform(request.platform());
        entity.setAuthScheme(request.authScheme());
        Map<String, Object> extra = request.extra() == null
                ? new HashMap<>(4) : new HashMap<>(request.extra());
        List<String> keys = request.keys() == null ? List.of() : request.keys().stream()
                .filter(k -> k != null).map(String::trim)
                .filter(s -> !s.isEmpty()).distinct().toList();
        // 无余额令牌但表单里配齐 AK/SK（火山 Billing / 百度财务 / 七牛 / 阿里云 BSS 等）：
        // 用占位 Key 走签名链路试查，读数行标「AK/SK」
        boolean akSkOnly = keys.isEmpty() && hasCreditAkSk(extra);
        if (akSkOnly) {
            keys = List.of(CREDIT_AKSK_KEY);
        }
        String baseUrl = entity.getBaseUrl();
        String configuredKind = str(extra, "credit_kind");
        String kind = configuredKind == null ? "auto" : configuredKind;
        List<String> candidates = candidateKinds(entity, extra);
        String level = creditLevel(entity, extra);
        CreditPanelPayload blocked = null;
        if (baseUrl.isBlank() && str(extra, "credit_url") == null) {
            blocked = new CreditPanelPayload("test", baseUrl, kind, candidates, level, null,
                    false, null, null, List.of(), null, null, null, "请先填写上游地址再测试");
        } else if (keys.isEmpty()) {
            blocked = new CreditPanelPayload("test", baseUrl, kind, candidates, level, null,
                    false, null, null, List.of(), null, null, null, "请先填写余额密钥再测试");
        }
        if (blocked != null) {
            return ApiResult.ok(blocked);
        }
        List<CreditReading> rows = new ArrayList<>(keys.size());
        BigDecimal sumRemain = BigDecimal.ZERO;
        BigDecimal sumUsed = BigDecimal.ZERO;
        boolean anyRemain = false;
        String currency = null;
        for (int i = 0; i < keys.size(); i++) {
            CreditReading row = creditOfKey(entity, extra, keys.get(i), i);
            rows.add(row);
            if (row.remain() == null) {
                continue;
            }
            anyRemain = true;
            if (currency == null) {
                currency = row.currency();
            }
            if (currency == null || currency.equals(row.currency())) {
                sumRemain = sumRemain.add(row.remain());
                if (row.used() != null) {
                    sumUsed = sumUsed.add(row.used());
                }
            }
        }
        if (akSkOnly && !rows.isEmpty()) {
            rows.set(0, withMask(rows.get(0), CREDIT_AKSK_MASK));
        }
        boolean anyOk = rows.stream().anyMatch(CreditReading::ok);
        CreditPanelPayload payload = new CreditPanelPayload("test", baseUrl, kind, candidates, level,
                null, anyOk, currency, creditTotal(anyRemain ? sumRemain : null, sumUsed), rows, false, null, null, null);
        return ApiResult.ok(payload);
    }

    /**
     * 余额试查请求体（编辑页表单的即时快照）。
     *
     * @param url        上游地址（表单里的 apiUrl）
     * @param platform   厂商标识（表单里的 providerType）
     * @param authScheme 认证方式（决定余额令牌放 Authorization 还是自定义头）
     * @param extra      表单即将保存的 extra 片段（credit_* 与 auth_header / auth_prefix）
     * @param keys       余额密钥列表（每行一个，逐 Key 试查）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CreditTestRequest(String url, String platform,
                             @JsonProperty("auth_scheme") String authScheme,
                             Map<String, Object> extra, List<String> keys) {
    }

    /**
     * 找供应商的「账号登录」入口页。
     *
     * <p>优先级：页面显式配置（extra.credit_login_url）→ 候选余额实现里第一个
     * 给出登录页的（如 Agnes / DeepSeek / sub2api，其中 sub2api 按站点地址推出登录页）。</p>
     *
     * @param entity 供应商
     * @param extra  供应商扩展配置
     * @return 登录页地址；没有支持账号登录的实现为 null
     */
    private String findLoginUrl(SysAccountChannel entity, Map<String, Object> extra) {
        String explicit = str(extra, "credit_login_url");
        if (explicit != null) {
            return explicit;
        }
        for (String kind : candidateKinds(entity, extra)) {
            CreditQueryProvider provider = CreditQueries.find(kind);
            if (provider != null && provider.loginPageUrl(entity.getBaseUrl()) != null) {
                return provider.loginPageUrl(entity.getBaseUrl());
            }
        }
        return null;
    }

    /**
     * 单个 Key 的余额读数：按候选实现依次尝试，第一条成功的胜出。
     *
     * <p>全部失败时把各实现的错误拼进一行返回，页面据此提示「为什么查不到」，
     * 而不是笼统显示查询失败。</p>
     *
     * @param entity 供应商
     * @param extra  供应商扩展配置
     * @param key    API Key（作为查询令牌）
     * @param index  Key 序号（从 0 开始，页面按它排序）
     * @return 单个 Key 的读数行
     */
    private CreditReading creditOfKey(SysAccountChannel entity, Map<String, Object> extra,
                                      String key, int index) {
        List<CreditQuery> queries = creditCandidates(entity, extra, key);
        List<String> failures = new ArrayList<>(queries.size());
        for (CreditQuery query : queries) {
            CreditResult result = CreditQueries.query(query);
            if (result.isOk()) {
                return creditRow(result, index, key);
            }
            String reason = result.getError() == null ? "未返回余额" : result.getError();
            failures.add(query.getKind() + ": " + reason);
        }
        return new CreditReading(index, maskKey(key), false, null, null, null, null, null, null,
                creditErrorText(failures), null);
    }

    /**
     * 组装候选余额查询：显式配置优先，否则按厂商与地址自动回落。
     *
     * @param entity 供应商
     * @param extra  供应商扩展配置
     * @param key    查询令牌
     * @return 候选查询（按尝试顺序）
     */
    private List<CreditQuery> creditCandidates(SysAccountChannel entity, Map<String, Object> extra, String key) {
        List<String> kinds = candidateKinds(entity, extra);
        String kind = explicitCreditKind(extra);
        String explicitUrl = str(extra, "credit_url");
        String expression = str(extra, "credit_expression");
        String brand = creditBrand(entity, extra);
        // 显式指定唯一实现时，候选只有它一条
        List<String> attempts = kind != null ? List.of(kind) : kinds;
        List<CreditQuery> queries = new ArrayList<>(attempts.size());
        for (String candidate : attempts) {
            CreditQuery query = new CreditQuery();
            query.setKind(candidate);
            query.setProviderId(brand);
            query.setExpression(expression);
            // Key 所属站点：host / openai 用它自证「这把 Key 是不是这家的」
            query.setOriginUrl(entity.getBaseUrl());
            // 厂商官方 / OpenAI 官方 / 登记表 / Moss 实现自带地址，只有显式配了地址才覆盖；
            // billing / custom 需要站点根地址，没配就用供应商的上游地址
            boolean builtinUrl = "host".equals(candidate) || "openai".equals(candidate)
                    || "registry".equals(candidate) || "moss".equals(candidate)
                    || "agnes".equals(candidate) || "deepseek".equals(candidate)
                    || "opencode".equals(candidate) || "opencode-console".equals(candidate)
                    || "shusheng".equals(candidate) || "zai".equals(candidate)
                    || "volc-plan".equals(candidate)
                    || "ppio".equals(candidate) || "anthropic".equals(candidate)
                    || "xai".equals(candidate) || "sensenova".equals(candidate);
            String url = explicitUrl != null ? explicitUrl : (builtinUrl ? null : entity.getBaseUrl());
            query.setUrl(url);
            if ("custom".equals(candidate)) {
                // 自定义映射：后端字段 → JSONPath / XPath 表达式
                query.setMapping(creditMappings(extra));
            }
            applyCreditAuth(query, entity, extra, key, candidate);
            applyCreditClientHeaders(query, extra);
            // AK/SK 签名类余额接口（如阿里云百炼走 BSS 账户余额）从扩展配置取密钥；
            // Bearer 类实现不读这两个字段，配了也无副作用
            String accessKey = str(extra, "credit_access_key");
            String secretKey = str(extra, "credit_secret_key");
            if (accessKey != null && !accessKey.isBlank()) {
                query.setAccessKey(accessKey.trim());
            }
            if (secretKey != null && !secretKey.isBlank()) {
                query.setSecretKey(secretKey.trim());
            }
            queries.add(query);
        }
        return queries;
    }

    /**
     * 透传页面配置的 UA / 自定义请求头到余额查询。
     *
     * <p>{@code extra.credit_ua} 覆盖默认 User-Agent；{@code extra.credit_headers}
     * 支持 {@code {name:value}} 对象与 {@code [{name,value}]} 数组两种页面形态。
     * 不配置时不设头，由 {@code CreditHttp} 落到后端默认值。</p>
     *
     * @param query 余额查询请求
     * @param extra 供应商扩展配置
     */
    private static void applyCreditClientHeaders(CreditQuery query, Map<String, Object> extra) {
        String ua = strOf(extra, "credit_ua");
        if (ua != null && !ua.isBlank()) {
            query.header("User-Agent", ua.trim());
        }
        Object raw = extra.get("credit_headers");
        if (raw instanceof Map<?, ?> headers) {
            for (Map.Entry<?, ?> entry : headers.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                String name = entry.getKey().toString().trim();
                String value = entry.getValue().toString().trim();
                if (!name.isBlank() && !value.isBlank()) {
                    query.header(name, value);
                }
            }
        } else if (raw instanceof List<?> rows) {
            for (Object item : rows) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Object name = map.get("name");
                Object value = map.get("value");
                if (name == null || value == null) {
                    continue;
                }
                String nameText = name.toString().trim();
                String valueText = value.toString().trim();
                if (!nameText.isBlank() && !valueText.isBlank()) {
                    query.header(nameText, valueText);
                }
            }
        }
    }

    /**
     * 读取显式配置的余额实现（extra.credit_kind）。
     *
     * <p>「自动」在表单里是删除键；历史上若已存成字面量 auto，按未配置处理。</p>
     *
     * @param extra 供应商扩展配置
     * @return 实现标识；未配置或「自动」时为 null
     */
    private String explicitCreditKind(Map<String, Object> extra) {
        String kind = str(extra, "credit_kind");
        if (kind == null || "auto".equalsIgnoreCase(kind.trim())) {
            return null;
        }
        return kind.trim();
    }

    /**
     * 自动识别时按顺序尝试的实现标识。
     *
     * <p>也是页面「已尝试」链路的唯一来源：查不到余额时把它显示出来，
     * 用户就能判断该换哪条口径（或该去配 credit_url + 表达式）。</p>
     *
     * @param entity 供应商
     * @param extra  供应商扩展配置
     * @return 实现标识（按尝试顺序）
     */
    private List<String> candidateKinds(SysAccountChannel entity, Map<String, Object> extra) {
        String kind = explicitCreditKind(extra);
        if (kind != null) {
            return List.of(kind);
        }
        List<String> kinds = new ArrayList<>(5);
        String brand = creditBrand(entity, extra);
        // DeepSeek 平台门户余额接口：用「账号登录」截获的门户会话令牌查
        // platform.deepseek.com，排最前（会话令牌在官方 API 余额接口上必失败，
        // 先试它省一次白发请求）；登记表里的 API Key 口径（host）保留在后面兜底
        if (isDeepSeekProvider(entity)) {
            kinds.add("deepseek");
        }
        // OpenCode（Go / Zen）官方用量 + 预算聚合接口：API Key Bearer 鉴权，多端点聚合
        // （登记表单表达式装不下），只对 OpenCode 官方站点有意义，放 host 之前
        if (isOpencodeProvider(entity)) {
            kinds.add("opencode");
        }
        // OpenCode 控制台现金余额：控制台同源接口（非公开文档 API），需 __Host-console_session
        // 会话 Cookie（API Key 会 403），放在官方 API 聚合之后作为 Zen 现金余额口径的回落
        if (isOpencodeProvider(entity)) {
            kinds.add("opencode-console");
        }
        // 官方预设只在这家 Key 确实属于该厂商站点时才试：中转站的 Key 问厂商官方接口只会 401
        if (brand != null && HostCreditQueryProvider.supportsHost(brand)
                && HostCreditQueryProvider.sameOrigin(brand, entity.getBaseUrl())) {
            kinds.add("host");
        }
        // 阿里云百炼（DashScope）的 sk- API Key 没有厂商余额接口（/v1/credits 等路径均 404），
        // 账户余额走登记表的阿里云 BSS 预设（AK/SK 签名，品牌别名挂在 aliyun-bss 上）；
        // 缺 AK/SK 时签名器提前拦下并提示配置，不发必然失败的请求
        if (isAliyunBailianProvider(entity)) {
            kinds.add("host");
        }
        // 腾讯云 / 华为云 / 七牛云 / 百度千帆：模型域名（hunyuan.cloud.tencent.com /
        // modelarts-maas.com / api.qnaigc.com / qianfan.baidubce.com）与计费域名不同根，
        // 同样走登记表的账户余额预设（AK/SK 签名，品牌别名挂在 tencent-billing / huawei-bss /
        // qiniu / baidu-bce 上）；缺 AK/SK 时签名器提前拦下并提示配置，不发必然失败的请求
        if (isTencentCloudProvider(entity) || isHuaweiCloudProvider(entity) || isQiniuProvider(entity)
                || isQianfanProvider(entity)) {
            kinds.add("host");
        }
        // 豆包（火山方舟）：模型域名（ark.cn-beijing.volces.com）与计费域名
        // （billing.volcengineapi.com）不同根。订阅聚合（余额 + 个人套餐 + 用量窗口，
        // kind=volc-plan）放最前——首个成功者胜出，聚合成功时把纯余额的 host 口径
        // 顶掉，页面才能看到订阅窗口；聚合失败（如方舟接口抖动）仍有 host 纯余额兜底。
        // 均为 AK/SK 签名，缺 AK/SK 时各自提前拦下并提示配置，不发必然失败的请求
        if (isDoubaoProvider(entity)) {
            kinds.add("volc-plan");
            kinds.add("host");
        }
        if (isOfficialOpenAi(entity.getBaseUrl())) {
            kinds.add("openai");
        }
        // Agnes 平台余额接口：只对 Agnes 站点有意义，别的站点上纯属白发请求；
        // 放在通用探测之前，Agnes 站点不必先白发 billing / new-api / sub2api 三个请求
        if (isAgnesProvider(entity)) {
            kinds.add("agnes");
        }
        // SenseNova（商汤）平台余额接口：只对商汤官方站点有意义，同上放探测之前
        if (isSenseNovaProvider(entity)) {
            kinds.add("sensenova");
        }
        // 书生（InternAI）平台余额接口：用「账号登录」截获的门户会话 Cookie（uaa-token）
        // 查 discovery.intern-ai.org.cn，只对书生官方站点有意义，同上放探测之前
        if (isShushengProvider(entity)) {
            kinds.add("shusheng");
        }
        // sub2api 订阅余额接口：用「账号登录」截获的会话 JWT 查 /api/v1/subscriptions，
        // 只对 platform=sub2api 的行有意义，放通用探测之前
        if (isSub2ApiProvider(entity)) {
            kinds.add("sub2api");
        }
        // MiniMax 平台余额接口：用「账号登录」截获的会话令牌查 Coding Plan 配额，
        // 只对 MiniMax 官方站点有意义，放通用探测之前（kind 用 minimax-platform，避开 account 旧渠道体系已占用的 minimax）
        if (isMinimaxProvider(entity)) {
            kinds.add("minimax-platform");
        }
        // ModelScope（魔搭）没有独立余额端点：发一次 max_tokens=1 的最小对话请求，
        // 从响应头读当日限流配额；只对魔搭官方站点有意义，放通用探测之前
        if (isModelScopeProvider(entity)) {
            kinds.add("modelscope");
        }
        // z.ai（智谱 GLM）余额与 Coding Plan 配额：标准 API Key Bearer 鉴权，按 Key 所属
        // 站点取国际站 api.z.ai / 国内站 open.bigmodel.cn 口径，放通用探测之前
        if (isZaiProvider(entity)) {
            kinds.add("zai");
        }
        // OpenRouter 余额：官方 /api/v1/credits 返回累计充值/消费、需管理 Key，普通推理 Key
        // 会 403，实现里自动回落 /api/v1/key 读本 Key 用量；只对 OpenRouter 官方站点有意义，
        // 放通用探测之前
        if (isOpenRouterProvider(entity)) {
            kinds.add("openrouter");
        }
        // PPIO 派欧云余额：计费域 api.ppio.com 与模型网关 api.ppinfra.com 不同主域，
        // 登记表 sameOrigin 兜底不可用，走专用实现；只对 PPIO 官方站点有意义，
        // 放通用探测之前
        if (isPpioProvider(entity)) {
            kinds.add("ppio");
        }
        // Anthropic 官方预付余额：官方没有 API-Key 余额接口，走控制台非公开接口
        // （sessionKey 会话 Cookie 鉴权，org_id 可自动发现）；只对 Anthropic 官方站点
        // 有意义，放通用探测之前
        if (isAnthropicProvider(entity)) {
            kinds.add("anthropic");
        }
        // xAI 官方预付余额：余额在 Management API，需管理密钥（推理 API Key 查不了），
        // team_id 可经管理密钥校验接口自动发现；只对 xAI 官方站点有意义，放通用探测之前
        if (isXaiProvider(entity)) {
            kinds.add("xai");
        }
        // 探测只对「上游不是厂商官方站点」有意义：官方站点没有 OpenAI 兼容计费接口，
        // 拿官方 Key 去问 /v1/dashboard/billing/* 只会 404，纯属白发一次请求。
        // 显式配了 credit_url 时以用户配置为准，照旧探测。
        if (str(extra, "credit_url") != null || !isOfficialSite(entity)) {
            // 自动适配：先按站点指纹认出中转网关软件（new-api / sub2api），把专属实现
            // 排到通用计费探测之前；认不出维持通用探测。指纹按站点缓存，不逐请求探测。
            for (String relayKind : relayCreditKinds(entity)) {
                if (!kinds.contains(relayKind)) {
                    kinds.add(relayKind);
                }
            }
        }
        // Moss 非官方余额接口：只对 Moss 站点有意义，别的站点上纯属白发请求
        if (isMossProvider(entity)) {
            kinds.add("moss");
        }
        if (str(extra, "credit_expression") != null) {
            kinds.add(CreditQueries.KIND_EXPRESSION);
        }
        return kinds;
    }

    /**
     * 自定义供应商的「自动适配」：按站点指纹认出中转网关软件，返回它的专属余额实现。
     *
     * <p>自定义供应商不登记平台名，余额口径原本只能靠通用探测（OpenAI dashboard 计费
     * 接口）碰运气。这里先认网关：sub2api 站点的公开模型广场（{@code /api/v1/model-plaza}）
     * 与 new-api / one-api 系站点的 {@code /api/status} 都是各自软件的标志性端点。认出后把
     * 专属实现排到通用探测之前——new-api 按 {@code /api/usage/token} 的站点额度口径读数
     * （500000 额度 = 1 美元）；sub2api 按订阅口径读数（要「账号登录」截获的会话 JWT，
     * API Key 查不了，面板会给出登录入口，见 {@code Sub2ApiCreditQueryProvider}）。</p>
     *
     * <p>指纹按站点缓存（{@link #relayFingerprints}），不逐请求探测。认不出（或站点无
     * 响应）返回空列表，候选链保持原有通用探测；认错了也无害——专属实现对不上的站点
     * 会自然失败，链路继续落回通用探测，不会产出错误读数。</p>
     *
     * @param entity 供应商
     * @return 认出的余额实现标识（按尝试顺序）；未认出为空列表
     */
    private List<String> relayCreditKinds(SysAccountChannel entity) {
        String base = relayProbeBase(entity.getBaseUrl());
        if (base == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        RelayFingerprint cached = relayFingerprints.get(base);
        if (cached != null && now - cached.at() < RELAY_FINGERPRINT_TTL_MILLIS) {
            return cached.kinds();
        }
        List<String> kinds = probeRelayKinds(entity.getProxy(), base);
        relayFingerprints.put(base, new RelayFingerprint(List.copyOf(kinds), now));
        return kinds;
    }

    /**
     * 逐个探测站点指纹，认出中转网关软件。
     *
     * @param proxy 代理地址（可为空）
     * @param base  站点根地址
     * @return 认出的余额实现标识（按尝试顺序）；未认出为空列表
     */
    private List<String> probeRelayKinds(String proxy, String base) {
        List<String> kinds = new ArrayList<>(2);
        // sub2api：模型广场是它的公开标志性端点（code/data.groups 信封），无需密钥。
        // 排在 new-api 之前：sub2api 的订阅接口认「账号登录」的会话 JWT，命中后即使
        // 还没绑定账号，失败原因也会明确出现在「已尝试」链路里提示用户去绑定
        if (isSub2ApiGateway(proxy, base)) {
            kinds.add("sub2api");
        }
        // new-api / one-api 系：/api/status 是这一族网关共同的标志性端点
        // （success=true + data.version）。纯 one-api 没有 /api/usage/token，
        // 误认成 new-api 时该候选自然失败，链路落回通用探测，不会出错数
        if (isNewApiGateway(proxy, base)) {
            kinds.add("new-api");
        }
        return kinds;
    }

    /**
     * 是否是 sub2api 站点：公开的模型广场端点返回它的 {@code code} / {@code data.groups} 信封。
     *
     * @param proxy 代理地址（可为空）
     * @param base  站点根地址
     * @return 命中 sub2api 指纹为 true
     */
    private boolean isSub2ApiGateway(String proxy, String base) {
        String raw = relayProbeGet(proxy, CreditHttp.join(base, "/api/v1/model-plaza"));
        if (raw == null) {
            return false;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(raw);
            return root.path("code").isNumber() && root.path("data").has("groups");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 是否是 new-api / one-api 系站点：{@code /api/status} 返回成功信封与版本号。
     *
     * @param proxy 代理地址（可为空）
     * @param base  站点根地址
     * @return 命中 new-api / one-api 系指纹为 true
     */
    private boolean isNewApiGateway(String proxy, String base) {
        String raw = relayProbeGet(proxy, CreditHttp.join(base, "/api/status"));
        if (raw == null) {
            return false;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(raw);
            return root.path("success").asBoolean(false) && root.path("data").has("version");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 发一次只读 GET 指纹探测。
     *
     * @param proxy 代理地址（可为空）
     * @param url   探测地址
     * @return 2xx 响应体；非 2xx 或网络异常为 null
     */
    private String relayProbeGet(String proxy, String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(RELAY_PROBE_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("User-Agent", "spring-support-ai/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient(proxy).send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                return null;
            }
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 站点根地址：剥掉尾部接口前缀（长前缀优先），得到协议 + 主机 + 部署子路径。
     *
     * @param url 供应商上游地址
     * @return 站点根地址；缺失、非 http(s) 时为 null
     */
    private static String relayProbeBase(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String text = url.trim();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        for (String suffix : RELAY_API_PREFIXES) {
            if (text.endsWith(suffix)) {
                text = text.substring(0, text.length() - suffix.length());
                break;
            }
        }
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.startsWith("http://") || text.startsWith("https://") ? text : null;
    }

    /**
     * 「检测协议」时顺带识别地址背后的中转网关软件，创建自定义供应商当场告知
     * 余额会按哪套口径自动查询。
     *
     * @param apiUrl 上游地址
     * @param proxy  代理地址（可为空）
     * @return 网关名称与说明；未认出为 null
     */
    private Map<String, Object> detectRelayGateway(String apiUrl, String proxy) {
        String base = relayProbeBase(apiUrl);
        if (base == null) {
            return null;
        }
        if (isSub2ApiGateway(proxy, base)) {
            return Map.of("name", "sub2api", "label", "sub2api 网关",
                    "detail", "余额将按订阅口径自动查询；请在该供应商的余额面板绑定「账号登录」获取会话令牌");
        }
        if (isNewApiGateway(proxy, base)) {
            return Map.of("name", "new-api", "label", "new-api 网关",
                    "detail", "余额将按站点额度口径自动查询（500000 额度 = 1 美元）");
        }
        return null;
    }

    /**
     * 一次网关指纹识别的结论：认出的余额实现（按尝试顺序）与识别时间。
     *
     * @param kinds 余额实现标识
     * @param at    识别时间（epoch 毫秒）
     */
    private record RelayFingerprint(List<String> kinds, long at) {
    }

    /**
     * 余额口径分级（只读判定，不出网）。
     *
     * <p>200+ 家供应商不可能逐家写死余额实现，页面按这几级告诉用户「这家凭什么能查 /
     * 为什么查不到」，而不是只回一句失败：</p>
     * <ul>
     *   <li>{@code official}：厂商在余额登记表里有官方预设；</li>
     *   <li>{@code openai}：上游就是 OpenAI 官方；</li>
     *   <li>{@code uncovered}：上游是厂商官方站点，但没有可用的官方余额接口；</li>
     *   <li>{@code relay}：中转 / 聚合站，按协议依次探测（OpenAI 兼容计费）；</li>
     *   <li>{@code manual}：页面上指定了唯一实现（含自定义地址 + 表达式）；</li>
     *   <li>{@code none}：没有上游地址，没有可查的东西。</li>
     * </ul>
     *
     * @param entity 供应商
     * @param extra  供应商扩展配置
     * @return 分级标识
     */
    private String creditLevel(SysAccountChannel entity, Map<String, Object> extra) {
        if (explicitCreditKind(extra) != null) {
            return "manual";
        }
        String brand = creditBrand(entity, extra);
        if (brand != null && HostCreditQueryProvider.supportsHost(brand)
                && HostCreditQueryProvider.sameOrigin(brand, entity.getBaseUrl())) {
            return "official";
        }
        if (isOfficialOpenAi(entity.getBaseUrl())) {
            return "openai";
        }
        // 本地推理（ONNX / 本机地址）没有远端账户，不存在余额可查
        if (isLocalEndpoint(entity.getBaseUrl())) {
            return "none";
        }
        boolean remote = (entity.getBaseUrl() != null && !entity.getBaseUrl().isBlank())
                || str(extra, "credit_url") != null;
        if (!remote) {
            return "none";
        }
        // Agnes / DeepSeek / SenseNova / 书生 / sub2api / MiniMax 有专属平台实现（官方平台接口 + 账号登录取令牌）：
        // 即使登记表把它们当官方站点，也不算「未收录」，照常查询；
        // 阿里云百炼 / 腾讯云 / 华为云 / 七牛云 / 百度千帆 / 豆包（火山方舟）走登记表的账户余额预设（AK/SK 签名），同样算已收录；
        // ModelScope（魔搭）走「对话请求读响应头限流配额」的专用实现，也算已收录；
        // OpenRouter 走专用实现（/credits 需管理 Key，普通推理 Key 回落 /api/v1/key），也算已收录；
        // OpenCode 走官方用量 + 预算聚合的专属实现（kind=opencode），也算已收录；
        // PPIO / Anthropic / xAI 走各自官方余额口径的专属实现（跨主域 / 控制台 Cookie / Management API），也算已收录；
        // Moss 走门户非官方余额接口（platform.moss.cn，Cookie 会话令牌，kind=moss），也算已收录：
        // 不加这条会被上面的「官方站点未收录」分支拦下，余额面板永远只显示「未收录」而发不出查询
        if (isAgnesProvider(entity) || isDeepSeekProvider(entity) || isSenseNovaProvider(entity)
                || isShushengProvider(entity) || isSub2ApiProvider(entity) || isMinimaxProvider(entity)
                || isAliyunBailianProvider(entity) || isTencentCloudProvider(entity)
                || isHuaweiCloudProvider(entity) || isQiniuProvider(entity)
                || isQianfanProvider(entity) || isDoubaoProvider(entity)
                || isModelScopeProvider(entity) || isZaiProvider(entity)
                || isOpenRouterProvider(entity) || isOpencodeProvider(entity)
                || isPpioProvider(entity) || isAnthropicProvider(entity) || isXaiProvider(entity)
                || isMossProvider(entity)) {
            return "official";
        }
        // 官方站点但没有可用的官方余额接口：不当中转站，一个请求都不发。
        // 官方站点上没有 OpenAI 兼容计费接口，探测只会 404，还会把「查不到」写成假结论。
        // 例外：页面显式配了 credit_url（自定义地址）时以用户配置为准，照旧探测。
        if (str(extra, "credit_url") == null && isOfficialSite(entity)) {
            return "uncovered";
        }
        return "relay";
    }

    /**
     * 上游是否是该供应商自己的官方站点。
     *
     * <p>依据是供应商登记表里每家的 {@code hosts}（与目录 / 定价 / 能力位共用同一份数据），
     * 见 {@link ProviderRegistries#isOfficialSite(String, String)}。登记表未收录的供应商
     * 返回 {@code false}，按中转处理，维持原有回落链路。</p>
     *
     * @param entity 供应商
     * @return 官方站点为 true
     */
    private static boolean isOfficialSite(SysAccountChannel entity) {
        return ProviderRegistries.isOfficialSite(entity.getPlatform(), entity.getBaseUrl());
    }

    /**
     * 上游地址是否指向本机（本地推理没有远端账户余额）。
     *
     * @param url 上游地址
     * @return 本机地址为 true
     */
    private static boolean isLocalEndpoint(String url) {
        String host = hostOf(url);
        if (host == null) {
            return false;
        }
        return "localhost".equals(host) || "127.0.0.1".equals(host) || "0.0.0.0".equals(host)
                || "::1".equals(host) || "[::1]".equals(host);
    }

    /**
     * 把供应商的认证方式带到余额请求上。
     *
     * <p>口径与出站聊天请求一致：头名取 {@code extra.auth_header}（缺省
     * {@code Authorization}），前缀取 {@code extra.auth_prefix}（自定义头缺省无前缀）。
     * 自定义头走 {@link CreditQuery#header}，标准头走令牌字段，交给请求出口统一补前缀。</p>
     *
     * <p>例外：Moss 门户与 MiniMax 控制台认 Cookie 会话令牌（Moss 为
     * {@code moss_studio_prod_access_token}，MiniMax 确切 Cookie 名以真实登录观测为准），
     * 令牌原值交给各自实现包装成 Cookie；Agnes / DeepSeek / sub2api / z.ai 余额接口
     * 固定 Bearer，令牌原值交给请求出口补前缀。这些实现都不走渠道鉴权前缀。</p>
     *
     * @param query  余额查询
     * @param entity 供应商
     * @param extra  供应商扩展配置
     * @param key    API Key
     * @param kind   余额实现标识
     */
    private static void applyCreditAuth(CreditQuery query, SysAccountChannel entity,
                                        Map<String, Object> extra, String key, String kind) {
        if ("moss".equals(kind) || "agnes".equals(kind) || "deepseek".equals(kind) || "sub2api".equals(kind)
                || "minimax-platform".equals(kind) || "zai".equals(kind) || "opencode".equals(kind)
                || "opencode-console".equals(kind) || "ppio".equals(kind)
                || "anthropic".equals(kind) || "xai".equals(kind)) {
            // Moss / MiniMax / OpenCode / Anthropic 控制台认 Cookie 会话（opencode-console 为
            // __Host-console_session、anthropic 为 sessionKey，由各自实现包装成 Cookie 头）、
            // Agnes / DeepSeek / sub2api / z.ai / OpenCode / PPIO / xAI 官方 API 认 Bearer：
            // 令牌原值交给各自实现 / 请求出口包装，不走渠道鉴权前缀，避免与余额接口的实际口径错位
            query.setToken(key);
            return;
        }
        if (AuthHeaders.isNone(entity.getAuthScheme())) {
            return;
        }
        String rawHeader = strOf(extra, "auth_header");
        String header = AuthHeaders.headerName(rawHeader);
        String prefix = AuthHeaders.headerPrefix(rawHeader, strOf(extra, "auth_prefix"));
        String value = prefix + key;
        if (AuthHeaders.DEFAULT_HEADER.equalsIgnoreCase(header)) {
            query.setToken(value);
        } else {
            query.header(header, value);
        }
    }

    /**
     * 供应商对应的厂商标识（用于 host 余额实现）。
     *
     * <p>优先取 {@code extra.credit_host}，其次按上游地址主机名查登记表，再次平台名，
     * 最后从上游地址的域名模糊推断；推断不出时回落平台名，非收录厂商会由实现返回
     * 「未收录」。</p>
     *
     * <p>上游地址命中登记表主机时以地址为准：同一厂商的多个站点是不同登记条目
     * （如 SiliconFlow 国内站 api.siliconflow.cn 与国际站 api.siliconflow.com），
     * 平台名只能命中其中一个，拿错站预设会被同源校验拦下，官方余额候选被误杀。</p>
     *
     * @param entity 供应商
     * @param extra  供应商扩展配置
     * @return 厂商标识；都取不到时为 null
     */
    private static String creditBrand(SysAccountChannel entity, Map<String, Object> extra) {
        String explicit = strOf(extra, "credit_host");
        if (explicit != null) {
            return explicit.trim().toLowerCase(Locale.ROOT);
        }
        String platform = entity.getPlatform() == null
                ? "" : entity.getPlatform().trim().toLowerCase(Locale.ROOT);
        String host = hostOf(entity.getBaseUrl());
        if (host != null) {
            String byHost = HostCreditQueryProvider.brandByHost(host);
            if (byHost != null && !byHost.isBlank()) {
                return byHost;
            }
        }
        if (CREDIT_HOST_BRANDS.contains(platform)) {
            return platform;
        }
        if (host != null) {
            for (String brand : CREDIT_HOST_BRANDS) {
                if (host.contains(brand)) {
                    return brand;
                }
            }
        }
        if (!platform.isBlank()) {
            return platform;
        }
        return host;
    }

    /**
     * 上游地址是否是 OpenAI 官方（官方余额接口只对它有意义）。
     *
     * @param url 上游地址
     * @return 官方域名时为 true
     */
    private static boolean isOfficialOpenAi(String url) {
        String host = hostOf(url);
        return host != null && ("api.openai.com".equals(host) || host.endsWith(".openai.com"));
    }

    /**
     * 是否是 Moss 站点：只有它才值得试 Moss 门户的非官方余额接口。
     *
     * <p>Moss 的对话 API 在 {@code api.mosi.cn}，余额接口在 {@code platform.moss.cn}，
     * 域名不同源，所以平台名与两个域名都认。</p>
     *
     * @param entity 供应商
     * @return Moss 站点为 true
     */
    private static boolean isMossProvider(SysAccountChannel entity) {
        String platform = entity.getPlatform() == null
                ? "" : entity.getPlatform().trim().toLowerCase(Locale.ROOT);
        if ("moss".equals(platform)) {
            return true;
        }
        String host = hostOf(entity.getBaseUrl());
        return host != null && (host.endsWith("moss.cn") || host.endsWith("mosi.cn"));
    }

    /**
     * 是否是 Agnes 站点：只有它才值得试 Agnes 平台余额接口与「账号登录」。
     *
     * <p>Agnes 的对话与余额接口都在 {@code *.agnes-ai.com}，平台名与域名都认。</p>
     *
     * @param entity 供应商
     * @return Agnes 站点为 true
     */
    private static boolean isAgnesProvider(SysAccountChannel entity) {
        String platform = entity.getPlatform() == null
                ? "" : entity.getPlatform().trim().toLowerCase(Locale.ROOT);
        if ("agnes".equals(platform) || "agnes-ai".equals(platform) || "agnes ai".equals(platform)) {
            return true;
        }
        String host = hostOf(entity.getBaseUrl());
        return host != null && host.endsWith("agnes-ai.com");
    }

    /**
     * 是否是 DeepSeek 站点：只有它才值得试 DeepSeek 平台门户余额接口与「账号登录」。
     *
     * <p>DeepSeek 的对话 API 在 {@code api.deepseek.com}（登记表里的 API Key 余额
     * 口径），门户与余额接口在 {@code platform.deepseek.com}（会话令牌口径），
     * 平台名与两个域名都认。</p>
     *
     * @param entity 供应商
     * @return DeepSeek 站点为 true
     */
    private static boolean isDeepSeekProvider(SysAccountChannel entity) {
        String platform = entity.getPlatform() == null
                ? "" : entity.getPlatform().trim().toLowerCase(Locale.ROOT);
        if ("deepseek".equals(platform)) {
            return true;
        }
        String host = hostOf(entity.getBaseUrl());
        return host != null && host.endsWith("deepseek.com");
    }

    /**
     * 是否是 SenseNova（商汤）站点：只有它才值得试 SenseNova 平台余额接口与「账号登录」。
     *
     * <p>SenseNova 的对话 API 在 {@code token.sensenova.cn}，登录与余额接口在
     * {@code platform.sensenova.cn}，平台名（含别名 sensetime）与域名都认。</p>
     *
     * @param entity 供应商
     * @return SenseNova 站点为 true
     */
    private static boolean isSenseNovaProvider(SysAccountChannel entity) {
        String platform = entity.getPlatform() == null
                ? "" : entity.getPlatform().trim().toLowerCase(Locale.ROOT);
        if ("sensenova".equals(platform) || "sensetime".equals(platform)) {
            return true;
        }
        String host = hostOf(entity.getBaseUrl());
        return host != null && host.endsWith("sensenova.cn");
    }

    /**
     * 是否是书生（InternAI）站点：只有它才值得试书生平台余额接口与「账号登录」。
     *
     * <p>书生的对话 API 在 {@code discovery-api.intern-ai.org.cn}，门户与墨点余额接口在
     * {@code discovery.intern-ai.org.cn}（SSO 登录在 sso.openxlab.org.cn），平台名
     * （含别名 intern-ai / internai）与域名都认。</p>
     *
     * @param entity 供应商
     * @return 书生站点为 true
     */
    private static boolean isShushengProvider(SysAccountChannel entity) {
        String platform = entity.getPlatform() == null
                ? "" : entity.getPlatform().trim().toLowerCase(Locale.ROOT);
        if ("shusheng".equals(platform) || "intern-ai".equals(platform) || "internai".equals(platform)) {
            return true;
        }
        String host = hostOf(entity.getBaseUrl());
        return host != null && host.endsWith("intern-ai.org.cn");
    }

    /**
     * 是否是阿里云百炼（DashScope / 通义千问）站点。
     *
     * <p>百炼的 sk- API Key 没有厂商余额接口，账户余额走登记表里的阿里云 BSS 预设
     * （AK/SK 签名，品牌别名挂在 aliyun-bss 上），所以平台名（含 dashscope / qwen /
     * bailian / tongyi / aliyun / alibaba 及编程、Token 套餐端点别名）与
     * aliyuncs.com 域名都认。</p>
     *
     * @param entity 供应商
     * @return 阿里云百炼站点为 true
     */
    private static boolean isAliyunBailianProvider(SysAccountChannel entity) {
        String platform = entity.getPlatform() == null
                ? "" : entity.getPlatform().trim().toLowerCase(Locale.ROOT);
        if ("aliyun-bailian".equals(platform) || "dashscope".equals(platform) || "bailian".equals(platform)
                || "qwen".equals(platform) || "tongyi".equals(platform) || "aliyun".equals(platform)
                || "alibaba".equals(platform) || "alibaba-coding-plan-cn".equals(platform)
                || "alibaba-coding-plan".equals(platform) || "alibaba-token-plan-cn".equals(platform)
                || "alibaba-token-plan".equals(platform)) {
            return true;
        }
        String host = hostOf(entity.getBaseUrl());
        return host != null && host.endsWith("aliyuncs.com");
    }

    /**
     * 是否是腾讯云（混元）站点。
     *
     * <p>腾讯云的对话 API 在 {@code api.hunyuan.cloud.tencent.com}，账户余额走登记表里的
     * 腾讯云 Billing 预设（AK/SK 签名，品牌别名挂在 tencent-billing 上），所以平台名
     * （含 tencent / tencentcloud / hunyuan 等写法）与 tencent 系域名都认。</p>
     *
     * @param entity 供应商
     * @return 腾讯云站点为 true
     */
    private static boolean isTencentCloudProvider(SysAccountChannel entity) {
        String platform = entity.getPlatform() == null
                ? "" : entity.getPlatform().trim().toLowerCase(Locale.ROOT);
        if (platform.startsWith("tencent") || "hunyuan".equals(platform)) {
            return true;
        }
        String host = hostOf(entity.getBaseUrl());
        return host != null && (host.endsWith("tencentcloudapi.com") || host.endsWith("tencent.com"));
    }

    /**
     * 是否是华为云（MaaS）站点。
     *
     * <p>华为云 MaaS 的对话 API 在 {@code api.modelarts-maas.com}，账户余额走登记表里的
     * 华为云 BSS 预设（AK/SK 签名，品牌别名挂在 huawei-bss 上），所以平台名
     * （含 huawei / huaweicloud / huawei-maas 等写法）与相关域名都认。</p>
     *
     * @param entity 供应商
     * @return 华为云站点为 true
     */
    private static boolean isHuaweiCloudProvider(SysAccountChannel entity) {
        String platform = entity.getPlatform() == null
                ? "" : entity.getPlatform().trim().toLowerCase(Locale.ROOT);
        if (platform.startsWith("huawei")) {
            return true;
        }
        String host = hostOf(entity.getBaseUrl());
        return host != null && (host.endsWith("myhuaweicloud.com") || host.endsWith("modelarts-maas.com")
                || host.endsWith("huaweicloud.com"));
    }

    /**
     * 是否是七牛云站点。
     *
     * <p>七牛云 AI 推理的对话 API 在 {@code api.qnaigc.com}，账户余额走登记表里的
     * 七牛财务预设（AK/SK 签名，品牌别名挂在 qiniu 上），所以平台名
     * （qiniu / qiniu-ai）与两个域名都认。</p>
     *
     * @param entity 供应商
     * @return 七牛云站点为 true
     */
    private static boolean isQiniuProvider(SysAccountChannel entity) {
        String platform = entity.getPlatform() == null
                ? "" : entity.getPlatform().trim().toLowerCase(Locale.ROOT);
        if ("qiniu".equals(platform) || "qiniu-ai".equals(platform)) {
            return true;
        }
        String host = hostOf(entity.getBaseUrl());
        return host != null && (host.endsWith("qiniu.com") || host.endsWith("qnaigc.com"));
    }

    /**
     * 是否是百度千帆（百度智能云）站点。
     *
     * <p>千帆的对话 API 在 {@code qianfan.baidubce.com}，账户余额走登记表里的
     * 百度智能云财务预设（AK/SK 签名，品牌别名挂在 baidu-bce 上），所以平台名
     * （qianfan / qianfan-coding-plan）与 baidubce.com 域名都认。</p>
     *
     * @param entity 供应商
     * @return 百度千帆站点为 true
     */
    private static boolean isQianfanProvider(SysAccountChannel entity) {
        String platform = entity.getPlatform() == null
                ? "" : entity.getPlatform().trim().toLowerCase(Locale.ROOT);
        if ("qianfan".equals(platform) || "qianfan-coding-plan".equals(platform)) {
            return true;
        }
        String host = hostOf(entity.getBaseUrl());
        return host != null && host.endsWith("baidubce.com");
    }

    /**
     * 是否是豆包（火山方舟 / Volcengine Ark）站点。
     *
     * <p>方舟的对话 API 在 {@code ark.cn-beijing.volces.com}（OpenAI 兼容），没有独立的
     * 厂商余额接口，账户余额走登记表里的火山 BSS 预设（AK/SK 签名，品牌别名挂在
     * volc-billing 上），所以平台名（doubao / volcengine / volcengine-coding-plan /
     * ark 等写法）与 volcengine 系域名都认。</p>
     *
     * @param entity 供应商
     * @return 豆包（火山方舟）站点为 true
     */
    private static boolean isDoubaoProvider(SysAccountChannel entity) {
        String platform = entity.getPlatform() == null
                ? "" : entity.getPlatform().trim().toLowerCase(Locale.ROOT);
        if (platform.contains("doubao") || platform.startsWith("volcengine")
                || "ark".equals(platform) || platform.startsWith("ark-")) {
            return true;
        }
        String host = hostOf(entity.getBaseUrl());
        return host != null && (host.endsWith("volces.com") || host.endsWith("volcengine.com"));
    }

    /**
     * 是否是 sub2api 站点：只有它才值得试 sub2api 订阅余额接口与「账号登录」。
     *
     * <p>sub2api 是开源自建中转，没有统一官方域名，只能按供应商行登记的平台名识别
     * （目录别名 sub2api / sub-api 都认）。</p>
     *
     * @param entity 供应商
     * @return sub2api 站点为 true
     */
    private static boolean isSub2ApiProvider(SysAccountChannel entity) {
        String platform = entity.getPlatform() == null
                ? "" : entity.getPlatform().trim().toLowerCase(Locale.ROOT);
        return "sub2api".equals(platform) || "sub-api".equals(platform);
    }

    /**
     * 是否是 MiniMax 站点：只有它才值得试 MiniMax 平台余额接口与「账号登录」。
     *
     * <p>MiniMax 的对话 API 在 {@code api.minimaxi.chat} / {@code api.minimaxi.com}，
     * 控制台与 Coding Plan 配额接口在 {@code platform.minimax.cn} / {@code www.minimaxi.com}
     * （国际站 {@code *.minimax.io}），平台名与相关域名都认。</p>
     *
     * @param entity 供应商
     * @return MiniMax 站点为 true
     */
    private static boolean isMinimaxProvider(SysAccountChannel entity) {
        String platform = entity.getPlatform() == null
                ? "" : entity.getPlatform().trim().toLowerCase(Locale.ROOT);
        if (platform.contains("minimax")) {
            return true;
        }
        String host = hostOf(entity.getBaseUrl());
        return host != null && host.contains("minimax");
    }

    /**
     * 是否是 z.ai（智谱 GLM）站点：只有它才值得试 z.ai 余额与 Coding Plan 配额接口。
     *
     * <p>z.ai 的对话 API 在 {@code api.z.ai}（国际站）与 {@code open.bigmodel.cn}
     * （国内站），余额与 Coding Plan 配额也在同域；平台名（z.ai / zai / zhipu / glm
     * 等别名）与两个官方域名都认。</p>
     *
     * @param entity 供应商
     * @return z.ai 站点为 true
     */
    private static boolean isZaiProvider(SysAccountChannel entity) {
        String platform = entity.getPlatform() == null
                ? "" : entity.getPlatform().trim().toLowerCase(Locale.ROOT);
        if (platform.contains("zai") || platform.contains("zhipu") || platform.contains("glm")) {
            return true;
        }
        String host = hostOf(entity.getBaseUrl());
        if (host == null) {
            return false;
        }
        return "z.ai".equals(host) || host.endsWith(".z.ai")
                || "bigmodel.cn".equals(host) || host.endsWith(".bigmodel.cn");
    }

    /**
     * 是否是 OpenCode 站点：只有它才值得试 OpenCode 官方用量 + 预算聚合接口。
     *
     * <p>OpenCode 的对话上游在 {@code opencode.ai}（Zen / Go），官方用量端点
     * {@code /zen/go/v1/usage} 与预算端点 {@code /console/api/policies/current-budgets}
     * 同域；平台名（opencode / opencode-go / opencode-zen 等含 opencode 的写法）
     * 与域名都认。</p>
     *
     * @param entity 供应商
     * @return OpenCode 站点为 true
     */
    private static boolean isOpencodeProvider(SysAccountChannel entity) {
        String platform = entity.getPlatform() == null
                ? "" : entity.getPlatform().trim().toLowerCase(Locale.ROOT);
        if (platform.contains("opencode")) {
            return true;
        }
        String host = hostOf(entity.getBaseUrl());
        return host != null && host.endsWith("opencode.ai");
    }

    /**
     * 是否是 ModelScope（魔搭）站点：只有它才值得试「对话请求读限流头」的余额口径。
     *
     * <p>魔搭的对话 API 在 {@code api-inference.modelscope.cn}（OpenAI 兼容），没有
     * 独立余额端点；平台名（含别名 model-scope）与 modelscope.cn 域名都认。</p>
     *
     * @param entity 供应商
     * @return 魔搭站点为 true
     */
    private static boolean isModelScopeProvider(SysAccountChannel entity) {
        String platform = entity.getPlatform() == null
                ? "" : entity.getPlatform().trim().toLowerCase(Locale.ROOT);
        if ("modelscope".equals(platform) || "model-scope".equals(platform)) {
            return true;
        }
        String host = hostOf(entity.getBaseUrl());
        return host != null && host.endsWith("modelscope.cn");
    }

    /**
     * 是否是 OpenRouter 站点：只有它才值得试官方余额接口（/api/v1/credits 需管理 Key，
     * 普通推理 Key 403 时回落 /api/v1/key）。平台名与 openrouter.ai 域名都认。
     *
     * @param entity 供应商
     * @return OpenRouter 站点为 true
     */
    private static boolean isOpenRouterProvider(SysAccountChannel entity) {
        String platform = entity.getPlatform() == null
                ? "" : entity.getPlatform().trim().toLowerCase(Locale.ROOT);
        if (platform.contains("openrouter")) {
            return true;
        }
        String host = hostOf(entity.getBaseUrl());
        return host != null && ("openrouter.ai".equals(host) || host.endsWith(".openrouter.ai"));
    }

    /**
     * 是否是 PPIO 派欧云站点：只有它才值得试 PPIO 计费域余额接口。
     *
     * <p>PPIO 的模型网关在 {@code api.ppinfra.com}，余额在计费域 {@code api.ppio.com}
     * （两个主域不同）；注意 {@code ppinfra} 里不含连续的 {@code ppio} 子串，
     * contains 判定会漏，域名按后缀判定。平台名（含 ppio / ppinfra / 派欧写法）
     * 与两个官方域名都认。</p>
     *
     * @param entity 供应商
     * @return PPIO 站点为 true
     */
    private static boolean isPpioProvider(SysAccountChannel entity) {
        String platform = entity.getPlatform() == null
                ? "" : entity.getPlatform().trim().toLowerCase(Locale.ROOT);
        if (platform.contains("ppio") || platform.contains("ppinfra") || platform.contains("派欧")) {
            return true;
        }
        String host = hostOf(entity.getBaseUrl());
        return host != null && (host.endsWith("ppinfra.com") || host.endsWith("ppio.com"));
    }

    /**
     * 是否是 Anthropic 官方站点：只有它才值得试控制台预付余额接口。
     *
     * <p>Anthropic 的对话 API 在 {@code api.anthropic.com}，控制台与预付余额接口在
     * {@code platform.claude.com}；平台名（含 anthropic / claude 写法）与
     * anthropic.com / claude.com 官方域名（含子域）都认。</p>
     *
     * @param entity 供应商
     * @return Anthropic 站点为 true
     */
    private static boolean isAnthropicProvider(SysAccountChannel entity) {
        String platform = entity.getPlatform() == null
                ? "" : entity.getPlatform().trim().toLowerCase(Locale.ROOT);
        if (platform.contains("anthropic") || platform.contains("claude")) {
            return true;
        }
        String host = hostOf(entity.getBaseUrl());
        if (host == null) {
            return false;
        }
        return "anthropic.com".equals(host) || host.endsWith(".anthropic.com")
                || "claude.com".equals(host) || host.endsWith(".claude.com");
    }

    /**
     * 是否是 xAI 官方站点：只有它才值得试 Management API 余额接口。
     *
     * <p>xAI 的对话 API 在 {@code api.x.ai}，管理 API 在 {@code management-api.x.ai}；
     * 平台名（含 xai / grok 写法）与 x.ai 域名都认——域名只按「等于 x.ai 或以
     * {@code .x.ai} 结尾」判定，不能用 {@code endsWith("x.ai")}，否则会把
     * apex.ai 之类的其它域名误配进来。</p>
     *
     * @param entity 供应商
     * @return xAI 站点为 true
     */
    private static boolean isXaiProvider(SysAccountChannel entity) {
        String platform = entity.getPlatform() == null
                ? "" : entity.getPlatform().trim().toLowerCase(Locale.ROOT);
        if (platform.contains("xai") || platform.contains("grok")) {
            return true;
        }
        String host = hostOf(entity.getBaseUrl());
        return host != null && ("x.ai".equals(host) || host.endsWith(".x.ai"));
    }

    /**
     * 读取自定义字段映射（extra.credit_mapping）。
     *
     * <p>结构：{@code [{field: remain|used|currency, expression: JSONPath/XPath}]}。
     * 空缺行跳过；一条有效映射都没有时返回 null，custom 实现会回落到整条表达式口径。</p>
     *
     * @param extra 供应商扩展配置
     * @return 映射列表；没有有效映射为 null
     */
    private static List<CreditMapping> creditMappings(Map<String, Object> extra) {
        Object raw = extra.get("credit_mapping");
        if (!(raw instanceof List<?> rows)) {
            return null;
        }
        List<CreditMapping> mappings = new ArrayList<>(rows.size());
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            Object field = map.get("field");
            Object expression = map.get("expression");
            if (!(field instanceof String fieldName) || fieldName.isBlank()
                    || !(expression instanceof String expr) || expr.isBlank()) {
                continue;
            }
            CreditMapping mapping = new CreditMapping();
            mapping.setField(fieldName.trim().toLowerCase(Locale.ROOT));
            mapping.setExpression(expr.trim());
            mappings.add(mapping);
        }
        return mappings.isEmpty() ? null : mappings;
    }

    /**
     * 取地址的域名。
     *
     * @param url 地址
     * @return 小写域名；地址非法或缺失时为 null
     */
    private static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Map 取值（静态版，供静态辅助方法使用）。
     *
     * @param map 映射
     * @param key 键
     * @return 非空字符串；空串或缺失为 null
     */
    private static String strOf(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    /**
     * 一次查询结果 → 页面读数行。
     *
     * <p>约定 {@code parts[0]} 是剩余、{@code parts[1]} 是已用/用量，总量是两者之和；
     * 只有一段时把这一段同时当作总量。</p>
     *
     * @param result 查询结果
     * @param index  Key 序号
     * @param key    API Key（生成脱敏展示）
     * @return 读数行
     */
    private CreditReading creditRow(CreditResult result, int index, String key) {
        List<BalancePart> rawParts = result.getParts() == null ? List.of() : result.getParts();
        List<CreditPart> parts = new ArrayList<>(rawParts.size());
        for (BalancePart part : rawParts) {
            parts.add(new CreditPart(part.getLabel(), part.getAmount(), part.getCurrency()));
        }
        BigDecimal remain = rawParts.isEmpty() ? null : rawParts.get(0).getAmount();
        BigDecimal used = rawParts.size() > 1 ? rawParts.get(1).getAmount() : null;
        // JWT 形态的令牌把 exp 过期时间合入 extra：面板据此在临期时提醒续票
        // （商汤等供应商的令牌只有几小时有效期）。extra 随快照 sys_ai_provider_credit_extra
        // 列落库回放，前端无需二次解析 JWT。
        Map<String, Object> extra = result.getExtra();
        Long expiresAt = jwtExpiresAt(key);
        if (expiresAt != null) {
            Map<String, Object> merged = extra == null
                    ? new LinkedHashMap<>(4) : new LinkedHashMap<>(extra);
            merged.put("expires_at", expiresAt);
            extra = merged;
        }
        return new CreditReading(index, maskKey(key), true, result.getSource(), remain, used,
                remain == null ? null : (used == null ? remain : remain.add(used)),
                result.getCurrency(), parts, null, extra);
    }

    /**
     * 从三段式 JWT 令牌解析 exp 过期时间。
     *
     * <p>非 JWT（分段数不对）、payload 非 JSON、没有 exp 声明、解码失败，
     * 一律返回 null：过期提示是增值信息，任何解析问题都不影响余额主链路。
     * exp 是秒级时间戳（10~11 位）时乘 1000 转毫秒，毫秒级（13 位）直接用。</p>
     *
     * @param key 余额密钥（可能是三段式 JWT）
     * @return 过期时间（毫秒）；无法解析为 null
     */
    private Long jwtExpiresAt(String key) {
        if (key == null) {
            return null;
        }
        String[] segments = key.split("\\.");
        if (segments.length != 3) {
            return null;
        }
        try {
            byte[] payload = java.util.Base64.getUrlDecoder().decode(segments[1]);
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(payload);
            com.fasterxml.jackson.databind.JsonNode exp = node.get("exp");
            if (exp == null || !exp.canConvertToLong()) {
                return null;
            }
            long value = exp.asLong();
            return value < 100_000_000_000L ? value * 1000L : value;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 汇总余额。
     *
     * <p>没有任何带余额的行（如 ModelScope 只有当日用量、无主余额）时返回 null，
     * 载荷里省略 total，避免下游把占位的 0 当「余额 0.00」展示。</p>
     *
     * @param remain 各 Key 剩余之和；无余额行时为 null
     * @param used   各 Key 已用之和
     * @return 汇总余额（remain / used / total）；无余额行时为 null
     */
    private static CreditTotal creditTotal(BigDecimal remain, BigDecimal used) {
        if (remain == null) {
            return null;
        }
        return new CreditTotal(remain, used, remain.add(used == null ? BigDecimal.ZERO : used));
    }

    /**
     * 各实现的失败原因合成一行提示。
     *
     * @param failures 失败原因
     * @return 提示文本（过长时截断）
     */
    private static String creditErrorText(List<String> failures) {
        String text = failures.isEmpty() ? "没有可用的余额查询实现" : String.join("；", failures);
        return text.length() <= CREDIT_ERROR_LIMIT ? text : text.substring(0, CREDIT_ERROR_LIMIT) + "…";
    }

    /**
     * 用指定 Key 调上游 /模型，判定该 Key 是否有效（只读、不改库）。
     *
     * <p>入参字段名是 {@code apiUrl / apiKey / protocol}（可选 {@code proxy}、
     * {@code auth_scheme / auth_header / auth_prefix}）。同时兼容旧前端的
     * {@code url}：历史版本发的是 url，而后端只读 apiUrl，会被判成「地址为空」
     * 直接返回失败 —— 测试请求根本不出网，看起来却像是「测了但没报错」。</p>
     *
     * @param body 请求体，不允许为 null
     * @return Api结果 对象
     */
    @Permission("admin")
    @PostMapping("/providers/test-connection")
    public ApiResult<Map<String, Object>> testConnection(@RequestBody Map<String, Object> body) {
        String apiUrl = str(body, "apiUrl");
        if (apiUrl == null) {
            // 兼容旧字段名 url（前端迁移期两种都可能出现）
            apiUrl = str(body, "url");
        }
        if (apiUrl != null) {
            apiUrl = apiUrl.trim();
        }
        String apiKey = str(body, "apiKey");
        if (apiKey != null) {
            apiKey = apiKey.trim();
        }
        String protocol = str(body, "protocol");
        if (protocol == null) {
            protocol = "openai";
        }
        String proxy = str(body, "proxy");
        Map<String, Object> out = new LinkedHashMap<>(4);
        if (apiUrl == null) {
            out.put("success", false);
            out.put("message", "API地址不能为空");
            return ApiResult.ok(out);
        }
        if (apiKey == null) {
            out.put("success", false);
            out.put("message", "API Key不能为空");
            return ApiResult.ok(out);
        }
        Map<String, Object> result = testSingleKey(protocol, apiUrl, apiKey, UpstreamAuth.ofBody(body), proxy, 0);
        out.put("success", result.get("ok"));
        out.put("message", result.getOrDefault("error", result.getOrDefault("message", "测试完成")));
        out.put("latency", result.get("latency_ms"));
        return ApiResult.ok(out);
    }

    /**
     * 检测 API 地址实际支持什么协议。
     *
     * <p>用户只填一个 baseUrl 时不知道它支持 OpenAI 兼容、Gemini 还是 Claude(CC) 形态。
     * 这里对 4 种协议各探测一次，每种协议都按「api 原生路径 + 该协议自己的前后缀路径」
     * 排一串候选地址一起试（见 {@link #modelListCandidates}）。判定口径：只有 404 才算
     * 「该地址不支持此协议形态」，其余状态（2xx/400/401/403/405/429/5xx）都视为端点存在、
     * 协议形态被支持，按状态码给出「可用 / 不支持 / 连不上」的结果行，前端按行展示。</p>
     *
     * @param body 含 apiUrl / apiKey / proxy / auth_*（协议探测是只读的，不动库）
     * @return results 数组 + foot 汇总文案
     */
    @Permission("admin")
    @PostMapping("/providers/detect-protocol")
    public ApiResult<Map<String, Object>> detectProtocol(@RequestBody Map<String, Object> body) {
        String apiUrl = str(body, "apiUrl");
        if (apiUrl != null) {
            apiUrl = apiUrl.trim();
        }
        String apiKey = str(body, "apiKey");
        if (apiKey != null) {
            apiKey = apiKey.trim();
        }
        String proxy = str(body, "proxy");
        UpstreamAuth auth = UpstreamAuth.ofBody(body);
        List<Map<String, Object>> results = new ArrayList<>(4);
        probeProtocol(results, "openai", "OpenAI", "openai", apiUrl, apiKey, auth, proxy);
        probeProtocol(results, "openai-response", "OpenAI Response", "openai", apiUrl, apiKey, auth, proxy);
        probeProtocol(results, "anthropic", "Anthropic Claude", "anthropic", apiUrl, apiKey, auth, proxy);
        probeProtocol(results, "gemini", "Gemini", "gemini", apiUrl, apiKey, auth, proxy);
        boolean anyOk = false;
        for (Map<String, Object> r : results) {
            if (Boolean.TRUE.equals(r.get("ok"))) {
                anyOk = true;
                break;
            }
        }
        boolean keyBlank = apiKey == null || apiKey.isBlank();
        String foot;
        if (!anyOk) {
            boolean all404 = true;
            for (Map<String, Object> r : results) {
                if (!String.valueOf(r.getOrDefault("message", "")).contains("404")) {
                    all404 = false;
                    break;
                }
            }
            if (all404) {
                foot = "四个协议都返回 404：该地址没有可用的模型列表端点，请检查 API 地址是否正确";
            } else if (keyBlank) {
                foot = "都没有响应，且还没有填写 API 密钥。请在「API 密钥」中粘贴密钥后再测试";
            } else {
                foot = "四个协议都未探测到模型列表端点（404 或无法连接）：请检查 API 地址是否正确，或该服务商只支持其中某一种协议";
            }
        } else if (keyBlank) {
            foot = "已有协议响应，但未填 API 密钥，模型列表可能为空；粘贴密钥后再检测一次。";
        } else {
            foot = "已标「可用」的协议行即为该地址支持的协议。";
        }
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("results", results);
        out.put("foot", foot);
        // 自动适配：顺带识别地址背后的中转网关软件（new-api / sub2api），
        // 创建自定义供应商时当场告知余额将按哪套口径自动查询
        Map<String, Object> gateway = detectRelayGateway(apiUrl, proxy);
        if (gateway != null) {
            out.put("gateway", gateway);
        }
        return ApiResult.ok(out);
    }

    /** 对单个协议发只读探测，结果写入 results。 */
    private void probeProtocol(List<Map<String, Object>> results, String protocol, String label, String icon,
                               String baseUrl, String apiKey, UpstreamAuth auth, String proxy) {
        Map<String, Object> r = new LinkedHashMap<>(6);
        r.put("protocol", protocol);
        r.put("label", label);
        r.put("icon", icon);
        long start = System.currentTimeMillis();
        if (baseUrl == null || baseUrl.isBlank()) {
            r.put("ok", false);
            r.put("message", "API 地址为空");
            r.put("latency_ms", 0);
            results.add(r);
            return;
        }
        // 同一份地址在不同服务商那儿版本前缀不一样（有的要 /v1，有的直接 /models），
        // 所以每个协议都排一串候选路径：逐个试，只有 404 才换下一个。
        List<String> candidates = modelListCandidates(protocol, baseUrl);
        String lastUrl = candidates.isEmpty() ? baseUrl : candidates.get(0);
        int lastStatus = -1;
        try {
            for (String candidate : candidates) {
                String u = candidate;
                // 原生 Gemini 把 key 放查询参数；v1beta/openai 那层是 OpenAI 兼容，走请求头
                boolean geminiQueryKey = "gemini".equals(protocol) && !candidate.contains("/openai/");
                if (geminiQueryKey && apiKey != null && !apiKey.isBlank()) {
                    u += (u.indexOf("?") >= 0 ? "&" : "?") + "key=" + apiKey;
                }
                lastUrl = u;
                HttpRequest.Builder b = HttpRequest.newBuilder(java.net.URI.create(u)).GET().timeout(UPSTREAM_TIMEOUT);
                if ("anthropic".equals(protocol)) {
                    // Anthropic 形态：x-api-key 头 + anthropic-version
                    if (apiKey != null && !apiKey.isBlank()) {
                        b.header("x-api-key", apiKey);
                    }
                    b.header("anthropic-version", "2023-06-01");
                } else if (!"gemini".equals(protocol) || !geminiQueryKey) {
                    // OpenAI 兼容形态：Authorization 头（openai-response 与 v1beta/openai 同理）
                    Map.Entry<String, String> resolved = auth == null ? null
                            : AuthHeaders.resolve(auth.scheme(), auth.header(), auth.prefix(), apiKey);
                    if (resolved != null) {
                        b.header(resolved.getKey(), resolved.getValue());
                    }
                }
                HttpResponse<String> resp = httpClient(proxy).send(b.build(), HttpResponse.BodyHandlers.ofString());
                lastStatus = resp.statusCode();
                if (lastStatus == 404) {
                    // 这层路径没有该协议的模型列表端点，换下一个候选
                    continue;
                }
                fillProbeResult(r, lastStatus, resp.body());
                break;
            }
            if (lastStatus == 404) {
                r.put("ok", false);
                r.put("message", "该地址下没有此协议的模型列表端点（404）");
            }
            // Gemini 的 key 只能放查询参数，展示时掩码，不把凭据打印到屏幕上
            r.put("url", "gemini".equals(protocol) ? maskQueryKey(lastUrl) : lastUrl);
            r.put("latency_ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            r.put("latency_ms", System.currentTimeMillis() - start);
            String reason = friendlyNetworkReason(e);
            r.put("ok", false);
            r.put("message", reason != null ? reason : "无法连接");
        }
        results.add(r);
    }

    /**
     * 给出该协议的候选探测地址：api 原生路径 + 各协议后缀路径，逐个试。
     *
     * <p>同一个 api 地址在不同服务商那儿挂法不一样，只看一种拼法必然误判。
     * 以 {@code https://api.mosi.cn} 为例：OpenAI 端点在 {@code /v1/chat/completions}
     * 与 {@code /v1/models}（403 也算存在），Claude/CC 端点在 {@code /v1/messages}，
     * 而 {@code /models}、{@code /v1beta/models}、{@code /cc/*}、{@code /gemini/*} 都是 404。
     * 旧实现 OpenAI 只拼 {@code /models} → 404 → 界面上只剩「Claude 支持」。</p>
     *
     * <p>所以每个协议都排一整串候选（api 原生 /v1 路径 + 该协议自己的前缀路径），
     * 探测循环里只有 404 才换下一个，其余状态都算端点存在。</p>
     *
     * @param protocol 协议名（openai / openai-response / anthropic / gemini）
     * @param baseUrl  用户填的地址
     * @return 依次尝试的完整地址（已去重保序）
     */
    private static List<String> modelListCandidates(String protocol, String baseUrl) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        List<String> out = new ArrayList<>(8);
        if ("gemini".equals(protocol)) {
            // 原生 Gemini 只有 v1beta 这条线；v1beta/openai 是 Google 官方 OpenAI 兼容层。
            // 不能用 /v1/models 兜底：那是 OpenAI 端点，会把 Gemini 行误判成支持。
            if (base.endsWith("/v1beta")) {
                out.add(base + "/models");
                out.add(base + "/openai/models");
            } else {
                out.add(base + "/v1beta/models");
                out.add(base + "/v1beta/openai/models");
            }
            out.add(base + "/gemini/v1beta/models");
            out.add(base + "/gemini/models");
        } else if ("anthropic".equals(protocol)) {
            // Claude / CC：原生 /v1/messages；模型列表在 /v1/models
            if (base.endsWith("/v1")) {
                out.add(base + "/messages");
                out.add(base + "/models");
            } else {
                out.add(base + "/v1/models");
                out.add(base + "/v1/messages");
                out.add(base + "/models");
            }
            out.add(base + "/cc/v1/messages");
            out.add(base + "/cc/models");
            out.add(base + "/cc/v1/models");
        } else {
            // OpenAI / Responses：原生 /v1/models 与 /v1/chat/completions，再试 openai 前缀形态
            if (base.endsWith("/v1") || base.endsWith("/v2") || base.endsWith("/v3")) {
                out.add(base + "/models");
                out.add(base + "/chat/completions");
            } else {
                out.add(base + "/v1/models");
                out.add(base + "/models");
                out.add(base + "/v1/chat/completions");
            }
            out.add(base + "/openai/v1/models");
            out.add(base + "/openai/models");
            out.add(base + "/openai/v1/chat/completions");
        }
        List<String> unique = new ArrayList<>(out.size());
        for (String u : out) {
            if (!unique.contains(u)) {
                unique.add(u);
            }
        }
        return unique;
    }

    /**
     * 按状态码判定探测结论：只有 404 算「没有这个端点」，其余状态都说明端点存在。
     *
     * @param r      结果行
     * @param st     HTTP 状态码
     * @param body   响应体
     */
    private static void fillProbeResult(Map<String, Object> r, int st, String body) {
        if (st >= 200 && st < 300) {
            int count = countModelIds(body);
            r.put("ok", true);
            r.put("message", count > 0 ? "可用（" + count + " 个模型）" : "连接成功但没有可用试用的模型");
        } else if (st == 401) {
            r.put("ok", true);
            r.put("message", "端点存在，API Key 无效或已过期（401）");
        } else if (st == 400) {
            // 探测用 GET 打对话/消息端点时，不少网关先回 400「参数不对」——端点本身是通的
            r.put("ok", true);
            r.put("message", "端点存在，探测请求缺少必要参数（400）");
        } else if (st == 403) {
            r.put("ok", true);
            r.put("message", "端点存在，但无权限访问（403）");
        } else if (st == 405) {
            r.put("ok", true);
            r.put("message", "端点存在，但不接受该探测方法（405）");
        } else if (st == 429) {
            r.put("ok", true);
            r.put("message", "端点存在，但请求过于频繁（429）");
        } else {
            r.put("ok", true);
            r.put("message", "端点存在，供应商接口返回 " + st);
        }
    }

    /**
     * 把地址里 {@code key=...} 这类凭据参数掩码掉，只留前 3 位。
     *
     * <p>检测结果会把整条探测地址渲染到界面上，Gemini 形态的 key 只能放在
     * 查询参数里 —— 不掩码就等于把凭据打印在屏幕上。</p>
     *
     * @param url 原始探测地址
     * @return 掩码后的地址
     */
    private static String maskQueryKey(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("([?&](?:key|api_key|apikey|token)=)([^&]{3})[^&]*", "$1$2***");
    }

    /** 粗略统计模型列表 JSON 里的条目数（探测语义，不做完整解析）。 */
    private static int countModelIds(String body) {
        if (body == null || body.isBlank()) {
            return 0;
        }
        int n = 0;
        String key = "\"id\"";
        int from = 0;
        while ((from = body.indexOf(key, from)) >= 0) {
            n++;
            from += key.length();
        }
        return n;
    }

    /**
     * 测试指定提供者模型的连通性延迟。
     *
     * <p>模型管理卡片的模型来自 {@code /api/providers} 的
     * {@code provider_id + model} 复合主键，不是旧 {@code sys_ai_model}
     * 的数字主键，因此不能复用 {@code /v2/models/{id}/test-latency}。
     * 这里用第一个可用 Key 请求上游模型目录并测量耗时，供云端模型卡片展示。
     * 成功结果写入该模型 metrics 的 {@code probe_latency_ms} 和 {@code latency_ms}，
     * 列表接口会优先回读模型自身指标，刷新后仍能显示最近一次实测延迟。</p>
     *
     * @param providerId 提供者标识
     * @param modelName 模型名
     * @return 延迟测试结果
     */
    @Permission("admin")
    @PostMapping("/providers/{provider_id}/models/{model_name}/test-latency")
    public ApiResult<Map<String, Object>> testProviderModelLatency(
            @PathVariable("provider_id") String providerId,
            @PathVariable("model_name") String modelName) {
        SysAccountChannel provider = requireProvider(providerId);
        SysAccountChannelModel model = accountChannelModelService.find(providerId, modelName);
        if (model == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "模型不存在: " + modelName);
        }
        List<String> keys = extractAllKeys(provider.getCredentials());
        if (keys.isEmpty()) {
            Map<String, Object> noKey = new LinkedHashMap<>(3);
            noKey.put("success", false);
            noKey.put("message", "提供者未配置 API Key");
            noKey.put("latency", null);
            return ApiResult.ok(noKey);
        }
        Map<String, Object> result = testSingleKey(provider.getPlatform(), provider.getBaseUrl(),
                keys.get(0), UpstreamAuth.of(provider.getAuthScheme(), provider.getExtra()),
                provider.getProxy(), 0);
        Object latency = result.get("latency_ms");
        if (Boolean.TRUE.equals(result.get("ok"))) {
            Map<String, Object> metrics = model.getMetrics() != null
                    ? new LinkedHashMap<>(model.getMetrics())
                    : new LinkedHashMap<>(4);
            metrics.put("probe_latency_ms", latency);
            metrics.put("latency_ms", latency);
            model.setMetrics(metrics);
            model.setUpdatedAt(System.currentTimeMillis());
            accountChannelModelService.updateByKey(model);
        }
        Map<String, Object> out = new LinkedHashMap<>(5);
        out.put("provider_id", providerId);
        out.put("model", modelName);
        out.put("success", result.get("ok"));
        out.put("message", result.getOrDefault("error", result.getOrDefault("message", "测试完成")));
        out.put("latency", latency);
        return ApiResult.ok(out);
    }

    /**
     * 用指定 Key 调上游 /模型，判定该 Key 是否有效（只读、不改库）。
    */
    private Map<String, Object> testSingleKey(String platform, String baseUrl, String apiKey,
                                              UpstreamAuth auth, String proxy, int index) {
        Map<String, Object> r = new LinkedHashMap<>(6);
        r.put("index", index);
        r.put("key_mask", maskKey(apiKey));
        long latencyStart = System.currentTimeMillis();
        try {
            if (baseUrl == null || baseUrl.isBlank()) {
                r.put("ok", false);
                r.put("error", "base_url 未配置");
                r.put("latency_ms", System.currentTimeMillis() - latencyStart);
                return r;
            }
            Map<String, Object> fakeCreds = new LinkedHashMap<>(2);
            fakeCreds.put("api_key", apiKey);
            // 协议判定必须与 fetchModelsFromUpstream 完全一致：平台为 google 时走
            // ?key= 查询参数，此前只认 "gemini"，导致 Google 的有效 Key 被按
            // Authorization: Bearer 发出去，上游回 401，界面误报「API Key 无效」。
            String platformLower = platform == null ? "" : platform.toLowerCase();
            boolean googleProtocol = platformLower.contains("gemini") || platformLower.contains("google");
            List<Map<String, Object>> models = requestUpstreamModels(baseUrl, apiKey, googleProtocol, proxy, auth);
            r.put("latency_ms", System.currentTimeMillis() - latencyStart);
            if (models == null) {
                // 404 / 网络失败：只说明「读不到模型列表」，不能断言 Key 无效
                r.put("ok", false);
                r.put("error", "无法从上游读取模型列表（地址或网络异常），未能校验 Key");
            } else {
                // 200 即使返回空列表也说明鉴权已通过，Key 有效；
                // 原实现把空列表判失败，导致有效 Key 被误报。
                r.put("ok", true);
                r.put("model_count", models.size());
                if (models.isEmpty()) {
                    r.put("message", "Key 校验通过，但上游未返回模型列表");
                }
            }
        } catch (Exception e) {
            r.put("latency_ms", System.currentTimeMillis() - latencyStart);
            r.put("ok", false);
            r.put("error", e.getMessage() != null ? e.getMessage() : e.toString());
        }
        return r;
    }

    /**
     * 脱敏 Key：仅保留前 6 位 + 后 4 位，避免明文回显。
     *
     * @param key Key
     * @return maskKey的结果
     */
    private static String maskKey(String key) {
        if (key == null || key.length() <= 10) {
            return "****";
        }
        return key.substring(0, 6) + "..." + key.substring(key.length() - 4);
    }

    /**
     * 提取凭证中全部 Key（api_Key 数组 + api_Key 标量，去重保序）。
     *
     * @param credentials 凭证
     * @return extract全部Key的结果
     */
    private List<String> extractAllKeys(Map<String, Object> credentials) {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        if (credentials != null) {
            Object multi = credentials.get("api_keys");
            if (multi instanceof List<?> list) {
                for (Object o : list) {
                    if (o != null && !o.toString().isBlank()) {
                        set.add(o.toString());
                    }
                }
            }
            Object single = credentials.get("api_key");
            if (single instanceof String s && !s.isBlank()) {
                // 多 Key 以逗号拼接存储（与 api_keys 列表、列表统计 api_key_count 的口径一致），
                // 按逗号拆开后逐项入集，避免只取到单个 Key 导致余额面板少显示
                for (String part : s.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        set.add(trimmed);
                    }
                }
            }
        }
        return new ArrayList<>(set);
    }

    // ==================== 提供者 Connections ====================

    /**
     * 列举 提供者 的额外连接（按 角色）。
     *
     * @param providerId 提供者 标识
     * @return 提供者连接响应列表
     */
    @GetMapping("/providers/{provider_id}/connections")
    public ApiResult<List<Map<String, Object>>> listConnections(@PathVariable("provider_id") String providerId) {
        requireVisibleProvider(providerId);
        List<SysAccountChannelConnection> conns = accountChannelService.connectionsOf(providerId);
        List<Map<String, Object>> items = conns.stream()
                .map(this::toConnectionResponse)
                .collect(Collectors.toList());
        return ApiResult.ok(items);
    }

    /**
     * 获取单条 提供者 连接。
     *
     * @param providerId 提供者 标识
     * @param role       连接角色
     * @return 提供者连接详情
     */
    @GetMapping("/providers/{provider_id}/connections/{role}")
    public ApiResult<Map<String, Object>> getConnection(@PathVariable("provider_id") String providerId,
                                                        @PathVariable("role") String role) {
        requireVisibleProvider(providerId);
        // 账号隔离与「自己名下优先」的取值规则由账号模块的连接业务负责
        return ApiResult.ok(toConnectionResponse(accountChannelConnectionService.require(providerId, role)));
    }

    /**
     * 保存（新建或更新）提供者 连接。
     *
     * @param providerId 提供者 标识
     * @param body       保存提供者connection请求
     * @return 提供者连接详情
     */
    @Permission("admin")
    @PutMapping("/providers/{provider_id}/connections")
    @Transactional
    public ApiResult<Map<String, Object>> saveConnection(@PathVariable("provider_id") String providerId,
                                                         @RequestBody Map<String, Object> body) {
        SysAccountChannel entity = requireProvider(providerId);
        requireStr(body, "role");
        // 新建必填凭证、更新只覆盖显式字段、归属隔离与事件广播都在账号模块的连接业务里
        return ApiResult.ok(toConnectionResponse(accountChannelConnectionService.save(providerId, body)));
    }

    /**
     * 删除 提供者 连接。
     *
     * @param providerId 提供者 标识
     * @param role       连接角色
     * @return 成功
     */
    @Permission("admin")
    @DeleteMapping("/providers/{provider_id}/connections/{role}")
    @Transactional
    public ApiResult<Void> deleteConnection(@PathVariable("provider_id") String providerId,
                                            @PathVariable("role") String role) {
        requireProvider(providerId);
        // 只删自己名下的连接，别人的同 role 行不会被这条请求带走
        accountChannelConnectionService.delete(providerId, role);
        return ApiResult.ok();
    }

    // ==================== 提供者 模型 ====================

    /**
     * 列举 提供者_模型 行（可按 提供者_标识 过滤）。
     *
     * @param providerId 可选过滤
     * @return 提供者模型响应列表
     */
    @GetMapping("/provider-models")
    public ApiResult<List<Map<String, Object>>> listProviderModels(
            @RequestParam(required = false) String provider_id) {
        List<SysAccountChannelModel> models = accountChannelModelService.listAll(provider_id);
        // ONNX 供应商（用户自己添加、仅管理员可见）的模型同样不外泄
        boolean admin = com.chua.starter.ai.support.util.AiUserContext.isAdmin();
        List<Map<String, Object>> items = new ArrayList<>(8); // 模型数量不可预估
        for (SysAccountChannelModel model : models) {
            SysAccountChannel provider = accountChannelService.find(model.getProviderId());
            if (provider != null) {
                if (!admin && isOnnxRow(provider)) {
                    continue;
                }
                items.add(toProviderModelResponse(provider, model));
            }
        }
        return ApiResult.ok(items);
    }

    /**
     * 全量保存（upsert）某 提供者 下某模型的完整能力集。
     *
     * @param body 保存提供者模型请求
     * @return 提供者模型详情
     */
    @Permission("admin")
    @PutMapping("/provider-models")
    @Transactional
    public ApiResult<Map<String, Object>> saveProviderModel(@RequestBody Map<String, Object> body) {
        String providerId = requireStr(body, "provider_id");
        SysAccountChannel provider = requireProvider(providerId);
        Map<String, Object> modelInput = uncheckedCast(body.get("model"));
        if (modelInput == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "model 不能为空");
        }
        String modelName = requireStr(modelInput, "model");

        SysAccountChannelModel existing = accountChannelModelService.find(providerId, modelName);

        if (existing == null) {
            SysAccountChannelModel newModel = toModelEntity(providerId, modelInput, System.currentTimeMillis());
            // 新增：模型 ID 确定后，用已采集的指标补全上下文长度等参数（仅未配置时）
            enrichFromMetrics(provider, newModel);
            // 唯一性与排序由账号模块的模型业务负责
            accountChannelModelService.create(newModel);
            refreshOnnxDevice(provider);
            invalidateRouteCache("provider-model-created");
            return ApiResult.ok(toProviderModelResponse(provider, newModel));
        } else {
            applyModelInput(existing, modelInput);
            // 可空字段显式更新（允许清空）由账号模块的模型业务负责
            accountChannelModelService.saveEdited(existing);
            refreshOnnxDevice(provider);
            invalidateRouteCache("provider-model-updated");
            return ApiResult.ok(toProviderModelResponse(provider, existing));
        }
    }

    /**
     * 重命名模型 标识（复合主键变更：复制行到新 标识 并删除旧行，原子完成）。
     *
     * @param body { 提供者_标识, 模型, 新_模型 }
     * @return 重命名后的 提供者模型响应
     */
    @Permission("admin")
    @PostMapping("/provider-models/rename")
    @Transactional
    public ApiResult<Map<String, Object>> renameProviderModel(@RequestBody Map<String, Object> body) {
        String providerId = requireStr(body, "provider_id");
        SysAccountChannel provider = requireProvider(providerId);
        String oldName = requireStr(body, "model");
        String newName = requireStr(body, "new_model");
        SysAccountChannelModel existing = accountChannelModelService.find(providerId, oldName);
        if (existing == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "provider model 不存在: " + oldName);
        }
        // 同名校验、复制到新标识并删除旧行，统一由账号模块的模型业务负责
        SysAccountChannelModel renamed = accountChannelModelService.rename(providerId, oldName, newName);
        // 改名后：新 ID 可能对应另一份已采集指标，用它补全上下文长度等（仅未配置时）
        if (enrichFromMetrics(provider, renamed)) {
            accountChannelModelService.updateByKey(renamed);
        }
        refreshOnnxDevice(provider);
        return ApiResult.ok(toProviderModelResponse(provider, renamed));
    }

    /**
     * 删除 提供者_模型 行（按复合主键 提供者_标识 + 模型）。
     *
     * @param providerId 提供者 标识
     * @param model      模型名
     * @return 成功
     */
    @Permission("admin")
    @DeleteMapping("/provider-models")
    @Transactional
    public ApiResult<Void> deleteProviderModel(@RequestParam("provider_id") String providerId,
                                               @RequestParam("model") String model) {
        SysAccountChannel provider = requireProvider(providerId);
        // 模型行由账号模块删除；路由组成员与 ONNX 设备覆盖属于 AI 侧，紧随其后清理
        accountChannelModelService.delete(providerId, model);
        pruneRouterGroupMembers(providerId, model);
        refreshOnnxDevice(provider);
        return ApiResult.ok();
    }

    /**
     * 批量删除 提供者_模型 行（按复合主键 提供者_标识 + 模型）。
     *
     * <p>复合 id 用途与 {@link #deleteProviderModel(String, String)} 一致
     * （如 {@code openai:gpt-4o}），用 {@code ApiErrorCode.NOT_FOUND} 拦住不存在的 provider 行。
     * 整批解析完成后才删，避免部分删除。每删一个 provider 组刷新一次 ONNX 推理设备覆盖。</p>
     *
     * @param body 请求体，须含 {@code ids} 数组（providerId:modelName）
     * @return 包含 success 与 deletedCount 的结果
     */
    @Permission("admin")
    @DeleteMapping("/provider-models/batch")
    @Transactional
    public ApiResult<Map<String, Object>> batchDeleteProviderModels(@RequestBody Map<String, ?> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = body == null ? null : (List<String>) body.get("ids");
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "ids 不能为空");
        }

        // 格式校验、整批定位与逐条删除都在账号模块的模型业务里
        int deleted = accountChannelModelService.deleteBatch(ids);
        // 路由组成员与 ONNX 设备覆盖属于 AI 侧，删完模型行后一并清理
        String lastProviderId = null;
        for (String id : ids) {
            com.chua.starter.account.channel.service.AccountChannelModelService.ModelKey key =
                    com.chua.starter.account.channel.service.AccountChannelModelService.ModelKey.parse(id);
            if (key == null) {
                continue;
            }
            pruneRouterGroupMembers(key.providerId(), key.model());
            // 同一渠道连续删除时只刷新一次设备覆盖
            if (!key.providerId().equals(lastProviderId)) {
                refreshOnnxDevice(requireProvider(key.providerId()));
                lastProviderId = key.providerId();
            }
        }

        Map<String, Object> result = new HashMap<>(2);
        result.put("success", true);
        result.put("deletedCount", deleted);
        return ApiResult.ok(result);
    }

    // ==================== 路由组 ====================

    /**
     * 路由组成员候选：已加入模型库、且用户能在模型页看到的模型。
     *
     * <p>候选集必须与 {@link #listProviders()} 的可见口径一致，否则会出现
     * 「模型页只有 3 个模型，挑选成员却冒出几十个」：渠道管理给每个 OAuth 渠道
     * 镀的那行 provider 在模型页是隐藏的，但它下面的模型行仍然 enabled，
     * 直接按 enabled 查就会把它们当候选。</p>
     *
     * @param keyword 模型名关键字
     * @return 候选列表
     */
    @GetMapping("/router-groups/candidates")
    public ApiResult<List<Map<String, Object>>> listRouterGroupCandidates(
            @RequestParam(value = "keyword", required = false) String keyword) {
        boolean onnxVisible = com.chua.starter.ai.support.util.AiUserContext.isAdmin();
        List<Map<String, Object>> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SysAccountChannelModel row : routerGroupService.searchMemberCandidates(keyword)) {
            SysAccountChannel owner = accountChannelService.find(row.getProviderId());
            if (owner == null || ProviderRouteTargets.isRouterGroup(owner.getPlatform())) {
                continue;
            }
            // 供应商本身停用 → 它的模型在会话里也选不到，不能进候选
            if (Boolean.FALSE.equals(owner.getEnabled())) {
                continue;
            }
            // 与模型页同一口径：渠道 OAuth 镀铬行（非免登录渠道）在模型页不展示；
            // chat-<渠道> 免登录行与免费渠道行一样保留
            if (isOAuthChannelRow(owner) && !isFreeChannelRow(owner)) {
                continue;
            }
            if (isOnnxRow(owner) && !onnxVisible) {
                continue;
            }
            String key = row.getProviderId() + ":" + row.getModel();
            if (!seen.add(key)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("provider_id", row.getProviderId());
            item.put("provider_name", owner.getName());
            item.put("platform", owner.getPlatform());
            item.put("model", row.getModel());
            item.put("alias", row.getAlias());
            item.put("enabled", row.getEnabled());
            // 挑选成员时要能判断「这个模型能干什么、吃多少、多少钱」
            item.put("description", row.getDescription());
            item.put("family", row.getFamily());
            item.put("context_window", row.getContextWindow());
            item.put("max_tokens", row.getMaxTokens());
            item.put("metrics", row.getMetrics());
            item.put("capabilities", row.getCapabilities());
            item.put("effort", routerGroupService.readEffort(row));
            items.add(item);
        }
        return ApiResult.ok(items);
    }

    /**
     * 读取路由组实时决策快照（供管理页的实时连线视图使用）。
     *
     * <p>只读内存快照：进程重启后为空，视图显示「等待请求」即可。不落库、不改配置。</p>
     *
     * @param providerId 路由组供应商 ID
     * @return 最近决策（最新在前）
     */
    @GetMapping("/providers/{provider_id}/router-group/live")
    public ApiResult<Map<String, Object>> getRouterGroupLive(@PathVariable("provider_id") String providerId) {
        SysAccountChannel provider = requireProvider(providerId);
        if (!routerGroupService.isRouterGroup(provider)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该供应商不是路由组: " + providerId);
        }
        List<Map<String, Object>> items = new ArrayList<>(8);
        for (com.chua.common.support.ai.chat.aggregate.RouterGroupActivity.Decision d
                : com.chua.common.support.ai.chat.aggregate.RouterGroupActivity.recent(providerId)) {
            Map<String, Object> item = new LinkedHashMap<>(8);
            item.put("member_label", d.memberLabel());
            item.put("member_model", d.memberModel());
            item.put("at", d.at());
            item.put("success", d.success());
            item.put("cost_ms", d.costMs());
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("decisions", items);
        result.put("server_time", System.currentTimeMillis());
        return ApiResult.ok(result);
    }

    /**
     * 读取路由组配置（成员顺序 / 策略 / 每模型 effort）。
     *
     * @param providerId 路由组供应商 ID
     * @return 配置
     */
    @GetMapping("/providers/{provider_id}/router-group")
    public ApiResult<Map<String, Object>> getRouterGroup(@PathVariable("provider_id") String providerId) {
        SysAccountChannel provider = requireProvider(providerId);
        if (!routerGroupService.isRouterGroup(provider)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该供应商不是路由组: " + providerId);
        }
        com.chua.common.support.ai.chat.aggregate.RouterGroupSetting setting =
                routerGroupService.readSetting(provider);
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("setting", routerGroupService.toResponse(setting));
        result.put("members", routerGroupService.describeMembers(setting));
        return ApiResult.ok(result);
    }

    /**
     * 路由拓扑快照：一次请求拿齐「入站 AppKey → 网关（路由组）→ 上游成员」三层，
     * 供独立的路由页画连线与命中高亮。
     *
     * <p>为什么做聚合端点：拓扑页每 2 秒刷新一次，如果让前端分别打
     * {@code router-group}、{@code router-group/live}、流量聚合三个接口，
     * 三层数据来自不同时刻，连线会「对不上人」。这里在同一个请求里完成读取，
     * 各层的时序一致。</p>
     *
     * <p>数据来源都是现成的：配置与成员来自 {@link RouterGroupService}，
     * 实时命中来自内存 {@code RouterGroupActivity}（进程内最近 20 条），
     * 入站流量来自 {@code sys_ai_service_access_log} 的按 AppKey 聚合。</p>
     *
     * @param providerId  路由组供应商 ID
     * @param windowHours 入站流量回看窗口（小时，1~720，默认 1）
     * @param keyLimit    入站 AppKey 最多返回条数（1~50，默认 8）
     * @return 拓扑快照
     */
    @GetMapping("/providers/{provider_id}/router-group/topology")
    public ApiResult<Map<String, Object>> getRouterGroupTopology(
            @PathVariable("provider_id") String providerId,
            @RequestParam(value = "window_hours", required = false, defaultValue = "1") int windowHours,
            @RequestParam(value = "key_limit", required = false, defaultValue = "8") int keyLimit) {
        SysAccountChannel provider = requireProvider(providerId);
        if (!routerGroupService.isRouterGroup(provider)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该供应商不是路由组: " + providerId);
        }
        int hours = Math.min(Math.max(windowHours, 1), 720);
        int limit = Math.min(Math.max(keyLimit, 1), 50);
        long nowMs = System.currentTimeMillis();

        com.chua.common.support.ai.chat.aggregate.RouterGroupSetting setting =
                routerGroupService.readSetting(provider);
        List<Map<String, Object>> members = routerGroupService.describeMembers(setting);

        // 内存决策：进程重启后为空，视图显示「等待请求」即可
        List<com.chua.common.support.ai.chat.aggregate.RouterGroupActivity.Decision> decisions =
                com.chua.common.support.ai.chat.aggregate.RouterGroupActivity.recent(providerId);
        List<Map<String, Object>> decisionItems = new ArrayList<>(decisions.size());
        Map<String, long[]> hitsByLabel = new HashMap<>(8);
        for (com.chua.common.support.ai.chat.aggregate.RouterGroupActivity.Decision d : decisions) {
            Map<String, Object> item = new LinkedHashMap<>(8);
            item.put("member_label", d.memberLabel());
            item.put("member_model", d.memberModel());
            item.put("at", d.at());
            item.put("success", d.success());
            item.put("cost_ms", d.costMs());
            decisionItems.add(item);
            long[] stat = hitsByLabel.computeIfAbsent(d.memberLabel(), k -> new long[3]);
            stat[0] = stat[0] + 1;
            if (d.success()) {
                stat[1] = stat[1] + 1;
                // 平均耗时只统计成功样本：把超时/失败的长尾算进去，「这条通道 8s」实际是
                // 「一半请求直接失败」，会误导排障时误判为上游慢
                stat[2] = stat[2] + Math.max(d.costMs(), 0);
            }
        }

        List<Map<String, Object>> upstreams = new ArrayList<>(members.size());
        for (Map<String, Object> member : members) {
            Map<String, Object> item = new LinkedHashMap<>(member);
            String label = member.get("label") == null ? null : String.valueOf(member.get("label"));
            long[] stat = label == null ? null : hitsByLabel.get(label);
            long hits = stat == null ? 0 : stat[0];
            long ok = stat == null ? 0 : stat[1];
            long totalCost = stat == null ? 0 : stat[2];
            item.put("hits", hits);
            item.put("success_hits", ok);
            item.put("avg_cost_ms", ok == 0 ? null : Math.round((double) totalCost / ok));
            item.put("hit_rate", decisions.size() == 0 ? null
                    : Math.round(hits * 1000.0 / decisions.size()) / 10.0);
            upstreams.add(item);
        }

        Map<String, Object> group = new LinkedHashMap<>(8);
        group.put("provider_id", provider.getProviderId());
        group.put("name", provider.getName());
        group.put("platform", provider.getPlatform());
        group.put("member_count", members.size());
        Map<String, Object> settingView = routerGroupService.toResponse(setting);
        settingView.forEach(group::putIfAbsent);

        Map<String, Object> gateway = new LinkedHashMap<>(8);
        gateway.put("provider_id", provider.getProviderId());
        gateway.put("name", provider.getName());
        gateway.put("platform", provider.getPlatform());

        Map<String, Object> inbound = buildInboundTraffic(nowMs, hours, limit, providerId);

        Map<String, Object> result = new LinkedHashMap<>(8);
        result.put("group", group);
        result.put("gateway", gateway);
        result.put("inbound", inbound);
        result.put("upstreams", upstreams);
        result.put("decisions", decisionItems);
        result.put("window_hours", hours);
        result.put("server_time", nowMs);
        return ApiResult.ok(result);
    }

    /**
     * 入站流量：按 AppKey 聚合近 {@code hours} 小时的请求，并回填 AppKey 的身份与配额。
     *
     * <p>归属口径：只有管理员能看到全部 Key 的流量；普通用户与无身份的机器调用都按
     * 「自己的 Key / 无主 Key」收敛（判定写在 mapper 的 {@code trafficScope} 里，
     * 因为过滤依赖 AppKey 台账而不是日志的 {@code user_id}）。此前这里没有任何归属过滤，
     * 任何登录用户都能从拓扑页看到别人 Key 的调用次数、token 与余额。</p>
     *
     * @param nowMs       当前时间（毫秒）
     * @param hours       回看窗口（小时）
     * @param limit       最多返回的 AppKey 数
     * @param routerGroup 路由组供应商 ID（拓扑页当前选中的组）
     * @return 入站节点数据
     */
    private Map<String, Object> buildInboundTraffic(long nowMs, int hours, int limit, String routerGroup) {
        LocalDateTime since = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(nowMs - hours * 3600_000L), ZoneId.systemDefault());
        boolean restrictOwner = !com.chua.starter.ai.support.util.AiUserContext.isAdmin();
        String ownerUserId = com.chua.starter.ai.support.util.AiUserContext.currentUserId();
        Map<String, Object> inbound = new LinkedHashMap<>(8);
        inbound.put("window_hours", hours);
        List<Map<String, Object>> keys = new ArrayList<>(limit);
        try {
            List<com.chua.starter.ai.support.mapper.SysAiServiceAccessLogMapper.AppKeyTrafficRow> rows =
                    accessLogMapper.selectAppKeyTraffic(since, routerGroup, restrictOwner, ownerUserId, limit);
            // 身份一次性批量取：8 个 Key = 8 条 selectByAppKey 会把这个 2 秒轮询的页面
            // 变成 8 倍数据库压力，且各 Key 的身份来自不同时刻
            Map<String, com.chua.starter.ai.support.entity.SysAiAppKey> identities = loadAppKeyIdentities(rows);
            for (com.chua.starter.ai.support.mapper.SysAiServiceAccessLogMapper.AppKeyTrafficRow row : rows) {
                Map<String, Object> item = new LinkedHashMap<>(12);
                item.put("app_key", maskAppKey(row.appKey()));
                item.put("calls", row.calls() == null ? 0 : row.calls());
                item.put("success_calls", row.successCalls() == null ? 0 : row.successCalls());
                item.put("tokens", row.tokens() == null ? 0 : row.tokens());
                item.put("avg_latency_ms", row.avgLatencyMs() == null ? null : Math.round(row.avgLatencyMs()));
                item.put("last_at", toMillis(row.lastAt()));
                applyAppKeyIdentity(item, identities.get(row.appKey()));
                keys.add(item);
            }
            com.chua.starter.ai.support.mapper.SysAiServiceAccessLogMapper.TrafficTotalRow total =
                    accessLogMapper.selectTrafficTotal(since, routerGroup, restrictOwner, ownerUserId);
            Map<String, Object> totalView = new LinkedHashMap<>(8);
            totalView.put("calls", total == null || total.calls() == null ? 0 : total.calls());
            totalView.put("success_calls", total == null || total.successCalls() == null ? 0 : total.successCalls());
            totalView.put("tokens", total == null || total.tokens() == null ? 0 : total.tokens());
            totalView.put("avg_latency_ms", total == null || total.avgLatencyMs() == null
                    ? null : Math.round(total.avgLatencyMs()));
            totalView.put("last_at", total == null ? null : toMillis(total.lastAt()));
            inbound.put("total", totalView);
        } catch (Exception e) {
            // 拓扑页只是观测视图：日志表结构异常不能把整个页面打挂
            log.warn("读取入站流量聚合失败: {}", e.getMessage());
            Map<String, Object> totalView = new LinkedHashMap<>(4);
            totalView.put("calls", 0);
            totalView.put("success_calls", 0);
            totalView.put("tokens", 0);
            totalView.put("avg_latency_ms", null);
            totalView.put("last_at", null);
            inbound.put("total", totalView);
        }
        inbound.put("keys", keys);
        return inbound;
    }

    /**
     * 批量读取 AppKey 台账身份。
     *
     * @param rows 聚合行
     * @return appKey 值 → 台账行；查不到的不在 Map 里
     */
    private Map<String, com.chua.starter.ai.support.entity.SysAiAppKey> loadAppKeyIdentities(
            List<com.chua.starter.ai.support.mapper.SysAiServiceAccessLogMapper.AppKeyTrafficRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        List<String> appKeys = new ArrayList<>(rows.size());
        for (com.chua.starter.ai.support.mapper.SysAiServiceAccessLogMapper.AppKeyTrafficRow row : rows) {
            if (row.appKey() != null && !row.appKey().isBlank()) {
                appKeys.add(row.appKey());
            }
        }
        if (appKeys.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        List<com.chua.starter.ai.support.entity.SysAiAppKey> found = appKeyMapper.selectList(
                new LambdaQueryWrapper<com.chua.starter.ai.support.entity.SysAiAppKey>()
                        .in(com.chua.starter.ai.support.entity.SysAiAppKey::getAppKey, appKeys));
        Map<String, com.chua.starter.ai.support.entity.SysAiAppKey> result =
                new HashMap<>(Math.max(found.size(), 4));
        for (com.chua.starter.ai.support.entity.SysAiAppKey key : found) {
            result.put(key.getAppKey(), key);
        }
        return result;
    }

    /**
     * AppKey 打码：只保留前 6 位与后 4 位。
     *
     * <p>拓扑页只需要「认得是哪把钥匙」，不需要完整凭据。此前把完整 app_key 塞进
     * 响应体和 DOM 的 {@code title} 属性，任何能打开这个页面的账号都能顺手抄走凭据。</p>
     *
     * @param appKey 原始 AppKey，可为 null
     * @return 打码后的展示串
     */
    private static String maskAppKey(String appKey) {
        if (appKey == null || appKey.isBlank()) {
            return appKey;
        }
        int len = appKey.length();
        if (len <= 10) {
            return appKey.substring(0, Math.min(3, len)) + "****";
        }
        return appKey.substring(0, 6) + "****" + appKey.substring(len - 4);
    }

    /**
     * 把 AppKey 台账行的名称、余额、配额回填进入站节点。
     *
     * @param item 入站节点
     * @param key  AppKey 台账行，可为 null（日志里的 Key 已被删除）
     */
    private void applyAppKeyIdentity(Map<String, Object> item,
                                     com.chua.starter.ai.support.entity.SysAiAppKey key) {
        if (key == null) {
            item.put("name", null);
            item.put("orphan", true);
            return;
        }
        item.put("name", key.getName());
        item.put("status", key.getStatus());
        item.put("group_id", key.getGroupId());
        item.put("balance", key.getBalance());
        item.put("total_cost", key.getTotalCost());
        item.put("total_tokens", key.getTotalTokens());
        item.put("max_tokens", key.getMaxTokens());
        item.put("use_count", key.getUseCount());
        item.put("max_use", key.getMaxUse());
        item.put("expire_datetime", key.getExpireDatetime() == null ? null : toMillis(key.getExpireDatetime()));
        item.put("overdraft_enabled", key.getOverdraftEnabled());
    }

    /**
     * 本地时间 → 毫秒时间戳（前端统一按毫秒比较新旧）。
     *
     * @param time 本地时间，可为 null
     * @return 毫秒时间戳；{@code null} 原样返回
     */
    private static Long toMillis(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 保存路由组配置：成员（新增 / 移除 / 顺序）、策略与每模型 effort。
     *
     * <p>成员一律来自「已添加的模型库」：{@code members} 里出现的每一行都必须能在
     * {@code sys_account_channel_model} 里查到，否则整批拒绝 —— 避免配置上引用一个不存在的模型，
     * 直到真正调用时才失败。写入后让该路由组的客户端缓存立即失效。</p>
     *
     * @param providerId 路由组供应商 ID
     * @param body       配置（members / strategy / on_rate_limit / on_slow / on_busy / on_context …）
     * @return 保存后的配置
     */
    @Permission("admin")
    @PutMapping("/providers/{provider_id}/router-group")
    @Transactional
    public ApiResult<Map<String, Object>> saveRouterGroup(@PathVariable("provider_id") String providerId,
                                                          @RequestBody Map<String, Object> body) {
        SysAccountChannel provider = requireProvider(providerId);
        if (!routerGroupService.isRouterGroup(provider)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该供应商不是路由组: " + providerId);
        }
        List<com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.Member> members =
                parseRouterGroupMembers(providerId, body);
        com.chua.common.support.ai.chat.aggregate.RouterGroupSetting setting =
                routerGroupService.applyRequest(provider, body, members);
        // extra 列由账号模块统一写入（带 JacksonTypeHandler，不会退化成 BLOB 字节）
        accountChannelService.updateExtra(providerId, routerGroupService.writeSetting(provider, setting));
        modelClientResolver.invalidateRouterGroup(providerId);
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("setting", routerGroupService.toResponse(setting));
        result.put("members", routerGroupService.describeMembers(setting));
        log.info("保存路由组配置 provider={} 成员={} 策略={}", providerId,
                members.size(), setting.getStrategy());
        return ApiResult.ok(result);
    }

    /**
     * 调整路由组成员顺序（前端拖拽后调用）。
     *
     * @param providerId 路由组供应商 ID
     * @param body       含 {@code labels} 数组（{@code providerId:model}，新顺序）
     * @return 保存后的配置
     */
    @Permission("admin")
    @PutMapping("/providers/{provider_id}/router-group/order")
    @Transactional
    public ApiResult<Map<String, Object>> reorderRouterGroup(@PathVariable("provider_id") String providerId,
                                                             @RequestBody Map<String, Object> body) {
        SysAccountChannel provider = requireProvider(providerId);
        if (!routerGroupService.isRouterGroup(provider)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该供应商不是路由组: " + providerId);
        }
        @SuppressWarnings("unchecked")
        List<String> labels = body == null ? null : (List<String>) body.get("labels");
        if (labels == null || labels.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "labels 不能为空");
        }
        com.chua.common.support.ai.chat.aggregate.RouterGroupSetting setting =
                routerGroupService.readSetting(provider);
        Map<String, com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.Member> byLabel =
                new LinkedHashMap<>();
        for (com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.Member m : setting.getMembers()) {
            byLabel.put(m.label(), m);
        }
        List<com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.Member> ordered = new ArrayList<>();
        for (String label : labels) {
            com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.Member m = byLabel.remove(label);
            if (m != null) {
                ordered.add(m);
            }
        }
        // 未出现在新顺序里的成员按原相对顺序补齐，不允许因排序丢成员
        ordered.addAll(byLabel.values());
        setting.setMembers(ordered);
        // extra 列由账号模块统一写入（带 JacksonTypeHandler）
        accountChannelService.updateExtra(providerId, routerGroupService.writeSetting(provider, setting));
        modelClientResolver.invalidateRouterGroup(providerId);
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("setting", routerGroupService.toResponse(setting));
        result.put("members", routerGroupService.describeMembers(setting));
        return ApiResult.ok(result);
    }

    /**
     * 解析请求体里的成员数组，逐个校验「确实已加入模型库」。
     *
     * @param groupId 路由组 ID（用于拒绝自引用）
     * @param body    请求体
     * @return 成员列表（保持请求顺序）
     */
    private List<com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.Member> parseRouterGroupMembers(
            String groupId, Map<String, Object> body) {
        Object raw = body == null ? null : body.get("members");
        List<com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.Member> members = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return members;
        }
        Set<String> seen = new HashSet<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            String memberProviderId = m.get("provider_id") == null ? null : m.get("provider_id").toString().trim();
            String model = m.get("model") == null ? null : m.get("model").toString().trim();
            if (memberProviderId == null || memberProviderId.isEmpty() || model == null || model.isEmpty()) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "成员必须同时给出 provider_id 与 model");
            }
            if (groupId.equals(memberProviderId)) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "路由组不能把自己加入成员: " + model);
            }
            SysAccountChannel owner = accountChannelService.find(memberProviderId);
            if (owner == null || ProviderRouteTargets.isRouterGroup(owner.getPlatform())) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM,
                        "成员必须是已添加的模型: " + memberProviderId + ":" + model);
            }
            if (!routerGroupService.memberExists(memberProviderId, model)) {
                throw new ApiException(ApiErrorCode.NOT_FOUND,
                        "成员模型不在模型库中或已停用: " + memberProviderId + ":" + model);
            }
            String label = memberProviderId + ":" + model;
            if (!seen.add(label)) {
                continue;
            }
            String effort = m.get("effort") == null ? null : m.get("effort").toString().trim();
            int weight = parseRouterWeight(m.get("weight"));
            Boolean free = m.get("free") == null ? null : Boolean.valueOf(m.get("free").toString());
            Long expireAt = parseRouterLong(m.get("expire_at"));
            members.add(new com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.Member(
                    memberProviderId, model, true, weight,
                    effort == null || effort.isEmpty() ? null : effort, free, expireAt));
        }
        return members;
    }

    /**
     * 解析成员权重，非法值回落到 1。
     *
     * @param value 原始值
     * @return 权重（至少 1）
     */
    private int parseRouterWeight(Object value) {
        if (value == null) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(value.toString().trim()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * 解析可空长整数，非法值按无值处理。
     *
     * @param value 原始值
     * @return 长整数或 null
     */
    private Long parseRouterLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================== 模型 协议 ====================

    /**
     * 模型协议能力清单（服务端运营默认）。
     *
     * @param preset  平台预设
     * @param task    请求任务
     * @param baseUrl 已存在 提供者 URL 提示
     * @return ModelProtocolManifestResponse
     */
    @GetMapping("/model-protocols")
    public ApiResult<Map<String, Object>> modelProtocols(@RequestParam String preset,
                                                         @RequestParam String task,
                                                         @RequestParam(required = false) String base_url) {
        return ApiResult.ok(buildManifest(preset, task, base_url));
    }

    /**
     * 路由组在「供应商 → 模型」视图里对外暴露的唯一虚拟模型。
     *
     * <p>路由组没有自己的 {@code sys_account_channel_model} 行（成员是模型库里已有的行），
     * 但会话的模型面板只认 {@code provider.models}，因此这里合成一条模型项，
     * 让「选中这个路由组」在会话里可选。它不落库，也不出现在模型管理页的模型列表里。</p>
     *
     * @param provider 路由组供应商
     * @return 虚拟模型项
     */
    private Map<String, Object> routerGroupVirtualModel(SysAccountChannel provider) {
        String providerId = provider.getProviderId();
        String modelName = provider.getName() == null || provider.getName().isBlank()
                ? providerId : provider.getName();
        Map<String, Object> model = new LinkedHashMap<>(16);
        model.put("provider_id", providerId);
        model.put("model", modelName);
        model.put("id", providerId + ":" + modelName);
        model.put("modelType", "text");
        model.put("enabled", Boolean.TRUE.equals(provider.getEnabled()));
        model.put("sort_order", 0);
        model.put("alias", modelName);
        model.put("router_group", true);
        Map<String, Object> capability = new LinkedHashMap<>(4);
        capability.put("task", "chat");
        capability.put("traits", List.of("input:text", "output:text"));
        model.put("capabilities", List.of(capability));
        return model;
    }

    /**
     * 构造 提供者响应（含模型行与连接档案）。
     */
    private Map<String, Object> toProviderResponse(SysAccountChannel provider,
                                                   List<SysAccountChannelModel> models,
                                                   List<SysAccountChannelConnection> connections) {        Map<String, Object> map = new LinkedHashMap<>(12);
        map.put("provider_id", provider.getProviderId());
        map.put("platform", provider.getPlatform());
        map.put("name", provider.getName());
        map.put("base_url", provider.getBaseUrl());
        // 官网 / 官方文档（只读展示）：模型管理右栏页头「官网」入口点它。
        // 官网是「213 家模板」这一层的公共数据，由 datasearch 的供应商登记表统一维护，
        // 渠道行（sys_account_channel）没有这一列、也不该为它加列；查不到就回 null，
        // 前端隐藏入口 —— 本机 ONNX / 路由组 / 自建供应商没有对外站点。
        com.chua.starter.datasearch.provider.model.ProviderDescriptor siteDescriptor =
                ProviderRegistries.find(provider.getPlatform() == null
                        ? provider.getProviderId() : provider.getPlatform());
        map.put("website", siteDescriptor == null ? null : siteDescriptor.getWebsite());
        map.put("doc", siteDescriptor == null ? null : siteDescriptor.getDoc());
        // ONNX 供应商补 model_root：本地模型的真实落盘位置（绝对路径），供管理面显示与核对。
        // 用 displayModelRoot() 而非 readModelRoot()：后者未绑定过目录时返回空串，
        // 页面就不知道「模型该放到哪」；前者回落注册表默认根（models/onnx）并按
        // user.dir 绝对化，永远给出可直接复制的完整路径。
        // 只放这一个字段：/providers 是非管理员也可调的接口（写操作才拦），
        // 而 ONNX 行对非管理员已整体不返回（listProviders / listProviderModels 都过滤），
        // 因此该路径只可能到达管理员；不放 credentials.api_key（那是凭证语义）。
        if (isOnnxRow(provider)) {
            map.put("model_root", onnxExternalDirectoryService.displayModelRoot());
        }
        map.put("auth_scheme", provider.getAuthScheme());
        boolean hasCreds = provider.getCredentials() != null && !provider.getCredentials().isEmpty();
        map.put("has_credentials", hasCreds);
        
        // 返回key数量和掩码，方便前端显示"已配置N个key"
        if (hasCreds) {
            Map<String, Object> creds = provider.getCredentials();
            int keyCount = 0;
            String firstKeyMask = "";
            
            // 单个key
            Object singleKey = creds.get("api_key");
            if (singleKey != null && !singleKey.toString().isEmpty()) {
                String[] keys = singleKey.toString().split(",");
                keyCount = keys.length;
                firstKeyMask = maskKey(keys[0].trim());
            }
            
            // 多个key（list形式）
            Object multiKeys = creds.get("api_keys");
            if (multiKeys instanceof List) {
                List<?> keys = (List<?>) multiKeys;
                keyCount = keys.size();
                if (!keys.isEmpty() && keys.getFirst() != null) {
                    firstKeyMask = maskKey(keys.getFirst().toString().trim());
                }
            }
            
            map.put("api_key_count", keyCount);
            map.put("api_key_mask", firstKeyMask);

            // 完整 Key 列表只回给管理员：
            // 本接口（GET /api/providers）是非管理员也能调的读接口，写操作才拦 admin。
            // 明文 Key 属于凭证语义 —— 回给只读账号等于把可用的上游密钥交给它。
            // 前端取 api_keys 的地方只有两处，都在 admin-only 的写流程里
            // （ProviderFormDialog 编辑回显、ModelFetchDialog 拉模型前置判断），
            // 非管理员拿不到这一段不影响任何只读展示。
            if (com.chua.starter.ai.support.util.AiUserContext.isAdmin()) {
                List<String> allKeys = new ArrayList<>();
                if (singleKey != null && !singleKey.toString().isEmpty()) {
                    for (String k : singleKey.toString().split(",")) {
                        String trimmed = k.trim();
                        if (!trimmed.isEmpty()) {
                            allKeys.add(trimmed);
                        }
                    }
                }
                if (multiKeys instanceof List) {
                    for (Object k : (List<?>) multiKeys) {
                        if (k != null && !k.toString().isEmpty()) {
                            allKeys.add(k.toString().trim());
                        }
                    }
                }
                map.put("api_keys", allKeys);
            }
        }
        List<Map<String, Object>> modelItems = new ArrayList<>(8); // 模型数量不可预估
        for (SysAccountChannelModel model : models) {
            modelItems.add(toProviderModelResponse(provider, model));
        }
        map.put("models", modelItems);
        map.put("enabled", provider.getEnabled());
        if (provider.getBedrockConfig() != null) {
            map.put("bedrock_config", provider.getBedrockConfig());
        }
        map.put("proxy", provider.getProxy());
        map.put("extra", provider.getExtra());
        map.put("sort_order", provider.getSortOrder());
        map.put("created_at", provider.getCreatedAt());
        map.put("updated_at", provider.getUpdatedAt());
        return map;
    }

    /**
     * 构造 提供者模型响应（含任务能力数组），并从缓存快照合并价格/延迟/速度等指标。
     * @param provider 提供者
     * @param model 模型
     * @return 转为提供者模型响应的结果
     */
    private Map<String, Object> toProviderModelResponse(SysAccountChannel provider, SysAccountChannelModel model) {
        Map<String, Object> map = new LinkedHashMap<>(20);
        map.put("provider_id", provider.getProviderId());
        map.put("model", model.getModel());
        map.put("id", provider.getProviderId() + ":" + model.getModel());
        map.put("modelType", "text");
        map.put("enabled", model.getEnabled());
        map.put("sort_order", model.getSortOrder());
        if (model.getAlias() != null) {
            map.put("alias", model.getAlias());
        }
        if (model.getDescription() != null) {
            map.put("description", model.getDescription());
        }
        // 窗口与采样参数为空表示未指定，前端卡片据此回退到指标表中的官方值
        map.put("context_window", model.getContextWindow());
        map.put("max_tokens", model.getMaxTokens());
        map.put("temperature", model.getTemperature());
        map.put("top_p", model.getTopP());
        map.put("frequency_penalty", model.getFrequencyPenalty());
        // 指标回填字段：实体列 + metrics JSON 双存（同步价格时自动回填，编辑可改）
        if (model.getFamily() != null) {
            map.put("family", model.getFamily());
        }
        if (model.getKnowledge() != null) {
            map.put("knowledge", model.getKnowledge());
        }
        if (model.getReleaseDate() != null) {
            map.put("release_date", model.getReleaseDate());
        }
        if (model.getLastUpdated() != null) {
            map.put("last_updated", model.getLastUpdated());
        }
        if (model.getModalities() != null && !model.getModalities().isEmpty()) {
            map.put("modalities", model.getModalities());
        }
        map.put("open_weights", model.getOpenWeights());
        map.put("attachment", model.getAttachment());
        map.put("reasoning", model.getReasoning());
        map.put("tool_call", model.getToolCall());
        map.put("temperature_supported", model.getTemperatureSupported());
        // ONNX 模型补 external / available：设置页的模型选择器在 onnx 分支里
        // 按 `m.external === true` 过滤（见 DefaultModelSettings.loadModels），
        // 而本方法此前从不返回这两个键 —— 于是「图片识别/人脸/OCR」等分组的
        // 模型弹框里 ONNX 模型一个都不显示，提示「没有匹配类型的模型」，
        // 用户无从配置，只能靠接口写库绕过去。
        // external=true 表示该模型由「绑定外部目录」登记（模板模型名 + 独立权重目录），
        // 这类模型的权重一定在本地；内置模型则用 ModelRegistry 是否已注册判定可用性，
        // 避免把上百个未下载的候选也列出来。
        if (isOnnxRow(provider)) {
            boolean external = isExternalBoundModel(model.getModel());
            map.put("external", external);
            map.put("available", external || com.chua.deeplearning.support.engine.ModelRegistry
                    .isRegistered(model.getModel()));
        }
        List<Map<String, Object>> caps = model.getCapabilities() != null
                ? model.getCapabilities().stream().map(this::toCapabilityResponse).collect(Collectors.toList())
                : new ArrayList<>(1);
        map.put("capabilities", caps);
        // 生成参数（参考图 / 张数上限 / 分辨率列表 / 时长列表 / 步数区间）：
        // 原样回传 capabilities[].generate 节点，前端编辑时原样带回即可
        if (caps.stream().anyMatch(c -> c.get("generate") != null)) {
            map.put("generate", caps.stream()
                    .map(c -> c.get("generate"))
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null));
        }
        // 思考强度档位：与 generate 同款——原样回传 capabilities[].thinking_efforts，
        // 缺省不返回该键，前端按默认集合（low/medium/high/max）处理
        if (caps.stream().anyMatch(c -> c.get("thinking_efforts") != null)) {
            map.put("thinking_efforts", caps.stream()
                    .map(c -> c.get("thinking_efforts"))
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null));
        }
        // 图片输入输出细化（张数 / 参考图）：与 generate 同款——原样回传 capabilities[].image，
        // 缺省不返回该键，前端编辑弹窗按 null / false 兜底
        if (caps.stream().anyMatch(c -> c.get("image") != null)) {
            map.put("image", caps.stream()
                    .map(c -> c.get("image"))
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null));
        }
        // 模型指标优先使用自身配置，再从缓存快照补充价格、延迟、智能和速度数据
        Map<String, Object> metrics = model.getMetrics() != null
                ? new LinkedHashMap<>(model.getMetrics())
                : new LinkedHashMap<>(4);
        Map<String, Object> cached = metricIndex().get(provider.getProviderId() + "::" + model.getModel());
        if (cached != null) {
            for (String key : METRIC_KEYS) {
                Object value = cached.get(key);
                if (value != null) {
                    metrics.putIfAbsent(key, value);
                }
            }
        }
        // 本地指标库兜底：速度 / 首字 / 智能 / 全知 / 价格 / 上下文。快照没给值的键
        // 从 sys_ai_model_metrics 补，已有的不动（模型自身配置和快照都优先）。
        try {
            fillFromMetricsLibrary(provider, model, metrics);
        } catch (Throwable e) {
            log.warn("[ApiProvider] 读取本地指标库失败: {}", e.getMessage());
        }
        // 公共目录兜底：免登录 / 未绑定账号的渠道没有 baseUrl，渠道目录取不到，
        // 历史模型行也从未写过模态，卡片上的「输入/输出」与输出上限就一直是空白。
        // 按模型 id 从 models.dev 快照补齐（只补缺，不覆盖已配置的值）。
        try {
            fillFromPublicCatalog(provider, model, metrics);
        } catch (Throwable e) {
            log.warn("[ApiProvider] 读取公共模型目录失败: {}", e.getMessage());
        }
        if (!metrics.isEmpty()) {
            map.put("metrics", metrics);
        }
        map.put("created_at", model.getCreatedAt());
        map.put("updated_at", model.getUpdatedAt());
        return map;
    }

    /**
     * 将键截断为固定长度（可排序、可索引）
     *
     * @param value  原值
     * @param maxLen 最大长度
     * @return 截断后的值；入参为空时返回空串
     */
    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    /**
     * 用本地指标库（sys_ai_model_metrics）补齐模型指标里缺失的键。
     *
     * <p><b>为什么需要</b>：卡片的「速度 / 首字 / 智能 / 全知 / 价格 / 上下文」原本只靠
     * 指标快照填充，而快照来自 Artificial Analysis 网页的 Next.js flight 数据。该页面
     * 2026-11 起已抓不到 {@code medianOutputTokensPerSecond} /
     * {@code medianTimeToFirstTokenSeconds} / {@code hle} 三个键（实测抓下来 893KB，
     * 三个键出现 0 次），这批指标就恒为 —。本地指标库由同步任务写库，这些值是有数的
     * （hle 305 条、首字 241 条、速度 159 条），按同一归一化模型名兜底补上。</p>
     *
     * <p><b>写入规则</b>：</p>
     * <ul>
     *   <li>只补缺：模型自身配置、快照已有的键一律不动，不覆盖可信数据；</li>
     *   <li>速度优先本地均值 {@code avg_output_speed_tps}，其次官方中位
     *       {@code output_speed_tps}（前端本来就按这个顺序取值）；</li>
     *   <li>首字延迟列单位混杂（多数是秒，少量历史行是毫秒）：只在 (0, 30] 区间按秒
     *       写入，超过 30 视作毫秒脏值丢弃 —— 宁可不显示，也不显示 94174ms；</li>
     *   <li>免费通道的模型名带 {@code -free} 后缀（如 deepseek-v4.1-flash-free）：指标库里
     *       这种后缀行多数是全空壳，真正有数的是去掉后缀的上游模型行，所以两行都读、
     *       精确行优先，缺的键再由上游行补齐。</li>
     * </ul>
     *
     * @param provider 供应商行
     * @param model    模型行
     * @param metrics  待补充的指标映射（就地写入）
     */
    private void fillFromMetricsLibrary(SysAccountChannel provider, SysAccountChannelModel model, Map<String, Object> metrics) {
        String key = truncate(
                AiModelMetricKeys.normalizeKey(provider.getProviderId() + "/" + model.getModel()), 160);
        if (key.isEmpty()) {
            return;
        }
        Map<String, SysAiModelMetrics> index = metricsLibraryIndex();
        applyMetricsRow(metrics, index.get(key));
        if (key.endsWith(FREE_MODEL_SUFFIX)) {
            // 指标库里常常同时存在「-free 行」和「上游模型行」：前者多为全空壳，
            // 后者才有速度/智能/HLE。两行都取，精确行优先，缺的键由上游行补。
            String upstreamKey = key.substring(0, key.length() - FREE_MODEL_SUFFIX.length());
            if (!upstreamKey.isEmpty()) {
                applyMetricsRow(metrics, index.get(upstreamKey));
            }
        }
    }

    /**
     * 用公开目录（models.dev 快照）按模型 id 补齐模态、上下限与能力位。
     *
     * <p><b>为什么走模型 id</b>：免登录渠道（DuckDuckGo / Pollinations / OpenCode）没有
     * {@code baseUrl}，登录型渠道在未绑定账号时也没有，「平台 + 上游地址」的渠道目录
     * 对它们一律落空；而 {@code sys_account_channel_model} 的历史行只存过 {@code tasks}，
     * 于是模型卡片的输入/输出一直是空白。这里按模型 id 查（{@link ProviderModelMetrics}），
     * 与渠道挂在哪家公司无关。</p>
     *
     * <p><b>写入规则</b>：只补缺，模型自身配置优先；目录没收录的模型什么都不写。</p>
     *
     * @param provider 供应商行
     * @param model    模型行
     * @param metrics  待补充的指标映射（就地写入）
     */
    private void fillFromPublicCatalog(SysAccountChannel provider, SysAccountChannelModel model,
                                       Map<String, Object> metrics) {
        Map<String, Object> catalog = ProviderModelMetrics.metricsOf(
                ProviderModelMetrics.of(provider.getProviderId(), model.getModel()));
        for (Map.Entry<String, Object> entry : catalog.entrySet()) {
            putIfAbsentMetric(metrics, entry.getKey(), entry.getValue());
        }
    }

    /**
     * 把指标库里的一行并入模型指标（只补缺）。
     *
     * @param metrics 模型指标映射（就地写入）
     * @param row     指标库行，可为空
     */
    private static void applyMetricsRow(Map<String, Object> metrics, SysAiModelMetrics row) {
        if (row == null) {
            return;
        }
        putIfAbsentMetric(metrics, "avg_output_speed_tps", row.getAvgOutputSpeedTps());
        putIfAbsentMetric(metrics, "output_speed_tps", row.getOutputSpeedTps());
        putIfAbsentMetric(metrics, "intelligence_index", row.getIntelligenceIndex());
        putIfAbsentMetric(metrics, "hle_score", row.getHleScore());
        putIfAbsentMetric(metrics, "omniscience_index", row.getOmniscienceIndex());
        putIfAbsentMetric(metrics, "price_input_per_musd", row.getPriceInputPerMusd());
        putIfAbsentMetric(metrics, "price_output_per_musd", row.getPriceOutputPerMusd());
        putIfAbsentMetric(metrics, "context_window_tokens", row.getContextWindowTokens());
        java.math.BigDecimal ttft = row.getLatencyFirstTokenSeconds();
        if (ttft != null && ttft.signum() > 0
                && ttft.compareTo(java.math.BigDecimal.valueOf(TRUSTED_FIRST_TOKEN_MAX_SECONDS)) <= 0) {
            putIfAbsentMetric(metrics, "latency_first_token_seconds", ttft);
        }
    }

    /**
     * 指标缺失时才写入（已有值不动）。
     *
     * @param metrics 指标映射
     * @param key     指标键
     * @param value   指标值，空则跳过
     */
    private static void putIfAbsentMetric(Map<String, Object> metrics, String key, Object value) {
        if (value != null) {
            metrics.putIfAbsent(key, value);
        }
    }

    /**
     * 构建（带 TTL 缓存）提供者标识::模型 → 缓存快照行的索引。
     *
     * <p>指标由 {@link ModelMetricsPushTask} 定时写入 Redis，本方法优先读取
     * Redis 快照并本地缓存 30 秒，避免每个模型行重复解析快照。</p>
     *
     * @return 指标索引，快照为空时返回空 映射
     */
    private Map<String, Map<String, Object>> metricIndex() {
        long now = System.currentTimeMillis();
        Map<String, Map<String, Object>> current = metricsIndex;
        if (now - metricsIndexLoadedAt <= METRICS_INDEX_TTL_MILLIS) {
            return current;
        }
        if (now < metricsIndexRetryAt) {
            // 上一次构建刚失败：退避期内直接给旧索引，别让每个请求都去打一次快照
            return current;
        }
        // 单飞：并发请求不排队也不重复重建，抢不到的那一个用旧索引立即返回。
        // 不加实例锁（锁会把外部延迟放大成全局串行），也不放任重复构建（冷启动 30 个并发
        // 就是 30 次全表查询，跨隧道等于 60 次往返）。
        if (!metricsIndexRefreshing.compareAndSet(false, true)) {
            return current;
        }
        try {
            Map<String, Map<String, Object>> index = new HashMap<>(64); // 提供者模型索引不可预估
            Map<String, Object> snapshot = metricsSnapshotService.latestSnapshot();
            Object modelsObj = snapshot.get("models");
            if (modelsObj instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> row)) {
                        continue;
                    }
                    Object providerId = row.get("provider_id");
                    Object modelName = row.get("model");
                    if (providerId != null && modelName != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> rowMap = (Map<String, Object>) row;
                        index.put(providerId + "::" + modelName, rowMap);
                    }
                }
            }
            metricsIndex = index;
            metricsIndexLoadedAt = System.currentTimeMillis();
            return index;
        } catch (Exception e) {
            // 失败绝不能覆盖已有索引：原来这里无条件 metricsIndex = index，
            // 一次抖动就把整页指标清空，且空表不进 TTL 缓存 → 后续每个请求都重查。
            metricsIndexRetryAt = System.currentTimeMillis() + METRICS_INDEX_RETRY_BACKOFF_MILLIS;
            log.warn("[ApiProvider] 缓存指标索引构建失败，继续使用旧索引: {}", e.getMessage());
            return current;
        } finally {
            metricsIndexRefreshing.set(false);
        }
    }

    /**
     * 构建（带 TTL 缓存）本地指标库索引：归一化模型键 → 指标行。
     *
     * <p>一次 {@code selectList} 取全表（千行级别）在内存里建索引，替代原先
     * 「每个模型 getByKey 一次」的做法。后者在跨网络访问数据库时会把一次列表
     * 请求放大成上百次往返，把接口拖到分钟级；同时本方法不加锁，冷启动并发
     * 最多重复构建一次，不会互相阻塞。</p>
     *
     * @return 模型键 → 指标行，读取失败时返回上一次的索引（可能为空）
     */
    private Map<String, SysAiModelMetrics> metricsLibraryIndex() {
        long now = System.currentTimeMillis();
        Map<String, SysAiModelMetrics> current = metricsLibraryIndex;
        if (now - metricsLibraryLoadedAt <= METRICS_LIBRARY_TTL_MILLIS) {
            return current;
        }
        if (now < metricsLibraryRetryAt) {
            return current;
        }
        if (!metricsLibraryRefreshing.compareAndSet(false, true)) {
            return current;
        }
        try {
            Map<String, SysAiModelMetrics> index = new HashMap<>(1024);
            for (SysAiModelMetrics row : sysAiModelMetricsService.listAll()) {
                index.put(row.getModelKey(), row);
            }
            metricsLibraryIndex = index;
            metricsLibraryLoadedAt = System.currentTimeMillis();
            return index;
        } catch (Exception e) {
            metricsLibraryRetryAt = System.currentTimeMillis() + METRICS_LIBRARY_RETRY_BACKOFF_MILLIS;
            log.warn("[ApiProvider] 本地指标库索引构建失败，继续使用旧索引: {}", e.getMessage());
            return current;
        } finally {
            metricsLibraryRefreshing.set(false);
        }
    }

    /**
     * 构造单任务能力响应（含最近健康探测结果回显）。
     * @param cap 模型能力配置映射
     * @return 转为capability响应的结果
     */
    private Map<String, Object> toCapabilityResponse(Map<String, Object> cap) {
        Map<String, Object> map = new LinkedHashMap<>(14);
        map.put("task", cap.get("task"));
        map.put("traits", cap.get("traits") != null ? cap.get("traits") : List.of());
        map.put("protocol", cap.get("protocol"));
        map.put("connection_role", cap.get("connection_role"));
        // OCR/人脸组合模型细分类型：与 generate 同理，必须显式透传，
        // 否则数据库虽已保存，前端刷新后只能看到粗类型。
        if (cap.get("subtype") != null) {
            map.put("subtype", cap.get("subtype"));
        }
        // 生成参数节点：白名单式构造会丢字段，这里显式透传，供模型弹框回显与编辑
        if (cap.get("generate") != null) {
            map.put("generate", cap.get("generate"));
        }
        // 思考强度档位（low/medium/high/max）：与 generate 同理，显式透传，
        // 供模型管理弹框回显与编辑；未配置时前端回退到默认集合
        if (cap.get("thinking_efforts") != null) {
            map.put("thinking_efforts", cap.get("thinking_efforts"));
        }
        // 图片输入输出细化（张数 / 参考图）：与 generate 同理，显式透传，
        // 供模型弹框回显与编辑；未配置时缺省不返回该键
        if (cap.get("image") != null) {
            map.put("image", cap.get("image"));
        }
        if (cap.get("base_url_override") != null) {
            map.put("base_url_override", cap.get("base_url_override"));
        }
        if (cap.get("endpoint") != null) {
            map.put("endpoint", cap.get("endpoint"));
        }
        if (cap.get("poll_endpoint") != null) {
            map.put("poll_endpoint", cap.get("poll_endpoint"));
        }
        if (cap.get("content_endpoint") != null) {
            map.put("content_endpoint", cap.get("content_endpoint"));
        }
        if (cap.get("realtime_endpoint") != null) {
            map.put("realtime_endpoint", cap.get("realtime_endpoint"));
        }
        map.put("allow_cross_origin_credentials", cap.get("allow_cross_origin_credentials") != null
                && (boolean) cap.get("allow_cross_origin_credentials"));
        map.put("provider_params", cap.get("provider_params"));
        if (cap.get("context_limit") != null) {
            map.put("context_limit", cap.get("context_limit"));
        }
        // 最近一次健康探测结果，包括状态、延迟和错误信息
        if (cap.get("health") != null) {
            map.put("health", cap.get("health"));
        }
        if (cap.get("health_checked_at") != null) {
            map.put("health_checked_at", cap.get("health_checked_at"));
        }
        map.put("created_at", cap.get("created_at"));
        map.put("updated_at", cap.get("updated_at"));
        return map;
    }

    /**
     * 构造 提供者connection响应（不回显 凭证）。
     * @param conn 提供者连接配置
     * @return 转为connection响应的结果
     */
    private Map<String, Object> toConnectionResponse(SysAccountChannelConnection conn) {
        Map<String, Object> map = new LinkedHashMap<>(10);
        map.put("connection_id", conn.getConnectionId());
        map.put("provider_id", conn.getProviderId());
        map.put("role", conn.getRole());
        if (conn.getLabel() != null) {
            map.put("label", conn.getLabel());
        }
        map.put("base_url", conn.getBaseUrl());
        map.put("auth_scheme", conn.getAuthScheme());
        map.put("has_credentials", conn.getCredentials() != null && !conn.getCredentials().isEmpty());
        map.put("extra", conn.getExtra());
        map.put("created_at", conn.getCreatedAt());
        map.put("updated_at", conn.getUpdatedAt());
        return map;
    }

    // ==================== 实体构建 ====================

    /**
     * 由模型输入构造模型实体（新建）。
     * @param providerId 提供者标识
     * @param input 输入
     * @param now now
     * @return 转为模型实体的结果
     */
    @SuppressWarnings("unchecked")
    private SysAccountChannelModel toModelEntity(String providerId, Map<String, Object> input, long now) {
        SysAccountChannelModel e = new SysAccountChannelModel();
        e.setProviderId(providerId);
        e.setModel(requireStr(input, "model"));
        e.setAlias(str(input, "alias"));
        e.setEnabled(bool(input, "enabled", true));
        e.setSortOrder(intv(input, "sort_order", 0));
        e.setDescription(str(input, "description"));
    // 窗口与采样参数：模型弹框填了就落库；留空表示「未指定」，
    // 前端模型卡片会回退到指标表（sys_ai_model_metrics）里的官方值
        e.setContextWindow(intOrNull(input, "context_window"));
        e.setMaxTokens(intOrNull(input, "max_tokens"));
        e.setTemperature(dblOrNull(input, "temperature"));
        e.setTopP(dblOrNull(input, "top_p"));
        e.setFrequencyPenalty(dblOrNull(input, "frequency_penalty"));
        // 模型能力列表直接保存原始数据
        Object capsObj = input.get("capabilities");
        if (capsObj instanceof List<?> capsList) {
            List<Map<String, Object>> caps = new ArrayList<>(8); // 能力数量不可预估
            for (Object c : capsList) {
                if (c instanceof Map<?, ?> m) {
                    caps.add((Map<String, Object>) m);
                }
            }
            e.setCapabilities(caps);
        } else {
            e.setCapabilities(new ArrayList<>(1));
        }
        // 生成参数（参考图 / 张数上限 / 分辨率列表 / 时长列表 / 步数区间）：
        // 复用 capabilities[].generate 节点，不新增数据库字段
        applyGenerateConfig(e, input.get("generate"));
        // 思考强度档位（none/minimal/low/medium/high/max）：
        // 同样复用 capabilities[].thinking_efforts
        applyThinkingEfforts(e, input.get("thinking_efforts"));
        // 指标回填字段（实体列，同步价格回填 + 编辑可改）
        e.setFamily(str(input, "family"));
        e.setKnowledge(str(input, "knowledge"));
        e.setReleaseDate(str(input, "release_date"));
        e.setLastUpdated(str(input, "last_updated"));
        e.setModalities(toModalitiesMap(input.get("modalities")));
        e.setOpenWeights(boolOrNull(input, "open_weights"));
        e.setAttachment(boolOrNull(input, "attachment"));
        e.setReasoning(boolOrNull(input, "reasoning"));
        e.setToolCall(boolOrNull(input, "tool_call"));
        e.setTemperatureSupported(boolOrNull(input, "temperature_supported"));
        // 指标数据直接保存延迟、智能和输出速度等原始数据
        Object metricsObj = input.get("metrics");
        if (metricsObj instanceof Map<?, ?> metricsMap) {
            e.setMetrics((Map<String, Object>) metricsMap);
        }
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    /**
     * 用采集到的模型指标补全模型参数。
     *
     * <p>在模型<strong>新增、批量导入或改名后</strong>调用，两级补齐：</p>
     * <ol>
     *   <li>AA 指标（上下文长度、输出上限、价格、各类评分）→ 实体列与 metrics JSON；</li>
     *   <li>服务商指标端点（Models.dev 等 {@link ChatClientIndicator}）→ 按模型名匹配，
     *       回填 family / knowledge / release_date / last_updated / modalities / 能力开关
     *       实体列与 metrics JSON。</li>
     * </ol>
     *
     * <p><b>只在未配置时回填</b>：{@code contextWindow} / {@code maxTokens} 及新增的
     * 指标实体列为 {@code null}（用户未填）才写入；用户已显式配置的值一律保留，不被采集数据覆盖。
     * 指标本身以 {@code putIfNotNull} 写入，采集侧无值时不会产生空字段。</p>
     *
     * @param provider 服务商行（用于解析指标端点）
     * @param model    模型实体（就地修改）
     * @return 是否有字段被回填
     */
    private boolean enrichFromMetrics(SysAccountChannel provider, SysAccountChannelModel model) {
        if (model == null || model.getModel() == null || model.getModel().isBlank()) {
            return false;
        }
        Map<String, Object> row = new LinkedHashMap<>(32);
        try {
            ProviderModelMetrics.mergeInto(model.getModel(), row);
        } catch (Exception e) {
            log.debug("[provider-model] 指标补全跳过 model={} 原因={}", model.getModel(), e.getMessage());
            row.clear();
        }
        boolean changed = false;
        if (model.getContextWindow() == null) {
            Integer ctx = intOrNull(row, "context_window_tokens");
            if (ctx != null && ctx > 0) {
                model.setContextWindow(ctx);
                changed = true;
            }
        }
        if (model.getMaxTokens() == null) {
            Integer max = intOrNull(row, "max_output_tokens");
            if (max == null) {
                max = intOrNull(row, "max_tokens");
            }
            if (max != null && max > 0) {
                model.setMaxTokens(max);
                changed = true;
            }
        }
        // 其余指标（价格、评分、能力标记）合并进 metrics 原始列，不覆盖已有键
        Map<String, Object> current = model.getMetrics();
        Map<String, Object> metrics = current == null
                ? new LinkedHashMap<>(32)
                : new LinkedHashMap<>(current);
        for (Map.Entry<String, Object> item : row.entrySet()) {
            String key = item.getKey();
            if (metrics.containsKey(key)) {
                continue;
            }
            if ("context_window_tokens".equals(key) || "max_output_tokens".equals(key)
                    || "max_tokens".equals(key)) {
                // 已映射到实体列，不再重复写入 metrics
                continue;
            }
            metrics.put(key, item.getValue());
        }
        if (!metrics.equals(current)) {
            model.setMetrics(metrics);
            changed = true;
        }
        // 服务商指标端点（Models.dev 等）：按模型名匹配快照，回填指标实体列
        changed |= enrichFromIndicator(provider, model);
        if (changed) {
            log.info("[provider-model] 已用采集指标补全参数 model={} contextWindow={} maxTokens={}",
                    model.getModel(), model.getContextWindow(), model.getMaxTokens());
        }
        return changed;
    }

    /**
     * 按模型名从服务商指标端点查指标快照（新增/编辑模型页「自动获取」用）。
     *
     * <p>精确匹配 → 归一化键匹配 → 归一化前缀包含匹配三级；未收录返回 null。</p>
     *
     * @param provider  服务商行
     * @param modelName 模型名
     * @return 指标快照（Models.dev 等 indicator 的标准键），未找到返回 null
     */
    private Map<String, Object> findIndicatorMetrics(SysAccountChannel provider, String modelName) {
        try {
            ChatClientIndicator indicator = resolveIndicator(provider);
            if (indicator == null) {
                return null;
            }
            Map<String, Map<String, Object>> all = indicator.modelMetrics();
            if (all == null || all.isEmpty()) {
                return null;
            }
            if (modelName == null || modelName.isBlank()) {
                return null;
            }
            Map<String, Object> hit = all.get(modelName);
            if (hit == null) {
                String key = AiModelMetricKeys.normalizeKey(modelName);
                if (key != null && !key.isBlank()) {
                    hit = all.get(key);
                    if (hit == null) {
                        for (Map.Entry<String, Map<String, Object>> e : all.entrySet()) {
                            if (key.equals(AiModelMetricKeys.normalizeKey(e.getKey()))) {
                                hit = e.getValue();
                                break;
                            }
                        }
                    }
                }
            }
            return hit;
        } catch (Exception e) {
            log.debug("[provider-model] 指标快照查询跳过 model={} 原因={}", modelName, e.getMessage());
            return null;
        }
    }

    /**
     * 从服务商指标端点（Models.dev 等）按模型名回填指标实体列与 metrics JSON。
     *
     * <p>复用 {@link SysAiModelMetricsService#enrichRowFromMetrics}：与价格同步
     * 走同一套口径（family / knowledge / release_date / last_updated / modalities /
     * open_weights / attachment / reasoning / tool_call / temperature_supported），
     * 仅在实体列为空时回填。指标端点不可用或未收录该模型时返回 false。</p>
     *
     * @param provider 服务商行
     * @param model    模型实体（就地修改）
     * @return 是否回填了指标实体列
     */
    private boolean enrichFromIndicator(SysAccountChannel provider, SysAccountChannelModel model) {
        try {
            Map<String, Object> hit = findIndicatorMetrics(provider, model.getModel());
            if (hit == null || hit.isEmpty()) {
                return false;
            }
            Map<String, Object> current = model.getMetrics() == null
                    ? new LinkedHashMap<>(32)
                    : new LinkedHashMap<>(model.getMetrics());
            ChatClientIndicator indicator = resolveIndicator(provider);
            Long capturedAt = indicator == null ? null : indicator.capturedAt();
            sysAiModelMetricsService.enrichRowFromMetrics(model, current, hit,
                    indicator == null ? null : indicator.source(),
                    indicator == null ? null : indicator.sourceUrl(),
                    capturedAt == null ? System.currentTimeMillis() : capturedAt);
            return true;
        } catch (Exception e) {
            log.debug("[provider-model] 指标端点回填跳过 model={} 原因={}", model.getModel(), e.getMessage());
            return false;
        }
    }

    /**
     * 把模型输入应用到已有实体（更新，替换完整能力集）。
     * @param e e
     * @param input 输入
     */
    @SuppressWarnings("unchecked")
    private void applyModelInput(SysAccountChannelModel e, Map<String, Object> input) {
        if (input.containsKey("alias")) {
            e.setAlias(str(input, "alias"));
        }
        if (input.containsKey("enabled")) {
            e.setEnabled(bool(input, "enabled", e.getEnabled()));
        }
        if (input.containsKey("sort_order")) {
            e.setSortOrder(intv(input, "sort_order", e.getSortOrder()));
        }
        if (input.containsKey("description")) {
            e.setDescription(str(input, "description"));
        }
    // 窗口与采样参数：按字段存在与否覆盖，允许显式传 null 清空
        if (input.containsKey("context_window")) {
            e.setContextWindow(intOrNull(input, "context_window"));
        }
        if (input.containsKey("max_tokens")) {
            e.setMaxTokens(intOrNull(input, "max_tokens"));
        }
        if (input.containsKey("temperature")) {
            e.setTemperature(dblOrNull(input, "temperature"));
        }
        if (input.containsKey("top_p")) {
            e.setTopP(dblOrNull(input, "top_p"));
        }
        if (input.containsKey("frequency_penalty")) {
            e.setFrequencyPenalty(dblOrNull(input, "frequency_penalty"));
        }
        if (input.containsKey("capabilities")) {
            Object capsObj = input.get("capabilities");
            if (capsObj instanceof List<?> capsList) {
List<Map<String, Object>> caps = new ArrayList<>(8); // 能力数量不可预估
                for (Object c : capsList) {
                    if (c instanceof Map<?, ?> m) {
                        caps.add((Map<String, Object>) m);
                    }
                }
                e.setCapabilities(caps);
            }
        }
        // 生成参数：按字段存在与否覆盖（复用 capabilities[].generate 节点）
        if (input.containsKey("generate")) {
            applyGenerateConfig(e, input.get("generate"));
        }
        // 思考强度档位：按字段存在与否覆盖（复用 capabilities[].thinking_efforts 节点）
        if (input.containsKey("thinking_efforts")) {
            applyThinkingEfforts(e, input.get("thinking_efforts"));
        }
        if (input.containsKey("metrics")) {
            Object metricsObj = input.get("metrics");
            if (metricsObj instanceof Map<?, ?> metricsMap) {
                e.setMetrics((Map<String, Object>) metricsMap);
            } else {
                e.setMetrics(null);
            }
        }
    }

    /**
     * 把请求体中的生成参数写入实体的 {@code capabilities[].generate} 节点。
     *
     * <p>capabilities 为空时会先建一条空记录，保证 generate 有处安放；整段生成参数
     * 复用已有 JSON 列，不新增数据库字段。</p>
     *
     * @param entity 模型实体（原地修改）
     * @param raw    请求体中的 generate 对象，可为 null（表示不处理）
     */
    @SuppressWarnings("unchecked")
    private void applyGenerateConfig(SysAccountChannelModel entity, Object raw) {
        if (raw == null) {
            return;
        }
        Map<String, Object> gen;
        if (raw instanceof Map<?, ?> map) {
            gen = (Map<String, Object>) map;
        } else {
            return;
        }
        List<Map<String, Object>> caps = entity.getCapabilities();
        if (caps == null || caps.isEmpty()) {
            caps = new ArrayList<>(1);
            caps.add(new LinkedHashMap<>(4));
            entity.setCapabilities(caps);
        }
        Map<String, Object> node = caps.getFirst();
        if (node == null) {
            node = new LinkedHashMap<>(4);
            caps.set(0, node);
        }
        node.put("generate", gen);
    }

    /**
     * 把请求体中的思考强度档位写入实体的 {@code capabilities[].thinking_efforts} 节点。
     *
     * <p>与 {@link #applyGenerateConfig} 同款：整段档位清单复用已有 JSON 列，不新增
     * 数据库字段；空列表表示「未配置」——前端按默认集合（low/medium/high/max）兜底。</p>
     *
     * @param entity 模型实体
     * @param raw    档位清单（List&lt;String&gt;；null 表示本次不改动）
     */
    @SuppressWarnings("unchecked")
    private void applyThinkingEfforts(SysAccountChannelModel entity, Object raw) {
        if (!(raw instanceof List<?> list)) {
            return;
        }
        List<String> efforts = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item != null && !String.valueOf(item).isBlank()) {
                efforts.add(String.valueOf(item).trim());
            }
        }
        List<Map<String, Object>> caps = entity.getCapabilities();
        if (caps == null || caps.isEmpty()) {
            caps = new ArrayList<>(1);
            caps.add(new LinkedHashMap<>(4));
            entity.setCapabilities(caps);
        }
        Map<String, Object> node = caps.getFirst();
        if (node == null) {
            node = new LinkedHashMap<>(4);
            caps.set(0, node);
        }
        node.put("thinking_efforts", efforts);
    }

    /**
     * 由连接输入构造连接实体（新建）。
     * @param providerId 提供者标识
     * @param input 输入
     * @param now now
     * @return 转为connection实体的结果
     */
    private SysAccountChannelConnection toConnectionEntity(String providerId, Map<String, Object> input, long now) {
        SysAccountChannelConnection e = new SysAccountChannelConnection();
        e.setConnectionId(String.valueOf(System.currentTimeMillis() + (int)(Math.random() * 1000)));
        e.setProviderId(providerId);
        e.setRole(requireStr(input, "role"));
        e.setLabel(str(input, "label"));
        // 归属**故意留空**：本链路的写入入口全部是 admin-only（createProvider / saveConnection /
        // cloneProvider），落成 user_id IS NULL 即 SysAccountChannelConnection 里定义的
        // 「管理员/系统账号池」—— 管理员读得到（账号模块的归属过滤额外放行 NULL），
        // 普通用户读不到，免费渠道（_free 作用域）也靠这批行取账号。
        // 不要在这里写 currentUserId()：那样免费渠道路由会一条都取不到。
        e.setBaseUrl(requireStr(input, "base_url"));
        e.setAuthScheme(requireStr(input, "auth_scheme"));
        e.setCredentials(uncheckedCast(input.get("credentials")));
        e.setExtra(uncheckedCast(input.get("extra")));
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    // ==================== 模型协议清单 ====================

    /**
     * 构建manifest。
     * @param preset preset
     * @param task 任务
     * @param baseUrl baseurl
     * @return 构建manifest的结果
     */
    private Map<String, Object> buildManifest(String preset, String task, String baseUrl) {
        // 前端 preset 可能传大写形式（如 Zhipu），统一转小写后匹配
        String presetKey = preset == null ? null : preset.toLowerCase();
        ProtocolPreset pp = PROTOCOL_PRESETS.getOrDefault(presetKey,
                new ProtocolPreset(presetKey != null ? presetKey : "custom", "https://api.openai.com/v1"));
        // 本地模型（Ollama/ONNX）无需鉴权
        boolean localModel = presetKey != null && (presetKey.contains("ollama") || presetKey.contains("onnx"));
        String defaultAuthScheme = localModel ? "none" : "api_key";
        Map<String, Object> manifest = new LinkedHashMap<>(8);
        manifest.put("tasks", ALL_TASKS);
        manifest.put("preset", preset);
        manifest.put("platform", pp.platform);
        manifest.put("requested_task", task);
        manifest.put("platform_default_base_url", baseUrl != null ? baseUrl : pp.defaultBaseUrl);
        manifest.put("requires_user_input", false);
        manifest.put("default_auth_scheme", defaultAuthScheme);
        manifest.put("auth_schemes", authSchemes());

        Map<String, Object> recommendation = new LinkedHashMap<>(6);
        recommendation.put("protocol_id", pp.platform + "-openai-compat");
        recommendation.put("connection_role", "default");
        recommendation.put("default_base_url", pp.defaultBaseUrl);
        recommendation.put("default_auth_scheme", defaultAuthScheme);
        recommendation.put("base_url_override_required", false);
        manifest.put("recommendation", recommendation);

        manifest.put("protocols", List.of(buildProtocol(pp, task, defaultAuthScheme)));
        return manifest;
    }

    /**
     * 构建协议。
     * @param pp pp
     * @param task 任务
     * @param defaultAuthScheme 默认认证scheme
     * @return 构建协议的结果
     */
    private Map<String, Object> buildProtocol(ProtocolPreset pp, String task, String defaultAuthScheme) {
        Map<String, Object> protocol = new LinkedHashMap<>(8);
        protocol.put("protocol_id", pp.platform + "-openai-compat");
        protocol.put("supported_tasks", ALL_TASKS);
        protocol.put("executor", "model_invoke");
        protocol.put("transport", "http");
        protocol.put("allowed_auth_schemes", List.of("api_key", "bearer", "oauth", "none"));
        protocol.put("scopes", List.of("official_compat", "custom"));
        protocol.put("platforms", List.of(pp.platform));

        Map<String, Object> dc = new LinkedHashMap<>(8);
        dc.put("preset", pp.platform);
        dc.put("platform", pp.platform);
        dc.put("connection_role", "default");
        dc.put("connection_label", null);
        dc.put("base_url", pp.defaultBaseUrl);
        dc.put("auth_scheme", defaultAuthScheme);
        // 本地模型（Ollama/ONNX）无需凭证
        dc.put("requires_credentials", !"none".equals(defaultAuthScheme));
        protocol.put("default_connections", List.of(dc));
        protocol.put("endpoints", List.of());
        return protocol;
    }

    /**
     * 认证schemes。
     * @return 认证schemes的结果
     */
    private List<Map<String, Object>> authSchemes() {
        return List.of(
                /**
                 * 认证scheme。
                 * @param "api_key" "api_键"
                 * @param true true
                 */
                authScheme("api_key", true),
                /**
                 * 认证scheme。
                 * @param "bearer" "bearer"
                 * @param true true
                 */
                authScheme("bearer", true),
                /**
                 * 认证scheme。
                 * @param "oauth" "OAuth"
                 * @param true true
                 */
                authScheme("oauth", true),
                /**
                 * 认证scheme。
                 * @param "none" "无"
                 * @param false false
                 */
                authScheme("none", false)
        );
    }

    /**
     * 认证scheme。
     * @param scheme scheme
     * @param parameterized parameterized
     * @return 认证scheme的结果
     */
    private Map<String, Object> authScheme(String scheme, boolean parameterized) {
        Map<String, Object> m = new LinkedHashMap<>(4);
        m.put("parameterized", parameterized);
        return m;
    }

    /**
     * 协议preset。
     * @param platform platform
     * @param defaultBaseUrl 默认baseurl
     * @return 协议preset的结果
     */
    private record ProtocolPreset(String platform, String defaultBaseUrl) {
    }

    private static final List<String> ALL_TASKS = List.of(
            "chat", "realtime_conversation", "image_generation", "image_edit",
            "video_generation", "speech_synthesis", "speech_recognition",
            "embedding", "rerank"
    );

    private static final Map<String, ProtocolPreset> PROTOCOL_PRESETS; // 协议presets

    static {
        Map<String, ProtocolPreset> m = new LinkedHashMap<>(48);
        m.put("openai", new ProtocolPreset("openai", "https://api.openai.com/v1"));
        m.put("anthropic", new ProtocolPreset("anthropic", "https://api.anthropic.com/v1"));
        m.put("google", new ProtocolPreset("google", "https://generativelanguage.googleapis.com/v1beta"));
        m.put("gemini", new ProtocolPreset("gemini", "https://generativelanguage.googleapis.com/v1beta"));
        m.put("gemini-vertex-ai", new ProtocolPreset("gemini-vertex-ai", "https://aiplatform.googleapis.com/v1"));
        m.put("azure", new ProtocolPreset("azure", "https://api.openai.azure.com"));
        m.put("bedrock", new ProtocolPreset("bedrock", "https://bedrock-runtime.us-east-1.amazonaws.com"));
        m.put("siliconflow", new ProtocolPreset("siliconflow", "https://api.siliconflow.cn/v1"));
        m.put("deepseek", new ProtocolPreset("deepseek", "https://api.deepseek.com/v1"));
        m.put("qwen", new ProtocolPreset("qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1"));
        m.put("dashscope", new ProtocolPreset("dashscope", "https://dashscope.aliyuncs.com/compatible-mode/v1"));
        m.put("dashscope-coding", new ProtocolPreset("dashscope-coding", "https://dashscope.aliyuncs.com/compatible-mode/v1"));
        // 阿里云百炼国际站与编程 / Token 套餐端点（前端模板 id，OpenAI 兼容）；
        // 余额走登记表 aliyun-bss 预设（AK/SK 签名），品牌别名已覆盖这些 platform id
        m.put("alibaba", new ProtocolPreset("alibaba", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"));
        m.put("alibaba-coding-plan-cn", new ProtocolPreset("alibaba-coding-plan-cn", "https://coding.dashscope.aliyuncs.com/v1"));
        m.put("alibaba-coding-plan", new ProtocolPreset("alibaba-coding-plan", "https://coding-intl.dashscope.aliyuncs.com/v1"));
        m.put("alibaba-token-plan-cn", new ProtocolPreset("alibaba-token-plan-cn", "https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"));
        m.put("alibaba-token-plan", new ProtocolPreset("alibaba-token-plan", "https://token-plan.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1"));
        m.put("moonshot", new ProtocolPreset("moonshot", "https://api.moonshot.cn/v1"));
        m.put("moonshot-cn", new ProtocolPreset("moonshot-cn", "https://api.moonshot.cn/v1"));
        m.put("moonshot-global", new ProtocolPreset("moonshot-global", "https://api.moonshot.ai/v1"));
        m.put("grok", new ProtocolPreset("grok", "https://api.x.ai/v1"));
        m.put("xai", new ProtocolPreset("xai", "https://api.x.ai/v1"));
        m.put("ollama", new ProtocolPreset("ollama", "http://localhost:11434/v1"));
        m.put("nomifun-free-model", new ProtocolPreset("nomifun-free-model", "https://free.nomifun.com/v1"));
        // 中国平台的官方兼容接口地址
        m.put("zhipu", new ProtocolPreset("zhipu", "https://open.bigmodel.cn/api/paas/v4"));
        m.put("glm-coding-plan", new ProtocolPreset("glm-coding-plan", "https://open.bigmodel.cn/api/coding/paas/v4"));
        m.put("ark", new ProtocolPreset("ark", "https://ark.cn-beijing.volces.com/api/v3"));
        m.put("ark-coding-plan", new ProtocolPreset("ark-coding-plan", "https://ark.cn-beijing.volces.com/api/v3"));
        m.put("ark-agent-plan", new ProtocolPreset("ark-agent-plan", "https://ark.cn-beijing.volces.com/api/v3"));
        m.put("qianfan", new ProtocolPreset("qianfan", "https://qianfan.baidubce.com/v2"));
        m.put("qianfan-coding-plan", new ProtocolPreset("qianfan-coding-plan", "https://qianfan.baidubce.com/v2"));
        m.put("hunyuan", new ProtocolPreset("hunyuan", "https://api.hunyuan.cloud.tencent.com/v1"));
        m.put("hunyuan-global", new ProtocolPreset("hunyuan-global", "https://api.hunyuan.cloud.tencent.com/v1"));
        // 腾讯云混元平台名与编程 / Token 套餐、TokenHub 端点（前端模板 id，OpenAI 兼容）；
        // 余额走登记表 tencent-billing 预设（AK/SK 签名），品牌别名已覆盖这些 platform id
        m.put("tencent", new ProtocolPreset("tencent", "https://api.hunyuan.cloud.tencent.com/v1"));
        m.put("tencent-coding-plan", new ProtocolPreset("tencent-coding-plan", "https://api.lkeap.cloud.tencent.com/coding/v3"));
        m.put("tencent-token-plan", new ProtocolPreset("tencent-token-plan", "https://api.lkeap.cloud.tencent.com/plan/v3"));
        m.put("tencent-tokenhub", new ProtocolPreset("tencent-tokenhub", "https://tokenhub.tencentmaas.com/v1"));
        m.put("lingyi", new ProtocolPreset("lingyi", "https://api.lingyiwanwu.com/v1"));
        m.put("stepfun", new ProtocolPreset("stepfun", "https://api.stepfun.com/v1"));
        m.put("stepfun-plan", new ProtocolPreset("stepfun-plan", "https://api.stepfun.com/v1"));
        // 阶跃星辰国际站（前端模板 id stepfun-ai / stepfun-ai-step-plan，OpenAI 兼容），
        // 与国内站同形状，仅域名不同；余额走登记表 stepfun-ai 预设（美元计费）
        m.put("stepfun-ai", new ProtocolPreset("stepfun-ai", "https://api.stepfun.ai/v1"));
        m.put("stepfun-ai-step-plan", new ProtocolPreset("stepfun-ai-step-plan", "https://api.stepfun.ai/v1"));
        // 七牛云 AI 大模型推理（前端模板 id qiniu-ai，OpenAI 兼容）；余额走登记表 qiniu 预设（AK/SK 签名）
        m.put("qiniu-ai", new ProtocolPreset("qiniu-ai", "https://api.qnaigc.com/v1"));
        m.put("qiniu", new ProtocolPreset("qiniu", "https://api.qnaigc.com/v1"));
        m.put("minimax", new ProtocolPreset("minimax", "https://api.minimaxi.com/v1"));
        m.put("minimax-code", new ProtocolPreset("minimax-code", "https://api.minimaxi.com/v1"));
        m.put("minimax-coding-plan", new ProtocolPreset("minimax-coding-plan", "https://api.minimaxi.com/v1"));
        m.put("mimo", new ProtocolPreset("mimo", "https://api.mimo.xiaomi.com/v1"));
        m.put("mimo-token-plan-cn", new ProtocolPreset("mimo-token-plan-cn", "https://api.mimo.xiaomi.com/v1"));
        m.put("mimo-token-plan-sgp", new ProtocolPreset("mimo-token-plan-sgp", "https://api.mimo.xiaomi.com/v1"));
        m.put("mimo-token-plan-ams", new ProtocolPreset("mimo-token-plan-ams", "https://api.mimo.xiaomi.com/v1"));
        // Moss 开放平台：自有一组端点（/audio/speech、/audio/transcriptions、/responses、/images/generations）
        m.put("moss", new ProtocolPreset("moss", "https://api.mosi.cn/v1"));
        m.put("mosi", new ProtocolPreset("mosi", "https://api.mosi.cn/v1"));
        m.put("deepgram", new ProtocolPreset("deepgram", "https://api.deepgram.com/v1"));
        m.put("novita", new ProtocolPreset("novita", "https://api.novita.ai/openai/v1"));
        m.put("openrouter", new ProtocolPreset("openrouter", "https://openrouter.ai/api/v1"));
        m.put("poe", new ProtocolPreset("poe", "https://api.poe.com/v1"));
        m.put("ppio", new ProtocolPreset("ppio", "https://api.ppinfra.com/v3/openai"));
        m.put("modelscope", new ProtocolPreset("modelscope", "https://api-inference.modelscope.cn/v1"));
        m.put("infiniai", new ProtocolPreset("infiniai", "https://cloud.infini-ai.com/maas/v1"));
        m.put("ctyun", new ProtocolPreset("ctyun", "https://modelverse.cn/v1"));
        m.put("new-api", new ProtocolPreset("new-api", "http://localhost:3000/v1"));
        // 在线神经网络推理模型的基础地址保存模型文件路径，没有远程端点
        m.put("onnx", new ProtocolPreset("onnx", ""));
        m.put("custom", new ProtocolPreset("custom", "https://api.openai.com/v1"));
        PROTOCOL_PRESETS = Map.copyOf(m);
    }

    // ==================== 模型拉取 ====================

    /**
     * 上游鉴权参数。
     *
     * <p>把「认证方案 + 头名称 + 值前缀」三件套打包传递，避免在多层调用里逐个加参数。
     * 头名称与前缀来自供应商 {@code extra.auth_header} / {@code extra.auth_prefix}，
     * 用于 Dots Studio 这类只认 {@code api-key} 头、不认 {@code Authorization: Bearer} 的服务商。</p>
     *
     * @param scheme 认证方案（api_key / bearer / oauth / none）
     * @param header 认证头名称覆盖，可为 null
     * @param prefix 认证值前缀覆盖，可为 null
     */
    private record UpstreamAuth(String scheme, String header, String prefix) {

        /**
         * 从供应商实体构建。
         *
         * @param authScheme 认证方案
         * @param extra      扩展配置（读取 auth_header / auth_prefix）
         * @return 鉴权参数
         */
        static UpstreamAuth of(String authScheme, Map<String, Object> extra) {
            return new UpstreamAuth(authScheme, extraStr(extra, "auth_header"), extraStr(extra, "auth_prefix"));
        }

        /**
         * 从请求体构建（匿名预拉取 / 测试连接场景）。
         *
         * @param body 请求体
         * @return 鉴权参数
         */
        static UpstreamAuth ofBody(Map<String, Object> body) {
            if (body == null) {
                return new UpstreamAuth(null, null, null);
            }
            return new UpstreamAuth(extraStr(body, "auth_scheme"),
                    extraStr(body, "auth_header"), extraStr(body, "auth_prefix"));
        }

        /**
         * 读取扩展配置中的字符串值。
         *
         * <p>空白串按「未配置」处理（返回 null）：前端测试连接会把空串原样发上来，
         * 落库时空串键又会被删除；若把空串直接交给 {@code AuthHeaders}，会被当成
         * 「显式不要前缀」，标准 {@code Authorization} 头不再补 {@code Bearer }，
         * 有效 Key 会被上游判成 401（表现为「测试连接报 Key 无效，实际调用却正常」）。</p>
         *
         * @param extra 扩展配置
         * @param key   键名
         * @return 字符串值；缺失或空白返回 null
         */
        private static String extraStr(Map<String, Object> extra, String key) {
            if (extra == null) {
                return null;
            }
            Object value = extra.get(key);
            if (value == null) {
                return null;
            }
            String text = value.toString();
            return text.isBlank() ? null : text;
        }
    }

    /**
     * 真实调用上游 {@code GET {base_url}/模型} 拉取模型目录。
     *
     * <p>OpenAI 兼容协议：{@code Authorization: Bearer <key>}，响应 {@code data[].id}；
     * Google Gemini 协议：{@code ?key=<key>} 查询参数，响应 {@code models[].name}（形如 模型/gemini-xx）。
     * 拉取失败时抛出与前端契约对应的错误码（UNAUTHORIZED / FORBIDDEN / RATE_限制 / 超时 / BAD_GATEWAY）。</p>
     *
     * @param platform    平台标识（zhipu/openai/google/...）
     * @param baseUrl     供应商 API 基础地址
     * @param credentials 凭证（兼容 api_Key 字符串与 api_Key 数组两种形态）
     * @param auth        鉴权参数（方案 + 头名 + 前缀）
     * @param proxy       HTTP 代理地址（可为空）
     * @param tryFix      是否在 404 时尝试补全 /v1 后重试
     * @return 上游模型列表响应（models + 可选 fixed_基础_url）
     */
    private Map<String, Object> fetchModelsFromUpstream(String platform, String baseUrl,
                                                        Map<String, Object> credentials,
                                                        UpstreamAuth auth, String proxy, boolean tryFix,
                                                        Map<String, Object> extra, String providerId) {
        // 平台标识统一小写后再判断协议族（前端可能传大写形式）
        String platformLower = platform == null ? "" : platform.toLowerCase();
        // 本地推理（ONNX）没有远端模型目录可拉，改为分发给本地模型门面（ModelRegistry），
        // 由门面返回已注册且已就绪的模型 id 清单 —— 返回形态与其它供应商的「从提供商获取」一致。
        // 注意：ONNX 的基础地址与凭证与其余供应商同字段同语义，地址指向本机，
        // 不再把凭证当作模型文件路径使用。
        if (platformLower.contains("onnx")) {
            return fetchLocalModelsFromFacade();
        }
        // 免费渠道是纯路由聚合：模型清单 = 运行时同一份合并路由表（静态 extra.routes +
        // 动态渠道管理里的 OAuth 渠道模型 + Magpie 网关），**绝不真实请求占位 baseUrl 的 /models**。
        // 之前只读 extra.routes，导致「渠道管理里明明有模型，FreeChannel 拉取却看不到」。
        if (ProviderRouteTargets.isFreeChannel(platform)) {
            return fetchFreeChannelRoutes(providerId, extra);
        }
        // 免登录内置渠道（DuckDuckGo 等）：没有上游地址，模型目录由 ChatClient SPI 自己给出，
        // 不能按云端供应商去请求 {base_url}/models，也不能报「base_url 未配置」。
        Map<String, Object> builtinModels = fetchKeylessBuiltinModels(platform, baseUrl, auth);
        if (builtinModels != null) {
            return builtinModels;
        }
        // 基础地址缺失直接判参数错误
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "base_url 未配置，无法拉取模型列表");
        }
        String apiKey = extractApiKey(credentials);
        boolean googleProtocol = platformLower.contains("gemini") || platformLower.contains("google");
        boolean ollamaProtocol = platformLower.contains("ollama");

        // 首次按原始地址拉取
        List<Map<String, Object>> models = requestUpstreamModels(baseUrl, apiKey, googleProtocol, proxy, auth);
        String fixedBaseUrl = null;
        // 404 且允许修正时，尝试补全 /v1 再拉一次（用户常见漏填 /v1 的场景）
        if (models == null && tryFix && !googleProtocol) {
            String fixed = baseUrl.endsWith("/") ? baseUrl + "v1" : baseUrl + "/v1";
            models = requestUpstreamModels(fixed, apiKey, false, proxy, auth);
            if (models != null) {
                fixedBaseUrl = fixed;
            }
        }
        // 兼容回退使用原生标签接口，响应中包含模型名称列表
        if (models == null && ollamaProtocol) {
            String tagsUrl = baseUrl.replaceAll("/v1/?$", "");
            models = requestOllamaTags(tagsUrl, apiKey, proxy);
        }
        // 仍然失败则按无法连接处理
        if (models == null) {
            throw new ApiException(ApiErrorCode.BAD_GATEWAY, "无法从供应商拉取模型列表，请检查 API 地址和 Key");
        }
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("models", models);
        if (fixedBaseUrl != null) {
            out.put("fixed_base_url", fixedBaseUrl);
        }
        return out;
    }

    /**
     * 免登录内置渠道的模型清单（不发任何 HTTP 请求）。
     *
     * <p>DuckDuckGo 这类渠道由 {@code ChatClient} 扩展自身提供模型表（{@code models()}），
     * 供应商行没有 base_url、也没有 Key。此前「从提供商获取模型列表」固定按
     * {@code {base_url}/models} 拉取，这类渠道一律卡在「base_url 未配置」。</p>
     *
     * <p>触发条件（同时满足，避免误伤正常云端供应商）：</p>
     * <ul>
     *   <li>base_url 为空；</li>
     *   <li>auth_scheme=none，或 platform 是渠道管理物化的 {@code chat-<渠道>} 行。</li>
     * </ul>
     *
     * @param platform 平台标识（{@code duckduckgo} / {@code CHAT-DUCKDUCKGO} 等）
     * @param baseUrl  基础地址（为空才走本分支）
     * @param auth     鉴权参数
     * @return 与其它供应商同形态的 {@code {models:[...]}}；不适用时返回 {@code null}
     */
    private Map<String, Object> fetchKeylessBuiltinModels(String platform, String baseUrl, UpstreamAuth auth) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return null;
        }
        String platformLower = platform == null ? "" : platform.trim().toLowerCase(Locale.ROOT);
        boolean keyless = platformLower.startsWith(CHAT_CHANNEL_PREFIX)
                || (auth != null && "none".equalsIgnoreCase(String.valueOf(auth.scheme()).trim()));
        if (!keyless || platformLower.isEmpty()) {
            return null;
        }
        String clientName = platformLower.startsWith(CHAT_CHANNEL_PREFIX)
                ? platformLower.substring(CHAT_CHANNEL_PREFIX.length())
                : platformLower;
        ChatClient client = ChatClient.create(ChatClientSetting.builder().provider(clientName).build());
        if (client == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM,
                    "该供应商没有上游地址，且未找到内置对话客户端: " + clientName);
        }
        List<ModelDefinition> definitions;
        try {
            definitions = client.models();
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.BAD_GATEWAY,
                    "内置对话客户端读取模型清单失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        List<Map<String, Object>> models = new ArrayList<>(8);
        if (definitions != null) {
            for (ModelDefinition definition : definitions) {
                if (definition == null || definition.getId() == null || definition.getId().isBlank()) {
                    continue;
                }
                String modelId = definition.getId().trim();
                Map<String, Object> row = new LinkedHashMap<>(6);
                row.put("id", modelId);
                String displayName = definition.getName();
                row.put("name", displayName == null || displayName.isBlank() ? modelId : displayName);
                row.put("tasks", inferTasks(modelId));
                row.put("traits", List.of("streaming"));
                String description = definition.getDescription();
                if (description != null && !description.isBlank()) {
                    row.put("description", description);
                }
                models.add(row);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("models", models);
        if (models.isEmpty()) {
            out.put("hint", "该渠道未提供模型清单");
        }
        return out;
    }

    /**
     * 免费渠道（free-channel）拉模型：返回路由表 {@code extra.routes} 的模型/前缀键清单。
     *
     * <p>免费渠道是纯路由聚合，没有真实上游目录可拉；模型清单即路由键（如
     * {@code glm-4.7}、{@code kilo}），用户选中哪个键，请求就路由到对应的
     * baseUrl —— 对应 {@code FreeChannelChatClient.route(model)} 的命中键。</p>
     *
     * <p>{@code default} 键是兜底路由，不是可选择的模型，返回时剔除。
     * 未配置 {@code routes} 时返回空清单 + 提示，不抛错。</p>
     *
     * <p>每行补充路由表节点提供的上游信息（provider / baseUrl / authScheme）；
     * 语义交给前端的 {@code describeFetchRow} 渲染成行内小字，让用户看到这一行
     * 实际走哪个上游通道，而不是只看到裸路由键。</p>
     *
     * @param extra 供应商扩展配置（{@code extra.routes}）
     * @return { models: [...], hint?: 字符串 }；形态与其它供应商拉取一致
     */
    private Map<String, Object> fetchFreeChannelRoutes(String providerId, Map<String, Object> extra) {
        Map<String, RouteTarget> routes = ProviderRouteTargets.fromExtra(extra);
        // 与运行时同源：FreeChannelChatClient 用的就是 ModelClientResolver#mergedRoutes
        // （静态 extra.routes + 从渠道管理动态收集的 OAuth 渠道路由 + Magpie）。
        // 拉取列表若只读 extra.routes，就会出现「渠道管理里 82 个模型、这里只看到 2 个」。
        if (providerId != null && !providerId.isBlank()) {
            try {
                SysAccountChannel channelProvider = requireProvider(providerId);
                routes = modelClientResolver.mergedRoutes(
                        channelProvider, com.chua.starter.ai.support.chat.ModelClientResolver.ownerScopeOf(channelProvider));
            } catch (Exception e) {
                // 必须 WARN：退回 extra.routes 只会给出静态那几条路由（实测 2 条），
                // 用户在界面上看到的就是「渠道里明明有 80+ 个模型，这里只拉到 2 个」。
                // 之前打 DEBUG，日志被 INFO 级别吞掉，排查时只能看到结果不对劲，
                // 看不到「合并路由失败」这个真正原因。
                log.warn("[Provider] 合并渠道路由失败，已退回 extra.routes（模型清单会明显变少）: {}",
                        e.toString());
            }
        }
        List<Map<String, Object>> models = new ArrayList<>(8);
        if (routes != null) {
            // 渠道前缀键（平台名，如 OPENCODE / KILO）也是路由键，但它只是「该渠道任意模型」
            // 的前缀兜底，不是可选模型；混在模型列表里会让人以为有个叫 OPENCODE 的模型。
            java.util.Set<String> channelPlatformKeys = new java.util.HashSet<>();
            try {
                for (SysAccountChannel p : accountChannelService.listByAuthScheme("oauth")) {
                    if (p.getPlatform() != null && !p.getPlatform().isBlank()) {
                        channelPlatformKeys.add(p.getPlatform().trim().toLowerCase(java.util.Locale.ROOT));
                    }
                }
            } catch (Exception e) {
                log.debug("[Provider] 读取渠道平台键失败（不影响模型列表）: {}", e.getMessage());
            }
            // 静态 extra.routes 里显式写下的键一律保留：它们可能正好与渠道平台同名
            // （例如 kilo 渠道 + extra.routes 里的 kilo 路由），那是管理员明确要暴露的入口。
            // 注意必须按**原样大小写**判断：平台键是 KILO，静态键是 kilo，两者不同名。
            Map<String, RouteTarget> staticRoutes = ProviderRouteTargets.fromExtra(extra);
            for (Map.Entry<String, RouteTarget> entry : routes.entrySet()) {
                String key = entry.getKey();
                // default 是兜底路由，不作为可选模型暴露
                if ("default".equalsIgnoreCase(key)) {
                    continue;
                }
                // 渠道前缀键同样跳过，但静态路由显式声明的不动（见 staticRoutes 注释）
                String lowerKey = key.trim().toLowerCase(java.util.Locale.ROOT);
                if (channelPlatformKeys.contains(lowerKey) && (staticRoutes == null || !staticRoutes.containsKey(key))) {
                    continue;
                }
                RouteTarget target = entry.getValue();
                Map<String, Object> m = new LinkedHashMap<>(7);
                // 路由表节点信息：这一行实际路由到的上游提供商（后端状态色、
                // 前端通道徽标都靠它，不是裸路由键）
                m.put("id", key);
                m.put("name", key);
                m.put("provider", target.getProvider());
                m.put("baseUrl", target.getBaseUrl());
                m.put("authScheme", target.getAuthScheme());
                m.put("tasks", inferTasks(key));
                m.put("traits", List.of("streaming"));
                m.put("description", describeRouteTarget(key, target));
                models.add(m);
            }
        }
        // Magpie 网关的模型目录由网关自己维护、不落库：拉取时并入当前可用模型。
        // 与路由合并口径一致——已由 extra.routes 显式登记的 id 不重复添加。
        java.util.Set<String> seenIds = new HashSet<>();
        for (Map<String, Object> m : models) {
            seenIds.add(String.valueOf(m.get("id")));
        }
        try {
            for (Map<String, Object> row : magpieRouteSource.modelRows("_free")) {
                String id = String.valueOf(row.get("id"));
                if (!seenIds.add(id)) {
                    continue;
                }
                Object upstream = row.get("provider");
                Map<String, Object> m = new LinkedHashMap<>(7);
                m.put("id", id);
                m.put("name", row.get("name"));
                m.put("provider", MagpieRouteSource.CHANNEL);
                m.put("baseUrl", row.get("baseUrl"));
                m.put("authScheme", "api_key");
                m.put("tasks", inferTasks(id));
                m.put("traits", List.of("streaming"));
                m.put("description", "Magpie 网关"
                        + (upstream == null ? "" : " · 上游 " + upstream)
                        + (row.get("baseUrl") == null ? "" : " · " + row.get("baseUrl")));
                models.add(m);
            }
        } catch (Exception e) {
            log.debug("[Provider] 并入 Magpie 网关模型失败: {}", e.getMessage());
        }
        // 免登录生成类渠道（文生图 / 文生视频）：duckduckgo / pollinations 等渠道的产出模型
        // 不在聊天路由表里（FreeChannelChatClient 只跑对话），但用户要的是「免费渠道下能看到
        // 所有渠道的全部 chat 与 image 模型」。这里按渠道实现各自声明的 tasks 补进行目录，
        // 出图模型才不会被当成对话模型或直接缺席。
        appendKeylessMediaModels(models, seenIds);
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("models", models);
        out.put("route_keys", true);
        if (models.isEmpty()) {
            out.put("hint", "暂无可用模型：请先在渠道管理启用模型，或检查上游渠道连接后重试");
        }
        return out;
    }

    /**
     * 把免登录生成类渠道（文生图 / 文生视频）的内置模型并入免费渠道目录。
     *
     * <p>这些渠道不出现在聊天路由表里：免费渠道客户端只负责对话路由，出图 / 出视频
     * 由账号模块的 {@code ImageChannelProvider} / {@code VideoChannelProvider} 直接执行。
     * 但「从供应商获取模型」是模型目录，用户要在免费渠道下看到渠道管理里的<b>全部</b>
     * chat 与 image 模型，因此这里按渠道声明的 {@code tasks} 补挂目录行：
     * 例如 {@code image-pollinations} 的 {@code flux} / {@code turbo} 会以
     * {@code image_generation} 能力出现，而不是缺席或被误标成对话模型。</p>
     *
     * <p>去重沿用调用方传入的 {@code seenIds}：聊天路由或 Magpie 已经给出的同名模型
     * 不再重复追加（同一模型有两份目录行会让批量录入产生重复行）。</p>
     *
     * @param models  已收集的模型行（原地追加）
     * @param seenIds 已出现的模型标识（原地更新）
     */
    private void appendKeylessMediaModels(List<Map<String, Object>> models, Set<String> seenIds) {
        Map<String, KeylessMediaChannel> channels;
        try {
            channels = ServiceProvider.of(KeylessMediaChannel.class).list();
        } catch (Exception e) {
            log.debug("[Provider] 免登录生成类渠道未接入: {}", e.getMessage());
            return;
        }
        if (channels == null || channels.isEmpty()) {
            return;
        }
        for (KeylessMediaChannel channel : channels.values()) {
            if (channel == null || StringUtils.isBlank(channel.channel())) {
                continue;
            }
            List<Map<String, Object>> rows;
            try {
                rows = channel.listModels();
            } catch (Exception e) {
                // 单个渠道取模型失败不影响其它渠道的目录
                log.debug("[Provider] 生成类渠道 {} 模型清单读取失败: {}", channel.channel(), e.getMessage());
                continue;
            }
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            String source = StringUtils.isBlank(channel.extension()) ? channel.channel() : channel.extension();
            List<String> tasks = channel.tasks();
            for (Map<String, Object> row : rows) {
                Object rawId = row == null ? null : row.get("id");
                if (rawId == null || StringUtils.isBlank(String.valueOf(rawId))) {
                    continue;
                }
                String id = String.valueOf(rawId).trim();
                if (!seenIds.add(id)) {
                    continue;
                }
                // modelName 必须落到模型标识（id）：批量录入按 modelName 建行，
                // 用展示名（如「Flux（默认）」）入库会得到一条永远路由不到的模型。
                // 展示名改放描述，列表里仍能看到人可读的名称。
                Object rawName = row.get("name");
                String modelLabel = rawName == null ? "" : String.valueOf(rawName).trim();
                Map<String, Object> m = new LinkedHashMap<>(8);
                m.put("id", id);
                m.put("name", id);
                m.put("provider", source);
                m.put("baseUrl", "");
                m.put("authScheme", "none");
                m.put("tasks", tasks == null || tasks.isEmpty() ? List.of("chat") : tasks);
                // 生成类模型不是流式对话能力，只有上游行显式给出 traits 才带
                m.put("traits", row.get("traits") instanceof List<?> traits ? traits : List.of());
                m.put("description", modelLabel.isEmpty() || modelLabel.equals(id)
                        ? channel.displayName() : channel.displayName() + " · " + modelLabel);
                models.add(m);
            }
        }
    }

    /**
     * 免费渠道行内描述：这条路由通往的上游 provider 与地址。
     *
     * <p>文本直接落到模型行的 {@code description} 字段，前端 {@code describeFetchRow}
     * 取到它后在模型名下方渲染一行小字；没有描述信息时返回空串，前端不渲染。</p>
     *
     * @param key    路由键（线程缺失 provider 时的兜底标识）
     * @param target 路由目标
     * @return 描述文本（provider · baseUrl，鉴权方案存在时一并给出）
     */
    private String describeRouteTarget(String key, RouteTarget target) {
        if (target == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(target.getProvider() != null ? target.getProvider() : key);
        String baseUrl = target.getBaseUrl();
        if (baseUrl != null && !baseUrl.isBlank()) {
            sb.append(" · ").append(baseUrl);
        }
        String scheme = target.getAuthScheme();
        if (scheme != null && !scheme.isBlank() && !"none".equalsIgnoreCase(scheme)) {
            sb.append(" · ").append(scheme);
        }
        return sb.toString();
    }

    /**
     * 请求 Ollama 原生 {@code GET {base}/api/标签} 模型列表。
     *
     * @param baseUrl 基础地址（不含 /v1）
     * @param apiKey  API Key（Ollama 通常无需鉴权，可为 空）
     * @param proxy   HTTP 代理地址（可为空）
     * @return 模型条目列表；请求失败返回 空
     */
    private List<Map<String, Object>> requestOllamaTags(String baseUrl, String apiKey, String proxy) {
        String url = baseUrl.endsWith("/") ? baseUrl + "api/tags" : baseUrl + "/api/tags";
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(UPSTREAM_TIMEOUT)
                .GET();
        try {
            HttpResponse<String> response = httpClient(proxy).send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            List<Map<String, Object>> models = new ArrayList<>(8); // 模型数量不可预估
            com.fasterxml.jackson.databind.JsonNode root = readJson(response.body());
            com.fasterxml.jackson.databind.JsonNode data = root == null ? null : root.get("models");
            if (data != null && data.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode item : data) {
                    String name = item.path("name").asText(null);
                    // 跳过无名条目
                    if (name == null || name.isBlank()) {
                        continue;
                    }
                    Map<String, Object> m = new LinkedHashMap<>(4);
                    m.put("id", name);
                    m.put("name", name);
                    m.put("tasks", inferTasks(name));
                    m.put("traits", List.of("streaming"));
                    models.add(m);
                }
            }
            return models;
        } catch (Exception e) {
            // 网络层异常按不可达处理
            return null;
        }
    }

    /**
     * 请求上游模型列表接口。
     *
     * @param baseUrl        基础地址
     * @param apiKey         API Key（可能为 空，如 Ollama 本地实例）
     * @param googleProtocol 是否使用 Gemini 协议
     * @param proxy          HTTP 代理地址（可为空）
     * @param auth           鉴权参数（方案 + 头名 + 前缀）
     * @return 模型条目列表；请求失败返回 空（由调用方决定是否重试/报错）
     */
    private List<Map<String, Object>> requestUpstreamModels(String baseUrl, String apiKey, boolean googleProtocol,
                                                           String proxy, UpstreamAuth auth) {
        // 地址为空时必须在拼 URL 之前拦住：原实现的 baseUrl.endsWith(...) 会直接 NPE，
        // 异常 message 为 null，最终在界面上显示成「无法连接供应商模型接口: null」。
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "base_url 未配置，无法拉取模型列表");
        }
        // 每次请求前清空，避免线程池复用时带上上一次的响应头
        lastUpstreamHeaders.remove();
        baseUrl = baseUrl.trim();
        // 凭证里的 Key 可能带首尾空白（复制粘贴 / 多行输入），先归一化再发请求
        if (apiKey != null) {
            apiKey = apiKey.trim();
        }
        // 拼接模型列表地址，兼容接口使用标准路径，生成式接口附带密钥参数
        StringBuilder url = new StringBuilder(baseUrl.endsWith("/") ? baseUrl + "models" : baseUrl + "/models");
        if (googleProtocol && apiKey != null && !apiKey.isBlank()) {
            url.append(url.indexOf("?") >= 0 ? "&" : "?").append("key=").append(apiKey);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(UPSTREAM_TIMEOUT)
                .GET();
    // 按供应商配置的认证方案发送认证头。
        // 历史实现恒发 Authorization: Bearer，导致 Dots Studio 这类只认 api-key 头的
        // 服务商拉取模型必然 401；auth_scheme / extra.auth_header 此前被完全忽略。
        if (!googleProtocol) {
            Map.Entry<String, String> resolved = auth == null ? null
                    : AuthHeaders.resolve(auth.scheme(), auth.header(), auth.prefix(), apiKey);
            if (resolved != null) {
                builder.header(resolved.getKey(), resolved.getValue());
            }
        }
        try {
            HttpResponse<String> response = httpClient(proxy).send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            // 按前端契约映射错误码
            if (status == 401) {
                throw new ApiException(ApiErrorCode.UNAUTHORIZED, "API Key 无效或已过期");
            }
            if (status == 403) {
                throw new ApiException(ApiErrorCode.FORBIDDEN, "该 API Key 没有读取模型列表的权限");
            }
            if (status == 429) {
                throw new ApiException(ApiErrorCode.RATE_LIMITED, "供应商请求过于频繁，请稍后重试");
            }
            if (status == 404) {
                // 返回空结果，由调用方决定是否尝试修复后重试
                return null;
            }
            if (status < 200 || status >= 300) {
                throw new ApiException(ApiErrorCode.BAD_GATEWAY, "供应商模型接口返回 " + status);
            }
            // 限制探测复用同一次请求的响应头（x-ratelimit-* / ratelimit-*），不额外发一次。
            // 只在拿到 2xx 时留存：401 / 429 的响应头来自失败的候选（鉴权不对 / 已被限流），
            // 拿它当这家供应商的限额只会写进一条假口径。
            lastUpstreamHeaders.set(new LinkedHashMap<>(response.headers().map()));
            return googleProtocol ? parseGoogleModels(response.body()) : parseOpenAiModels(response.body());
        } catch (ApiException e) {
            // 业务异常直接向上抛出
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new ApiException(ApiErrorCode.TIMEOUT, "供应商模型接口请求超时");
        } catch (Exception e) {
            // 网络层异常统一按网关错误处理。e.getMessage() 可能为 null（如 NPE），
            // 不能拼进字符串，否则界面出现「无法连接供应商模型接口: null」这种无信息量的报错。
            // 按异常类型给出可操作的文案，别再笼统显示「（网络异常）」。
            String reason = friendlyNetworkReason(e);
            throw new ApiException(ApiErrorCode.BAD_GATEWAY,
                    "无法连接供应商模型接口" + (reason == null ? "" : "：" + reason));
        }
    }

    /**
     * 把上游请求的网络异常翻译成可操作的提示。
     *
     * <p>测试延迟报「（网络异常）」= 原始 message 为 null/空，对排查没有帮助。
     * 这里按根因分类：DNS / 拒连 / TLS / 超时 / IO 中断，各自给一句话。</p>
     */
    private String friendlyNetworkReason(Exception e) {
        Throwable root = e;
        for (int i = 0; i < 8 && root.getCause() != null && root.getCause() != root; i++) {
            root = root.getCause();
        }
        if (root instanceof java.net.UnknownHostException) {
            return "域名解析失败（DNS），请检查 baseUrl 的域名";
        }
        if (root instanceof java.net.ConnectException) {
            return "连接被拒绝（端口未开或地址错误）";
        }
        if (root instanceof javax.net.ssl.SSLException || root instanceof java.security.cert.CertificateException) {
            return "TLS/SSL 握手失败，请确认地址是否应为 https";
        }
        if (root instanceof java.net.SocketTimeoutException || root instanceof java.net.http.HttpTimeoutException) {
            return "请求超时，请检查网络或加大超时时间";
        }
        if (root instanceof java.io.IOException) {
            return "网络不可达（连接或读取中断）";
        }
        String msg = e.getMessage();
        return (msg == null || msg.isBlank()) ? null : msg;
    }

    /**
     * 解析 打开AI 兼容的模型列表响应（数据[].标识）。
     *
     * <p>除了 {@code id/name}，上游条目里的上下文窗口、输出上限、价格、能力开关、
     * 输入输出模态等元数据也一并解析（见 {@link #upstreamModelRow}）——
     * 这正是「模型元数据必须来自真实 {@code /models} 响应」的入口，
     * 不能用模型名猜。</p>
     *
     * @param body 响应体 JSON
     * @return 模型条目列表
     */
    private List<Map<String, Object>> parseOpenAiModels(String body) {
        List<Map<String, Object>> models = new ArrayList<>(8); // 模型数量不可预估
        com.fasterxml.jackson.databind.JsonNode root = readJson(body);
        // 响应格式为数据数组，每个元素包含模型标识等字段。
        // 少数网关直接回裸数组或把列表放在 models 键下，这里一并兼容，
        // 避免「Key 有效但列表为空」被误判。
        com.fasterxml.jackson.databind.JsonNode data = root == null ? null
                : (root.isArray() ? root : root.get("data"));
        if (data == null && root != null) {
            data = root.get("models");
        }
        if (data != null && data.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode item : data) {
                String id = item.path("id").asText(null);
            // 跳过缺少模型标识的条目
                if (id == null || id.isBlank()) {
                    continue;
                }
                models.add(upstreamModelRow(id, item.path("name").asText(id), item));
            }
        }
        return models;
    }

    /**
     * 解析 Google Gemini 的模型列表响应（模型[].名称，形如 模型/gemini-xx）。
     *
     * <p>Gemini 的条目用 {@code inputTokenLimit} / {@code outputTokenLimit} 表达窗口与
     * 输出上限（已由统一解析口径识别），这里只补 Gemini 特有的
     * {@code temperature} 能力与 {@code description / version}。</p>
     *
     * @param body 响应体 JSON
     * @return 模型条目列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseGoogleModels(String body) {
        List<Map<String, Object>> models = new ArrayList<>(8); // 模型数量不可预估
        com.fasterxml.jackson.databind.JsonNode root = readJson(body);
        com.fasterxml.jackson.databind.JsonNode data = root == null ? null : root.get("models");
        if (data != null && data.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode item : data) {
                String name = item.path("name").asText(null);
            // 去掉模型路径前缀作为模型标识
                String id = name != null && name.startsWith("models/") ? name.substring("models/".length()) : name;
                if (id == null || id.isBlank()) {
                    continue;
                }
                String displayName = item.path("displayName").asText(id);
                Map<String, Object> m = upstreamModelRow(id, displayName, item);
                // description 已由 upstreamModelRow 统一读取（description / summary），
                // 这里不再单独补一次，避免两处口径分叉。
                Map<String, Object> extra = new LinkedHashMap<>(4);
                // temperature 字段存在即表示支持采样温度（默认 1.0），不是「取值」
                if (item.has("temperature") && item.get("temperature").isNumber()) {
                    extra.put("temperature", true);
                }
                Long version = longOrNull(item, "version");
                if (version != null && version > 0) {
                    extra.put("created", version);
                }
                if (!extra.isEmpty()) {
                    Map<String, Object> merged = m.get("metrics") instanceof Map<?, ?> raw
                            ? new LinkedHashMap<>((Map<String, Object>) raw)
                            : new LinkedHashMap<>(4);
                    merged.putAll(extra);
                    m.put("metrics", merged);
                    applyUpstreamMetrics(m, merged);
                }
                models.add(m);
            }
        }
        return models;
    }

    /**
     * 上游模型条目 → 「从提供商获取」列表行（含可落库的指标元数据）。
     *
     * <p>上游 {@code /models} 的元数据字段各家差异很大（OpenRouter 的
     * {@code pricing} / {@code limit}、intern-ai schema 2.4 的模态数组、
     * Pollinations 的 {@code supported_parameters} 等），统一交给
     * {@link HttpChatClientIndicator#parseModelMetrics} 归一成标准指标键，
     * 再推导出模型弹框与批量录入认识的实体列键。</p>
     *
     * <p><b>只放上游真实给过的值</b>：解析不到的键不写入，前端按「未知」处理。
     * 后续指标库快照只在字段仍为空时兜底，不会覆盖上游真实元数据。</p>
     *
     * @param id   模型标识
     * @param name 模型展示名
     * @param item 上游条目 JSON
     * @return 列表行
     */
    private Map<String, Object> upstreamModelRow(String id, String name,
                                                 com.fasterxml.jackson.databind.JsonNode item) {
        Map<String, Object> m = new LinkedHashMap<>(12);
        m.put("id", id);
        m.put("name", name);
        m.put("tasks", inferTasks(id));
        m.put("traits", List.of("streaming"));
        // 模型描述：OpenAI 兼容目录（OpenRouter / intern-ai 等）用 description，
        // 少数网关用 summary。此前只有 Google 分支单独补了 description，其余协议
        // 一律拿不到 —— 「从提供商获取」列表与保存后的模型副标题都因此为空。
        String description = textOrNull(item, "description");
        if (description == null) {
            description = textOrNull(item, "summary");
        }
        if (description != null) {
            m.put("description", description);
        }
        Map<String, Object> metrics = HttpChatClientIndicator.parseModelMetrics(item);
        if (!metrics.isEmpty()) {
            m.put("metrics", metrics);
            applyUpstreamMetrics(m, metrics);
        }
        return m;
    }

    /**
     * 标准指标键 → 模型实体列键（字段名与「保存提供者模型」接口完全一致）。
     *
     * <p>映射口径与 {@link SysAiModelMetricsService#enrichRowFromMetrics} 相同，
     * 提前做一次是为了让「从提供商获取」列表与批量录入拿到同一份元数据，
     * 避免列表里看得到、落库后却丢了。</p>
     *
     * @param row     列表行（就地写入）
     * @param metrics 标准指标
     */
    private void applyUpstreamMetrics(Map<String, Object> row, Map<String, Object> metrics) {
        // 免费渠道 / 路由聚合行的「模型」是路由键，没有上游指标；调用方会传 null，
        // 这里直接跳过，否则 NPE 会把整个 remote_models 打成 500（实测）。
        if (metrics == null || metrics.isEmpty()) {
            return;
        }
        putIfPresent(row, "context_window", metrics.get("context_window_tokens"));
        putIfPresent(row, "max_tokens", metrics.get("max_output_tokens"));
        putIfPresent(row, "family", metrics.get("model_family"));
        putIfPresent(row, "knowledge", metrics.get("knowledge_cutoff"));
        putIfPresent(row, "release_date", metrics.get("release_date"));
        putIfPresent(row, "last_updated", metrics.get("last_updated"));
        putIfPresent(row, "open_weights", metrics.get("open_weights"));
        putIfPresent(row, "attachment", metrics.get("attachment"));
        putIfPresent(row, "reasoning", metrics.get("reasoning"));
        putIfPresent(row, "tool_call", metrics.get("function_calling"));
        putIfPresent(row, "temperature_supported", metrics.get("temperature"));
        // 价格：上游 /models 的 pricing 已由 parseModelMetrics 归一成「每百万 token 美元」。
        // 提到行顶层是为了让「从提供商获取」列表能直接标注价格/免费，
        // 落库时仍走 metrics 同一份数据（见 mergeUpstreamMetadata）。
        putIfPresent(row, "price_input_per_musd", metrics.get("price_input_per_musd"));
        putIfPresent(row, "price_output_per_musd", metrics.get("price_output_per_musd"));
        putIfPresent(row, "price_cache_hit_per_musd", metrics.get("price_cache_hit_per_musd"));
        putIfPresent(row, "price_cache_write_per_musd", metrics.get("price_cache_write_per_musd"));
        putIfPresent(row, "cost_per_task", metrics.get("cost_per_task"));
        Map<String, Object> modalities = modalityMapOf(metrics);
        if (!modalities.isEmpty()) {
            row.put("modalities", modalities);
        }
    }

    /**
     * 由标准指标组装 {@code {input:[...], output:[...]}} 模态结构。
     *
     * <p>优先使用上游显式声明的 {@code input_modalities} / {@code output_modalities}；
     * 输入侧缺失时按 image / audio / video / file 能力标记补齐。元素一定是字符串，
     * 与前端 {@code modalities} 契约一致。</p>
     *
     * @param metrics 标准指标
     * @return 模态 Map；两侧都无信息时返回空 Map
     */
    private static Map<String, Object> modalityMapOf(Map<String, Object> metrics) {
        Map<String, Object> result = new LinkedHashMap<>(4);
        List<String> inputs = stringValues(metrics.get("input_modalities"));
        if (inputs.isEmpty()) {
            inputs = new ArrayList<>(4);
            inputs.add("text");
            for (String[] pair : new String[][]{{"image_input", "image"}, {"audio_input", "audio"},
                    {"video_input", "video"}, {"file_input", "file"}}) {
                if (Boolean.TRUE.equals(metrics.get(pair[0]))) {
                    inputs.add(pair[1]);
                }
            }
        }
        if (!inputs.isEmpty()) {
            result.put("input", inputs);
        }
        List<String> outputs = stringValues(metrics.get("output_modalities"));
        if (!outputs.isEmpty()) {
            result.put("output", outputs);
        }
        return result;
    }

    /**
     * 宽松转字符串列表（字符串元素 / {@code {name}} 对象都接受）。
     *
     * @param value 原始值
     * @return 字符串列表；无法识别时返回空列表
     */
    private static List<String> stringValues(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof String text) {
                if (!text.isBlank()) {
                    values.add(text);
                }
            } else if (item instanceof Map<?, ?> map) {
                Object name = map.get("name") != null ? map.get("name") : map.get("type");
                if (name != null) {
                    values.add(String.valueOf(name));
                }
            }
        }
        return values;
    }

    /**
     * 值非空时写入行。
     *
     * @param row   目标行
     * @param key   键
     * @param value 值
     */
    private static void putIfPresent(Map<String, Object> row, String key, Object value) {
        if (value != null) {
            row.put(key, value);
        }
    }

    /**
     * 读取文本字段（缺失 / 非文本 / 空白返回 null）。
     *
     * <p>{@code asText(null)} 在节点存在但为 JSON null 时会回字符串 {@code "null"}，
     * 直接用会把字面量 {@code "null"} 写进模型描述；这里统一走一次判空与去空白。</p>
     *
     * @param node 节点
     * @param key  字段名
     * @return 文本；无有效值时 null
     */
    private static String textOrNull(com.fasterxml.jackson.databind.JsonNode node, String key) {
        if (node == null) {
            return null;
        }
        com.fasterxml.jackson.databind.JsonNode value = node.get(key);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }
        String text = value.asText();
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    /**
     * 读取 Long 字段（缺失或非数值返回 null）。
     *
     * @param node 节点
     * @param key  字段名
     * @return 数值
     */
    private static Long longOrNull(com.fasterxml.jackson.databind.JsonNode node, String key) {
        if (node == null) {
            return null;
        }
        com.fasterxml.jackson.databind.JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asLong();
        }
        try {
            return Long.parseLong(value.asText().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 解析 JSON 字符串，失败返回 空。
     *
     * @param body JSON 字符串
     * @return JsonNode 或 空
     */
    private com.fasterxml.jackson.databind.JsonNode readJson(String body) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 依据模型 标识 关键字推断任务类型。
     *
     * @param modelId 模型 标识
     * @return 任务类型列表（默认 对话）
     */
    private List<String> inferTasks(String modelId) {
        String lower = modelId.toLowerCase();
        // 按关键字匹配任务类型
        if (lower.contains("embedding") || lower.contains("embed") || lower.contains("-bge-")) {
            return List.of("embedding");
        }
        if (lower.contains("rerank")) {
            return List.of("rerank");
        }
        if (lower.contains("tts") || lower.contains("speech-synthesis") || lower.contains("speech_synthesis")) {
            return List.of("speech_synthesis");
        }
        if (lower.contains("whisper") || lower.contains("asr") || lower.contains("speech-recognition")
                || lower.contains("transcribe") || lower.contains("transcription")) {
            return List.of("speech_recognition");
        }
        // 图像/视频：除 dall-e/cogview 这类专名，还要认裸 token。
        // 「厂商-类型-版本」命名（agnes-image-2.1-flash / agnes-video-2.5）原先只匹配
        // image-gen/video-gen，全部回落成 chat —— 人工建模时是手选类型，批量录入没
        // 这个机会，模型选择器就会把图像/视频模型分进「文本」组。
        // 代价：视频理解类对话模型（video-llava 之类）会被误判成生成类，罕见且只影响分组。
        if (lower.contains("image") || lower.contains("dall-e") || lower.contains("cogview")
                || lower.contains("flux") || lower.contains("sdxl")) {
            return List.of("image_generation");
        }
        if (lower.contains("video")) {
            return List.of("video_generation");
        }
        if (lower.contains("realtime")) {
            return List.of("realtime_conversation");
        }
        return List.of("chat");
    }

    /**
     * 从凭证 映射 中提取 API Key，兼容 api_Key（字符串）与 api_Key（数组）两种形态。
     *
     * @param credentials 凭证 映射（可能为 空）
     * @return API Key 或 空
     */
    private String extractApiKey(Map<String, Object> credentials) {
        // 没有凭证时直接返回空结果
        if (credentials == null || credentials.isEmpty()) {
            return null;
        }
        Object single = credentials.get("api_key");
        if (single instanceof String && !((String) single).isBlank()) {
            return (String) single;
        }
        Object multi = credentials.get("api_keys");
        // 多密钥数组取第一个值作为拉取凭证
        if (multi instanceof List<?> list && !list.isEmpty()) {
            Object first = list.getFirst();
            if (first instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    // ==================== 工具方法 ====================

    /**
     * 获取 提供者 实体，不存在则抛 NOT_FOUND。
     * @param providerId 提供者标识
     * @return require提供者的结果
     */
    private SysAccountChannel requireProvider(String providerId) {
        return accountChannelService.require(providerId);
    }

    /**
     * ONNX 供应商或其模型行变更后，刷新 provider 级推理设备覆盖。
     *
     * @param provider 供应商行
     */
    private void refreshOnnxDevice(SysAccountChannel provider) {
        if (isOnnxRow(provider)) {
            onnxProviderMaterializer.syncProviderDevice(provider.getProviderId());
        }
    }

    /**
     * 获取 提供者 实体，并对非管理员隐藏 ONNX 行（不存在语义，不泄露该行存在）。
     *
     * <p>供所有「读」路径使用：详情、连接列表、连接详情。写路径本身已是 admin-only，
     * 无需再判断；此处只负责让非管理员读不到 ONNX 那一行。</p>
     *
     * @param providerId 提供者 标识
     * @return 提供者实体
     */
    private SysAccountChannel requireVisibleProvider(String providerId) {
        SysAccountChannel e = requireProvider(providerId);
        if (isOnnxRow(e) && !com.chua.starter.ai.support.util.AiUserContext.isAdmin()) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "provider 不存在: " + providerId);
        }
        return e;
    }


    /**
     * str。
     * @param map 映射
     * @param key Key
     * @return str的结果
     */
    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }

    /**
     * requirestr。
     * @param map 映射
     * @param key Key
     * @return requireStr的结果
     */
    private String requireStr(Map<String, Object> map, String key) {
        String s = str(map, key);
        if (s == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "字段 " + key + " 不能为空");
        }
        return s;
    }

    /**
     * bool。
     * @param map 映射
     * @param key Key
     * @param def def
     * @return bool的结果
     */
    private boolean bool(Map<String, Object> map, String key, boolean def) {
        Object v = map.get(key);
        if (v == null) {
            return def;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }

    /**
     * boolOrNull：可空的布尔（前端未传时保持 null，不覆盖指标回填值）。
     * @param map 映射
     * @param key Key
     * @return Boolean 或 null
     */
    private Boolean boolOrNull(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(v);
        if (s.isBlank() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        return Boolean.parseBoolean(s);
    }

    /**
     * toModalitiesMap：归一化 inputs。 input / output 模态为字符串列表的 Map。
     *
     * <p>接受 {@code {input:[...], output:[...]}} 结构或 null；字符串值视为
     * 逗号分隔列表。返回值可变，便于后续写入实体。</p>
     *
     * @param value 原始值
     * @return 归一化的 Map；无有效内容时返回空 Map
     */
    private Map<String, Object> toModalitiesMap(Object value) {
        Map<String, Object> result = new java.util.LinkedHashMap<>(2);
        List<String> reduced = new java.util.ArrayList<>(6);
        if (value instanceof Map<?, ?> raw) {
            Object in = raw.get("input");
            reduced.clear();
            collectModalityItems(in, reduced);
            if (!reduced.isEmpty()) {
                result.put("input", reduced);
            }
            Object out = raw.get("output");
            reduced = new java.util.ArrayList<>(6);
            collectModalityItems(out, reduced);
            if (!reduced.isEmpty()) {
                result.put("output", reduced);
            }
        }
        return result;
    }

    /**
     * collectModalityItems：把模态字段值规整成字符串列表（字符串按逗号拆分）。
     * @param value 原始值
     * @param target 收集目标
     */
    private void collectModalityItems(Object value, List<String> target) {
        if (value instanceof java.util.Collection<?> coll) {
            for (Object item : coll) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    target.add(String.valueOf(item).trim());
                }
            }
        } else if (value != null && !String.valueOf(value).isBlank()) {
            for (String part : String.valueOf(value).split(",")) {
                if (!part.isBlank()) {
                    target.add(part.trim());
                }
            }
        }
    }

    /**
     * intv。
     * @param map 映射
     * @param key Key
     * @param def def
     * @return intv的结果
     */
    private int intv(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        if (v == null) {
            return def;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    /**
     * 取可空整数值（缺省 / 空串 / 非数字一律返回 null）。
     *
     * <p>用于「未指定即回退指标表」的窗口类字段：null 与 0 语义不同，
     * 不能用 {@link #intv(Map, String, int)} 的默认值表达。</p>
     *
     * @param map 映射
     * @param key Key
     * @return 解析结果，无法解析时为 null
     */
    private Integer intOrNull(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 取可空小数值（缺省 / 空串 / 非数字一律返回 null）。
     *
     * @param map 映射
     * @param key Key
     * @return 解析结果，无法解析时为 null
     */
    private Double dblOrNull(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * 列表的str。
     * @param map 映射
     * @param key Key
     * @return 列表的str的结果
     */
    private List<String> listOfStr(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (!(v instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>(4); // 元素数量不可预估
        for (Object o : list) {
            if (o != null) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    /**
     * 列表的映射。
     * @param map 映射
     * @param key Key
     * @return 列表的映射的结果
     */
    private List<Map<String, Object>> listOfMap(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (!(v instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(4); // 元素数量不可预估
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    /**
     * uncheckedcast。
     * @param obj obj
     * @return uncheckedCast的结果
     */
    private <T> T uncheckedCast(Object obj) {
        return (T) obj;
    }

    /**
     * 按代理地址获取（缓存）的 HTTP 客户端。
     *
     * <p>未配置代理时返回共享 {@link #upstreamHttpClient}；配置了代理（如
     * {@code http://127.0.0.1:7890}、{@code socks5://127.0.0.1:1080}）时按代理地址
     * 复用缓存的带 {@link ProxySelector} 客户端，避免每个请求重复创建。</p>
     *
     * @param proxy HTTP 代理地址，可为空
     * @return HttpClient
     */
    private HttpClient httpClient(String proxy) {
        if (proxy == null || proxy.isBlank()) {
            return upstreamHttpClient;
        }
        return proxyHttpClients.computeIfAbsent(proxy.trim(), addr -> HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .proxy(ProxySelector.of(new java.net.InetSocketAddress(
                        proxyHost(addr), proxyPort(addr))))
                .build());
    }

    /**
     * 解析代理地址的主机名（支持 {@code http://host:port} 与 {@code socks5://host:port}）。
     * @param proxy 代理
     * @return 代理主机的结果
     */
    private static String proxyHost(String proxy) {
        String trimmed = proxy.trim();
        int scheme = trimmed.indexOf("://");
        String rest = scheme >= 0 ? trimmed.substring(scheme + 3) : trimmed;
        int colon = rest.lastIndexOf(':');
        return colon > 0 ? rest.substring(0, colon) : rest;
    }

    /**
     * 解析代理地址的端口（默认 8080）。
     * @param proxy 代理
     * @return 代理端口的结果
     */
    private static int proxyPort(String proxy) {
        String trimmed = proxy.trim();
        int scheme = trimmed.indexOf("://");
        String rest = scheme >= 0 ? trimmed.substring(scheme + 3) : trimmed;
        int colon = rest.lastIndexOf(':');
        if (colon <= 0 || colon == rest.length() - 1) {
            return 8080;
        }
        try {
            return Integer.parseInt(rest.substring(colon + 1));
        } catch (NumberFormatException ex) {
            return 8080;
        }
    }

    /**
     * 所有已注册的 对话客户端 列表
     */
    private final Map<String, HttpClient> proxyHttpClients = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * canonical Dir
     * @param raw 字符串
     * @return 返回值
    */
    private static String canonicalDir(String raw) {
        return com.chua.starter.ai.support.manage.service.OnnxExternalDirectoryService.canonicalDir(raw);
    }

        /**
     * 通过本地模型门面拉取 ONNX 模型清单。
     *
     * <p>ONNX 提供商的"凭证"是本地模型路径而非远程 Key，没有可拉取的远程目录，
     * 因此不能走 HTTP 拉取；改为按平台类型分发给本地模型门面
     * （{@code AiModelCatalogService}，底层为 {@code ModelRegistry} SPI 注册表），
     * 与其它提供商的「从提供商获取」体验保持一致。</p>
     *
     * <p>只返回用户可见范围内的模型，避免把整个注册表（上百个未下载模型）全列出来：</p>
     * <ul>
     *   <li>外部目录模型：仅返回「绑定外部目录」中登记过的路径</li>
     *   <li>内置模型：仅在模型文件已就绪时返回</li>
     * </ul>
     *
     * @return 与上游一致的 models 结构（{@code name}/{@code modelName}）并附带 onnx 专有字段
     */
    private Map<String, Object> fetchLocalModelsFromFacade() {
        List<Map<String, Object>> models = listVisibleLocalModels();
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("models", models);
        return out;
    }

    /**
     * 列出用户可见的本地模型（ONNX 提供商的模型目录）。
     *
     * <p>数据来源统一走本地模型门面 {@code AiModelCatalogService}（底层 {@code ModelRegistry} SPI），
     * 不再自己遍历注册表。门面的 {@code available} 字段由只读探测得出，不会触发模型下载。</p>
     *
     * <p>过滤规则（避免把注册表里上百个未下载模型全部列出）：</p>
     * <ul>
     *   <li>外部目录模型：仅返回「绑定外部目录」中登记过的路径</li>
     *   <li>内置模型：仅在模型文件已就绪（{@code available=true}）时返回</li>
     * </ul>
     *
     * @return 模型条目列表（含 id/name/modelName/capability/external/available）
     */
    private List<Map<String, Object>> listVisibleLocalModels() {
        List<Map<String, Object>> details;
        try {
            details = aiModelCatalogService.listLocalModelDetails();
        } catch (Exception e) {
            log.warn("[ONNX] 本地模型门面返回失败: {}", e.getMessage());
            return List.of();
        }
        if (details == null) {
            return List.of();
        }

        // 已绑定的外部目录：目录 → 任务（按绑定时登记的模板模型归类）
        Set<String> boundDirs = new HashSet<>();
        Map<String, String> externalTaskByDir = new java.util.HashMap<>(8);
        for (Map<String, String> en : onnxExternalDirectoryService.readEntries()) {
            String d = canonicalDir(en.get("dir"));
            if (d != null && !d.isBlank()) {
                boundDirs.add(d);
                String tpl = en.get("template");
                externalTaskByDir.put(d, classifyOnnxTask(tpl == null ? d : tpl));
            }
        }

        List<Map<String, Object>> models = new ArrayList<>();
        // 外部目录模型以「用户通过 ReFileSystem 选中的目录清单」为准：
        // 外部目录在 ModelRegistry 里是独立结构，不会出现在 getAll()，
        // 只靠目录门面过滤会一个都取不到（ONNX 刷新模型返回空）。
        for (Map<String, String> en : onnxExternalDirectoryService.readEntries()) {
            String d = canonicalDir(en.get("dir"));
            if (d == null || d.isBlank()) {
                continue;
            }
            String tpl = en.get("template");
            // id/name/modelName 用注册表模型标识（template），与其它提供商的模型名同构；
            // 目录路径单独放 path，不混进模型名
            String name = tpl == null || tpl.isBlank() ? dirNameOf(d) : tpl.trim();
            String task = classifyOnnxTask(name);
            Map<String, Object> item = new LinkedHashMap<>(9);
            item.put("id", name);
            item.put("name", name);
            item.put("modelName", name);
            item.put("path", d);
            item.put("capability", null);
            item.put("external", true);
            item.put("available", true);
            item.put("task", task);
            item.put("description", "外部目录 · " + d + " · " + onnxTaskLabel(task));
            models.add(item);
        }
        // 再补上本地已就绪的内置模型（available=true），避免把上百个未下载候选全列出来
        for (Map<String, Object> detail : details) {
            Object modelId = detail.get("modelId");
            if (modelId == null || String.valueOf(modelId).isBlank()) {
                continue;
            }
            String id = String.valueOf(modelId);
            if (models.stream().anyMatch(m -> id.equals(m.get("id")))) {
                continue;
            }
            // 外部目录在注册表里是以「绝对路径」为 key 注册的独立条目，会被模型门面
            // 当作内置模型一并返回；这些模型上面已按模板模型标识登记过，若不去重，
            // 同一个模型会以目录路径的形态重复出现（「从提供商获取」列表里混进路径）。
            if (boundDirs.contains(canonicalDir(id)) || isAbsolutePathLike(id)) {
                continue;
            }
            if (!Boolean.TRUE.equals(detail.get("available"))) {
                continue;
            }
            String task = classifyOnnxTask(id);
            Map<String, Object> item = new LinkedHashMap<>(12);
            item.put("id", id);
            item.put("name", id);
            item.put("modelName", id);
            item.put("path", null);
            item.put("capability", detail.get("capability"));
            item.put("external", false);
            item.put("available", true);
            item.put("task", task);
            // 批量录入（import-all）读的是 tasks/subtype：不带上这两个字段，
            // ONNX 模型入库后会没有 capabilities，前端只能按名字兜底成「文本」。
            item.put("tasks", List.of(task));
            String subType = OnnxModelTaxonomy.subtype(id, task);
            if (!subType.isBlank()) {
                item.put("subtype", subType);
            }
            item.put("description", "ONNX 本地模型 · " + onnxTaskLabel(task));
            models.add(item);
        }
        return models;
    }

    /**
     * id Contains
     * @param id 字符串
     * @param tokens 参数
     * @return 返回值
    */
    private static boolean idContains(String id, String... tokens) {
        for (String t : tokens) {
            if (id.contains(t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断模型标识是否形态上是一个绝对文件系统路径。
     *
     * <p>正常的模型标识是「模板模型名」（如 {@code bge-large-zh-embedding}），
     * 不应以路径出现；注册表里的外部目录条目则以绝对路径为 key，需要据此识别并过滤。</p>
     *
     * @param id 模型标识候选值
     * @return true 表示形如 {@code /xxx}（Unix）或 {@code X:/xxx} / {@code X:\xxx}（Windows）
     */
    /**
     * 判断某个 ONNX 模型名是否由「绑定外部目录」登记而来。
     *
     * <p>外部目录绑定时会把「模板模型名」写进条目（{@code template} 字段），
     * 所以拿模型名去比对 template 即可 —— 目录路径本身不是模型名，不参与比对。</p>
     *
     * <p>为什么需要：设置页模型选择器在 onnx 分支按 {@code external === true} 过滤，
     * 而 {@code /api/providers} 原先不返回该键，导致所有 ONNX 模型都选不到。</p>
     *
     * @param modelName 模型名（如 {@code image-colorize}）
     * @return true 表示由外部目录登记
     */
    private boolean isExternalBoundModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        try {
            for (Map<String, String> en : onnxExternalDirectoryService.readEntries()) {
                String tpl = en.get("template");
                if (tpl != null && !tpl.isBlank() && tpl.trim().equals(modelName.trim())) {
                    return true;
                }
            }
        } catch (Exception e) {
            // 外部目录读取失败不能让整个 providers 列表挂掉，按「内置模型」处理
            log.debug("[ONNX] 读取外部目录失败，按内置处理: {}", e.getMessage());
        }
        return false;
    }

    /**
     * is Absolute Path Like
     * @param id 字符串
     * @return 返回值
    */
    private static boolean isAbsolutePathLike(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String v = id.trim();
        if (v.startsWith("/") || v.startsWith("\\\\")) {
            return true;
        }
        return v.length() >= 3
                && Character.isLetter(v.charAt(0))
                && v.charAt(1) == ':'
                && (v.charAt(2) == '/' || v.charAt(2) == '\\');
    }

    /**
     * 判断是否为本地推理供应商（ONNX）。
     *
     * <p>ONNX 与其它供应商同构，供应商行就落在 {@code sys_account_channel} 表里；
     * 但「从提供商获取」这一步不同：其它供应商是向远端地址发 HTTP 拉取模型目录，
     * ONNX 没有远端目录，只能由本地模型门面（{@code ModelRegistry}）列出已注册的模型。
     * 因此模型拉取分支要绕开上游 HTTP，直接走门面 —— 返回的仍然是模型 id，与其它供应商一致。</p>
     *
     * @param providerId 提供商标识
     * @return true=本地推理供应商
     */
    private static boolean isVirtualLocalProvider(String providerId) {
        return isOnnxProvider(providerId);
    }

    /**
     * 按模型名约定把 ONNX 注册表模型归类到唯一规范 capability.task。 顺序敏感：布局 → OCR → 人脸 → 语音 → 嵌入 → 重排序 → 生成类 → 其余归图片识别。
     * @param modelId 字符串
     * @return 返回值
    */
    private static String classifyOnnxTask(String modelId) {
        // 统一走 OnnxModelTaxonomy：修复了旧实现里 "age" 命中 "image" 把图像编码器
        // 误判成人脸的问题，并与物化器 / 前端使用同一套词表。
        return OnnxModelTaxonomy.task(modelId);
    }

    /**
     * onnx Traits For Task
     * @param task 字符串
     * @return 返回值
    */
    private static List<String> onnxTraitsForTask(String task) {
        switch (task) {
            case "embedding": return List.of("input:text", "output:vector");
            case "speech_recognition": return List.of("input:audio", "output:text");
            case "speech_synthesis": return List.of("input:text", "output:audio");
            case "image_generation": return List.of("input:text", "output:image");
            case "video_generation": return List.of("input:text", "output:video");
            case "face_recognition":
            case "ocr":
            case "layout":
            case "image_cv":
            default: return List.of("input:image", "output:text");
        }
    }

    /**
     * 取目录名（未登记模板时作为模型名的回退）。
     *
     * @param dir 模型目录
     * @return 目录名
     */
    private static String dirNameOf(String dir) {
        int slash = dir.lastIndexOf('/');
        return slash < 0 || slash == dir.length() - 1 ? dir : dir.substring(slash + 1);
    }

    /**
     * onnx Task Label
     * @param task 字符串
     * @return 返回值
    */
    private static String onnxTaskLabel(String task) {
        switch (task) {
            case "layout": return "布局识别";
            case "ocr": return "OCR 识别";
            case "face_recognition": return "人脸识别";
            case "speech_recognition": return "语音识别";
            case "speech_synthesis": return "语音合成";
            case "embedding": return "特征嵌入";
            case "rerank": return "重排序";
            case "image_generation": return "文生图";
            case "video_generation": return "文生视频";
            case "image_cv":
            default: return "图片识别";
        }
    }

    // ==================== ONNX 外部服务器目录 ====================

    /**
     * 应用就绪后恢复已持久化的 ONNX 外部目录（重启后自动重新注册）。
     *
     * <p>排序必须最晚：ONNX 供应商物化与模型参数回填在启动阶段也会调用
     * {@code ModelRegistry#discoverAll}，而该调用会把模型根重置为库默认值。
     * 早于它们执行会让刚恢复的模型根被覆盖，目录选择器随即指向不存在的默认目录。</p>
     */
    @org.springframework.core.annotation.Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void restoreOnnxExternalDirectories() {
        onnxExternalDirectoryService.restoreAll();
    }

    /**
     * 浏览服务器目录，供 ONNX 模型目录选择器使用。
     *
     * <p>本接口与后台文件系统接口 {@code GET /api/fs/browse} 同契约、同解析规则，
     * 前端 ReFileSystem 目录选择器直接消费：选择器里能打开的目录，才可能被
     * {@code POST /onnx/external} 登记进来。</p>
     *
     * @param path      目录绝对路径，空值时以当前用户主目录为起点
     * @param showFiles 是否返回文件
     * @return 目录条目与定位信息
     */
    @Permission("admin")
    @GetMapping("/onnx/external/browse")
    public ApiResult<Map<String, Object>> browseOnnxExternal(
            @RequestParam(defaultValue = "") String path,
            @RequestParam(defaultValue = "false") boolean showFiles) {
        try {
            return ApiResult.ok(onnxExternalDirectoryService.browse(path, showFiles));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, ex.getMessage());
        } catch (IOException ex) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "浏览目录失败: " + ex.getMessage());
        }
    }

    /**
     * 添加 ONNX 外部服务器目录模型。
     *
     * @param body {dir: 服务器模型目录绝对路径, template: 模板模型标识（可空）}
     * @return 新增模型信息
     */
    @Permission("admin")
    @PostMapping("/onnx/external")
    public ApiResult<Map<String, Object>> addOnnxExternal(@RequestBody Map<String, Object> body) {
        String dir = body == null ? null : String.valueOf(body.getOrDefault("dir", "")).trim();
        String template = body == null ? null : String.valueOf(body.getOrDefault("template", "")).trim();
        try {
            Map<String, Object> added = onnxExternalDirectoryService.add(dir, template);
            // 绑定目录后立即镜像进模型表，页面才能当场看到新模型；
            // 用户还没添加 ONNX 供应商时 syncModels 自己跳过（不凭空写模型行）。
            onnxProviderMaterializer.syncModels();
            return ApiResult.ok(added);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, ex.getMessage());
        }
    }

    /**
     * 删除 ONNX 外部服务器目录模型。
     *
     * @param dirParam 服务器模型目录绝对路径（query 参数，DELETE 推荐）
     * @param body     兼容 body 形式 {dir: 服务器模型目录绝对路径}
     * @return 是否移除
     */
    @Permission("admin")
    @DeleteMapping("/onnx/external")
    public ApiResult<Map<String, Object>> removeOnnxExternal(
            @RequestParam(value = "dir", required = false) String dirParam,
            @RequestBody(required = false) Map<String, Object> body) {
        String dir = (dirParam != null && !dirParam.isBlank()) ? dirParam.trim()
                : (body == null ? null : String.valueOf(body.getOrDefault("dir", "")).trim());
        Map<String, Object> result = new LinkedHashMap<>(2);
        result.put("removed", onnxExternalDirectoryService.remove(dir));
        return ApiResult.ok(result);
    }

    /**
     * 构建 Agent 虚拟 提供者（含已注册的 Agent 模型列表）。
     * @param modelNames 模型名称
     * @return 构建Agent提供者的结果
     */
    private Map<String, Object> buildAgentProvider(List<String> modelNames) {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> models = new ArrayList<>(8); // 模型数量不可预估
        for (String modelName : modelNames) {
            Map<String, Object> model = new LinkedHashMap<>(10);
            model.put("provider_id", "agent");
            model.put("model", modelName);
            model.put("enabled", true);
            model.put("sort_order", 0);
            model.put("description", "Agent: " + modelName.replace("agent-", ""));
            Map<String, Object> capability = new LinkedHashMap<>(8);
            capability.put("task", "chat");
            capability.put("traits", List.of());
            capability.put("protocol", "agent");
            capability.put("connection_role", "user");
            capability.put("allow_cross_origin_credentials", false);
            capability.put("provider_params", null);
            capability.put("created_at", now);
            capability.put("updated_at", now);
            model.put("capabilities", List.of(capability));
            model.put("created_at", now);
            model.put("updated_at", now);
            models.add(model);
        }
        Map<String, Object> provider = new LinkedHashMap<>(10);
        provider.put("provider_id", "agent");
        provider.put("platform", "agent");
        provider.put("name", "Agent");
        provider.put("base_url", "");
        provider.put("auth_scheme", "none");
        provider.put("has_credentials", false);
        provider.put("models", models);
        provider.put("enabled", true);
        provider.put("sort_order", -1);
        provider.put("created_at", now);
        provider.put("updated_at", now);
        return provider;
    }

    /**
     * 供应商「同步」：限制 + 计费标准 + 模型描述一次完成。
     *
     * <p>与原「同步价格」的差别：</p>
     * <ul>
     *   <li><b>限制</b>：走 {@link ProviderLimitCollector} 的三段链路（手动配置 → 官方文档预设
     *       → 上游响应头），结果落到 {@code sys_account_channel.extra.provider_limits}；</li>
     *   <li><b>计费标准</b>：官方指标源（{@link ChatClientIndicator}）与上游
     *       {@code /models} 自带 pricing 两条都写进 {@code sys_ai_model_metrics}；</li>
     *   <li><b>模型描述</b>：把上游目录里的 description / summary 补到已配置模型行，
     *       默认只补空值（{@code overwrite_descriptions=true} 才覆盖），缺失的再由
     *       公开目录（models.dev）补位；</li>
     *   <li><b>公开目录</b>：models.dev 的说明 / 上下文 / 多模态 / 能力位 / 计费标准，
     *       价格写入 {@code sys_ai_model_metrics}，其余只补空值。</li>
     * </ul>
     *
     * <p>上游只请求一次 {@code GET /models}：描述与计费共用这份目录，限制复用同一次
     * 请求的响应头。拉取失败不会让整个同步失败 —— 限制与官方指标源仍可独立完成，
     * 失败原因在 {@code fetch_error} / 各分节里如实返回。</p>
     *
     * @param providerId 提供商标识
     * @param body       可选 { try_fix, overwrite_descriptions }
     * @return 各分节同步结果
     */
    @Permission("admin")
    @PostMapping("/providers/{provider_id}/sync")
    @Transactional
    public ApiResult<Map<String, Object>> syncProvider(@PathVariable("provider_id") String providerId,
                                                       @RequestBody(required = false) Map<String, Object> body) {
        SysAccountChannel entity = requireProvider(providerId);
        boolean tryFix = body != null && Boolean.TRUE.equals(body.get("try_fix"));
        boolean overwriteDescriptions = body != null
                && Boolean.TRUE.equals(body.get("overwrite_descriptions"));
        long now = System.currentTimeMillis();
        Map<String, Object> out = new LinkedHashMap<>(10);
        out.put("provider_id", providerId);
        out.put("synced_at", now);

        List<Map<String, Object>> upstreamModels = new ArrayList<>(0);
        String fetchError = null;
        try {
            Map<String, Object> upstream = fetchModelsFromUpstream(entity.getPlatform(), entity.getBaseUrl(),
                    entity.getCredentials(), UpstreamAuth.of(entity.getAuthScheme(), entity.getExtra()),
                    entity.getProxy(), tryFix, entity.getExtra(), entity.getProviderId());
            if (upstream.get("models") instanceof List<?> raw) {
                for (Object item : raw) {
                    if (item instanceof Map<?, ?>) {
                        upstreamModels.add(uncheckedCast(item));
                    }
                }
            }
        } catch (Exception e) {
            fetchError = e.getMessage() == null ? e.toString() : e.getMessage();
        }
        // 取走即清：ThreadLocal 不能留到下一次请求
        Map<String, List<String>> headers = lastUpstreamHeaders.get();
        lastUpstreamHeaders.remove();
        out.put("models", upstreamModels.size());

        Map<String, Object> limits = syncProviderLimits(entity, headers);
        out.put("limits", limits);
        out.put("descriptions", syncProviderDescriptions(entity, upstreamModels,
                overwriteDescriptions, fetchError, now));
        out.put("pricing", syncProviderPricing(entity, upstreamModels, fetchError, limits));
        out.put("catalog", providerCatalogSyncService.sync(entity));
        // 余额分节：逐 Key 真查并按 Key 落库（sys_ai_provider_credit，先删后增）
        out.put("credit", syncProviderCredit(entity));
        if (fetchError != null) {
            out.put("fetch_error", fetchError);
        }
        return ApiResult.ok(out);
    }

    /**
     * 限制同步：探测并持久化到 {@code extra.provider_limits}。
     *
     * @param entity  提供商行
     * @param headers 本次上游 {@code /models} 响应头（可为空）
     * @return 限制报告（declared / observed / attempted / supported / pricing_url）
     */
    private Map<String, Object> syncProviderLimits(SysAccountChannel entity, Map<String, List<String>> headers) {
        ProviderLimitContext context = ProviderLimitContext.builder()
                .platform(entity.getPlatform())
                .baseUrl(entity.getBaseUrl())
                .apiKey(extractApiKey(entity.getCredentials()))
                .authScheme(entity.getAuthScheme())
                .authHeader(extraString(entity.getExtra(), "auth_header"))
                .authPrefix(extraString(entity.getExtra(), "auth_prefix"))
                .extraHeaders(extractExtraHeaders(entity.getExtra()))
                .proxy(entity.getProxy())
                .extra(entity.getExtra())
                .responseHeaders(headers)
                .build();
        Map<String, Object> report = ProviderLimitCollector.collect(context);
        report.put("supported", report.get("declared") != null || report.get("observed") != null);
        report.put("pricing_url", ChatClientIndicators.officialPricingUrl(entity.getPlatform()));

        Map<String, Object> extra = entity.getExtra() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(entity.getExtra());
        extra.put("provider_limits", report);
        // extra 列由账号模块统一写入（带 JacksonTypeHandler）
        accountChannelService.updateExtra(entity.getProviderId(), extra);
        log.info("[ProviderSync] provider={} 限制来源={} declared={} observed={}",
                entity.getProviderId(), report.get("source"),
                report.get("declared") != null, report.get("observed") != null);
        return report;
    }

    /**
     * 模型描述同步：上游目录 → 已配置模型行的 {@code description}。
     *
     * <p>默认只补空值（不覆盖管理员手写的描述）；{@code overwrite=true} 时才用上游
     * 文本覆盖。上游条目按模型 id 优先、展示名兜底匹配，避免把 OpenRouter 这类
     * 「name 是展示名」的目录匹配错。</p>
     *
     * @param entity      提供商行
     * @param upstream    上游模型目录
     * @param overwrite   是否覆盖已有描述
     * @param fetchError  上游拉取失败原因（空表示成功）
     * @param now         本次同步时间
     * @return 统计与失败原因
     */
    private Map<String, Object> syncProviderDescriptions(SysAccountChannel entity,
                                                         List<Map<String, Object>> upstream,
                                                         boolean overwrite, String fetchError, long now) {
        Map<String, Object> out = new LinkedHashMap<>(8);
        int fetched = upstream == null ? 0 : upstream.size();
        out.put("supported", fetchError == null);
        out.put("fetched", fetched);
        out.put("updated", 0);
        out.put("unchanged", 0);
        out.put("missing", 0);
        if (fetchError != null) {
            out.put("message", "拉取上游模型目录失败：" + fetchError);
            return out;
        }
        if (fetched == 0) {
            out.put("message", "上游未返回模型目录");
            return out;
        }
        Map<String, SysAccountChannelModel> existing = new HashMap<>(16);
        for (SysAccountChannelModel row : accountChannelModelService.listAll(entity.getProviderId())) {
            if (row.getModel() != null) {
                existing.put(row.getModel(), row);
            }
        }
        int updated = 0;
        int unchanged = 0;
        int missing = 0;
        for (Map<String, Object> m : upstream) {
            String id = m.get("id") instanceof String s ? s : null;
            String name = m.get("name") instanceof String s ? s : null;
            SysAccountChannelModel row = id == null ? null : existing.get(id);
            if (row == null && name != null) {
                row = existing.get(name);
            }
            if (row == null) {
                missing++;
                continue;
            }
            Object rawDescription = m.get("description");
            String description = rawDescription instanceof String s && !s.isBlank() ? s.trim() : null;
            if (description == null) {
                unchanged++;
                continue;
            }
            boolean hasCurrent = row.getDescription() != null && !row.getDescription().isBlank();
            if (hasCurrent && !overwrite) {
                unchanged++;
                continue;
            }
            if (description.equals(row.getDescription())) {
                unchanged++;
                continue;
            }
            row.setDescription(description);
            row.setUpdatedAt(now);
            accountChannelModelService.updateByKey(row);
            updated++;
        }
        out.put("updated", updated);
        out.put("unchanged", unchanged);
        out.put("missing", missing);
        if (updated == 0) {
            out.put("message", "上游未提供新的模型描述");
        }
        return out;
    }

    /**
     * 计费标准同步：官方指标源 + 上游 {@code /models} pricing。
     *
     * @param entity   提供商行
     * @param upstream 上游模型目录
     * @param fetchError 上游拉取失败原因（空表示成功）
     * @param limits   限制报告（取 {@code billing_basis}）
     * @return 计费同步分节
     */
    private Map<String, Object> syncProviderPricing(SysAccountChannel entity,
                                                    List<Map<String, Object>> upstream,
                                                    String fetchError,
                                                    Map<String, Object> limits) {
        Map<String, Object> out = new LinkedHashMap<>(12);
        ChatClientIndicator indicator = resolveIndicator(entity);
        if (indicator != null) {
            SysAiModelMetricsService.SyncResult result =
                    sysAiModelMetricsService.syncFromIndicator(indicator, entity.getProviderId());
            out.put("supported", true);
            out.put("source", indicator.source());
            out.put("source_url", indicator.sourceUrl());
            out.put("models", indicator.modelMetrics() == null ? 0 : indicator.modelMetrics().size());
            out.put("updated", result.getUpdated());
            out.put("inserted", result.getInserted());
            out.put("provider_models", result.getProviderModels());
        } else {
            out.put("supported", false);
            out.put("source", null);
            out.put("source_url", null);
            out.put("updated", 0);
            out.put("inserted", 0);
            out.put("provider_models", 0);
            out.put("pricing_url", ChatClientIndicators.officialPricingUrl(entity.getPlatform()));
        }
        Object billingBasis = billingBasisOf(limits);
        if (billingBasis != null) {
            out.put("billing_basis", billingBasis);
        }
        if (fetchError == null) {
            // 先删后增：本来源（upstream:/models）的指标行整体替换，陈旧价格不残留；
            // 其它来源与手动锁定的行不动
            SysAiModelMetricsService.UpstreamPricingResult upstreamPricing =
                    sysAiModelMetricsService.replaceUpstreamPricing(entity.getProviderId(), upstream,
                            entity.getBaseUrl(), "upstream:/models");
            Map<String, Object> upstreamRow = new LinkedHashMap<>(4);
            upstreamRow.put("updated", upstreamPricing.getUpdated());
            upstreamRow.put("inserted", upstreamPricing.getInserted());
            upstreamRow.put("provider_models", upstreamPricing.getProviderModels());
            out.put("upstream", upstreamRow);
        } else {
            out.put("upstream_error", fetchError);
        }
        return out;
    }

    /**
     * 余额刷新：逐 Key 真查一次上游余额并按 Key 落库（sys_ai_provider_credit，先删后增）。
     *
     * <p>三个调用方共用本方法：「同步」的余额分节、面板的 {@code refresh=true} 强制回源
     * （经 {@code /credit}）、时间轮定时刷新（{@code CreditRefreshScheduler}）。候选实现与
     * 鉴权口径与面板查询完全一致，但不带面板的 5 分钟读缓存——每次调用都强制回源，
     * 并把读数写成新的按 Key 快照供重启后回放。</p>
     *
     * @param entity 提供商行
     * @return 余额报告（keys / ok / success / failed / synced_at）；无 Key 或无余额口径时返回 null
     */
    public CreditSyncReport refreshProviderCredit(SysAccountChannel entity) {
        Map<String, Object> extra = entity.getExtra() == null ? new HashMap<>(4) : entity.getExtra();
        // 与 /credit 同口径：显式余额密钥优先，无令牌但配齐 AK/SK 时走签名链路（见 effectiveCreditKeys）
        List<String> keys = effectiveCreditKeys(entity, extra);
        if (keys.isEmpty()) {
            return null;
        }
        // 无上游地址（none）或官方站点未收录余额接口（uncovered）：没有可查的口径，不浪费出网
        String level = creditLevel(entity, extra);
        if ("none".equals(level) || "uncovered".equals(level)) {
            return null;
        }
        List<CreditReading> rows = new ArrayList<>(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            rows.add(creditOfKey(entity, extra, keys.get(i), i));
        }
        // AK/SK 占位链路：唯一读数行标「AK/SK」，快照与面板同口径
        if (keys.size() == 1 && CREDIT_AKSK_KEY.equals(keys.get(0)) && !rows.isEmpty()) {
            rows.set(0, withMask(rows.get(0), CREDIT_AKSK_MASK));
        }
        long success = rows.stream().filter(CreditReading::ok).count();
        persistCreditSnapshots(entity.getProviderId(), keys, rows);
        // 刷新后失效面板的进程内读缓存：下次打开 /credit 会读到刚落库的新快照
        CREDIT_CACHE.remove(creditFingerprint(entity.getProviderId(),
                entity.getBaseUrl() == null ? "" : entity.getBaseUrl().trim(),
                keys, str(extra, "credit_kind"), extra));
        return new CreditSyncReport(keys.size(), success > 0, success, keys.size() - success,
                System.currentTimeMillis(), null);
    }

    /**
     * 「同步」里的余额分节报告：无 Key / 无口径时给出可读的跳过原因。
     *
     * @param entity 提供商行
     * @return 余额同步报告
     */
    private CreditSyncReport syncProviderCredit(SysAccountChannel entity) {
        CreditSyncReport report = refreshProviderCredit(entity);
        if (report != null) {
            log.info("[ProviderSync] provider={} 余额同步完成：{}/{} 个 Key 查到读数",
                    entity.getProviderId(), report.success(), report.keys());
            return report;
        }
        return CreditSyncReport.skipped("没有可用的 Key 或未收录余额口径，跳过余额同步");
    }

    /**
     * 从限制报告里取计费口径（declared 优先，其次 observed）。
     *
     * @param limits 限制报告
     * @return 计费口径文本；没有返回 null
     */
    private static Object billingBasisOf(Map<String, Object> limits) {
        if (limits == null) {
            return null;
        }
        for (String key : new String[]{"declared", "observed"}) {
            if (limits.get(key) instanceof Map<?, ?> map && map.get("billing_basis") != null) {
                return map.get("billing_basis");
            }
        }
        return null;
    }

    /**
     * 失效免费渠道路由缓存（提交后执行）。
     *
     * <p>供应商 / 渠道模型的写入会改变「可路由的模型集合」，而
     * {@code ModelClientResolver} 的 {@code routeCache} 默认缓存 5 分钟；
     * 不主动失效时，页面刚保存完开关、免费渠道里却仍是旧清单。</p>
     *
     * <p>调用点都在 {@code @Transactional} 写方法内，这里挂到事务提交之后：
     * 提交前清缓存会被并发请求用旧数据重新填充，等于把陈旧窗口再延长一个 TTL。</p>
     *
     * @param reason 变更原因（日志用）
     */
    private void invalidateRouteCache(String reason) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doInvalidateRouteCache(reason);
                }
            });
            return;
        }
        doInvalidateRouteCache(reason);
    }

    /**
     * 实际清理两处缓存。
     *
     * @param reason 变更原因（日志用）
     */
    private void doInvalidateRouteCache(String reason) {
        try {
            modelClientResolver.invalidateRouteCache();
        } catch (Exception e) {
            log.debug("[Provider] 清理模型路由缓存失败 reason={} : {}", reason, e.getMessage());
        }
    }
}
