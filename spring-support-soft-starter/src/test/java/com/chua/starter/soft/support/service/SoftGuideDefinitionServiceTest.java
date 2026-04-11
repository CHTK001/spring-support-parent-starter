package com.chua.starter.soft.support.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SoftGuideDefinitionServiceTest {

    @Mock
    private SoftPackageMapper packageMapper;
    @Mock
    private SoftPackageVersionMapper versionMapper;
    @Mock
    private SoftTargetMapper targetMapper;
    @Mock
    private SoftPackageProfileMapper profileMapper;
    @Mock
    private SoftPackageProfileFieldMapper fieldMapper;
    @Mock
    private SoftPackageProfileTemplateMapper templateMapper;

    private SoftGuideDefinitionService service;

    @BeforeEach
    void setUp() {
        service = new SoftGuideDefinitionService(
                packageMapper,
                versionMapper,
                targetMapper,
                profileMapper,
                fieldMapper,
                templateMapper
        );
    }

    @Test
    void shouldBuildGuideWithMysqlProfileAndVersionOverrides() {
        stubMysqlProfile();

        SoftPackageGuide guide = service.getGuide(1, 10, 11);

        assertEquals("mysql", guide.getProfileCode());
        assertEquals("MySQL", guide.getProfileName());
        assertEquals(2, guide.getInstallFields().size());
        assertEquals(1, guide.getServiceFields().size());
        assertEquals(2, guide.getConfigFields().size());
        SoftGuideField port = findField(guide.getInstallFields(), "port");
        assertEquals("3307", port.getDefaultValue());
        SoftGuideField timezone = findField(guide.getConfigFields(), "timezone");
        assertEquals("Asia/Shanghai", timezone.getDefaultValue());
    }

    @Test
    void shouldRenderPreviewPathsScriptsAndSummary() {
        stubMysqlProfile();

        SoftGuidePreviewRequest request = new SoftGuidePreviewRequest();
        request.setSoftTargetId(11);
        request.setInstallationName("mysql-prod");
        request.setInstallPath("/srv/mysql/8.0.36");
        request.setServiceName("mysql-8036");
        request.setInstallOptions(new LinkedHashMap<>(Map.of(
                "installationName", "mysql-prod",
                "installPath", "/srv/mysql/8.0.36"
        )));
        request.setServiceOptions(new LinkedHashMap<>(Map.of(
                "serviceName", "mysql-8036"
        )));
        request.setConfigOptions(new LinkedHashMap<>(Map.of(
                "characterSet", "utf8mb4"
        )));

        SoftGuidePreviewResponse response = service.preview(1, 10, request);

        assertEquals("3307", String.valueOf(response.getResolvedVariables().get("port")));
        assertTrue(response.getRenderedScripts().containsKey("INSTALL_SCRIPT"));
        assertEquals(1, response.getRenderedConfigFiles().size());
        assertEquals("/srv/mysql/8.0.36/conf/custom.cnf", response.getRenderedConfigFiles().get(0).getTemplatePath());
        assertTrue(response.getConfigPaths().contains("/srv/mysql/8.0.36/conf/custom.cnf"));
        assertTrue(response.getTemplateSummaryJson().contains("\"profileCode\":\"mysql\""));
        assertTrue(response.getTemplateSummaryJson().contains("\"timezone\":\"Asia/Shanghai\""));
    }

    private void stubMysqlProfile() {
        when(packageMapper.selectById(1)).thenReturn(mysqlPackage());
        when(versionMapper.selectById(10)).thenReturn(mysqlVersion());
        when(targetMapper.selectById(11)).thenReturn(target());
        when(profileMapper.selectOne(any())).thenReturn(profile());
        when(fieldMapper.selectList(any())).thenReturn(profileFields());
        when(templateMapper.selectList(any())).thenReturn(profileTemplates());
    }

    private SoftGuideField findField(List<SoftGuideField> fields, String key) {
        return fields.stream()
                .filter(item -> key.equals(item.getFieldKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("字段不存在: " + key));
    }

    private SoftPackage mysqlPackage() {
        SoftPackage value = new SoftPackage();
        value.setSoftPackageId(1);
        value.setPackageCode("mysql-community");
        value.setPackageName("MySQL Community");
        value.setProfileCode("mysql");
        return value;
    }

    private SoftPackageVersion mysqlVersion() {
        SoftPackageVersion value = new SoftPackageVersion();
        value.setSoftPackageVersionId(10);
        value.setSoftPackageId(1);
        value.setVersionCode("8.0.36");
        value.setVersionName("8.0.36");
        value.setMetadataJson("""
                {
                  "guide": {
                    "fieldDefaults": {
                      "port": "3307"
                    },
                    "templateOverrides": {
                      "CONFIG_TEMPLATE": {
                        "templatePath": "${installPath}/conf/custom.cnf"
                      }
                    },
                    "extraFields": [
                      {
                        "fieldKey": "timezone",
                        "fieldLabel": "时区",
                        "fieldScope": "config",
                        "componentType": "input",
                        "sortOrder": 65,
                        "defaultValue": "Asia/Shanghai"
                      }
                    ]
                  }
                }
                """);
        return value;
    }

    private SoftTarget target() {
        SoftTarget value = new SoftTarget();
        value.setSoftTargetId(11);
        value.setTargetType("SSH");
        value.setOsType("LINUX");
        value.setBaseDirectory("/opt/apps");
        value.setHost("10.0.0.11");
        value.setPort(22);
        return value;
    }

    private SoftPackageProfile profile() {
        SoftPackageProfile value = new SoftPackageProfile();
        value.setSoftPackageProfileId(101);
        value.setProfileCode("mysql");
        value.setProfileName("MySQL");
        value.setMetadataJson("""
                {
                  "logPaths": ["${installPath}/logs/error.log"],
                  "configPaths": ["${installPath}/conf/my.cnf"]
                }
                """);
        return value;
    }

    private List<SoftPackageProfileField> profileFields() {
        return List.of(
                field("installPath", "install", true, "${baseDirectory}/mysql/${versionCode}", 10, null),
                field("port", "install", true, "3306", 20, "{\"min\":1,\"max\":65535}"),
                field("serviceName", "service", false, "${packageCode}", 30, null),
                field("characterSet", "config", false, "utf8mb4", 40, null)
        );
    }

    private SoftPackageProfileField field(
            String key,
            String scope,
            boolean required,
            String defaultValue,
            int sortOrder,
            String validationJson
    ) {
        SoftPackageProfileField value = new SoftPackageProfileField();
        value.setFieldKey(key);
        value.setFieldLabel(key);
        value.setFieldScope(scope);
        value.setComponentType("input");
        value.setRequiredFlag(required);
        value.setDefaultValue(defaultValue);
        value.setSortOrder(sortOrder);
        value.setValidationJson(validationJson);
        value.setMetadataJson("{}");
        return value;
    }

    private List<SoftPackageProfileTemplate> profileTemplates() {
        SoftPackageProfileTemplate config = new SoftPackageProfileTemplate();
        config.setTemplateCode("CONFIG_TEMPLATE");
        config.setTemplateScope("CONFIG_TEMPLATE");
        config.setTemplateName("mysql-config");
        config.setTemplatePath("${installPath}/conf/my.cnf");
        config.setTemplateContent("[mysqld]\\nport=${port}\\ncharacter-set-server=${characterSet}\\n");
        config.setSortOrder(10);

        SoftPackageProfileTemplate install = new SoftPackageProfileTemplate();
        install.setTemplateCode("INSTALL_SCRIPT");
        install.setTemplateScope("INSTALL_SCRIPT");
        install.setTemplateName("mysql-install");
        install.setTemplateContent("mkdir -p ${installPath}/conf ${installPath}/logs\\n");
        install.setSortOrder(20);

        return List.of(config, install);
    }
}
