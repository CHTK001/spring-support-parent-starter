package com.chua.starter.soft.support.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chua.starter.soft.support.entity.SoftPackage;
import com.chua.starter.soft.support.entity.SoftPackageVersion;
import com.chua.starter.soft.support.entity.SoftRepository;
import com.chua.starter.soft.support.mapper.SoftConfigSnapshotMapper;
import com.chua.starter.soft.support.mapper.SoftInstallationMapper;
import com.chua.starter.soft.support.mapper.SoftOperationLogMapper;
import com.chua.starter.soft.support.mapper.SoftPackageMapper;
import com.chua.starter.soft.support.mapper.SoftPackageVersionMapper;
import com.chua.starter.soft.support.mapper.SoftRepositoryMapper;
import com.chua.starter.soft.support.mapper.SoftTargetMapper;
import com.chua.starter.soft.support.model.SoftRepositorySource;
import com.chua.starter.soft.support.spi.SoftConfigManager;
import com.chua.starter.soft.support.spi.SoftInstallExecutor;
import com.chua.starter.soft.support.spi.SoftLogStreamProvider;
import com.chua.starter.soft.support.spi.SoftRepositorySyncProvider;
import com.chua.starter.soft.support.spi.SoftServiceManager;
import com.chua.starter.soft.support.config.SoftManagementProperties;
import com.chua.starter.server.support.service.ServerServiceService;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftManagementServiceRepositorySyncTest {

    @Mock
    private SoftRepositoryMapper repositoryMapper;
    @Mock
    private SoftPackageMapper packageMapper;
    @Mock
    private SoftPackageVersionMapper packageVersionMapper;
    @Mock
    private SoftTargetMapper targetMapper;
    @Mock
    private SoftInstallationMapper installationMapper;
    @Mock
    private SoftOperationLogMapper operationLogMapper;
    @Mock
    private SoftConfigSnapshotMapper configSnapshotMapper;
    @Mock
    private SoftRepositorySyncProvider httpProvider;
    @Mock
    private SoftRepositorySyncProvider localProvider;
    @Mock
    private SoftServiceManager serviceManager;
    @Mock
    private ServerServiceService serverServiceService;
    @Mock
    private SoftLogStreamProvider logStreamProvider;
    @Mock
    private SoftConfigManager configManager;
    @Mock
    private SoftGuideDefinitionService softGuideDefinitionService;
    @Mock
    private SoftRealtimePublisher realtimePublisher;
    @Mock
    private SoftManagementProperties properties;

    private SoftManagementService service;

    @BeforeEach
    void setUp() {
        service = new SoftManagementService(
                repositoryMapper,
                packageMapper,
                packageVersionMapper,
                targetMapper,
                installationMapper,
                operationLogMapper,
                configSnapshotMapper,
                List.of(httpProvider, localProvider),
                List.<SoftInstallExecutor>of(),
                serviceManager,
                serverServiceService,
                logStreamProvider,
                configManager,
                softGuideDefinitionService,
                realtimePublisher,
                properties
        );
        when(httpProvider.supports("HTTP_JSON")).thenReturn(true);
        when(localProvider.supports("LOCAL_DIR")).thenReturn(true);
        when(packageMapper.selectOne(any())).thenReturn(null);
        when(packageVersionMapper.selectOne(any())).thenReturn(null);
        AtomicInteger packageId = new AtomicInteger(10);
        doAnswer(invocation -> {
            SoftPackage value = invocation.getArgument(0);
            value.setSoftPackageId(packageId.getAndIncrement());
            return 1;
        }).when(packageMapper).insert(any(SoftPackage.class));
    }

    @Test
    void shouldSyncMultipleSourcesAndSkipDisabledSources() throws Exception {
        SoftRepository repository = enabledRepository();
        when(repositoryMapper.selectById(1)).thenReturn(repository);
        when(httpProvider.sync(any(), any())).thenAnswer(invocation -> {
            SoftRepositorySource source = invocation.getArgument(1);
            if ("https://primary.example/repository.json".equals(source.getSourceUrl())) {
                return payload("mysql-community", "8.0.36");
            }
            if ("https://mirror.example/repository.json".equals(source.getSourceUrl())) {
                return payload("redis", "7.2.4");
            }
            throw new IllegalStateException("unexpected source");
        });

        SoftRepository stored = service.syncRepository(1);

        assertEquals("SUCCESS", stored.getLastSyncStatus());
        assertTrue(stored.getLastSyncMessage().contains("成功 2 个"));
        assertTrue(stored.getLastSyncMessage().contains("禁用/跳过 1 个"));
        assertEquals(3, stored.getSourceConfigs().size());
        verify(httpProvider).sync(any(), eq(repository.getSourceConfigs().get(0)));
        verify(httpProvider).sync(any(), eq(repository.getSourceConfigs().get(1)));
        verify(localProvider, never()).sync(any(), any());
        verify(packageMapper, times(2)).insert(any(SoftPackage.class));
        verify(packageVersionMapper, times(2)).insert(any(SoftPackageVersion.class));
    }

    @Test
    void shouldRejectDisabledRepositorySync() throws Exception {
        SoftRepository repository = enabledRepository();
        repository.setEnabled(Boolean.FALSE);
        when(repositoryMapper.selectById(1)).thenReturn(repository);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.syncRepository(1));

        assertEquals("仓库已禁用，无法执行同步", error.getMessage());
        verify(repositoryMapper).updateById(repository);
        verify(httpProvider, never()).sync(any(), any());
    }

    private SoftRepository enabledRepository() {
        SoftRepository repository = new SoftRepository();
        repository.setSoftRepositoryId(1);
        repository.setRepositoryName("综合仓库");
        repository.setRepositoryCode("soft-main");
        repository.setRepositoryType("MANUAL");
        repository.setEnabled(Boolean.TRUE);
        repository.setSourceConfigsJson("""
                [
                  {
                    "sourceName": "主源",
                    "sourceType": "HTTP_JSON",
                    "sourceUrl": "https://primary.example/repository.json",
                    "enabled": true
                  },
                  {
                    "sourceName": "镜像源",
                    "sourceType": "HTTP_JSON",
                    "sourceUrl": "https://mirror.example/repository.json",
                    "enabled": true
                  },
                  {
                    "sourceName": "本地归档",
                    "sourceType": "LOCAL_DIR",
                    "localDirectory": "/data/soft-repo",
                    "enabled": false
                  }
                ]
                """);
        return repository;
    }

    private SoftRepositorySyncProvider.RepositorySyncPayload payload(String packageCode, String versionCode) {
        SoftPackage softPackage = new SoftPackage();
        softPackage.setPackageCode(packageCode);
        softPackage.setPackageName(packageCode);
        SoftPackageVersion version = new SoftPackageVersion();
        version.setPackageCode(packageCode);
        version.setVersionCode(versionCode);
        version.setVersionName(versionCode);
        version.setDownloadUrlsJson("[\"https://download.example/" + packageCode + "-" + versionCode + ".tar.gz\"]");
        version.setEnabled(Boolean.TRUE);
        return new SoftRepositorySyncProvider.RepositorySyncPayload(List.of(softPackage), List.of(version));
    }
}
