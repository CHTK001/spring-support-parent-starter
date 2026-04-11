package com.chua.starter.soft.support.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chua.starter.soft.support.config.SoftBuiltinProfileBootstrapper;
import com.chua.starter.soft.support.entity.SoftPackage;
import com.chua.starter.soft.support.entity.SoftPackageProfile;
import com.chua.starter.soft.support.entity.SoftPackageProfileField;
import com.chua.starter.soft.support.entity.SoftPackageProfileTemplate;
import com.chua.starter.soft.support.entity.SoftPackageVersion;
import com.chua.starter.soft.support.entity.SoftTarget;
import com.chua.starter.soft.support.mapper.SoftPackageMapper;
import com.chua.starter.soft.support.mapper.SoftPackageProfileFieldMapper;
import com.chua.starter.soft.support.mapper.SoftPackageProfileMapper;
import com.chua.starter.soft.support.mapper.SoftPackageProfileTemplateMapper;
import com.chua.starter.soft.support.mapper.SoftPackageVersionMapper;
import com.chua.starter.soft.support.mapper.SoftTargetMapper;
import com.chua.starter.soft.support.model.SoftGuideField;
import com.chua.starter.soft.support.model.SoftGuidePreviewRequest;
import com.chua.starter.soft.support.model.SoftGuidePreviewResponse;
import com.chua.starter.soft.support.model.SoftPackageGuide;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SoftBuiltinProfileGuidePreviewTest {

    @Test
    void shouldResolveBuiltinProfilesAndRenderPreviewForBuiltinPackages() throws Exception {
        BuiltinProfileFixture fixture = bootstrapFixture();

        assertBuiltinProfilePreview(fixture, new BuiltinProfileCase(
                "custom-agent",
                "Custom Agent",
                "1.0.0",
                "generic",
                "environmentName",
                "prod",
                "/opt/apps/custom-agent/1.0.0",
                "custom-agent",
                "/opt/apps/custom-agent/1.0.0/conf/app.conf",
                "app.env=prod"));
        assertBuiltinProfilePreview(fixture, new BuiltinProfileCase(
                "mysql-community",
                "MySQL Community",
                "8.0.36",
                "mysql",
                "port",
                "3306",
                "/opt/apps/mysql/8.0.36",
                "mysql-8.0.36",
                "/opt/apps/mysql/8.0.36/conf/my.cnf",
                "character-set-server=utf8mb4"));
        assertBuiltinProfilePreview(fixture, new BuiltinProfileCase(
                "redis-stack",
                "Redis Stack",
                "7.2.4",
                "redis",
                "appendonly",
                "true",
                "/opt/apps/redis/7.2.4",
                "redis-7.2.4",
                "/opt/apps/redis/7.2.4/conf/redis.conf",
                "appendonly true"));
        assertBuiltinProfilePreview(fixture, new BuiltinProfileCase(
                "nginx-mainline",
                "Nginx Mainline",
                "1.26.0",
                "nginx",
                "httpPort",
                "80",
                "/opt/apps/nginx/1.26.0",
                "nginx-1.26.0",
                "/opt/apps/nginx/1.26.0/conf/nginx.conf",
                "listen 80;"));
        assertBuiltinProfilePreview(fixture, new BuiltinProfileCase(
                "minio-server",
                "MinIO Server",
                "2026.04.01",
                "minio",
                "apiPort",
                "9000",
                "/opt/apps/minio/2026.04.01",
                "minio-2026.04.01",
                "/opt/apps/minio/2026.04.01/conf/minio.env",
                "MINIO_ROOT_USER=minioadmin"));
    }

    private void assertBuiltinProfilePreview(BuiltinProfileFixture fixture, BuiltinProfileCase profileCase) {
        SoftPackageMapper packageMapper = mock(SoftPackageMapper.class);
        SoftPackageVersionMapper versionMapper = mock(SoftPackageVersionMapper.class);
        SoftTargetMapper targetMapper = mock(SoftTargetMapper.class);
        SoftPackageProfileMapper profileMapper = mock(SoftPackageProfileMapper.class);
        SoftPackageProfileFieldMapper fieldMapper = mock(SoftPackageProfileFieldMapper.class);
        SoftPackageProfileTemplateMapper templateMapper = mock(SoftPackageProfileTemplateMapper.class);

        SoftPackage softPackage = new SoftPackage();
        softPackage.setSoftPackageId(1);
        softPackage.setPackageCode(profileCase.packageCode());
        softPackage.setPackageName(profileCase.packageName());

        SoftPackageVersion version = new SoftPackageVersion();
        version.setSoftPackageVersionId(10);
        version.setSoftPackageId(1);
        version.setVersionCode(profileCase.versionCode());
        version.setVersionName(profileCase.versionCode());

        SoftTarget target = new SoftTarget();
        target.setSoftTargetId(11);
        target.setTargetType("LOCAL");
        target.setOsType("WINDOWS");
        target.setBaseDirectory("/opt/apps");

        SoftPackageProfile profile = fixture.profileByCode().get(profileCase.profileCode());
        List<SoftPackageProfileField> fields = fixture.fieldsByProfileCode().get(profileCase.profileCode());
        List<SoftPackageProfileTemplate> templates = fixture.templatesByProfileCode().get(profileCase.profileCode());

        when(packageMapper.selectById(1)).thenReturn(softPackage);
        when(versionMapper.selectById(10)).thenReturn(version);
        when(targetMapper.selectById(11)).thenReturn(target);
        when(profileMapper.selectOne(any())).thenReturn(profile);
        when(fieldMapper.selectList(any())).thenReturn(fields);
        when(templateMapper.selectList(any())).thenReturn(templates);

        SoftGuideDefinitionService service = new SoftGuideDefinitionService(
                packageMapper,
                versionMapper,
                targetMapper,
                profileMapper,
                fieldMapper,
                templateMapper
        );

        SoftPackageGuide guide = service.getGuide(1, 10, 11);
        assertEquals(profileCase.profileCode(), guide.getProfileCode());
        assertTrue(!guide.getInstallFields().isEmpty());
        SoftGuideField expectedField = findField(guide, profileCase.expectedFieldKey());
        assertEquals(profileCase.expectedFieldDefaultValue(), String.valueOf(expectedField.getDefaultValue()));

        SoftGuidePreviewRequest request = new SoftGuidePreviewRequest();
        request.setSoftTargetId(11);
        SoftGuidePreviewResponse preview = service.preview(1, 10, request);

        assertEquals(profileCase.expectedInstallPath(), String.valueOf(preview.getResolvedVariables().get("installPath")));
        assertEquals(profileCase.expectedServiceName(), String.valueOf(preview.getResolvedVariables().get("serviceName")));
        assertTrue(preview.getRenderedScripts().containsKey("INSTALL_SCRIPT"));
        assertTrue(preview.getConfigPaths().contains(profileCase.expectedConfigPath()));
        assertTrue(preview.getRenderedConfigFiles().stream()
                .anyMatch(item -> profileCase.expectedConfigPath().equals(item.getTemplatePath())
                        && item.getContent().contains(profileCase.expectedConfigSnippet())),
                "profile=" + profileCase.profileCode() + ", files=" + preview.getRenderedConfigFiles());
        assertTrue(preview.getTemplateSummaryJson().contains("\"profileCode\":\"" + profileCase.profileCode() + "\""));
    }

    private SoftGuideField findField(SoftPackageGuide guide, String fieldKey) {
        return List.of(guide.getInstallFields(), guide.getServiceFields(), guide.getConfigFields()).stream()
                .flatMap(List::stream)
                .filter(item -> fieldKey.equals(item.getFieldKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("字段不存在: " + fieldKey));
    }

    private BuiltinProfileFixture bootstrapFixture() throws Exception {
        SoftPackageProfileMapper profileMapper = mock(SoftPackageProfileMapper.class);
        SoftPackageProfileFieldMapper fieldMapper = mock(SoftPackageProfileFieldMapper.class);
        SoftPackageProfileTemplateMapper templateMapper = mock(SoftPackageProfileTemplateMapper.class);

        List<SoftPackageProfile> profiles = new ArrayList<>();
        List<SoftPackageProfileField> fields = new ArrayList<>();
        List<SoftPackageProfileTemplate> templates = new ArrayList<>();
        int[] profileSequence = {100};
        int[] fieldSequence = {1000};
        int[] templateSequence = {2000};

        when(profileMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            SoftPackageProfile profile = invocation.getArgument(0);
            profile.setSoftPackageProfileId(profileSequence[0]++);
            profiles.add(profile);
            return 1;
        }).when(profileMapper).insert(any(SoftPackageProfile.class));
        doAnswer(invocation -> {
            SoftPackageProfileField field = invocation.getArgument(0);
            field.setSoftPackageProfileFieldId(fieldSequence[0]++);
            fields.add(field);
            return 1;
        }).when(fieldMapper).insert(any(SoftPackageProfileField.class));
        doAnswer(invocation -> {
            SoftPackageProfileTemplate template = invocation.getArgument(0);
            template.setSoftPackageProfileTemplateId(templateSequence[0]++);
            templates.add(template);
            return 1;
        }).when(templateMapper).insert(any(SoftPackageProfileTemplate.class));

        SoftBuiltinProfileBootstrapper bootstrapper = new SoftBuiltinProfileBootstrapper(profileMapper, fieldMapper, templateMapper);
        bootstrapper.run(null);

        Map<String, SoftPackageProfile> profileByCode = new LinkedHashMap<>();
        profiles.forEach(item -> profileByCode.put(item.getProfileCode(), item));

        Map<String, List<SoftPackageProfileField>> fieldsByProfileCode = new LinkedHashMap<>();
        fields.forEach(item -> {
            String profileCode = profileById(profileByCode, item.getSoftPackageProfileId());
            fieldsByProfileCode.computeIfAbsent(profileCode, key -> new ArrayList<>()).add(item);
        });

        Map<String, List<SoftPackageProfileTemplate>> templatesByProfileCode = new LinkedHashMap<>();
        templates.forEach(item -> {
            String profileCode = profileById(profileByCode, item.getSoftPackageProfileId());
            templatesByProfileCode.computeIfAbsent(profileCode, key -> new ArrayList<>()).add(item);
        });

        return new BuiltinProfileFixture(profileByCode, fieldsByProfileCode, templatesByProfileCode);
    }

    private String profileById(Map<String, SoftPackageProfile> profiles, Integer profileId) {
        return profiles.values().stream()
                .filter(item -> profileId.equals(item.getSoftPackageProfileId()))
                .map(SoftPackageProfile::getProfileCode)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("画像不存在: " + profileId));
    }

    private record BuiltinProfileCase(
            String packageCode,
            String packageName,
            String versionCode,
            String profileCode,
            String expectedFieldKey,
            String expectedFieldDefaultValue,
            String expectedInstallPath,
            String expectedServiceName,
            String expectedConfigPath,
            String expectedConfigSnippet
    ) {
    }

    private record BuiltinProfileFixture(
            Map<String, SoftPackageProfile> profileByCode,
            Map<String, List<SoftPackageProfileField>> fieldsByProfileCode,
            Map<String, List<SoftPackageProfileTemplate>> templatesByProfileCode
    ) {
    }
}
