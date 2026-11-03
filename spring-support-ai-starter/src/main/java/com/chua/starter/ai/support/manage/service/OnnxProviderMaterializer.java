// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.deeplearning.support.engine.ModelParams;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.service.AiModelCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ONNX 虚拟供应商物化服务。
 *
 * <p>背景：ONNX 供应商与模型原先完全由 {@link ModelRegistry} 在内存中动态拼装
 * （见 {@code ApiProviderController#buildOnnxProvider}），不落 {@code sys_ai_provider}
 * 表。这导致后台的「编辑供应商」「删除供应商」「修改模型 ID」等操作全部 404，
 * 因为这些接口都以 {@code requireProvider()} 查库为前提。</p>
 *
 * <p>本服务在应用启动后把 ONNX 供应商<strong>物化</strong>到库中：</p>
 * <ul>
 *   <li>{@code sys_ai_provider} 写入一行 {@code provider_id = 'onnx'}、{@code platform = 'onnx'}；</li>
 *   <li>{@code sys_ai_provider_model} 为每个已注册 ONNX 模型写入一行镜像，
 *       {@code model} 即 modelId（外部目录模型为其目录路径），并以
 *       {@code capabilities[0].protocol = 'onnx'} 标记，供运行时区分。</li>
 * </ul>
 *
 * <p><b>不影响 AI 业务流程</b>：模型能力的实际解析仍走 {@link ModelRegistry}
 * （按 modelId → relativePath → classpath → downloadUrl），本服务只负责
 * 管理面的持久化镜像。ONNX 不参与对话模型路由（{@code ModelClientResolver} 只处理
 * LLM 的 {@code providerId:model}），因此物化后路由行为不变。</p>
 *
 * <p>同步策略：以 {@link ModelRegistry} 为准做增量同步——已存在的模型行只补
 * {@code capabilities}/{@code description} 等派生字段，<strong>不覆盖</strong>
 * 用户在管理界面改过的 {@code enabled}/{@code alias}/{@code contextWindow} 等人工字段；
 * 注册表里已不存在的模型行保留但标记为不可用，避免用户配置被静默删除。</p>
 *
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnnxProviderMaterializer {

    /**
     * ONNX 供应商标识（与 {@code ApiProviderController#ONNX_PROVIDER_ID} 保持一致）。
     */
    public static final String ONNX_PROVIDER_ID = "onnx";

    /**
     * 协议标记：写入 capabilities[0].protocol，供运行时区分 ONNX 与在线协议。
     */
    public static final String ONNX_PROTOCOL = "onnx";

    /**
     * 平台名。
     */
    private static final String PLATFORM = "onnx";

    /**
     * 认证方式：与其它提供商统一为 api_key，本地地址同样通过凭证字段取用。
     */
    private static final String AUTH_SCHEME = "api_key";

    /**
     * 供应商显示名。
     */
    private static final String PROVIDER_NAME = "ONNX 本地推理";

    /**
     * 供应商描述。
     */
    private static final String PROVIDER_DESC = "本地 ONNX 推理模型（由模型注册表动态同步）";

    /**
     * 历史遗留的 appkey 占位串：ONNX 的 appkey 语义是本地模型目录，该固定值需要被刷新掉
     */
    private static final String LEGACY_API_KEY = "local-onnx";

    /**
     * 供应商排序（排在最前，与原先虚拟实现一致）。
     */
    private static final int PROVIDER_SORT = -2;

    /**
     * ONNX 基础地址：指向本机推理服务，与其它提供商同构，只是地址是本地的。
     */
    @org.springframework.beans.factory.annotation.Value("${ai.onnx.base-url:http://127.0.0.1:8080/v1}")
    private String onnxBaseUrl;

    /**
     * ONNX 凭证：本地地址形态的取用凭据，写入 credentials.api_key，与其它提供商同字段。
     *
     * <p>语义是「本地模型目录」，不是密钥。配置项 {@code ai.onnx.api-key} 在未选择过
     * 目录时兜底；该值可以是相对路径（如 {@code models/onnx}），
     * {@link #currentModelDir()} 会按 {@code user.dir} 绝对化。</p>
     */
    @org.springframework.beans.factory.annotation.Value("${ai.onnx.api-key:local-onnx}")
    private String onnxApiKey;

    /**
     * 供应商表操作。
     */
    private final SysAiProviderMapper providerMapper;

    /**
     * 供应商模型表操作。
     */
    private final SysAiProviderModelMapper providerModelMapper;

    /**
     * ONNX 外部目录服务：提供 ReFileSystem 选中的模型根，并负责把根推回模型注册表
     */
    private final OnnxExternalDirectoryService onnxExternalDirectoryService;

    /**
     * 本地模型目录门面：用于取「已注册且文件就绪」的内置 ONNX 模型清单。
     */
    private final AiModelCatalogService aiModelCatalogService;

    /**
     * 应用启动完成后物化 ONNX 供应商及其模型。
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(rollbackFor = Exception.class)
    public void materialize() {
        // 必须先确保外部目录已注册进 ModelRegistry：
        // discoverAll 只加载内置模板目录（可下载候选），不会发现已登记的外部目录。
        // 外部目录的注册在 restoreAll() 里，若靠 @Order 排序就会与本方法形成先后耦合，
        // 顺序一变这里就会同步到 0 个模型。直接显式调用，彻底去掉排序依赖。
        int restored = onnxExternalDirectoryService.restoreAll();
        try {
            onnxExternalDirectoryService.applyModelRoot();
            ModelRegistry.discoverAll();
        } catch (Exception e) {
            log.warn("[OnnxProvider] 模型注册表发现失败，跳过物化: {}", e.getMessage());
            return;
        }
        try {
            SysAiProvider provider = ensureProvider();
            int models = syncModels();
            syncProviderDevice();
            log.info("[OnnxProvider] 物化完成 provider={} 新增模型={} 恢复目录={}",
                    provider.getProviderId(), models, restored);
        } catch (Exception e) {
            log.warn("[OnnxProvider] 物化失败（不影响 AI 能力解析）: {}", e.getMessage());
        }
    }

    /**
     * 确保 ONNX 供应商行存在。
     *
     * @return 供应商行
     */
    private SysAiProvider ensureProvider() {
        SysAiProvider existing = providerMapper.selectById(ONNX_PROVIDER_ID);
        long now = System.currentTimeMillis();
        if (existing != null) {
            return backfillLocalEndpoint(existing, now);
        }
        SysAiProvider provider = new SysAiProvider();
        provider.setProviderId(ONNX_PROVIDER_ID);
        provider.setPlatform(PLATFORM);
        provider.setName(PROVIDER_NAME);
        provider.setBaseUrl(onnxBaseUrl);
        provider.setAuthScheme(AUTH_SCHEME);
        provider.setCredentials(localCredentials());
        provider.setEnabled(Boolean.TRUE);
        provider.setSortOrder(PROVIDER_SORT);
        provider.setCreatedAt(now);
        provider.setUpdatedAt(now);
        providerMapper.insert(provider);
        log.info("[OnnxProvider] 已创建 ONNX 供应商行 provider_id={} base_url={}", ONNX_PROVIDER_ID, onnxBaseUrl);
        return provider;
    }

    /**
     * 为已存在的 ONNX 供应商行补齐本地地址与凭证字段。
     *
     * <p>ONNX 与其它提供商同构：基础地址、认证方式、凭证都放在同一张表的同一组列上。
     * 早期实现把 {@code base_url} 留空、{@code auth_scheme} 置 {@code none}、不写凭证，
     * 与其它提供商不一致。这里只在字段为空时回填，不覆盖管理员已经改过的值。</p>
     *
     * @param existing 已存在的供应商行
     * @param now      当前毫秒时间戳
     * @return 供应商行（已回填则返回回填后的同一实例）
     */
    private SysAiProvider backfillLocalEndpoint(SysAiProvider existing, long now) {
        String expectedDir = currentModelDir();
        boolean baseUrlBlank = existing.getBaseUrl() == null || existing.getBaseUrl().isBlank();
        boolean authSchemeBlank = isLegacyAuthScheme(existing.getAuthScheme());
        boolean credentialsBlank = isLegacyCredentials(existing.getCredentials());
        if (!baseUrlBlank && !authSchemeBlank && !credentialsBlank) {
            return existing;
        }
        if (baseUrlBlank) {
            existing.setBaseUrl(onnxBaseUrl);
        }
        if (authSchemeBlank) {
            existing.setAuthScheme(AUTH_SCHEME);
        }
        if (credentialsBlank) {
            existing.setCredentials(localCredentials());
        }
        existing.setUpdatedAt(now);
        providerMapper.updateById(existing);
        log.info("[OnnxProvider] 已回填 ONNX 供应商 base_url={} auth_scheme={} api_key={}",
                existing.getBaseUrl(), existing.getAuthScheme(), expectedDir);
        return existing;
    }

    /**
     * 判断认证方式是否仍是遗留值（空或 none）。
     *
     * <p>ONNX 与其它提供商同构，认证方式统一为 {@code api_key}；历史行写的 {@code none}
     * 需要一并纠正，否则与其它提供商不一致。管理员显式改过的其它值不动。</p>
     *
     * @param authScheme 当前认证方式
     * @return 需要回填返回 {@code true}
     */
    private boolean isLegacyAuthScheme(String authScheme) {
        return authScheme == null || authScheme.isBlank() || "none".equalsIgnoreCase(authScheme.trim());
    }

    /**
     * 判断凭证是否仍是遗留占位值。
     *
     * <p>ONNX 的 appkey 语义是 ReFileSystem 选中的本地目录。历史行写的是固定占位串
     * {@code local-onnx}，需要刷新为当前目录；管理员填过真实值的不动。</p>
     *
     * @param credentials 当前凭证映射
     * @return 需要回填返回 {@code true}
     */
    private boolean isLegacyCredentials(Map<String, Object> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            return true;
        }
        Object apiKey = credentials.get("api_key");
        return apiKey == null || LEGACY_API_KEY.equals(String.valueOf(apiKey));
    }

    /**
     * 构造本地凭证映射，取用键名与其它提供商一致（{@code api_key}）。
     *
     * <p>ONNX 的 appkey 就是 ReFileSystem 选中的本地模型目录，与其它提供商「appkey 即接入凭据」
     * 的语义同构：别的提供商 appkey 是密钥，ONNX 的 appkey 是路径。因此优先取持久化的模型根，
     * 未选择过目录时才退回 {@code ai.onnx.api-key} 配置项。</p>
     *
     * @return 凭证映射
     */
    private Map<String, Object> localCredentials() {
        Map<String, Object> credentials = new LinkedHashMap<>(2);
        credentials.put("api_key", currentModelDir());
        return credentials;
    }

    /**
     * 当前生效的本地模型目录：优先 ReFileSystem 选中的模型根，其次配置项。
     *
     * <p><b>必须返回绝对路径</b>：{@code ModelRegistry} 把模型根与模型声明的相对路径
     * （如 {@code ocr/PP-OCRv6/tiny/det_infer}）直接拼接解析，根是相对路径时解析结果
     * 仍是相对路径，会落到 JVM 工作目录而不是模型实际所在处，表现为
     * 「未在 classpath 找到匹配 *.onnx 的文件」而文件其实就在盘上。
     * 相对配置项一律按 {@code user.dir} 绝对化。</p>
     *
     * @return 本地模型目录（绝对路径）
     */
    private String currentModelDir() {
        String selected = onnxExternalDirectoryService.readModelRoot();
        if (selected != null && !selected.isBlank()) {
            return toAbsolute(selected);
        }
        // 遗留占位串不是路径，原样返回，不然会被绝对化成 user.dir/local-onnx 这样的假路径
        if (LEGACY_API_KEY.equals(onnxApiKey)) {
            return onnxApiKey;
        }
        // 配置项语义是「默认模型根」而不是密钥：即使没绑定过目录也解析成绝对路径返回，
        // 管理面才能告诉用户模型该放到哪（yaml 默认 models/onnx → 显示 user.dir/models/onnx）
        return toAbsolute(onnxApiKey);
    }

    /**
     * 把路径绝对化；已是绝对路径 / 空值原样返回。
     *
     * @param raw 原始路径
     * @return 绝对路径；输入为空返回空串
     */
    private static String toAbsolute(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            Path p = Paths.get(raw.trim());
            if (p.isAbsolute()) {
                return p.normalize().toString();
            }
            String base = System.getProperty("user.dir");
            if (base == null || base.isBlank()) {
                return p.toAbsolutePath().normalize().toString();
            }
            return Paths.get(base).resolve(p).normalize().toString();
        } catch (Exception ex) {
            return raw.trim();
        }
    }

    /**
     * 同步 ONNX 模型镜像行。
     *
     * <p>只补派生字段，不覆盖人工维护的字段（enabled / alias / 上下文长度 / 采样参数等）。</p>
     *
     * @return 本次新增的模型行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int syncModels() {
        // 权威数据源是「用户通过 ReFileSystem 选中的目录清单」，不是 ModelRegistry.getAll()：
        // 外部目录在注册表里是独立结构，不会出现在 getAll() 中（那里只有内置模板目录），
        // 依赖它会随注册表实现与启动顺序漂移。ONNX 的 appkey 就是这个路径，
        // 它的模型自然等于清单里的目录。
        onnxExternalDirectoryService.applyModelRoot();
        List<Map<String, String>> entries = onnxExternalDirectoryService.readEntries();
        // 注意：外部目录为空也要继续——内置本地模型同样要入表（见下方 built-in 同步）。
        // 旧实现在这里直接 return 0，于是「模型管理 → ONNX」永远只剩外部目录那几条。
        // 清理历史遗留：旧版本把绝对路径写进了 model，而 (provider_id, model) 是复合主键，
        // 改主键字段靠 updateById 不会生效，只能删除后按新模型名重新插入
        int purged = 0;
        try {
            purged = providerModelMapper.delete(new LambdaQueryWrapper<SysAiProviderModel>()
                    .eq(SysAiProviderModel::getProviderId, ONNX_PROVIDER_ID)
                    .likeRight(SysAiProviderModel::getModel, "/"));
        } catch (Exception e) {
            log.warn("[OnnxProvider] 清理历史路径型模型行失败: {}", e.getMessage());
        }
        if (purged > 0) {
            log.info("[OnnxProvider] 已清理路径型模型行 {} 条，将按模型名重建", purged);
        }
        List<SysAiProviderModel> existing = providerModelMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, ONNX_PROVIDER_ID));
        int created = 0;
        int skipped = 0;
        long now = System.currentTimeMillis();
        for (Map<String, String> entry : entries) {
            String dir = OnnxExternalDirectoryService.canonicalDir(entry.get("dir"));
            if (dir == null || dir.isBlank() || !ModelRegistry.isExistingDirectory(dir)) {
                skipped++;
                continue;
            }
            String template = entry.get("template");
            if (template != null && !template.isBlank() && !ModelRegistry.isRegistered(template)) {
                skipped++;
                log.warn("[OnnxProvider] 目录跳过（模板模型未注册）: dir={}, template={}", dir, template);
                continue;
            }
            // model 字段用注册表里的模型标识（template），与其它提供商的模型名同构；
            // 目录路径属于 appkey/描述，不应出现在模型名里
            String modelName = template == null || template.isBlank()
                    ? dirName(dir)
                    : template.trim();
            List<java.util.Map<String, Object>> capabilities = List.of(OnnxModelTaxonomy.capability(modelName));
            String description = "外部目录 · " + dir;
            SysAiProviderModel hit = null;
            for (SysAiProviderModel row : existing) {
                if (modelName.equals(row.getModel())) {
                    hit = row;
                    break;
                }
            }
            if (hit != null) {
                // 仅刷新派生字段，保留用户已编辑的 enabled/alias/上下文等
                hit.setCapabilities(capabilities);
                hit.setDescription(description);
                hit.setUpdatedAt(now);
                providerModelMapper.updateById(hit);
            } else {
                SysAiProviderModel row = new SysAiProviderModel();
                row.setProviderId(ONNX_PROVIDER_ID);
                row.setModel(modelName);
                row.setEnabled(Boolean.TRUE);
                row.setSortOrder(0);
                row.setDescription(description);
                row.setCapabilities(capabilities);
                row.setCreatedAt(now);
                row.setUpdatedAt(now);
                providerModelMapper.insert(row);
                created++;
            }
        }
        // 内置本地模型（注册表内、文件就绪）也像其它提供商一样入表。
        // 之前只镜像外部目录，导致「模型管理 → ONNX」看不到 cn-clip-rn50-image 等
        // 正在被系统设置引用、运行时也能跑的模型，默认模型选择器只能靠前端旁路注入。
        Set<String> existingNames = new HashSet<>();
        for (SysAiProviderModel row : providerModelMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, ONNX_PROVIDER_ID))) {
            existingNames.add(row.getModel());
        }
        List<Map<String, Object>> details;
        try {
            details = aiModelCatalogService.listLocalModelDetails();
        } catch (Exception e) {
            details = List.of();
            log.warn("[OnnxProvider] 读取本地模型目录失败，跳过内置模型同步: {}", e.getMessage());
        }
        for (Map<String, Object> d : details) {
            Object mid = d.get("modelId");
            String modelId = mid == null ? null : String.valueOf(mid);
            if (modelId == null || modelId.isBlank() || !Boolean.TRUE.equals(d.get("available"))) {
                continue;
            }
            if (isExternal(modelId) || !existingNames.add(modelId)) {
                continue;
            }
            SysAiProviderModel row = new SysAiProviderModel();
            row.setProviderId(ONNX_PROVIDER_ID);
            row.setModel(modelId);
            row.setEnabled(Boolean.TRUE);
            row.setSortOrder(0);
            row.setDescription("ONNX 本地模型 · " + OnnxModelTaxonomy.task(modelId));
            row.setCapabilities(List.of(OnnxModelTaxonomy.capability(modelId)));
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            providerModelMapper.insert(row);
            created++;
        }
        log.info("[OnnxProvider] 模型同步完成 清单={} 新增={} 跳过={} 清理={} 模型根={}",
                entries.size(), created, skipped, purged, currentModelDir());
        return created;
    }

    /**
     * 把 ONNX 供应商的 {@code extra.device} 桥接到 {@link ModelParams} 的 Provider 级覆盖。
     *
     * <p>{@code provider.extra.device} 由前端在供应商编辑页保存（自动 / CPU / GPU），
     * 这里按模型镜像行逐个写入覆盖。Provider 级覆盖优先于原模型参数接口
     * （{@code /v2/onnx/model-params}）中保存的模型级 device，runtime（
     * {@code GpuHelper}、{@code ModelRegistry}）与接口的 resolvedDevice 共用
     * {@link ModelParams#deviceOf(String)} 裁决，因此无需改动各推理调用点。</p>
     *
     * <p>设备值为空、{@code auto} 或非法值时清除覆盖，让模型级参数重新生效。</p>
     */
    public void syncProviderDevice() {
        SysAiProvider provider = providerMapper.selectById(ONNX_PROVIDER_ID);
        if (provider == null) {
            log.info("[OnnxProvider] 推理设备同步跳过：ONNX 供应商不存在");
            return;
        }
        String device = providerDevice(provider.getExtra());
        List<SysAiProviderModel> models = providerModelMapper.selectList(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, ONNX_PROVIDER_ID));
        // 每次重建覆盖集：模型删除 / 改名后，旧 modelId 的覆盖不能残留在运行时。
        ModelParams.clearDeviceOverrides();
        for (SysAiProviderModel model : models) {
            if (device != null) {
                ModelParams.setDeviceOverride(model.getModel(), device);
            }
        }
        log.info("[OnnxProvider] 推理设备已同步 provider={} device={} models={}",
                ONNX_PROVIDER_ID, device == null ? "auto(模型级)" : device, models.size());
    }

    /**
     * 从供应商扩展配置中解析合法设备值。
     *
     * @param extra 供应商扩展配置
     * @return 归一化设备名（cpu / gpu），未配置或 auto 返回 {@code null}
     */
    private String providerDevice(Map<String, Object> extra) {
        if (extra == null) {
            return null;
        }
        Object value = extra.get("device");
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value).trim();
        if (raw.isBlank() || "auto".equalsIgnoreCase(raw)) {
            return null;
        }
        String lower = raw.toLowerCase();
        if ("gpu".equals(lower) || "cuda".equals(lower)) {
            return "gpu";
        }
        if ("cpu".equals(lower)) {
            return "cpu";
        }
        log.warn("[OnnxProvider] 忽略未知 device 值: {}", raw);
        return null;
    }

    /**
     * 取目录名作为模型名（未登记模板时的回退）。
     *
     * @param dir 模型目录
     * @return 目录名
     */
    private static String dirName(String dir) {
        int slash = dir.lastIndexOf('/');
        return slash < 0 || slash == dir.length() - 1 ? dir : dir.substring(slash + 1);
    }

    /**
     * 构造 capability 条目。
     *
     * @param task 能力任务名
     * @return capability map
     */
    private static java.util.Map<String, Object> capability(String task) {
        java.util.Map<String, Object> capability = new java.util.LinkedHashMap<>(8);
        capability.put("task", task);
        capability.put("traits", traitsForTask(task));
        capability.put("protocol", ONNX_PROTOCOL);
        capability.put("connection_role", "user");
        capability.put("allow_cross_origin_credentials", false);
        capability.put("provider_params", null);
        return capability;
    }

    /**
     * 依据能力任务返回输入 / 输出 traits（供前端模型卡片展示图标）。
     *
     * @param task 能力任务名
     * @return traits 列表
     */
    private static List<String> traitsForTask(String task) {
        if (task == null) {
            return List.of("input:image", "output:text");
        }
        return switch (task) {
            case "embedding" -> List.of("input:text", "output:vector");
            case "speech_recognition" -> List.of("input:audio", "output:text");
            case "speech_synthesis" -> List.of("input:text", "output:audio");
            case "image_generation" -> List.of("input:text", "output:image");
            case "video_generation" -> List.of("input:text", "output:video");
            default -> List.of("input:image", "output:text");
        };
    }

    /**
     * 由目录标识与登记的模板推导能力任务名。 <p>模板是绑定目录时登记的，比目录名更准确地反映能力（目录名只是路径片段）。</p>
     * @param dir 模型目录
     * @param modelName 字符串
     * @param template 登记的模板模型标识，可为空
     * @return 任务名
    */
    private static String taskOf(String modelName, String dir) {
        String source = modelName == null || modelName.isBlank() ? dir : modelName;
        return OnnxModelTaxonomy.task(source);
    }

    /**
     * 生成模型描述（外部目录）。
     *
     * @param dir 模型目录
     * @return 描述文本
     */
    private static String describe(String dir) {
        return "外部目录 · " + dir;
    }

    /**
     * 判断 modelId 是否为外部目录（绝对路径）。
     *
     * @param modelId 模型标识
     * @return true 表示外部目录模型
     */
    public static boolean isExternal(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        try {
            return Paths.get(modelId).isAbsolute();
        } catch (Exception e) {
            return false;
        }
    }
}
