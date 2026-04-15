package com.chua.starter.soft.support.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.server.support.enums.ServerServiceOperationType;
import com.chua.starter.server.support.model.ServerServiceCommandResult;
import com.chua.starter.server.support.model.ServerServiceUpsertRequest;
import com.chua.starter.server.support.service.ServerServiceService;
import com.chua.starter.soft.support.config.SoftManagementProperties;
import com.chua.starter.soft.support.constants.SoftSocketEvents;
import com.chua.starter.soft.support.entity.SoftConfigSnapshot;
import com.chua.starter.soft.support.entity.SoftInstallation;
import com.chua.starter.soft.support.entity.SoftOperationLog;
import com.chua.starter.soft.support.entity.SoftPackage;
import com.chua.starter.soft.support.entity.SoftPackageVersion;
import com.chua.starter.soft.support.entity.SoftRepository;
import com.chua.starter.soft.support.entity.SoftRepositorySourceEntity;
import com.chua.starter.soft.support.entity.SoftTarget;
import com.chua.starter.soft.support.enums.SoftInstallationStatus;
import com.chua.starter.soft.support.enums.SoftOperationStage;
import com.chua.starter.soft.support.enums.SoftOperationType;
import com.chua.starter.soft.support.enums.SoftRuntimeStatus;
import com.chua.starter.soft.support.mapper.SoftConfigSnapshotMapper;
import com.chua.starter.soft.support.mapper.SoftInstallationMapper;
import com.chua.starter.soft.support.mapper.SoftOperationLogMapper;
import com.chua.starter.soft.support.mapper.SoftPackageMapper;
import com.chua.starter.soft.support.mapper.SoftPackageVersionMapper;
import com.chua.starter.soft.support.mapper.SoftRepositoryMapper;
import com.chua.starter.soft.support.mapper.SoftRepositorySourceMapper;
import com.chua.starter.soft.support.mapper.SoftTargetMapper;
import com.chua.starter.soft.support.model.SoftConfigResponse;
import com.chua.starter.soft.support.model.SoftConfigWriteRequest;
import com.chua.starter.soft.support.model.SoftExecutionContext;
import com.chua.starter.soft.support.model.SoftGuidePreviewResponse;
import com.chua.starter.soft.support.model.SoftInstallRequest;
import com.chua.starter.soft.support.model.SoftLogResponse;
import com.chua.starter.soft.support.model.SoftLogWatchHandle;
import com.chua.starter.soft.support.model.SoftLogWatchTicket;
import com.chua.starter.soft.support.model.SoftOperationResult;
import com.chua.starter.soft.support.model.SoftOperationTicket;
import com.chua.starter.soft.support.model.SoftPackageCreateRequest;
import com.chua.starter.soft.support.model.SoftPackageUpdateRequest;
import com.chua.starter.soft.support.model.SoftPackageVersionCreateRequest;
import com.chua.starter.soft.support.model.SoftPackageVersionCopyInstallProfileRequest;
import com.chua.starter.soft.support.model.SoftRepositoryPackageSearchItem;
import com.chua.starter.soft.support.model.SoftRepositoryPackageSearchVersion;
import com.chua.starter.soft.support.model.SoftPackageVersionUpdateRequest;
import com.chua.starter.soft.support.model.SoftRealtimePayload;
import com.chua.starter.soft.support.model.SoftRenderedConfigFile;
import com.chua.starter.soft.support.model.SoftRepositorySource;
import com.chua.starter.soft.support.model.SoftRepositorySourceSearchItem;
import com.chua.starter.soft.support.model.SoftRepositorySourceUpdateRequest;
import com.chua.starter.soft.support.spi.SoftCommandObserver;
import com.chua.starter.soft.support.spi.SoftConfigManager;
import com.chua.starter.soft.support.spi.SoftInstallExecutor;
import com.chua.starter.soft.support.spi.SoftLogStreamProvider;
import com.chua.starter.soft.support.spi.SoftRepositorySyncProvider;
import com.chua.starter.soft.support.spi.SoftServiceManager;
import com.chua.starter.soft.support.util.SoftArtifactRepositorySupport;
import com.chua.starter.soft.support.util.SoftCommandSupport;
import com.chua.starter.soft.support.util.SoftJsons;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SoftManagementService {

    private final SoftRepositoryMapper repositoryMapper;
    private final SoftPackageMapper packageMapper;
    private final SoftPackageVersionMapper packageVersionMapper;
    private final SoftTargetMapper targetMapper;
    private final SoftRepositorySourceMapper repositorySourceMapper;
    private final SoftInstallationMapper installationMapper;
    private final SoftOperationLogMapper operationLogMapper;
    private final SoftConfigSnapshotMapper configSnapshotMapper;
    private final List<SoftRepositorySyncProvider> syncProviders;
    private final List<SoftInstallExecutor> installExecutors;
    private final SoftServiceManager serviceManager;
    private final ServerServiceService serverServiceService;
    private final SoftLogStreamProvider logStreamProvider;
    private final SoftConfigManager configManager;
    private final SoftGuideDefinitionService softGuideDefinitionService;
    private final SoftRealtimePublisher realtimePublisher;
    private final SoftManagementProperties properties;
    private final ExecutorService operationExecutor = Executors.newCachedThreadPool();
    private final AtomicLong watchIdGenerator = new AtomicLong(1L);
    private final Map<Long, SoftLogWatchHandle> logWatchHandles = new ConcurrentHashMap<>();

    public SoftManagementService(
            SoftRepositoryMapper repositoryMapper,
            SoftPackageMapper packageMapper,
            SoftPackageVersionMapper packageVersionMapper,
            SoftTargetMapper targetMapper,
            SoftRepositorySourceMapper repositorySourceMapper,
            SoftInstallationMapper installationMapper,
            SoftOperationLogMapper operationLogMapper,
            SoftConfigSnapshotMapper configSnapshotMapper,
            List<SoftRepositorySyncProvider> syncProviders,
            List<SoftInstallExecutor> installExecutors,
            SoftServiceManager serviceManager,
            ServerServiceService serverServiceService,
            SoftLogStreamProvider logStreamProvider,
            SoftConfigManager configManager,
            SoftGuideDefinitionService softGuideDefinitionService,
            SoftRealtimePublisher realtimePublisher,
            SoftManagementProperties properties
    ) {
        this.repositoryMapper = repositoryMapper;
        this.packageMapper = packageMapper;
        this.packageVersionMapper = packageVersionMapper;
        this.targetMapper = targetMapper;
        this.repositorySourceMapper = repositorySourceMapper;
        this.installationMapper = installationMapper;
        this.operationLogMapper = operationLogMapper;
        this.configSnapshotMapper = configSnapshotMapper;
        this.syncProviders = syncProviders;
        this.installExecutors = installExecutors;
        this.serviceManager = serviceManager;
        this.serverServiceService = serverServiceService;
        this.logStreamProvider = logStreamProvider;
        this.configManager = configManager;
        this.softGuideDefinitionService = softGuideDefinitionService;
        this.realtimePublisher = realtimePublisher;
        this.properties = properties;
    }

    @PreDestroy
    public void destroy() {
        logWatchHandles.values().forEach(SoftLogWatchHandle::stop);
        logWatchHandles.clear();
        operationExecutor.shutdownNow();
    }

    public List<SoftRepository> listRepositories() {
        List<SoftRepository> repositories = repositoryMapper.selectList(Wrappers.<SoftRepository>lambdaQuery()
                .orderByDesc(SoftRepository::getUpdateTime, SoftRepository::getCreateTime));
        repositories.forEach(this::hydrateRepository);
        return repositories;
    }

    public SoftRepository saveRepository(SoftRepository repository) {
        if (repository.getEnabled() == null) {
            repository.setEnabled(Boolean.TRUE);
        }
        normalizeRepository(repository);
        if (repository.getSoftRepositoryId() == null) {
            repositoryMapper.insert(repository);
        } else {
            repositoryMapper.updateById(repository);
        }
        replaceRepositorySourceRows(repository.getSoftRepositoryId(), repository.getSourceConfigs());
        SoftRepository stored = repositoryMapper.selectById(repository.getSoftRepositoryId());
        hydrateRepository(stored);
        return stored;
    }

    @Transactional(rollbackFor = Exception.class)
    public SoftRepository updateRepositorySources(Integer repositoryId, SoftRepositorySourceUpdateRequest request) {
        SoftRepository repository = requiredRepository(repositoryId);
        repository.setRepositoryUrl(request == null ? null : request.getRepositoryUrl());
        repository.setLocalDirectory(request == null ? null : request.getLocalDirectory());
        repository.setSourceConfigs(request == null ? List.of() : request.getSourceConfigs());
        normalizeRepository(repository);
        repositoryMapper.updateById(repository);
        replaceRepositorySourceRows(repositoryId, repository.getSourceConfigs());
        SoftRepository stored = repositoryMapper.selectById(repositoryId);
        hydrateRepository(stored);
        return stored;
    }

    public List<SoftRepositorySourceSearchItem> listRepositorySources(String keyword) {
        String normalizedKeyword = normalizeText(keyword);
        String keywordLower = normalizedKeyword == null ? null : normalizedKeyword.toLowerCase();
        List<SoftRepositorySourceSearchItem> results = new ArrayList<>();

        for (SoftRepository repository : listRepositories()) {
            SoftRepositorySource primary = buildPrimarySource(repository);
            if (primary != null) {
                SoftRepositorySourceSearchItem item = buildRepositorySourceSearchItem(repository, primary, true);
                if (matchesSourceKeyword(item, keywordLower)) {
                    results.add(item);
                }
            }
            for (SoftRepositorySource source : normalizeSourceConfigs(repository.getSourceConfigs())) {
                SoftRepositorySourceSearchItem item = buildRepositorySourceSearchItem(repository, source, false);
                if (matchesSourceKeyword(item, keywordLower)) {
                    results.add(item);
                }
            }
        }

        return results;
    }

    public List<SoftRepositorySource> listRepositorySourceItems(Integer repositoryId) {
        requiredRepository(repositoryId);
        return loadRepositorySourceItems(repositoryId);
    }

    @Transactional(rollbackFor = Exception.class)
    public SoftRepositorySource createRepositorySourceItem(Integer repositoryId, SoftRepositorySource source) {
        requiredRepository(repositoryId);
        SoftRepositorySource normalized = normalizeSourceConfig(source);
        if (normalized == null) {
            throw new IllegalStateException("来源配置不能为空");
        }
        SoftRepositorySourceEntity row = toRepositorySourceEntity(repositoryId, normalized, null);
        if (row.getSortOrder() == null) {
            Integer maxSort = repositorySourceMapper.selectList(Wrappers.<SoftRepositorySourceEntity>lambdaQuery()
                            .eq(SoftRepositorySourceEntity::getSoftRepositoryId, repositoryId)
                            .orderByDesc(SoftRepositorySourceEntity::getSortOrder))
                    .stream()
                    .map(SoftRepositorySourceEntity::getSortOrder)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(0);
            row.setSortOrder(maxSort + 10);
        }
        repositorySourceMapper.insert(row);
        syncRepositorySourcesJson(repositoryId);
        return toRepositorySourceModel(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public SoftRepositorySource updateRepositorySourceItem(Integer repositoryId, Integer sourceId, SoftRepositorySource source) {
        requiredRepository(repositoryId);
        SoftRepositorySourceEntity current = requiredRepositorySource(repositoryId, sourceId);
        SoftRepositorySource normalized = normalizeSourceConfig(source);
        if (normalized == null) {
            throw new IllegalStateException("来源配置不能为空");
        }
        SoftRepositorySourceEntity next = toRepositorySourceEntity(repositoryId, normalized, current.getSortOrder());
        next.setSoftRepositorySourceId(current.getSoftRepositorySourceId());
        repositorySourceMapper.updateById(next);
        syncRepositorySourcesJson(repositoryId);
        return toRepositorySourceModel(next);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRepositorySourceItem(Integer repositoryId, Integer sourceId) {
        requiredRepositorySource(repositoryId, sourceId);
        repositorySourceMapper.deleteById(sourceId);
        syncRepositorySourcesJson(repositoryId);
    }

    public List<SoftRepositoryPackageSearchItem> searchRepositoryPackages(
            String keyword,
            List<Integer> repositoryIds,
            String osType
    ) {
        String normalizedKeyword = normalizeText(keyword);
        String keywordLower = normalizedKeyword == null ? null : normalizedKeyword.toLowerCase();
        String normalizedOsType = SoftArtifactRepositorySupport.normalizeOsType(osType);
        List<Integer> normalizedRepositoryIds = Optional.ofNullable(repositoryIds).orElseGet(List::of).stream()
                .filter(Objects::nonNull)
                .filter(value -> value > 0)
                .distinct()
                .toList();

        List<SoftPackage> packages = packageMapper.selectList(Wrappers.<SoftPackage>lambdaQuery()
                .in(!normalizedRepositoryIds.isEmpty(), SoftPackage::getSoftRepositoryId, normalizedRepositoryIds)
                .orderByAsc(SoftPackage::getPackageName));
        if (packages.isEmpty()) {
            return List.of();
        }
        Map<Integer, SoftRepository> repositoryMap = repositoryMapper.selectBatchIds(
                        packages.stream()
                                .map(SoftPackage::getSoftRepositoryId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList()
                ).stream()
                .collect(java.util.stream.Collectors.toMap(SoftRepository::getSoftRepositoryId, value -> value, (a, b) -> a));
        List<Integer> packageIds = packages.stream()
                .map(SoftPackage::getSoftPackageId)
                .filter(Objects::nonNull)
                .toList();
        Map<Integer, List<SoftPackageVersion>> versionGroups = new HashMap<>();
        if (!packageIds.isEmpty()) {
            List<SoftPackageVersion> versions = packageVersionMapper.selectList(Wrappers.<SoftPackageVersion>lambdaQuery()
                    .in(SoftPackageVersion::getSoftPackageId, packageIds)
                    .orderByDesc(SoftPackageVersion::getUpdateTime, SoftPackageVersion::getCreateTime));
            versions.forEach(version -> versionGroups.computeIfAbsent(version.getSoftPackageId(), key -> new ArrayList<>()).add(version));
        }

        List<SoftRepositoryPackageSearchItem> results = new ArrayList<>();
        for (SoftPackage softPackage : packages) {
            List<SoftRepositoryPackageSearchVersion> versionItems = Optional.ofNullable(versionGroups.get(softPackage.getSoftPackageId()))
                    .orElseGet(List::of)
                    .stream()
                    .map(version -> toRepositoryPackageSearchVersion(softPackage, version))
                    .filter(item -> normalizedOsType == null || Objects.equals(item.getOsType(), normalizedOsType))
                    .filter(item -> matchesRepositoryPackageKeyword(softPackage, item, keywordLower))
                    .toList();
            if (versionItems.isEmpty()) {
                continue;
            }
            SoftRepository repository = repositoryMap.get(softPackage.getSoftRepositoryId());
            results.add(SoftRepositoryPackageSearchItem.builder()
                    .repositoryId(softPackage.getSoftRepositoryId())
                    .repositoryName(repository == null ? null : repository.getRepositoryName())
                    .repositoryCode(repository == null ? null : repository.getRepositoryCode())
                    .softPackageId(softPackage.getSoftPackageId())
                    .packageCode(softPackage.getPackageCode())
                    .packageName(softPackage.getPackageName())
                    .versions(versionItems)
                    .build());
        }
        return results;
    }

    public void deleteRepository(Integer repositoryId) {
        repositorySourceMapper.delete(Wrappers.<SoftRepositorySourceEntity>lambdaQuery()
                .eq(SoftRepositorySourceEntity::getSoftRepositoryId, repositoryId));
        repositoryMapper.deleteById(repositoryId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> uploadRepositoryArtifacts(Integer repositoryId, MultipartFile[] files) throws Exception {
        SoftRepository repository = requiredRepository(repositoryId);
        if (!"LOCAL_DIR".equalsIgnoreCase(repository.getRepositoryType())) {
            throw new IllegalStateException("仅本地目录仓库支持直接上传安装包");
        }
        if (files == null || files.length == 0) {
            throw new IllegalStateException("请选择至少一个安装包文件");
        }
        String targetDirectory = normalizeText(repository.getLocalDirectory());
        if (targetDirectory == null) {
            targetDirectory = resolveDefaultRepositoryDirectory(repository);
            repository.setLocalDirectory(targetDirectory);
            repositoryMapper.updateById(repository);
        }
        Path directory = Path.of(targetDirectory);
        Files.createDirectories(directory);
        List<Map<String, Object>> savedFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String originalFilename = normalizeText(file.getOriginalFilename());
            if (originalFilename == null) {
                originalFilename = "artifact-" + System.nanoTime();
            }
            Path targetFile = directory.resolve(Path.of(originalFilename).getFileName().toString());
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
            Map<String, Object> savedFile = new LinkedHashMap<>();
            savedFile.put("fileName", targetFile.getFileName().toString());
            savedFile.put("localPath", targetFile.toString());
            savedFile.put("size", file.getSize());
            savedFiles.add(savedFile);
        }
        SoftRepository syncedRepository = syncRepository(repositoryId);
        markUploadedArtifacts(repositoryId, savedFiles);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("repository", syncedRepository);
        payload.put("savedFiles", savedFiles);
        payload.put("localDirectory", targetDirectory);
        payload.put("message", "安装包已入库并完成同步");
        return payload;
    }

    @Transactional(rollbackFor = Exception.class)
    public SoftRepository syncRepository(Integer repositoryId) throws Exception {
        SoftRepository repository = requiredRepository(repositoryId);
        if (!Boolean.TRUE.equals(repository.getEnabled())) {
            repository.setLastSyncTime(LocalDateTime.now());
            repository.setLastSyncStatus("DISABLED");
            repository.setLastSyncMessage("仓库已禁用，无法执行同步");
            repositoryMapper.updateById(repository);
            throw new IllegalStateException("仓库已禁用，无法执行同步");
        }

        List<SoftRepositorySource> sources = resolveRepositorySources(repository);
        if (sources.isEmpty()) {
            repository.setLastSyncTime(LocalDateTime.now());
            repository.setLastSyncStatus("SUCCESS");
            repository.setLastSyncMessage("当前仓库未配置可同步源");
            repositoryMapper.updateById(repository);
            SoftRepository stored = repositoryMapper.selectById(repositoryId);
            hydrateRepository(stored);
            return stored;
        }

        Map<String, SoftPackage> packageMap = new LinkedHashMap<>();
        Map<String, SoftPackageVersion> versionMap = new LinkedHashMap<>();
        List<String> failureMessages = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        for (SoftRepositorySource source : sources) {
            if (!Boolean.TRUE.equals(source.getEnabled())) {
                skippedCount++;
                continue;
            }
            try {
                SoftRepositorySyncProvider provider = syncProviders.stream()
                        .filter(item -> item.supports(source.getSourceType()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("不支持的仓库源类型: " + source.getSourceType()));
                var payload = provider.sync(repository, source);
                successCount++;
                for (SoftPackage softPackage : payload.packages()) {
                    if (softPackage.getPackageCode() == null || softPackage.getPackageCode().isBlank()) {
                        continue;
                    }
                    softPackage.setSoftRepositoryId(repositoryId);
                    packageMap.put(packageIdentity(softPackage), softPackage);
                }
                for (SoftPackageVersion version : payload.versions()) {
                    if (version.getPackageCode() == null || version.getPackageCode().isBlank() || version.getVersionCode() == null || version.getVersionCode().isBlank()) {
                        continue;
                    }
                    versionMap.put(versionIdentity(version), version);
                }
            } catch (Exception e) {
                failedCount++;
                failureMessages.add(describeSource(source) + ": " + e.getMessage());
            }
        }

        Map<String, Integer> packageIdMap = new HashMap<>();
        for (Map.Entry<String, SoftPackage> entry : packageMap.entrySet()) {
            SoftPackage softPackage = entry.getValue();
            SoftPackage stored = upsertPackage(softPackage);
            packageIdMap.put(entry.getKey(), stored.getSoftPackageId());
        }
        for (SoftPackageVersion version : versionMap.values()) {
            Integer packageId = version.getSoftPackageId();
            if (packageId == null) {
                packageId = packageIdMap.get(resolveVersionPackageIdentity(version));
            }
            if (packageId == null) {
                continue;
            }
            version.setSoftPackageId(packageId);
            upsertVersion(version);
        }
        reconcileRepositoryPackages(repositoryId, packageMap.keySet(), versionMap.keySet());

        repository.setLastSyncTime(LocalDateTime.now());
        repository.setLastSyncStatus(resolveSyncStatus(successCount, failedCount));
        repository.setLastSyncMessage(buildRepositorySyncMessage(successCount, failedCount, skippedCount, packageMap.size(), versionMap.size(), failureMessages));
        repositoryMapper.updateById(repository);
        SoftRepository stored = repositoryMapper.selectById(repositoryId);
        hydrateRepository(stored);
        if (successCount == 0 && failedCount > 0) {
            throw new IllegalStateException(stored.getLastSyncMessage());
        }
        return stored;
    }

    public List<SoftPackage> listPackages() {
        List<SoftPackage> packages = packageMapper.selectList(Wrappers.<SoftPackage>lambdaQuery()
                .orderByAsc(SoftPackage::getPackageName));
        hydratePackages(packages);
        return packages;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createPackage(SoftPackageCreateRequest request) {
        if (request == null) {
            throw new IllegalStateException("创建请求不能为空");
        }
        String packageCode = normalizeSoftwareKey(request.getPackageCode());
        String packageName = normalizeText(request.getPackageName());
        String versionCode = normalizeText(request.getVersionCode());
        if (packageCode == null || packageName == null || versionCode == null) {
            throw new IllegalStateException("软件编码、软件名称、版本编码不能为空");
        }
        Integer repositoryId = resolveCreateRepositoryId(request.getSoftRepositoryId());
        String osType = SoftArtifactRepositorySupport.normalizeOsType(request.getOsType());
        String architecture = SoftArtifactRepositorySupport.normalizeArchitecture(request.getArchitecture());
        SoftPackage existed = packageMapper.selectOne(Wrappers.<SoftPackage>lambdaQuery()
                .eq(repositoryId != null, SoftPackage::getSoftRepositoryId, repositoryId)
                .eq(SoftPackage::getPackageCode, packageCode)
                .last("limit 1"));
        if (existed != null) {
            throw new IllegalStateException("软件已存在: " + packageCode + "（同仓库内不允许重复创建主档）");
        }

        SoftPackage softPackage = new SoftPackage();
        softPackage.setSoftRepositoryId(repositoryId);
        softPackage.setPackageCode(packageCode);
        softPackage.setPackageName(packageName);
        softPackage.setPackageCategory(normalizeText(request.getPackageCategory()));
        softPackage.setProfileCode(normalizeText(request.getProfileCode()));
        softPackage.setOsType(osType);
        softPackage.setArchitecture(architecture);
        softPackage.setDescription(normalizeText(request.getDescription()));
        softPackage.setIconUrl(normalizeText(request.getIconUrl()));
        normalizePackage(softPackage);
        packageMapper.insert(softPackage);

        SoftPackageVersion version = new SoftPackageVersion();
        version.setSoftPackageId(softPackage.getSoftPackageId());
        version.setPackageCode(softPackage.getPackageCode());
        version.setVersionCode(versionCode);
        version.setVersionName(Optional.ofNullable(normalizeText(request.getVersionName())).orElse(versionCode));
        version.setPackageName(packageName);
        version.setOsType(osType);
        version.setArchitecture(architecture);
        version.setSourceKind(normalizeSourceKind(request.getSourceKind(), null));
        version.setInstallMode(normalizeInstallMode(request.getInstallMode(), version.getSourceKind()));
        version.setRepositorySourceId(request.getRepositorySourceId());
        version.setArtifactPath(normalizeText(request.getArtifactPath()));
        version.setDownloadUrl(normalizeText(request.getDownloadUrl()));
        version.setDownloadUrlsJson(toJsonArray(request.getDownloadUrls()));
        version.setInstallScript(normalizeText(request.getInstallScript()));
        version.setUninstallScript(normalizeText(request.getUninstallScript()));
        version.setStartScript(normalizeText(request.getStartScript()));
        version.setStopScript(normalizeText(request.getStopScript()));
        version.setServiceRegisterScript(normalizeText(request.getServiceRegisterScript()));
        version.setServiceUnregisterScript(normalizeText(request.getServiceUnregisterScript()));
        version.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        version.setMetadataJson(buildCreatePackageMetadata(request, softPackage));
        normalizeVersion(version);
        packageVersionMapper.insert(version);
        hydrateVersion(version);
        hydratePackage(softPackage, List.of(version));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("package", softPackage);
        payload.put("version", version);
        return payload;
    }

    public Map<String, Object> getPackageDetail(Integer packageId) {
        SoftPackage softPackage = requiredPackage(packageId);
        List<SoftPackageVersion> versions = packageVersionMapper.selectList(Wrappers.<SoftPackageVersion>lambdaQuery()
                .eq(SoftPackageVersion::getSoftPackageId, packageId)
                .orderByDesc(SoftPackageVersion::getVersionCode));
        versions.forEach(this::hydrateVersion);
        hydratePackage(softPackage, versions);
        Map<String, Object> payload = new HashMap<>();
        payload.put("package", softPackage);
        payload.put("versions", versions);
        return payload;
    }

    @Transactional(rollbackFor = Exception.class)
    public SoftPackage updatePackage(Integer packageId, SoftPackageUpdateRequest request) {
        SoftPackage current = requiredPackage(packageId);
        if (request.getPackageName() != null) {
            String packageName = normalizeText(request.getPackageName());
            if (packageName != null) {
                current.setPackageName(packageName);
            }
        }
        if (request.getPackageCategory() != null) {
            current.setPackageCategory(normalizeText(request.getPackageCategory()));
        }
        if (request.getDescription() != null) {
            current.setDescription(normalizeText(request.getDescription()));
        }
        if (request.getIconUrl() != null) {
            current.setIconUrl(normalizeText(request.getIconUrl()));
        }
        if (request.getProfileCode() != null) {
            current.setProfileCode(normalizeText(request.getProfileCode()));
        }
        packageMapper.updateById(current);
        hydratePackage(current, null);
        return current;
    }

    @Transactional(rollbackFor = Exception.class)
    public SoftPackageVersion createPackageVersion(Integer packageId, SoftPackageVersionCreateRequest request) {
        SoftPackage softPackage = requiredPackage(packageId);
        if (request == null) {
            throw new IllegalStateException("新增版本请求不能为空");
        }
        String versionCode = normalizeText(request.getVersionCode());
        if (versionCode == null) {
            throw new IllegalStateException("版本编码不能为空");
        }

        SoftPackageVersion version = new SoftPackageVersion();
        version.setSoftPackageId(packageId);
        version.setVersionCode(versionCode);
        version.setVersionName(Optional.ofNullable(normalizeText(request.getVersionName())).orElse(versionCode));
        version.setPackageName(Optional.ofNullable(normalizeText(request.getPackageName()))
                .orElse(normalizeText(softPackage.getPackageName())));
        version.setOsType(Optional.ofNullable(SoftArtifactRepositorySupport.normalizeOsType(request.getOsType()))
                .orElse(SoftArtifactRepositorySupport.normalizeOsType(softPackage.getOsType())));
        version.setArchitecture(Optional.ofNullable(SoftArtifactRepositorySupport.normalizeArchitecture(request.getArchitecture()))
                .orElse(SoftArtifactRepositorySupport.normalizeArchitecture(softPackage.getArchitecture())));
        version.setSourceKind(normalizeSourceKind(request.getSourceKind(), null));
        version.setInstallMode(normalizeInstallMode(request.getInstallMode(), version.getSourceKind()));
        version.setRepositorySourceId(request.getRepositorySourceId());
        version.setArtifactPath(normalizeText(request.getArtifactPath()));
        version.setDownloadUrl(normalizeText(request.getDownloadUrl()));
        version.setTemplateFromVersionId(request.getTemplateFromVersionId());
        version.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        version.setDownloadUrlsJson(toJsonArray(request.getDownloadUrls()));
        version.setInstallScript(normalizeText(request.getInstallScript()));
        version.setUninstallScript(normalizeText(request.getUninstallScript()));
        version.setStartScript(normalizeText(request.getStartScript()));
        version.setStopScript(normalizeText(request.getStopScript()));
        version.setRestartScript(normalizeText(request.getRestartScript()));
        version.setStatusScript(normalizeText(request.getStatusScript()));
        version.setServiceRegisterScript(normalizeText(request.getServiceRegisterScript()));
        version.setServiceUnregisterScript(normalizeText(request.getServiceUnregisterScript()));
        version.setLogPathsJson(normalizeJsonText(request.getLogPathsJson()));
        version.setConfigPathsJson(normalizeJsonText(request.getConfigPathsJson()));
        version.setMetadataJson(normalizeJsonText(request.getMetadataJson()));
        normalizeVersion(version);

        LambdaQueryWrapper<SoftPackageVersion> versionQuery = Wrappers.<SoftPackageVersion>lambdaQuery()
                .eq(SoftPackageVersion::getSoftPackageId, version.getSoftPackageId())
                .eq(SoftPackageVersion::getVersionCode, version.getVersionCode());
        if (version.getPackageName() == null) {
            versionQuery.isNull(SoftPackageVersion::getPackageName);
        } else {
            versionQuery.eq(SoftPackageVersion::getPackageName, version.getPackageName());
        }
        if (version.getOsType() == null) {
            versionQuery.isNull(SoftPackageVersion::getOsType);
        } else {
            versionQuery.eq(SoftPackageVersion::getOsType, version.getOsType());
        }
        if (version.getArchitecture() == null) {
            versionQuery.isNull(SoftPackageVersion::getArchitecture);
        } else {
            versionQuery.eq(SoftPackageVersion::getArchitecture, version.getArchitecture());
        }

        SoftPackageVersion existed = packageVersionMapper.selectOne(versionQuery.last("limit 1"));
        if (existed != null) {
            throw new IllegalStateException("软件版本已存在: "
                    + version.getVersionCode()
                    + " / "
                    + Optional.ofNullable(version.getOsType()).orElse("UNKNOWN")
                    + " / "
                    + Optional.ofNullable(version.getArchitecture()).orElse("UNKNOWN"));
        }

        packageVersionMapper.insert(version);
        hydrateVersion(version);
        return version;
    }

    @Transactional(rollbackFor = Exception.class)
    public SoftPackageVersion updatePackageVersion(Integer packageId,
                                                   Integer versionId,
                                                   SoftPackageVersionUpdateRequest request) {
        requiredPackage(packageId);
        SoftPackageVersion current = requiredVersion(versionId);
        if (!Objects.equals(current.getSoftPackageId(), packageId)) {
            throw new IllegalStateException("软件版本不属于当前软件: " + versionId);
        }
        if (request.getVersionName() != null) {
            String versionName = normalizeText(request.getVersionName());
            if (versionName != null) {
                current.setVersionName(versionName);
            }
        }
        if (request.getPackageName() != null) {
            current.setPackageName(normalizeText(request.getPackageName()));
        }
        if (request.getOsType() != null) {
            current.setOsType(SoftArtifactRepositorySupport.normalizeOsType(request.getOsType()));
        }
        if (request.getArchitecture() != null) {
            current.setArchitecture(SoftArtifactRepositorySupport.normalizeArchitecture(request.getArchitecture()));
        }
        if (request.getSourceKind() != null) {
            current.setSourceKind(normalizeSourceKind(request.getSourceKind(), current.getSourceKind()));
        }
        if (request.getInstallMode() != null) {
            current.setInstallMode(normalizeInstallMode(request.getInstallMode(), current.getSourceKind()));
        }
        if (request.getRepositorySourceId() != null) {
            current.setRepositorySourceId(request.getRepositorySourceId());
        }
        if (request.getArtifactPath() != null) {
            current.setArtifactPath(normalizeText(request.getArtifactPath()));
        }
        if (request.getDownloadUrl() != null) {
            current.setDownloadUrl(normalizeText(request.getDownloadUrl()));
        }
        if (request.getTemplateFromVersionId() != null) {
            current.setTemplateFromVersionId(request.getTemplateFromVersionId());
        }
        if (request.getEnabled() != null) {
            current.setEnabled(request.getEnabled());
        }
        if (request.getDownloadUrlsJson() != null) {
            current.setDownloadUrlsJson(normalizeJsonText(request.getDownloadUrlsJson()));
        }
        if (request.getInstallScript() != null) {
            current.setInstallScript(normalizeText(request.getInstallScript()));
        }
        if (request.getUninstallScript() != null) {
            current.setUninstallScript(normalizeText(request.getUninstallScript()));
        }
        if (request.getStartScript() != null) {
            current.setStartScript(normalizeText(request.getStartScript()));
        }
        if (request.getStopScript() != null) {
            current.setStopScript(normalizeText(request.getStopScript()));
        }
        if (request.getRestartScript() != null) {
            current.setRestartScript(normalizeText(request.getRestartScript()));
        }
        if (request.getStatusScript() != null) {
            current.setStatusScript(normalizeText(request.getStatusScript()));
        }
        if (request.getServiceRegisterScript() != null) {
            current.setServiceRegisterScript(normalizeText(request.getServiceRegisterScript()));
        }
        if (request.getServiceUnregisterScript() != null) {
            current.setServiceUnregisterScript(normalizeText(request.getServiceUnregisterScript()));
        }
        if (request.getLogPathsJson() != null) {
            current.setLogPathsJson(normalizeJsonText(request.getLogPathsJson()));
        }
        if (request.getConfigPathsJson() != null) {
            current.setConfigPathsJson(normalizeJsonText(request.getConfigPathsJson()));
        }
        if (request.getMetadataJson() != null) {
            current.setMetadataJson(mergeVersionMetadata(current, request.getMetadataJson()));
        }
        normalizeVersion(current);
        packageVersionMapper.updateById(current);
        hydrateVersion(current);
        return current;
    }

    @Transactional(rollbackFor = Exception.class)
    public SoftPackageVersion copyVersionInstallProfile(Integer packageId,
                                                        Integer versionId,
                                                        SoftPackageVersionCopyInstallProfileRequest request) {
        requiredPackage(packageId);
        SoftPackageVersion targetVersion = requiredVersion(versionId);
        if (!Objects.equals(targetVersion.getSoftPackageId(), packageId)) {
            throw new IllegalStateException("软件版本不属于当前软件: " + versionId);
        }
        if (request == null || request.getSourceVersionId() == null) {
            throw new IllegalStateException("来源版本不能为空");
        }
        SoftPackageVersion sourceVersion = requiredVersion(request.getSourceVersionId());
        if (!Objects.equals(sourceVersion.getSoftPackageId(), packageId)) {
            throw new IllegalStateException("来源版本不属于当前软件: " + request.getSourceVersionId());
        }

        targetVersion.setSourceKind(normalizeSourceKind(sourceVersion.getSourceKind(), targetVersion.getSourceKind()));
        targetVersion.setInstallMode(normalizeInstallMode(sourceVersion.getInstallMode(), targetVersion.getSourceKind()));
        targetVersion.setRepositorySourceId(sourceVersion.getRepositorySourceId());
        targetVersion.setArtifactPath(normalizeText(sourceVersion.getArtifactPath()));
        targetVersion.setDownloadUrl(normalizeText(sourceVersion.getDownloadUrl()));
        targetVersion.setInstallScript(normalizeText(sourceVersion.getInstallScript()));
        targetVersion.setUninstallScript(normalizeText(sourceVersion.getUninstallScript()));
        targetVersion.setStartScript(normalizeText(sourceVersion.getStartScript()));
        targetVersion.setStopScript(normalizeText(sourceVersion.getStopScript()));
        targetVersion.setRestartScript(normalizeText(sourceVersion.getRestartScript()));
        targetVersion.setStatusScript(normalizeText(sourceVersion.getStatusScript()));
        targetVersion.setServiceRegisterScript(normalizeText(sourceVersion.getServiceRegisterScript()));
        targetVersion.setServiceUnregisterScript(normalizeText(sourceVersion.getServiceUnregisterScript()));
        targetVersion.setLogPathsJson(normalizeJsonText(sourceVersion.getLogPathsJson()));
        targetVersion.setConfigPathsJson(normalizeJsonText(sourceVersion.getConfigPathsJson()));
        targetVersion.setCapabilityFlagsJson(normalizeJsonText(sourceVersion.getCapabilityFlagsJson()));
        targetVersion.setTemplateFromVersionId(sourceVersion.getSoftPackageVersionId());
        if (Boolean.TRUE.equals(request.getCopyDownloadUrls())) {
            targetVersion.setDownloadUrlsJson(normalizeJsonText(sourceVersion.getDownloadUrlsJson()));
        }
        normalizeVersion(targetVersion);
        packageVersionMapper.updateById(targetVersion);
        hydrateVersion(targetVersion);
        return targetVersion;
    }

    public SoftPackage requiredPackageView(Integer packageId) {
        SoftPackage softPackage = requiredPackage(packageId);
        hydratePackage(softPackage, null);
        return softPackage;
    }

    public List<SoftTarget> listTargets() {
        return targetMapper.selectList(Wrappers.<SoftTarget>lambdaQuery()
                .orderByDesc(SoftTarget::getUpdateTime, SoftTarget::getCreateTime));
    }

    public SoftTarget saveTarget(SoftTarget target) {
        if (target.getEnabled() == null) {
            target.setEnabled(Boolean.TRUE);
        }
        if (target.getSoftTargetId() == null) {
            targetMapper.insert(target);
        } else {
            targetMapper.updateById(target);
        }
        return targetMapper.selectById(target.getSoftTargetId());
    }

    public void deleteTarget(Integer targetId) {
        targetMapper.deleteById(targetId);
    }

    public List<SoftInstallation> listInstallations() {
        List<SoftInstallation> installations = installationMapper.selectList(Wrappers.<SoftInstallation>lambdaQuery()
                .orderByDesc(SoftInstallation::getUpdateTime, SoftInstallation::getCreateTime));
        hydrateInstallations(installations);
        return installations;
    }

    public Map<String, Object> getInstallationDetail(Integer installationId) {
        SoftExecutionContext context = buildContext(requiredInstallation(installationId));
        Map<String, Object> payload = new HashMap<>();
        payload.put("installation", enrichInstallation(context.getInstallation(), context.getSoftPackage(), context.getVersion(), context.getTarget()));
        payload.put("package", context.getSoftPackage());
        payload.put("version", context.getVersion());
        payload.put("target", context.getTarget());
        payload.put("snapshots", listConfigSnapshots(installationId));
        return payload;
    }

    public List<SoftOperationLog> listOperationLogs() {
        return operationLogMapper.selectList(Wrappers.<SoftOperationLog>lambdaQuery()
                .orderByDesc(SoftOperationLog::getStartTime, SoftOperationLog::getCreateTime));
    }

    public SoftOperationTicket install(SoftInstallRequest request) {
        SoftPackage softPackage = requiredPackage(request.getSoftPackageId());
        SoftPackageVersion version = requiredVersion(request.getSoftPackageVersionId());
        SoftTarget target = requiredTarget(request.getSoftTargetId());
        SoftGuidePreviewResponse guidePreview = softGuideDefinitionService.resolveGuideAssets(
                softPackage,
                version,
                target,
                request.getInstallationName(),
                request.getInstallPath(),
                request.getServiceName(),
                request.getInstallOptions(),
                request.getServiceOptions(),
                request.getConfigOptions()
        );

        SoftInstallation installation = new SoftInstallation();
        installation.setSoftPackageId(softPackage.getSoftPackageId());
        installation.setSoftPackageVersionId(version.getSoftPackageVersionId());
        installation.setSoftTargetId(target.getSoftTargetId());
        installation.setInstallationName(stringValue(guidePreview.getResolvedVariables().get("installationName"), softPackage.getPackageName()));
        installation.setInstallPath(stringValue(guidePreview.getResolvedVariables().get("installPath"), target.getBaseDirectory() + "/" + softPackage.getPackageCode() + "/" + version.getVersionCode()));
        installation.setServiceName(stringValue(guidePreview.getResolvedVariables().get("serviceName"), sanitizeServiceName(softPackage.getPackageCode())));
        installation.setInstallOptionsJson(SoftJsons.toJson(Optional.ofNullable(request.getInstallOptions()).orElseGet(HashMap::new)));
        installation.setServiceOptionsJson(SoftJsons.toJson(Optional.ofNullable(request.getServiceOptions()).orElseGet(HashMap::new)));
        installation.setConfigOptionsJson(SoftJsons.toJson(Optional.ofNullable(request.getConfigOptions()).orElseGet(HashMap::new)));
        installation.setTemplateSummaryJson(guidePreview.getTemplateSummaryJson());
        installation.setInstallStatus(SoftInstallationStatus.INSTALLING.name());
        installation.setRuntimeStatus(SoftRuntimeStatus.UNKNOWN.name());
        installation.setInstalledVersion(version.getVersionCode());
        installation.setLastOperationTime(LocalDateTime.now());
        installation.setLastOperationMessage("安装任务已接收");
        installationMapper.insert(installation);

        SoftExecutionContext context = SoftExecutionContext.builder()
                .softPackage(softPackage)
                .version(version)
                .target(target)
                .installation(installation)
                .installOptions(Optional.ofNullable(request.getInstallOptions()).orElseGet(HashMap::new))
                .serviceOptions(Optional.ofNullable(request.getServiceOptions()).orElseGet(HashMap::new))
                .configOptions(Optional.ofNullable(request.getConfigOptions()).orElseGet(HashMap::new))
                .resolvedVariables(guidePreview.getResolvedVariables())
                .renderedScripts(guidePreview.getRenderedScripts())
                .renderedConfigFiles(guidePreview.getRenderedConfigFiles())
                .build();
        syncManagedServerService(context);
        SoftOperationLog operationLog = createOperationLog(installation, version, target, SoftOperationType.INSTALL, "安装任务已接收");
        operationLog.setTemplateSummaryJson(guidePreview.getTemplateSummaryJson());
        operationLog.setParameterSummaryJson(SoftJsons.toJson(Map.of(
                "installOptions", Optional.ofNullable(request.getInstallOptions()).orElseGet(HashMap::new),
                "serviceOptions", Optional.ofNullable(request.getServiceOptions()).orElseGet(HashMap::new),
                "configOptions", Optional.ofNullable(request.getConfigOptions()).orElseGet(HashMap::new)
        )));
        operationLogMapper.updateById(operationLog);
        SoftInstallExecutor executor = requiredInstallExecutor(target.getTargetType());
        submitOperation(() -> executeInstall(context, operationLog, executor));
        return buildTicket(operationLog);
    }

    public SoftOperationTicket uninstall(Integer installationId) {
        SoftExecutionContext context = buildContext(requiredInstallation(installationId));
        context.getInstallation().setInstallStatus(SoftInstallationStatus.UNINSTALLING.name());
        context.getInstallation().setLastOperationTime(LocalDateTime.now());
        context.getInstallation().setLastOperationMessage("卸载任务已接收");
        installationMapper.updateById(context.getInstallation());
        SoftOperationLog operationLog = createOperationLog(context.getInstallation(), context.getVersion(), context.getTarget(), SoftOperationType.UNINSTALL, "卸载任务已接收");
        SoftInstallExecutor executor = requiredInstallExecutor(context.getTarget().getTargetType());
        submitOperation(() -> executeUninstall(context, operationLog, executor));
        return buildTicket(operationLog);
    }

    public SoftOperationTicket registerService(Integer installationId) {
        return serviceOperation(installationId, SoftOperationType.SERVICE_REGISTER);
    }

    public SoftOperationTicket unregisterService(Integer installationId) {
        return serviceOperation(installationId, SoftOperationType.SERVICE_UNREGISTER);
    }

    public SoftOperationTicket startService(Integer installationId) {
        return serviceOperation(installationId, SoftOperationType.START);
    }

    public SoftOperationTicket stopService(Integer installationId) {
        return serviceOperation(installationId, SoftOperationType.STOP);
    }

    public SoftOperationTicket restartService(Integer installationId) {
        return serviceOperation(installationId, SoftOperationType.RESTART);
    }

    public SoftOperationTicket serviceStatus(Integer installationId) {
        return serviceOperation(installationId, SoftOperationType.STATUS);
    }

    public SoftLogResponse readLogs(Integer installationId, String logPath, Integer lines) throws Exception {
        SoftExecutionContext context = buildContext(requiredInstallation(installationId));
        LogSnapshot snapshot = readRecentLogs(context, logPath, lines == null ? 200 : lines);
        return SoftLogResponse.builder().logPath(snapshot.logPath()).lines(snapshot.lines()).build();
    }

    public SoftLogWatchTicket startLogWatch(Integer installationId, String logPath) throws Exception {
        SoftExecutionContext context = buildContext(requiredInstallation(installationId));
        String resolvedLogPath = resolveLogPath(context, logPath);
        if (resolvedLogPath == null || resolvedLogPath.isBlank()) {
            throw new IllegalStateException("未配置可监控日志路径");
        }
        Long watchId = watchIdGenerator.getAndIncrement();
        SoftLogWatchHandle handle = logStreamProvider.startWatch(
                watchId,
                context,
                resolvedLogPath,
                line -> publishRuntimeLog(context, line),
                error -> publishRuntimeLog(context, "[ERROR] " + error.getMessage()),
                reason -> logWatchHandles.remove(watchId)
        );
        logWatchHandles.put(watchId, handle);
        return SoftLogWatchTicket.builder()
                .watchId(watchId)
                .installationId(installationId)
                .logPath(resolvedLogPath)
                .acceptedAt(LocalDateTime.now())
                .build();
    }

    public boolean stopLogWatch(Long watchId) {
        SoftLogWatchHandle handle = logWatchHandles.remove(watchId);
        if (handle == null) {
            return false;
        }
        handle.stop();
        return true;
    }

    public SoftConfigResponse readConfig(Integer installationId, String configPath) throws Exception {
        SoftExecutionContext context = buildContext(requiredInstallation(installationId));
        String resolvedConfigPath = resolveConfigPath(context, configPath);
        String content = configManager.read(context, resolvedConfigPath);
        return SoftConfigResponse.builder()
                .configPath(resolvedConfigPath)
                .configContent(content)
                .availableConfigPaths(context.getVersion().getConfigPaths())
                .build();
    }

    public SoftOperationTicket writeConfig(Integer installationId, SoftConfigWriteRequest request) throws Exception {
        SoftExecutionContext context = buildContext(requiredInstallation(installationId));
        String resolvedConfigPath = resolveConfigPath(context, request.getConfigPath());
        String before = configManager.read(context, resolvedConfigPath);
        SoftConfigSnapshot snapshot = new SoftConfigSnapshot();
        snapshot.setSoftInstallationId(installationId);
        snapshot.setConfigPath(resolvedConfigPath);
        snapshot.setSnapshotName(Optional.ofNullable(request.getSnapshotName()).filter(name -> !name.isBlank()).orElse("snapshot-" + System.currentTimeMillis()));
        snapshot.setConfigContent(before);
        snapshot.setOperationRemark(request.getOperationRemark());
        configSnapshotMapper.insert(snapshot);

        SoftOperationLog operationLog = createOperationLog(context.getInstallation(), context.getVersion(), context.getTarget(), SoftOperationType.CONFIG_WRITE, "配置写入任务已接收");
        submitOperation(() -> executeConfigWrite(context, request, resolvedConfigPath, operationLog));
        return buildTicket(operationLog);
    }

    public List<SoftConfigSnapshot> listConfigSnapshots(Integer installationId) {
        return configSnapshotMapper.selectList(Wrappers.<SoftConfigSnapshot>lambdaQuery()
                .eq(SoftConfigSnapshot::getSoftInstallationId, installationId)
                .orderByDesc(SoftConfigSnapshot::getCreateTime));
    }

    public SoftOperationTicket rollbackConfig(Integer installationId, Integer snapshotId) throws Exception {
        SoftConfigSnapshot snapshot = configSnapshotMapper.selectById(snapshotId);
        if (snapshot == null) {
            throw new IllegalStateException("配置快照不存在");
        }
        SoftConfigWriteRequest request = new SoftConfigWriteRequest();
        request.setConfigPath(snapshot.getConfigPath());
        request.setConfigContent(snapshot.getConfigContent());
        request.setSnapshotName("rollback-" + snapshotId);
        request.setOperationRemark("回滚到快照 " + snapshotId);
        return writeConfig(installationId, request);
    }

    private void executeInstall(SoftExecutionContext context, SoftOperationLog operationLog, SoftInstallExecutor executor) {
        SoftInstallation installation = context.getInstallation();
        SoftExecutionReporter reporter = createReporter(operationLog, installation.getSoftInstallationId(), context);
        try {
            prepareRenderedAssets(context, reporter);
            SoftOperationResult result = executor.install(context, createCommandObserver(reporter));
            operationLog.setOperationCommand(result.getCommand());
            reporter.progress(SoftOperationStage.VERIFY, "校验安装结果", result.getMessage());
            installation.setLastOperationTime(LocalDateTime.now());
            installation.setLastOperationMessage(result.getMessage());
            installation.setInstallStatus(result.isSuccess() ? SoftInstallationStatus.INSTALLED.name() : SoftInstallationStatus.FAILED.name());
            if (result.isSuccess()) {
                if (shouldAutoRegisterService(context)) {
                    reporter.progress(SoftOperationStage.VERIFY, "执行服务引导注册", installation.getServiceName());
                    runServiceOperation(context, SoftOperationType.SERVICE_REGISTER);
                }
                installation.setInstalledTime(LocalDateTime.now());
            }
            installationMapper.updateById(installation);
            reporter.finish(result.isSuccess(), result.getMessage(), result.getMessage(), result.getOutput());
        } catch (Exception e) {
            installation.setInstallStatus(SoftInstallationStatus.FAILED.name());
            installation.setLastOperationTime(LocalDateTime.now());
            installation.setLastOperationMessage(e.getMessage());
            installationMapper.updateById(installation);
            reporter.finish(false, "安装执行失败", e.getMessage(), reporter.aggregatedOutput());
        }
    }

    private void prepareRenderedAssets(SoftExecutionContext context, SoftExecutionReporter reporter) throws Exception {
        if (context.getRenderedConfigFiles() == null || context.getRenderedConfigFiles().isEmpty()) {
            return;
        }
        reporter.progress(SoftOperationStage.PREPARE, "渲染并下发配置模板", context.getInstallation().getInstallPath());
        for (SoftRenderedConfigFile configFile : context.getRenderedConfigFiles()) {
            if (configFile.getTemplatePath() == null || configFile.getTemplatePath().isBlank()) {
                continue;
            }
            reporter.line("[CONFIG] " + configFile.getTemplatePath());
            configManager.write(context, configFile.getTemplatePath(), configFile.getContent());
        }
    }

    private void executeUninstall(SoftExecutionContext context, SoftOperationLog operationLog, SoftInstallExecutor executor) {
        SoftInstallation installation = context.getInstallation();
        SoftExecutionReporter reporter = createReporter(operationLog, installation.getSoftInstallationId(), context);
        try {
            SoftOperationResult result = executor.uninstall(context, createCommandObserver(reporter));
            operationLog.setOperationCommand(result.getCommand());
            reporter.progress(SoftOperationStage.VERIFY, "校验卸载结果", result.getMessage());
            installation.setLastOperationMessage(result.getMessage());
            installation.setLastOperationTime(LocalDateTime.now());
            installation.setInstallStatus(result.isSuccess() ? SoftInstallationStatus.UNINSTALLED.name() : SoftInstallationStatus.FAILED.name());
            installation.setRuntimeStatus(SoftRuntimeStatus.STOPPED.name());
            installationMapper.updateById(installation);
            if (result.isSuccess()) {
                serverServiceService.deleteBySoftInstallationId(installation.getSoftInstallationId());
            }
            reporter.finish(result.isSuccess(), result.getMessage(), result.getMessage(), result.getOutput());
        } catch (Exception e) {
            installation.setInstallStatus(SoftInstallationStatus.FAILED.name());
            installation.setLastOperationTime(LocalDateTime.now());
            installation.setLastOperationMessage(e.getMessage());
            installationMapper.updateById(installation);
            reporter.finish(false, "卸载执行失败", e.getMessage(), reporter.aggregatedOutput());
        }
    }

    private SoftOperationTicket serviceOperation(Integer installationId, SoftOperationType type) {
        SoftExecutionContext context = buildContext(requiredInstallation(installationId));
        context.getInstallation().setLastOperationTime(LocalDateTime.now());
        context.getInstallation().setLastOperationMessage(type.name() + " 任务已接收");
        installationMapper.updateById(context.getInstallation());
        SoftOperationLog operationLog = createOperationLog(context.getInstallation(), context.getVersion(), context.getTarget(), type, type.name() + " 任务已接收");
        submitOperation(() -> executeServiceOperation(context, operationLog, type));
        return buildTicket(operationLog);
    }

    private void executeServiceOperation(SoftExecutionContext context, SoftOperationLog operationLog, SoftOperationType type) {
        SoftInstallation installation = context.getInstallation();
        SoftExecutionReporter reporter = createReporter(operationLog, installation.getSoftInstallationId(), context);
        try {
            reporter.progress(SoftOperationStage.EXECUTE, "执行服务操作", type.name());
            SoftOperationResult result = runServiceOperation(context, type);
            operationLog.setOperationCommand(result.getCommand());
            appendOperationOutput(reporter, result);
            if (type == SoftOperationType.START) {
                completeStartOperation(context, installation, reporter, result);
                return;
            }
            reporter.progress(SoftOperationStage.VERIFY, "校验服务结果", result.getMessage());
            installation.setLastOperationTime(LocalDateTime.now());
            installation.setLastOperationMessage(result.getMessage());
            if (type == SoftOperationType.STOP && result.isSuccess()) {
                installation.setRuntimeStatus(SoftRuntimeStatus.STOPPED.name());
            } else if (type == SoftOperationType.STATUS) {
                installation.setRuntimeStatus(resolveRuntimeStatus(result.getOutput()).name());
            }
            installationMapper.updateById(installation);
            reporter.finish(result.isSuccess(), result.getMessage(), result.getMessage(), result.getOutput());
        } catch (Exception e) {
            installation.setLastOperationTime(LocalDateTime.now());
            installation.setLastOperationMessage(e.getMessage());
            installationMapper.updateById(installation);
            reporter.finish(false, "服务操作失败", e.getMessage(), reporter.aggregatedOutput());
        }
    }

    private SoftOperationResult runServiceOperation(SoftExecutionContext context, SoftOperationType type) throws Exception {
        Integer installationId = context.getInstallation() == null ? null : context.getInstallation().getSoftInstallationId();
        if (installationId != null) {
            try {
                ServerServiceCommandResult managedResult = serverServiceService.operateBySoftInstallationId(
                        installationId,
                        toServerServiceOperationType(type)
                );
                return SoftOperationResult.builder()
                        .success(managedResult.isSuccess())
                        .accepted(true)
                        .finished(true)
                        .exitCode(managedResult.getExitCode())
                        .command("SERVER_SERVICE:" + type.name())
                        .message(managedResult.getMessage())
                        .output(managedResult.getOutput())
                        .build();
            } catch (IllegalStateException ignored) {
            }
        }
        return switch (type) {
            case START -> serviceManager.start(context);
            case STOP -> serviceManager.stop(context);
            case RESTART -> serviceManager.restart(context);
            case SERVICE_REGISTER -> serviceManager.register(context);
            case SERVICE_UNREGISTER -> serviceManager.unregister(context);
            case STATUS -> serviceManager.status(context);
            default -> throw new IllegalStateException("不支持的服务操作: " + type);
        };
    }

    private ServerServiceOperationType toServerServiceOperationType(SoftOperationType type) {
        return switch (type) {
            case START -> ServerServiceOperationType.START;
            case STOP -> ServerServiceOperationType.STOP;
            case RESTART -> ServerServiceOperationType.RESTART;
            case SERVICE_REGISTER -> ServerServiceOperationType.REGISTER;
            case SERVICE_UNREGISTER -> ServerServiceOperationType.UNREGISTER;
            case STATUS -> ServerServiceOperationType.STATUS;
            default -> throw new IllegalStateException("不支持的服务映射: " + type);
        };
    }

    private void syncManagedServerService(SoftExecutionContext context) {
        try {
            serverServiceService.saveManagedService(buildManagedServiceRequest(context));
        } catch (IllegalStateException ignored) {
        }
    }

    private ServerServiceUpsertRequest buildManagedServiceRequest(SoftExecutionContext context) {
        SoftInstallation installation = context.getInstallation();
        SoftTarget target = context.getTarget();
        SoftPackageVersion version = context.getVersion();
        Map<String, Object> metadata = SoftJsons.toMap(target.getMetadataJson());
        ServerServiceUpsertRequest request = new ServerServiceUpsertRequest();
        Object serverId = metadata.get("serverId");
        if (serverId != null) {
            try {
                request.setServerId(Integer.parseInt(String.valueOf(serverId)));
            } catch (Exception ignored) {
            }
        }
        request.setServerType(target.getTargetType());
        request.setHost(target.getHost());
        request.setPort(target.getPort());
        request.setUsername(target.getUsername());
        request.setOsType(target.getOsType());
        request.setServiceName(installation.getServiceName());
        request.setServiceType(SoftCommandSupport.isWindows(target.getOsType()) ? "WINDOWS_SERVICE" : "SYSTEMD");
        request.setSoftPackageId(installation.getSoftPackageId());
        request.setSoftPackageVersionId(installation.getSoftPackageVersionId());
        request.setSoftInstallationId(installation.getSoftInstallationId());
        request.setInstallPath(installation.getInstallPath());
        request.setRegisterScript(resolveManagedServiceScript(context, "SERVICE_REGISTER_SCRIPT", version.getServiceRegisterScript()));
        request.setUnregisterScript(resolveManagedServiceScript(context, "SERVICE_UNREGISTER_SCRIPT", version.getServiceUnregisterScript()));
        request.setStartScript(resolveManagedServiceScript(context, "START_SCRIPT", version.getStartScript()));
        request.setStopScript(resolveManagedServiceScript(context, "STOP_SCRIPT", version.getStopScript()));
        request.setRestartScript(resolveManagedServiceScript(context, "RESTART_SCRIPT", version.getRestartScript()));
        request.setStatusScript(resolveManagedServiceScript(context, "STATUS_SCRIPT", version.getStatusScript()));
        request.setEnabled(Boolean.TRUE);
        request.setDescription("soft 安装实例关联的服务器服务");
        Map<String, Object> bindingMetadata = new LinkedHashMap<>();
        bindingMetadata.put("source", "soft-installation");
        bindingMetadata.put("serverId", request.getServerId());
        bindingMetadata.put("softTargetId", installation.getSoftTargetId());
        bindingMetadata.put("targetCode", target.getTargetCode());
        bindingMetadata.put("serverCode", metadata.get("serverCode"));
        request.setMetadataJson(SoftJsons.toJson(bindingMetadata));
        return request;
    }

    private String resolveManagedServiceScript(
            SoftExecutionContext context,
            String scriptCode,
            String versionScript
    ) {
        if (context.getRenderedScripts() != null) {
            String rendered = context.getRenderedScripts().get(scriptCode);
            if (rendered != null && !rendered.isBlank()) {
                return rendered;
            }
        }
        if (versionScript == null || versionScript.isBlank()) {
            return null;
        }
        return SoftCommandSupport.renderScript(versionScript, context, context.getInstallation().getInstallPath() + "/artifact.bin");
    }

    private void executeConfigWrite(
            SoftExecutionContext context,
            SoftConfigWriteRequest request,
            String resolvedConfigPath,
            SoftOperationLog operationLog
    ) {
        SoftInstallation installation = context.getInstallation();
        SoftExecutionReporter reporter = createReporter(operationLog, installation.getSoftInstallationId(), context);
        try {
            reporter.progress(SoftOperationStage.BACKUP, "写入前备份完成", resolvedConfigPath);
            reporter.progress(SoftOperationStage.WRITE, "写入配置文件", resolvedConfigPath);
            configManager.write(context, resolvedConfigPath, request.getConfigContent());
            reporter.progress(SoftOperationStage.VERIFY, "校验配置写入结果", resolvedConfigPath);
            installation.setLastOperationTime(LocalDateTime.now());
            installation.setLastOperationMessage("配置写入成功");
            installationMapper.updateById(installation);
            reporter.finish(true, "配置写入成功", request.getOperationRemark(), request.getConfigContent());
        } catch (Exception e) {
            installation.setLastOperationTime(LocalDateTime.now());
            installation.setLastOperationMessage(e.getMessage());
            installationMapper.updateById(installation);
            reporter.finish(false, "配置写入失败", e.getMessage(), reporter.aggregatedOutput());
        }
    }

    private SoftExecutionReporter createReporter(
            SoftOperationLog operationLog,
            Integer installationId,
            SoftExecutionContext context
    ) {
        return new SoftExecutionReporter(
                realtimePublisher,
                operationLog,
                installationId,
                context.getSoftPackage(),
                context.getVersion(),
                context.getTarget(),
                log -> operationLogMapper.updateById(log)
        );
    }

    private SoftCommandObserver createCommandObserver(SoftExecutionReporter reporter) {
        return new SoftCommandObserver() {
            @Override
            public void onStage(SoftOperationStage stage, String message, String detail) {
                reporter.progress(stage, message, detail);
            }

            @Override
            public void onStdout(String line) {
                reporter.line(line);
            }

            @Override
            public void onStderr(String line) {
                reporter.line(line);
            }
        };
    }

    private void publishRuntimeLog(SoftExecutionContext context, String line) {
        realtimePublisher.publish(
                SoftSocketEvents.RUNTIME_LOG,
                context.getInstallation().getSoftInstallationId(),
                SoftRealtimePayload.builder()
                        .installationId(context.getInstallation().getSoftInstallationId())
                        .operationType(SoftOperationType.LOG_TAIL.name())
                        .status("RUNNING")
                        .line(line)
                        .targetType(context.getTarget().getTargetType())
                        .packageCode(context.getSoftPackage().getPackageCode())
                        .versionCode(context.getVersion().getVersionCode())
                        .finished(false)
                        .build()
        );
    }

    private void submitOperation(Runnable task) {
        operationExecutor.submit(task);
    }

    private SoftOperationLog createOperationLog(
            SoftInstallation installation,
            SoftPackageVersion version,
            SoftTarget target,
            SoftOperationType operationType,
            String acceptedMessage
    ) {
        SoftOperationLog log = new SoftOperationLog();
        log.setSoftInstallationId(installation.getSoftInstallationId());
        log.setSoftPackageVersionId(version.getSoftPackageVersionId());
        log.setSoftTargetId(target.getSoftTargetId());
        log.setOperationType(operationType.name());
        log.setOperationStatus("RUNNING");
        log.setOperationStage(SoftOperationStage.PREPARE.name());
        log.setProgressPercent(SoftOperationStage.PREPARE.progressPercent());
        log.setOperationMessage(acceptedMessage);
        log.setStartTime(LocalDateTime.now());
        operationLogMapper.insert(log);
        return log;
    }

    private SoftOperationTicket buildTicket(SoftOperationLog operationLog) {
        return SoftOperationTicket.builder()
                .operationId(operationLog.getSoftOperationLogId())
                .installationId(operationLog.getSoftInstallationId())
                .operationType(operationLog.getOperationType())
                .operationStatus(operationLog.getOperationStatus())
                .acceptedAt(LocalDateTime.now())
                .build();
    }

    private SoftInstallExecutor requiredInstallExecutor(String targetType) {
        return installExecutors.stream()
                .filter(item -> item.supports(targetType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到安装执行器: " + targetType));
    }

    private SoftExecutionContext buildContext(SoftInstallation installation) {
        SoftPackage softPackage = requiredPackage(installation.getSoftPackageId());
        SoftPackageVersion version = requiredVersion(installation.getSoftPackageVersionId());
        SoftTarget target = requiredTarget(installation.getSoftTargetId());
        SoftGuidePreviewResponse guidePreview = softGuideDefinitionService.resolveGuideAssets(
                softPackage,
                version,
                target,
                installation.getInstallationName(),
                installation.getInstallPath(),
                installation.getServiceName(),
                SoftJsons.toMap(installation.getInstallOptionsJson()),
                SoftJsons.toMap(installation.getServiceOptionsJson()),
                SoftJsons.toMap(installation.getConfigOptionsJson())
        );
        version.setLogPaths(guidePreview.getLogPaths());
        version.setConfigPaths(guidePreview.getConfigPaths());
        return SoftExecutionContext.builder()
                .softPackage(softPackage)
                .version(version)
                .target(target)
                .installation(installation)
                .installOptions(SoftJsons.toMap(installation.getInstallOptionsJson()))
                .serviceOptions(SoftJsons.toMap(installation.getServiceOptionsJson()))
                .configOptions(SoftJsons.toMap(installation.getConfigOptionsJson()))
                .resolvedVariables(guidePreview.getResolvedVariables())
                .renderedScripts(guidePreview.getRenderedScripts())
                .renderedConfigFiles(guidePreview.getRenderedConfigFiles())
                .build();
    }

    private void hydrateInstallations(List<SoftInstallation> installations) {
        if (installations.isEmpty()) {
            return;
        }
        Map<Integer, SoftPackage> packages = new HashMap<>();
        Map<Integer, SoftPackageVersion> versions = new HashMap<>();
        Map<Integer, SoftTarget> targets = new HashMap<>();
        installations.forEach(item -> {
            packages.computeIfAbsent(item.getSoftPackageId(), this::requiredPackage);
            versions.computeIfAbsent(item.getSoftPackageVersionId(), this::requiredVersion);
            targets.computeIfAbsent(item.getSoftTargetId(), this::requiredTarget);
        });
        installations.replaceAll(item -> enrichInstallation(item, packages.get(item.getSoftPackageId()), versions.get(item.getSoftPackageVersionId()), targets.get(item.getSoftTargetId())));
    }

    private SoftInstallation enrichInstallation(SoftInstallation installation, SoftPackage softPackage, SoftPackageVersion version, SoftTarget target) {
        installation.setPackageName(softPackage == null ? null : softPackage.getPackageName());
        installation.setVersionName(version == null ? null : version.getVersionName());
        installation.setTargetName(target == null ? null : target.getTargetName());
        return installation;
    }

    private SoftPackage upsertPackage(SoftPackage value) {
        normalizePackage(value);
        var query = Wrappers.<SoftPackage>lambdaQuery()
                .eq(SoftPackage::getSoftRepositoryId, value.getSoftRepositoryId())
                .eq(SoftPackage::getPackageCode, value.getPackageCode());
        if (value.getOsType() == null) {
            query.isNull(SoftPackage::getOsType);
        } else {
            query.eq(SoftPackage::getOsType, value.getOsType());
        }
        if (value.getArchitecture() == null) {
            query.isNull(SoftPackage::getArchitecture);
        } else {
            query.eq(SoftPackage::getArchitecture, value.getArchitecture());
        }
        SoftPackage current = packageMapper.selectOne(query);
        if (current == null) {
            packageMapper.insert(value);
            hydratePackage(value, null);
            return value;
        }
        value.setSoftPackageId(current.getSoftPackageId());
        packageMapper.updateById(value);
        hydratePackage(value, null);
        return value;
    }

    private void reconcileRepositoryPackages(Integer repositoryId, Set<String> packageKeys, Set<String> versionKeys) {
        List<SoftPackage> existingPackages = packageMapper.selectList(Wrappers.<SoftPackage>lambdaQuery()
                .eq(SoftPackage::getSoftRepositoryId, repositoryId));
        if (existingPackages.isEmpty()) {
            return;
        }
        List<Integer> packageIds = existingPackages.stream()
                .map(SoftPackage::getSoftPackageId)
                .filter(Objects::nonNull)
                .toList();
        Map<Integer, SoftPackage> packageById = new HashMap<>();
        existingPackages.forEach(item -> packageById.put(item.getSoftPackageId(), item));
        List<SoftPackageVersion> existingVersions = packageIds.isEmpty()
                ? List.of()
                : packageVersionMapper.selectList(Wrappers.<SoftPackageVersion>lambdaQuery()
                .in(SoftPackageVersion::getSoftPackageId, packageIds));

        for (SoftPackageVersion version : existingVersions) {
            SoftPackage softPackage = packageById.get(version.getSoftPackageId());
            if (softPackage == null) {
                continue;
            }
            String versionKey = packageIdentity(softPackage) + "#" + normalizeText(version.getVersionCode());
            if (versionKeys.contains(versionKey)) {
                continue;
            }
            long installationCount = installationMapper.selectCount(Wrappers.<SoftInstallation>lambdaQuery()
                    .eq(SoftInstallation::getSoftPackageVersionId, version.getSoftPackageVersionId()));
            if (installationCount == 0) {
                packageVersionMapper.deleteById(version.getSoftPackageVersionId());
            }
        }

        for (SoftPackage softPackage : existingPackages) {
            if (packageKeys.contains(packageIdentity(softPackage))) {
                continue;
            }
            long installationCount = installationMapper.selectCount(Wrappers.<SoftInstallation>lambdaQuery()
                    .eq(SoftInstallation::getSoftPackageId, softPackage.getSoftPackageId()));
            if (installationCount > 0) {
                continue;
            }
            packageVersionMapper.delete(Wrappers.<SoftPackageVersion>lambdaQuery()
                    .eq(SoftPackageVersion::getSoftPackageId, softPackage.getSoftPackageId()));
            packageMapper.deleteById(softPackage.getSoftPackageId());
        }
    }

    private SoftPackageVersion upsertVersion(SoftPackageVersion value) {
        normalizeVersion(value);
        value.setPackageName(Optional.ofNullable(normalizeText(value.getPackageName())).orElse(normalizeText(value.getPackageCode())));
        LambdaQueryWrapper<SoftPackageVersion> query = Wrappers.<SoftPackageVersion>lambdaQuery()
                .eq(SoftPackageVersion::getSoftPackageId, value.getSoftPackageId())
                .eq(SoftPackageVersion::getVersionCode, value.getVersionCode());
        if (value.getPackageName() == null) {
            query.isNull(SoftPackageVersion::getPackageName);
        } else {
            query.eq(SoftPackageVersion::getPackageName, value.getPackageName());
        }
        if (value.getOsType() == null) {
            query.isNull(SoftPackageVersion::getOsType);
        } else {
            query.eq(SoftPackageVersion::getOsType, value.getOsType());
        }
        if (value.getArchitecture() == null) {
            query.isNull(SoftPackageVersion::getArchitecture);
        } else {
            query.eq(SoftPackageVersion::getArchitecture, value.getArchitecture());
        }
        SoftPackageVersion current = packageVersionMapper.selectOne(query.last("limit 1"));
        if (current == null) {
            packageVersionMapper.insert(value);
            return value;
        }
        value.setSoftPackageVersionId(current.getSoftPackageVersionId());
        packageVersionMapper.updateById(value);
        return value;
    }

    private void hydrateVersion(SoftPackageVersion version) {
        version.setDownloadUrls(SoftJsons.toStringList(version.getDownloadUrlsJson()));
        version.setLogPaths(SoftJsons.toStringList(version.getLogPathsJson()));
        version.setConfigPaths(SoftJsons.toStringList(version.getConfigPathsJson()));
        version.setCapabilityFlags(SoftJsons.toStringList(version.getCapabilityFlagsJson()));
    }

    private void hydratePackages(List<SoftPackage> packages) {
        if (packages == null || packages.isEmpty()) {
            return;
        }
        List<Integer> packageIds = packages.stream()
                .map(SoftPackage::getSoftPackageId)
                .filter(Objects::nonNull)
                .toList();
        Map<Integer, List<SoftPackageVersion>> versionGroups = new HashMap<>();
        if (!packageIds.isEmpty()) {
            List<SoftPackageVersion> versions = packageVersionMapper.selectList(Wrappers.<SoftPackageVersion>lambdaQuery()
                    .in(SoftPackageVersion::getSoftPackageId, packageIds)
                    .orderByDesc(SoftPackageVersion::getUpdateTime, SoftPackageVersion::getCreateTime));
            versions.forEach(version -> versionGroups.computeIfAbsent(version.getSoftPackageId(), key -> new ArrayList<>()).add(version));
        }
        packages.forEach(item -> hydratePackage(item, versionGroups.get(item.getSoftPackageId())));
    }

    private void hydratePackage(SoftPackage softPackage, List<SoftPackageVersion> versions) {
        if (softPackage == null) {
            return;
        }
        softPackage.setSoftwareKey(resolvePackageSoftwareKey(softPackage, versions));
    }

    private String resolvePackageSoftwareKey(SoftPackage softPackage, List<SoftPackageVersion> versions) {
        List<SoftPackageVersion> candidates = versions;
        if (candidates == null && softPackage.getSoftPackageId() != null) {
            candidates = packageVersionMapper.selectList(Wrappers.<SoftPackageVersion>lambdaQuery()
                    .eq(SoftPackageVersion::getSoftPackageId, softPackage.getSoftPackageId())
                    .orderByDesc(SoftPackageVersion::getUpdateTime, SoftPackageVersion::getCreateTime));
        }
        for (SoftPackageVersion version : Optional.ofNullable(candidates).orElseGet(List::of)) {
            String softwareKey = normalizeSoftwareKey(readMetadataText(version.getMetadataJson(), "softwareKey"));
            if (softwareKey != null) {
                return softwareKey;
            }
        }
        String softwareKey = normalizeSoftwareKey(softPackage.getPackageCode());
        if (softwareKey != null) {
            return softwareKey;
        }
        return normalizeSoftwareKey(softPackage.getPackageName());
    }

    private String resolveLogPath(SoftExecutionContext context, String logPath) {
        if (logPath != null && !logPath.isBlank()) {
            return logPath;
        }
        return context.getVersion().getLogPaths().stream().findFirst().orElse(null);
    }

    private String resolveConfigPath(SoftExecutionContext context, String configPath) {
        if (configPath != null && !configPath.isBlank()) {
            return configPath;
        }
        return context.getVersion().getConfigPaths().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("未配置可管理配置文件"));
    }

    private SoftRuntimeStatus resolveRuntimeStatus(String output) {
        String text = Objects.toString(output, "").toLowerCase();
        if (text.isBlank()) {
            return SoftRuntimeStatus.UNKNOWN;
        }
        if (text.contains("running") || text.contains("active")) {
            return SoftRuntimeStatus.RUNNING;
        }
        if (text.contains("stop") || text.contains("inactive") || text.contains("not running") || text.contains("dead")) {
            return SoftRuntimeStatus.STOPPED;
        }
        return SoftRuntimeStatus.RUNNING;
    }

    private void completeStartOperation(
            SoftExecutionContext context,
            SoftInstallation installation,
            SoftExecutionReporter reporter,
            SoftOperationResult startResult
    ) throws Exception {
        SoftOperationResult statusResult = verifyStarted(context, reporter);
        SoftRuntimeStatus runtimeStatus = resolveRuntimeStatus(statusResult.getOutput());
        boolean running = statusResult.isSuccess() && runtimeStatus == SoftRuntimeStatus.RUNNING;
        installation.setLastOperationTime(LocalDateTime.now());
        installation.setLastOperationMessage(running ? "启动成功" : "启动校验失败");
        installation.setRuntimeStatus(running ? SoftRuntimeStatus.RUNNING.name() : runtimeStatus.name());
        installationMapper.updateById(installation);
        reporter.finish(
                running,
                running ? "启动成功" : "启动校验失败",
                statusResult.getMessage(),
                mergeOutputs(startResult, statusResult)
        );
    }

    private SoftOperationResult verifyStarted(SoftExecutionContext context, SoftExecutionReporter reporter) throws Exception {
        SoftOperationResult latest = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            latest = runServiceOperation(context, SoftOperationType.STATUS);
            appendOperationOutput(reporter, latest);
            SoftRuntimeStatus runtimeStatus = resolveRuntimeStatus(latest.getOutput());
            if (latest.isSuccess() && runtimeStatus == SoftRuntimeStatus.RUNNING) {
                reporter.progress(SoftOperationStage.VERIFY, "校验服务结果", latest.getMessage());
                return latest;
            }
            if (attempt < 7) {
                Thread.sleep(1500L);
            }
        }
        reporter.progress(SoftOperationStage.VERIFY, "校验服务结果", latest == null ? "未获取到状态结果" : latest.getMessage());
        return latest == null
                ? SoftOperationResult.builder()
                .success(false)
                .accepted(false)
                .finished(true)
                .message("未获取到状态结果")
                .output("")
                .build()
                : latest;
    }

    private void appendOperationOutput(SoftExecutionReporter reporter, SoftOperationResult result) {
        if (result == null || result.getOutput() == null || result.getOutput().isBlank()) {
            return;
        }
        for (String line : result.getOutput().split("\\R")) {
            if (!line.isBlank()) {
                reporter.line(line);
            }
        }
    }

    private String mergeOutputs(SoftOperationResult first, SoftOperationResult second) {
        StringBuilder builder = new StringBuilder();
        appendOutput(builder, first == null ? null : first.getOutput());
        appendOutput(builder, second == null ? null : second.getOutput());
        return builder.toString().trim();
    }

    private void appendOutput(StringBuilder builder, String output) {
        if (output == null || output.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(System.lineSeparator());
        }
        builder.append(output.trim());
    }

    private LogSnapshot readRecentLogs(SoftExecutionContext context, String preferredLogPath, int lines) throws Exception {
        String resolvedLogPath = resolveLogPath(context, preferredLogPath);
        if (resolvedLogPath != null && !resolvedLogPath.isBlank()) {
            List<String> content = logStreamProvider.readRecent(context, resolvedLogPath, lines);
            if (!content.isEmpty() || preferredLogPath != null) {
                return new LogSnapshot(resolvedLogPath, content);
            }
        }
        List<String> candidates = Optional.ofNullable(context.getVersion().getLogPaths()).orElseGet(List::of);
        LogSnapshot fallback = resolvedLogPath == null ? null : new LogSnapshot(resolvedLogPath, List.of());
        for (String candidate : candidates) {
            if (Objects.equals(candidate, resolvedLogPath)) {
                continue;
            }
            try {
                List<String> content = logStreamProvider.readRecent(context, candidate, lines);
                if (!content.isEmpty()) {
                    return new LogSnapshot(candidate, content);
                }
                if (fallback == null) {
                    fallback = new LogSnapshot(candidate, content);
                }
            } catch (Exception ignored) {
            }
        }
        return fallback == null ? new LogSnapshot(resolvedLogPath, List.of()) : fallback;
    }

    private record LogSnapshot(String logPath, List<String> lines) {
    }

    private String sanitizeServiceName(String packageCode) {
        return packageCode == null ? "soft-service" : packageCode.replaceAll("[^a-zA-Z0-9_-]", "-");
    }

    private String stringValue(Object value, String fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return String.valueOf(value);
    }

    private boolean shouldAutoRegisterService(SoftExecutionContext context) {
        Map<String, Object> serviceOptions = context.getServiceOptions();
        if (serviceOptions == null || serviceOptions.isEmpty()) {
            return false;
        }
        Object value = serviceOptions.get("registerService");
        if (value == null) {
            value = serviceOptions.get("autoRegisterService");
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private SoftRepository requiredRepository(Integer id) {
        SoftRepository repository = repositoryMapper.selectById(id);
        if (repository == null) {
            throw new IllegalStateException("软件仓库不存在: " + id);
        }
        hydrateRepository(repository);
        return repository;
    }

    private void normalizeRepository(SoftRepository repository) {
        repository.setRepositoryUrl(normalizeText(repository.getRepositoryUrl()));
        repository.setLocalDirectory(normalizeText(repository.getLocalDirectory()));
        repository.setSyncCron(normalizeText(repository.getSyncCron()));
        repository.setSyncConfig(normalizeText(repository.getSyncConfig()));
        repository.setAuthType(normalizeText(repository.getAuthType()));
        repository.setUsername(normalizeText(repository.getUsername()));
        repository.setPassword(normalizeText(repository.getPassword()));
        repository.setToken(normalizeText(repository.getToken()));
        List<SoftRepositorySource> sourceConfigs = normalizeSourceConfigs(repository.getSourceConfigs());
        repository.setSourceConfigs(sourceConfigs);
        repository.setSourceConfigsJson(sourceConfigs.isEmpty() ? null : SoftJsons.toJson(sourceConfigs));
    }

    private void hydrateRepository(SoftRepository repository) {
        if (repository == null) {
            return;
        }
        List<SoftRepositorySource> sources = loadRepositorySourceItems(repository.getSoftRepositoryId());
        if (sources.isEmpty()) {
            sources = readSourceConfigs(repository.getSourceConfigsJson());
        }
        repository.setSourceConfigs(sources);
    }

    private List<SoftRepositorySource> readSourceConfigs(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<SoftRepositorySource> sources = SoftJsons.mapper().readerForListOf(SoftRepositorySource.class).readValue(json);
            return normalizeSourceConfigs(sources);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private List<SoftRepositorySource> normalizeSourceConfigs(List<SoftRepositorySource> sources) {
        if (sources == null || sources.isEmpty()) {
            return new ArrayList<>();
        }
        List<SoftRepositorySource> normalized = new ArrayList<>();
        Set<String> deduplicated = new java.util.LinkedHashSet<>();
        for (SoftRepositorySource source : sources) {
            SoftRepositorySource normalizedSource = normalizeSourceConfig(source);
            if (normalizedSource == null) {
                continue;
            }
            String identity = Objects.toString(normalizedSource.getSourceKind(), "")
                    + "#" + Objects.toString(normalizedSource.getSourceType(), "")
                    + "#" + Objects.toString(normalizedSource.getSourceUrl(), "")
                    + "#" + Objects.toString(normalizedSource.getLocalDirectory(), "");
            if (!deduplicated.add(identity)) {
                continue;
            }
            normalized.add(normalizedSource);
        }
        return normalized;
    }

    private SoftRepositorySource normalizeSourceConfig(SoftRepositorySource source) {
        if (source == null) {
            return null;
        }
        String sourceType = Optional.ofNullable(normalizeText(source.getSourceType())).orElse("HTTP_JSON");
        String sourceUrl = normalizeText(source.getSourceUrl());
        String localDirectory = normalizeText(source.getLocalDirectory());
        if (sourceUrl == null && localDirectory == null) {
            return null;
        }
        return SoftRepositorySource.builder()
                .softRepositorySourceId(source.getSoftRepositorySourceId())
                .sourceName(normalizeText(source.getSourceName()))
                .sourceKind(normalizeSourceKind(source.getSourceKind(), sourceType))
                .sourceType(sourceType)
                .sourceUrl(sourceUrl)
                .localDirectory(localDirectory)
                .enabled(source.getEnabled() == null ? Boolean.TRUE : source.getEnabled())
                .sourceConfig(normalizeText(source.getSourceConfig()))
                .build();
    }

    private List<SoftRepositorySource> loadRepositorySourceItems(Integer repositoryId) {
        if (repositoryId == null) {
            return new ArrayList<>();
        }
        return repositorySourceMapper.selectList(Wrappers.<SoftRepositorySourceEntity>lambdaQuery()
                        .eq(SoftRepositorySourceEntity::getSoftRepositoryId, repositoryId)
                        .orderByAsc(SoftRepositorySourceEntity::getSortOrder, SoftRepositorySourceEntity::getSoftRepositorySourceId))
                .stream()
                .map(this::toRepositorySourceModel)
                .toList();
    }

    private SoftRepositorySourceEntity requiredRepositorySource(Integer repositoryId, Integer sourceId) {
        SoftRepositorySourceEntity source = repositorySourceMapper.selectById(sourceId);
        if (source == null || !Objects.equals(source.getSoftRepositoryId(), repositoryId)) {
            throw new IllegalStateException("仓库来源不存在: " + sourceId);
        }
        return source;
    }

    private void replaceRepositorySourceRows(Integer repositoryId, List<SoftRepositorySource> sourceConfigs) {
        repositorySourceMapper.delete(Wrappers.<SoftRepositorySourceEntity>lambdaQuery()
                .eq(SoftRepositorySourceEntity::getSoftRepositoryId, repositoryId));
        List<SoftRepositorySource> normalized = normalizeSourceConfigs(sourceConfigs);
        int sort = 10;
        for (SoftRepositorySource source : normalized) {
            SoftRepositorySourceEntity row = toRepositorySourceEntity(repositoryId, source, sort);
            repositorySourceMapper.insert(row);
            sort += 10;
        }
    }

    private void syncRepositorySourcesJson(Integer repositoryId) {
        SoftRepository repository = repositoryMapper.selectById(repositoryId);
        if (repository == null) {
            return;
        }
        List<SoftRepositorySource> sources = loadRepositorySourceItems(repositoryId);
        repository.setSourceConfigs(sources);
        repository.setSourceConfigsJson(sources.isEmpty() ? null : SoftJsons.toJson(sources));
        repositoryMapper.updateById(repository);
    }

    private SoftRepositorySourceEntity toRepositorySourceEntity(Integer repositoryId,
                                                                SoftRepositorySource source,
                                                                Integer fallbackSortOrder) {
        SoftRepositorySource normalized = normalizeSourceConfig(source);
        if (normalized == null) {
            throw new IllegalStateException("来源配置不能为空");
        }
        SoftRepositorySourceEntity entity = new SoftRepositorySourceEntity();
        entity.setSoftRepositoryId(repositoryId);
        entity.setSourceName(normalized.getSourceName());
        entity.setSourceKind(normalized.getSourceKind());
        entity.setSourceType(normalized.getSourceType());
        entity.setSourceUrl(normalized.getSourceUrl());
        entity.setLocalDirectory(normalized.getLocalDirectory());
        entity.setEnabled(normalized.getEnabled() == null ? Boolean.TRUE : normalized.getEnabled());
        entity.setSourceConfig(normalized.getSourceConfig());
        entity.setSortOrder(fallbackSortOrder);
        return entity;
    }

    private SoftRepositorySource toRepositorySourceModel(SoftRepositorySourceEntity entity) {
        return SoftRepositorySource.builder()
                .softRepositorySourceId(entity.getSoftRepositorySourceId())
                .sourceName(entity.getSourceName())
                .sourceKind(normalizeSourceKind(entity.getSourceKind(), entity.getSourceType()))
                .sourceType(entity.getSourceType())
                .sourceUrl(entity.getSourceUrl())
                .localDirectory(entity.getLocalDirectory())
                .enabled(entity.getEnabled() == null ? Boolean.TRUE : entity.getEnabled())
                .sourceConfig(entity.getSourceConfig())
                .build();
    }

    private List<SoftRepositorySource> resolveRepositorySources(SoftRepository repository) {
        List<SoftRepositorySource> sources = new ArrayList<>();
        SoftRepositorySource primary = buildPrimarySource(repository);
        if (primary != null) {
            sources.add(primary);
        }
        sources.addAll(normalizeSourceConfigs(repository.getSourceConfigs()));
        return sources;
    }

    private SoftRepositorySource buildPrimarySource(SoftRepository repository) {
        String repositoryType = normalizeText(repository.getRepositoryType());
        if (repositoryType == null) {
            return null;
        }
        String repositoryUrl = normalizeText(repository.getRepositoryUrl());
        String localDirectory = normalizeText(repository.getLocalDirectory());
        if ("MANUAL".equalsIgnoreCase(repositoryType) && repositoryUrl == null && localDirectory == null) {
            return null;
        }
        if (requiresRemoteRepositoryUrl(repositoryType) && repositoryUrl == null) {
            return null;
        }
        if ("LOCAL_DIR".equalsIgnoreCase(repositoryType) && localDirectory == null) {
            return null;
        }
        return SoftRepositorySource.builder()
                .sourceName(repository.getRepositoryName())
                .sourceKind(normalizeSourceKind(null, repositoryType))
                .sourceType(repositoryType)
                .sourceUrl(repositoryUrl)
                .localDirectory(localDirectory)
                .enabled(Boolean.TRUE)
                .sourceConfig(repository.getSyncConfig())
                .build();
    }

    private SoftRepositorySourceSearchItem buildRepositorySourceSearchItem(
            SoftRepository repository,
            SoftRepositorySource source,
            boolean primarySource
    ) {
        String sourceAddress = normalizeText(source.getSourceUrl());
        if (sourceAddress == null) {
            sourceAddress = normalizeText(source.getLocalDirectory());
        }
        return SoftRepositorySourceSearchItem.builder()
                .softRepositorySourceId(source.getSoftRepositorySourceId())
                .repositoryId(repository.getSoftRepositoryId())
                .repositoryName(repository.getRepositoryName())
                .repositoryCode(repository.getRepositoryCode())
                .repositoryType(repository.getRepositoryType())
                .primarySource(primarySource)
                .sourceName(source.getSourceName())
                .sourceKind(source.getSourceKind())
                .sourceType(source.getSourceType())
                .sourceUrl(source.getSourceUrl())
                .localDirectory(source.getLocalDirectory())
                .sourceAddress(sourceAddress)
                .enabled(source.getEnabled() == null ? Boolean.TRUE : source.getEnabled())
                .build();
    }

    private boolean matchesSourceKeyword(SoftRepositorySourceSearchItem item, String keywordLower) {
        if (keywordLower == null) {
            return true;
        }
        return containsKeyword(item.getRepositoryName(), keywordLower)
                || containsKeyword(item.getRepositoryCode(), keywordLower)
                || containsKeyword(item.getRepositoryType(), keywordLower)
                || containsKeyword(item.getSourceName(), keywordLower)
                || containsKeyword(item.getSourceKind(), keywordLower)
                || containsKeyword(item.getSourceType(), keywordLower)
                || containsKeyword(item.getSourceUrl(), keywordLower)
                || containsKeyword(item.getLocalDirectory(), keywordLower)
                || containsKeyword(item.getSourceAddress(), keywordLower);
    }

    private boolean containsKeyword(String value, String keywordLower) {
        if (value == null || keywordLower == null) {
            return false;
        }
        return value.toLowerCase().contains(keywordLower);
    }

    private String normalizeSourceKind(String sourceKind, String sourceType) {
        String normalized = normalizeText(sourceKind);
        if (normalized != null) {
            normalized = normalized.toUpperCase();
            if ("LINUX_DEFAULT".equals(normalized)
                    || "LOCAL_REPOSITORY".equals(normalized)
                    || "THIRD_PARTY".equals(normalized)) {
                return normalized;
            }
        }
        String type = normalizeText(sourceType);
        if (type == null) {
            return "THIRD_PARTY";
        }
        if ("LOCAL_DIR".equalsIgnoreCase(type)) {
            return "LOCAL_REPOSITORY";
        }
        if ("RPM_REPO".equalsIgnoreCase(type)) {
            return "LINUX_DEFAULT";
        }
        return "THIRD_PARTY";
    }

    private String normalizeInstallMode(String installMode, String sourceKind) {
        String normalized = normalizeText(installMode);
        if (normalized != null) {
            normalized = normalized.toUpperCase();
            if ("PKG_MANAGER".equals(normalized)
                    || "LOCAL_UPLOAD".equals(normalized)
                    || "REMOTE_DOWNLOAD".equals(normalized)) {
                return normalized;
            }
        }
        return switch (normalizeSourceKind(sourceKind, null)) {
            case "LOCAL_REPOSITORY" -> "LOCAL_UPLOAD";
            case "LINUX_DEFAULT" -> "PKG_MANAGER";
            default -> "REMOTE_DOWNLOAD";
        };
    }

    private SoftRepositoryPackageSearchVersion toRepositoryPackageSearchVersion(SoftPackage softPackage,
                                                                                SoftPackageVersion version) {
        String resolvedPackageName = Optional.ofNullable(normalizeText(version.getPackageName()))
                .orElse(normalizeText(softPackage.getPackageName()));
        String resolvedOsType = Optional.ofNullable(SoftArtifactRepositorySupport.normalizeOsType(version.getOsType()))
                .orElse(SoftArtifactRepositorySupport.normalizeOsType(softPackage.getOsType()));
        String resolvedArchitecture = Optional.ofNullable(SoftArtifactRepositorySupport.normalizeArchitecture(version.getArchitecture()))
                .orElse(SoftArtifactRepositorySupport.normalizeArchitecture(softPackage.getArchitecture()));
        return SoftRepositoryPackageSearchVersion.builder()
                .softPackageVersionId(version.getSoftPackageVersionId())
                .versionCode(version.getVersionCode())
                .versionName(version.getVersionName())
                .packageName(resolvedPackageName)
                .osType(resolvedOsType)
                .architecture(resolvedArchitecture)
                .sourceKind(normalizeSourceKind(version.getSourceKind(), null))
                .installMode(normalizeInstallMode(version.getInstallMode(), version.getSourceKind()))
                .build();
    }

    private boolean matchesRepositoryPackageKeyword(SoftPackage softPackage,
                                                   SoftRepositoryPackageSearchVersion version,
                                                   String keywordLower) {
        if (keywordLower == null) {
            return true;
        }
        return containsKeyword(softPackage.getPackageCode(), keywordLower)
                || containsKeyword(softPackage.getPackageName(), keywordLower)
                || containsKeyword(version.getPackageName(), keywordLower)
                || containsKeyword(version.getVersionCode(), keywordLower)
                || containsKeyword(version.getVersionName(), keywordLower)
                || containsKeyword(version.getOsType(), keywordLower)
                || containsKeyword(version.getArchitecture(), keywordLower);
    }

    private boolean requiresRemoteRepositoryUrl(String repositoryType) {
        return "HTTP_JSON".equalsIgnoreCase(repositoryType)
                || "HTTP_DIR".equalsIgnoreCase(repositoryType)
                || "RPM_REPO".equalsIgnoreCase(repositoryType)
                || "MIRROR_REPO".equalsIgnoreCase(repositoryType);
    }

    private String resolveSyncStatus(int successCount, int failedCount) {
        if (failedCount == 0) {
            return "SUCCESS";
        }
        return successCount > 0 ? "PARTIAL_SUCCESS" : "FAILED";
    }

    private String buildRepositorySyncMessage(
            int successCount,
            int failedCount,
            int skippedCount,
            int packageCount,
            int versionCount,
            List<String> failureMessages
    ) {
        StringBuilder builder = new StringBuilder()
                .append("同步源成功 ").append(successCount)
                .append(" 个，失败 ").append(failedCount)
                .append(" 个，禁用/跳过 ").append(skippedCount)
                .append(" 个；软件 ").append(packageCount)
                .append(" 个，版本 ").append(versionCount).append(" 个");
        if (!failureMessages.isEmpty()) {
            builder.append("；失败详情：").append(String.join(" | ", failureMessages));
        }
        return builder.toString();
    }

    private String describeSource(SoftRepositorySource source) {
        if (source == null) {
            return "未知源";
        }
        String name = normalizeText(source.getSourceName());
        if (name != null) {
            return name;
        }
        if (source.getSourceUrl() != null && !source.getSourceUrl().isBlank()) {
            return source.getSourceUrl();
        }
        if (source.getLocalDirectory() != null && !source.getLocalDirectory().isBlank()) {
            return source.getLocalDirectory();
        }
        return Optional.ofNullable(source.getSourceType()).orElse("未知类型");
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeJsonText(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }
        try {
            return SoftJsons.mapper().writeValueAsString(SoftJsons.mapper().readTree(normalized));
        } catch (Exception ignored) {
            return normalized;
        }
    }

    private String normalizeSoftwareKey(String value) {
        return SoftArtifactRepositorySupport.normalizeSoftwareKey(normalizeText(value));
    }

    private Integer resolveCreateRepositoryId(Integer requestedRepositoryId) {
        if (requestedRepositoryId != null && requestedRepositoryId > 0) {
            return requestedRepositoryId;
        }
        SoftRepository repository = repositoryMapper.selectOne(Wrappers.<SoftRepository>lambdaQuery()
                .eq(SoftRepository::getEnabled, Boolean.TRUE)
                .orderByDesc(SoftRepository::getUpdateTime, SoftRepository::getCreateTime)
                .last("limit 1"));
        return repository == null ? null : repository.getSoftRepositoryId();
    }

    private String toJsonArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> normalized = values.stream()
                .map(this::normalizeText)
                .filter(item -> item != null && !item.isBlank())
                .toList();
        if (normalized.isEmpty()) {
            return null;
        }
        return SoftJsons.toJson(normalized);
    }

    private String buildCreatePackageMetadata(SoftPackageCreateRequest request, SoftPackage softPackage) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        String initScript = normalizeText(request.getInitScript());
        if (initScript != null) {
            metadata.put("initScript", initScript);
        }
        if (Boolean.TRUE.equals(request.getIntegrateServerService())) {
            Map<String, Object> serverService = new LinkedHashMap<>();
            serverService.put("enabled", true);
            serverService.put("serviceCode", normalizeText(request.getServerServiceCode()));
            serverService.put("serviceName", normalizeText(request.getServerServiceName()));
            serverService.put("serviceType", normalizeText(request.getServerServiceType()));
            serverService.put("startMode", normalizeText(request.getServerServiceStartMode()));
            serverService.put("executionProvider", normalizeText(request.getServerExecutionProvider()));
            serverService.put("source", "soft-package-create");
            metadata.put("serverServiceIntegration", serverService);
        }
        String softwareKey = normalizeSoftwareKey(softPackage.getPackageCode());
        if (softwareKey != null) {
            metadata.put("softwareKey", softwareKey);
        }
        String packageOsType = SoftArtifactRepositorySupport.normalizeOsType(softPackage.getOsType());
        if (packageOsType != null) {
            metadata.put("packageOsType", packageOsType);
        }
        String packageArchitecture = SoftArtifactRepositorySupport.normalizeArchitecture(softPackage.getArchitecture());
        if (packageArchitecture != null) {
            metadata.put("packageArchitecture", packageArchitecture);
        }
        return metadata.isEmpty() ? null : SoftJsons.toJson(metadata);
    }

    private String readMetadataText(String metadataJson, String key) {
        Object value = SoftJsons.toMap(metadataJson).get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private void normalizePackage(SoftPackage softPackage) {
        if (softPackage == null) {
            return;
        }
        softPackage.setPackageCode(normalizeSoftwareKey(softPackage.getPackageCode()));
        softPackage.setPackageName(normalizeText(softPackage.getPackageName()));
        softPackage.setPackageCategory(normalizeText(softPackage.getPackageCategory()));
        softPackage.setProfileCode(normalizeText(softPackage.getProfileCode()));
        softPackage.setOsType(SoftArtifactRepositorySupport.normalizeOsType(softPackage.getOsType()));
        softPackage.setArchitecture(SoftArtifactRepositorySupport.normalizeArchitecture(softPackage.getArchitecture()));
        softPackage.setDescription(normalizeText(softPackage.getDescription()));
        softPackage.setIconUrl(normalizeText(softPackage.getIconUrl()));
        softPackage.setSoftwareKey(resolvePackageSoftwareKey(softPackage, null));
    }

    private void normalizeVersion(SoftPackageVersion version) {
        if (version == null) {
            return;
        }
        version.setVersionCode(normalizeText(version.getVersionCode()));
        version.setVersionName(normalizeText(version.getVersionName()));
        version.setPackageName(normalizeText(version.getPackageName()));
        version.setOsType(SoftArtifactRepositorySupport.normalizeOsType(version.getOsType()));
        version.setArchitecture(SoftArtifactRepositorySupport.normalizeArchitecture(version.getArchitecture()));
        version.setSourceKind(normalizeSourceKind(version.getSourceKind(), null));
        version.setInstallMode(normalizeInstallMode(version.getInstallMode(), version.getSourceKind()));
        version.setArtifactPath(normalizeText(version.getArtifactPath()));
        version.setDownloadUrl(normalizeText(version.getDownloadUrl()));
        version.setDownloadUrlsJson(normalizeJsonText(version.getDownloadUrlsJson()));
        version.setInstallScript(normalizeText(version.getInstallScript()));
        version.setUninstallScript(normalizeText(version.getUninstallScript()));
        version.setStartScript(normalizeText(version.getStartScript()));
        version.setStopScript(normalizeText(version.getStopScript()));
        version.setRestartScript(normalizeText(version.getRestartScript()));
        version.setStatusScript(normalizeText(version.getStatusScript()));
        version.setServiceRegisterScript(normalizeText(version.getServiceRegisterScript()));
        version.setServiceUnregisterScript(normalizeText(version.getServiceUnregisterScript()));
        version.setLogPathsJson(normalizeJsonText(version.getLogPathsJson()));
        version.setConfigPathsJson(normalizeJsonText(version.getConfigPathsJson()));
        version.setCapabilityFlagsJson(normalizeJsonText(version.getCapabilityFlagsJson()));
        version.setMetadataJson(normalizeJsonText(version.getMetadataJson()));
    }

    private String mergeVersionMetadata(SoftPackageVersion current, String metadataJson) {
        Map<String, Object> merged = new LinkedHashMap<>(SoftJsons.toMap(current.getMetadataJson()));
        Map<String, Object> updates = SoftJsons.toMap(metadataJson);
        if (updates.isEmpty()) {
            return normalizeJsonText(metadataJson);
        }
        merged.putAll(updates);
        Object artifactKind = merged.get("artifactKind");
        if (artifactKind != null) {
            merged.put("artifactKind", String.valueOf(artifactKind).trim().toUpperCase());
        }
        String softwareKey = normalizeSoftwareKey(Objects.toString(merged.get("softwareKey"), current.getPackageCode()));
        if (softwareKey != null) {
            merged.put("softwareKey", softwareKey);
        }
        String packageOsType = SoftArtifactRepositorySupport.normalizeOsType(
                Objects.toString(merged.get("packageOsType"), null)
        );
        if (packageOsType != null) {
            merged.put("packageOsType", packageOsType);
        }
        String packageArchitecture = SoftArtifactRepositorySupport.normalizeArchitecture(
                Objects.toString(merged.get("packageArchitecture"), null)
        );
        if (packageArchitecture != null) {
            merged.put("packageArchitecture", packageArchitecture);
        }
        return SoftJsons.toJson(merged);
    }

    private void markUploadedArtifacts(Integer repositoryId, List<Map<String, Object>> savedFiles) {
        if (savedFiles == null || savedFiles.isEmpty()) {
            return;
        }
        List<Integer> packageIds = packageMapper.selectList(Wrappers.<SoftPackage>lambdaQuery()
                        .eq(SoftPackage::getSoftRepositoryId, repositoryId))
                .stream()
                .map(SoftPackage::getSoftPackageId)
                .filter(Objects::nonNull)
                .toList();
        if (packageIds.isEmpty()) {
            return;
        }
        Map<String, String> uploadedPaths = new HashMap<>();
        for (Map<String, Object> item : savedFiles) {
            String fileName = Objects.toString(item.get("fileName"), "").trim();
            String localPath = Objects.toString(item.get("localPath"), "").trim();
            if (!fileName.isEmpty()) {
                uploadedPaths.put(fileName, localPath);
            }
        }
        if (uploadedPaths.isEmpty()) {
            return;
        }
        List<SoftPackageVersion> versions = packageVersionMapper.selectList(Wrappers.<SoftPackageVersion>lambdaQuery()
                .in(SoftPackageVersion::getSoftPackageId, packageIds));
        for (SoftPackageVersion version : versions) {
            Map<String, Object> metadata = new LinkedHashMap<>(SoftJsons.toMap(version.getMetadataJson()));
            String artifactFileName = Objects.toString(metadata.get("artifactFileName"), "").trim();
            if (artifactFileName.isEmpty() || !uploadedPaths.containsKey(artifactFileName)) {
                continue;
            }
            String artifactPath = Objects.toString(metadata.get("artifactPath"), "").trim();
            String expectedPath = uploadedPaths.get(artifactFileName);
            if (!expectedPath.isBlank() && !artifactPath.isBlank()) {
                String normalizedActual = artifactPath.replace('\\', '/');
                String normalizedExpected = expectedPath.replace('\\', '/');
                if (!normalizedActual.equalsIgnoreCase(normalizedExpected)
                        && !normalizedActual.endsWith("/" + artifactFileName)) {
                    continue;
                }
            }
            metadata.put("artifactKind", "UPLOAD");
            version.setSourceKind("LOCAL_REPOSITORY");
            version.setInstallMode("LOCAL_UPLOAD");
            version.setArtifactPath(expectedPath);
            String softwareKey = normalizeSoftwareKey(Objects.toString(metadata.get("softwareKey"), version.getPackageCode()));
            if (softwareKey != null) {
                metadata.put("softwareKey", softwareKey);
            }
            String packageOsType = SoftArtifactRepositorySupport.normalizeOsType(
                    Objects.toString(metadata.get("packageOsType"), null)
            );
            if (packageOsType != null) {
                metadata.put("packageOsType", packageOsType);
            }
            String packageArchitecture = SoftArtifactRepositorySupport.normalizeArchitecture(
                    Objects.toString(metadata.get("packageArchitecture"), null)
            );
            if (packageArchitecture != null) {
                metadata.put("packageArchitecture", packageArchitecture);
            }
            version.setMetadataJson(SoftJsons.toJson(metadata));
            normalizeVersion(version);
            packageVersionMapper.updateById(version);
        }
    }

    private String resolveDefaultRepositoryDirectory(SoftRepository repository) {
        String repositoryCode = Optional.ofNullable(normalizeText(repository.getRepositoryCode()))
                .orElse("repository-" + repository.getSoftRepositoryId());
        return Path.of(properties.getArtifactUploadRoot(), repositoryCode).toString();
    }

    private String packageIdentity(SoftPackage softPackage) {
        return packageIdentity(
                softPackage == null ? null : softPackage.getPackageCode(),
                softPackage == null ? null : softPackage.getOsType(),
                softPackage == null ? null : softPackage.getArchitecture()
        );
    }

    private String packageIdentity(String packageCode, String osType, String architecture) {
        return SoftArtifactRepositorySupport.buildPackageIdentity(packageCode, osType, architecture);
    }

    private String versionIdentity(SoftPackageVersion version) {
        return resolveVersionPackageIdentity(version)
                + "#"
                + normalizeText(version.getVersionCode())
                + "#"
                + normalizeText(version.getPackageName());
    }

    private String resolveVersionPackageIdentity(SoftPackageVersion version) {
        Map<String, Object> metadata = SoftJsons.toMap(version.getMetadataJson());
        String packageOsType = Optional.ofNullable(SoftArtifactRepositorySupport.normalizeOsType(version.getOsType()))
                .orElse(Objects.toString(metadata.get("packageOsType"), null));
        String packageArchitecture = Optional.ofNullable(SoftArtifactRepositorySupport.normalizeArchitecture(version.getArchitecture()))
                .orElse(Objects.toString(metadata.get("packageArchitecture"), null));
        return packageIdentity(
                version == null ? null : version.getPackageCode(),
                packageOsType,
                packageArchitecture
        );
    }

    private SoftPackage requiredPackage(Integer id) {
        SoftPackage softPackage = packageMapper.selectById(id);
        if (softPackage == null) {
            throw new IllegalStateException("软件不存在: " + id);
        }
        hydratePackage(softPackage, null);
        return softPackage;
    }

    private SoftPackageVersion requiredVersion(Integer id) {
        SoftPackageVersion version = packageVersionMapper.selectById(id);
        if (version == null) {
            throw new IllegalStateException("软件版本不存在: " + id);
        }
        hydrateVersion(version);
        return version;
    }

    private SoftTarget requiredTarget(Integer id) {
        SoftTarget target = targetMapper.selectById(id);
        if (target == null) {
            throw new IllegalStateException("安装目标不存在: " + id);
        }
        return target;
    }

    private SoftInstallation requiredInstallation(Integer id) {
        SoftInstallation installation = installationMapper.selectById(id);
        if (installation == null) {
            throw new IllegalStateException("安装实例不存在: " + id);
        }
        return installation;
    }
}
