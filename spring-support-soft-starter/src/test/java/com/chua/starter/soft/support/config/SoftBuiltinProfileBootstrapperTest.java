package com.chua.starter.soft.support.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.chua.starter.soft.support.entity.SoftPackageProfile;
import com.chua.starter.soft.support.entity.SoftPackageProfileField;
import com.chua.starter.soft.support.entity.SoftPackageProfileTemplate;
import com.chua.starter.soft.support.mapper.SoftPackageProfileFieldMapper;
import com.chua.starter.soft.support.mapper.SoftPackageProfileMapper;
import com.chua.starter.soft.support.mapper.SoftPackageProfileTemplateMapper;
import com.chua.starter.soft.support.util.SoftJsons;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SoftBuiltinProfileBootstrapperTest {

    @Mock
    private SoftPackageProfileMapper profileMapper;
    @Mock
    private SoftPackageProfileFieldMapper fieldMapper;
    @Mock
    private SoftPackageProfileTemplateMapper templateMapper;

    private final List<SoftPackageProfile> insertedProfiles = new ArrayList<>();
    private final List<SoftPackageProfileField> insertedFields = new ArrayList<>();
    private final List<SoftPackageProfileTemplate> insertedTemplates = new ArrayList<>();

    private SoftBuiltinProfileBootstrapper bootstrapper;
    private int profileSequence = 100;
    private int fieldSequence = 1000;
    private int templateSequence = 2000;

    @BeforeEach
    void setUp() {
        bootstrapper = new SoftBuiltinProfileBootstrapper(profileMapper, fieldMapper, templateMapper);
        when(profileMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            SoftPackageProfile profile = invocation.getArgument(0);
            profile.setSoftPackageProfileId(profileSequence++);
            insertedProfiles.add(profile);
            return 1;
        }).when(profileMapper).insert(any(SoftPackageProfile.class));
        doAnswer(invocation -> {
            SoftPackageProfileField field = invocation.getArgument(0);
            field.setSoftPackageProfileFieldId(fieldSequence++);
            insertedFields.add(field);
            return 1;
        }).when(fieldMapper).insert(any(SoftPackageProfileField.class));
        doAnswer(invocation -> {
            SoftPackageProfileTemplate template = invocation.getArgument(0);
            template.setSoftPackageProfileTemplateId(templateSequence++);
            insertedTemplates.add(template);
            return 1;
        }).when(templateMapper).insert(any(SoftPackageProfileTemplate.class));
    }

    @Test
    void shouldBootstrapBuiltinProfilesWithFieldsAndTemplates() throws Exception {
        bootstrapper.run(null);

        assertEquals(List.of("generic", "mysql", "redis", "nginx", "minio", "nacos"),
                insertedProfiles.stream().map(SoftPackageProfile::getProfileCode).toList());
        assertEquals(48, insertedFields.size());
        assertEquals(14, insertedTemplates.size());

        assertProfileMetadata("generic", "/logs/${serviceName}.log", "${installPath}/conf/app.conf");
        assertProfileMetadata("mysql", "${installPath}/logs/error.log", "${installPath}/conf/my.cnf");
        assertProfileMetadata("redis", "${installPath}/logs/redis.log", "${installPath}/conf/redis.conf");
        assertProfileMetadata("nginx", "${installPath}/logs/access.log", "${installPath}/conf/nginx.conf");
        assertProfileMetadata("minio", "${installPath}/logs/minio.log", "${installPath}/conf/minio.env");
        assertProfileMetadata("nacos", "${installPath}/logs/start.out", "${installPath}/conf/application.properties");

        assertField("mysql", "port", "number", "目录与端口", "3306");
        assertField("mysql", "rootPassword", "password", "账号凭证", "root123456");
        assertField("redis", "appendonly", "switch", "配置初始化", "true");
        assertField("nginx", "configDirectory", "input", "配置初始化", "${installPath}/conf");
        assertField("minio", "secretKey", "password", "账号凭证", "minioadmin");
        assertField("nacos", "httpPort", "number", "目录与端口", "8848");
        assertField("nacos", "mode", "select", "配置初始化", "standalone");

        assertTemplateContainsByScope("generic", "CONFIG_TEMPLATE", "app.env=${environmentName}");
        assertTemplateContainsByScope("mysql", "CONFIG_TEMPLATE", "character-set-server=${characterSet}");
        assertTemplateContainsByScope("redis", "CONFIG_TEMPLATE", "requirepass ${password}");
        assertTemplateContainsByScope("nginx", "INSTALL_SCRIPT", "touch ${installPath}/sbin/nginx");
        assertTemplateContainsByScope("minio", "CONFIG_TEMPLATE", "MINIO_OPTS=--address :${apiPort} --console-address :${consolePort}");
        assertTemplateContainsByScope("nacos", "CONFIG_TEMPLATE", "server.port=${httpPort}");
        assertTemplateContainsByScope("nacos", "ENV_TEMPLATE", "MODE=${mode}");
    }

    private void assertProfileMetadata(String profileCode, String logPath, String configPath) {
        SoftPackageProfile profile = findProfile(profileCode);
        assertTrue(Boolean.TRUE.equals(profile.getBuiltin()));
        assertTrue(Boolean.TRUE.equals(profile.getEnabled()));
        Map<String, Object> metadata = SoftJsons.toMap(profile.getMetadataJson());
        assertTrue(toStringList(metadata.get("logPaths")).contains(logPath));
        assertTrue(toStringList(metadata.get("configPaths")).contains(configPath));
    }

    private void assertField(String profileCode, String fieldKey, String componentType, String groupName, String defaultValue) {
        SoftPackageProfileField field = fieldsByProfileCode().get(profileCode).stream()
                .filter(item -> fieldKey.equals(item.getFieldKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("字段不存在: " + profileCode + "." + fieldKey));
        assertEquals(componentType, field.getComponentType());
        assertEquals(groupName, field.getGroupName());
        assertEquals(defaultValue, field.getDefaultValue());
        assertNotNull(field.getSoftPackageProfileId());
    }

    private void assertTemplateContainsByScope(String profileCode, String templateScope, String snippet) {
        SoftPackageProfileTemplate template = templatesByProfileCode().get(profileCode).stream()
                .filter(item -> templateScope.equals(item.getTemplateScope()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("模板不存在: " + profileCode + "." + templateScope));
        assertTrue(template.getTemplateContent().contains(snippet));
        assertTrue(Boolean.TRUE.equals(template.getEnabled()));
        assertEquals("TEXT", template.getTemplateEngine());
    }

    private SoftPackageProfile findProfile(String profileCode) {
        return insertedProfiles.stream()
                .filter(item -> profileCode.equals(item.getProfileCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("画像不存在: " + profileCode));
    }

    private List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private Map<String, List<SoftPackageProfileField>> fieldsByProfileCode() {
        Map<Integer, String> profileCodes = new LinkedHashMap<>();
        insertedProfiles.forEach(item -> profileCodes.put(item.getSoftPackageProfileId(), item.getProfileCode()));
        Map<String, List<SoftPackageProfileField>> result = new LinkedHashMap<>();
        insertedFields.forEach(item -> result.computeIfAbsent(profileCodes.get(item.getSoftPackageProfileId()), key -> new ArrayList<>()).add(item));
        return result;
    }

    private Map<String, List<SoftPackageProfileTemplate>> templatesByProfileCode() {
        Map<Integer, String> profileCodes = new LinkedHashMap<>();
        insertedProfiles.forEach(item -> profileCodes.put(item.getSoftPackageProfileId(), item.getProfileCode()));
        Map<String, List<SoftPackageProfileTemplate>> result = new LinkedHashMap<>();
        insertedTemplates.forEach(item -> result.computeIfAbsent(profileCodes.get(item.getSoftPackageProfileId()), key -> new ArrayList<>()).add(item));
        return result;
    }
}
