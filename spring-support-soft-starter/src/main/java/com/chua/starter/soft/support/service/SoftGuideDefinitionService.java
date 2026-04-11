package com.chua.starter.soft.support.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import com.chua.starter.soft.support.model.SoftGuideTemplate;
import com.chua.starter.soft.support.model.SoftPackageGuide;
import com.chua.starter.soft.support.model.SoftRenderedConfigFile;
import com.chua.starter.soft.support.util.SoftCommandSupport;
import com.chua.starter.soft.support.util.SoftJsons;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SoftGuideDefinitionService {

    private static final String DEFAULT_PROFILE_CODE = "generic";

    private final SoftPackageMapper packageMapper;
    private final SoftPackageVersionMapper versionMapper;
    private final SoftTargetMapper targetMapper;
    private final SoftPackageProfileMapper profileMapper;
    private final SoftPackageProfileFieldMapper fieldMapper;
    private final SoftPackageProfileTemplateMapper templateMapper;

    public SoftGuideDefinitionService(
            SoftPackageMapper packageMapper,
            SoftPackageVersionMapper versionMapper,
            SoftTargetMapper targetMapper,
            SoftPackageProfileMapper profileMapper,
            SoftPackageProfileFieldMapper fieldMapper,
            SoftPackageProfileTemplateMapper templateMapper
    ) {
        this.packageMapper = packageMapper;
        this.versionMapper = versionMapper;
        this.targetMapper = targetMapper;
        this.profileMapper = profileMapper;
        this.fieldMapper = fieldMapper;
        this.templateMapper = templateMapper;
    }

    public SoftPackageGuide getGuide(Integer packageId, Integer versionId, Integer targetId) {
        SoftPackage softPackage = requiredPackage(packageId);
        SoftPackageVersion version = versionId == null ? latestVersion(packageId) : requiredVersion(versionId);
        SoftTarget target = targetId == null ? null : targetMapper.selectById(targetId);
        SoftPackageProfile profile = resolveProfile(softPackage);
        Map<String, Object> overrides = readGuideOverrides(version);
        List<SoftGuideField> allFields = buildGuideFields(profile, overrides, softPackage, version, target);
        return SoftPackageGuide.builder()
                .softPackageId(softPackage.getSoftPackageId())
                .softPackageVersionId(version == null ? null : version.getSoftPackageVersionId())
                .softPackageProfileId(profile == null ? null : profile.getSoftPackageProfileId())
                .profileCode(profile == null ? null : profile.getProfileCode())
                .profileName(profile == null ? null : profile.getProfileName())
                .installFields(filterScope(allFields, "install"))
                .serviceFields(filterScope(allFields, "service"))
                .configFields(filterScope(allFields, "config"))
                .templates(buildGuideTemplates(profile, overrides))
                .versionOverrides(overrides)
                .build();
    }

    public SoftGuidePreviewResponse preview(Integer packageId, Integer versionId, SoftGuidePreviewRequest request) {
        SoftPackage softPackage = requiredPackage(packageId);
        SoftPackageVersion version = versionId == null ? latestVersion(packageId) : requiredVersion(versionId);
        SoftTarget target = request == null || request.getSoftTargetId() == null ? null : targetMapper.selectById(request.getSoftTargetId());
        return resolveGuideAssets(
                softPackage,
                version,
                target,
                request == null ? null : request.getInstallationName(),
                request == null ? null : request.getInstallPath(),
                request == null ? null : request.getServiceName(),
                request == null ? null : request.getInstallOptions(),
                request == null ? null : request.getServiceOptions(),
                request == null ? null : request.getConfigOptions()
        );
    }

    public SoftGuidePreviewResponse resolveGuideAssets(
            SoftPackage softPackage,
            SoftPackageVersion version,
            SoftTarget target,
            String installationName,
            String installPath,
            String serviceName,
            Map<String, Object> installOptions,
            Map<String, Object> serviceOptions,
            Map<String, Object> configOptions
    ) {
        SoftPackageProfile profile = resolveProfile(softPackage);
        Map<String, Object> overrides = readGuideOverrides(version);
        List<SoftGuideField> fields = buildGuideFields(profile, overrides, softPackage, version, target);
        List<SoftGuideTemplate> templates = buildGuideTemplates(profile, overrides);
        return renderGuideAssets(softPackage, version, target, installationName, installPath, serviceName, installOptions, serviceOptions, configOptions, profile, fields, templates);
    }

    private SoftGuidePreviewResponse renderGuideAssets(
            SoftPackage softPackage,
            SoftPackageVersion version,
            SoftTarget target,
            String installationName,
            String installPath,
            String serviceName,
            Map<String, Object> installOptions,
            Map<String, Object> serviceOptions,
            Map<String, Object> configOptions,
            SoftPackageProfile profile,
            List<SoftGuideField> fields,
            List<SoftGuideTemplate> templates
    ) {
        Map<String, Object> installValueMap = new LinkedHashMap<>(safeMap(installOptions));
        Map<String, Object> serviceValueMap = new LinkedHashMap<>(safeMap(serviceOptions));
        Map<String, Object> configValueMap = new LinkedHashMap<>(safeMap(configOptions));
        Map<String, Object> resolvedVariables = new LinkedHashMap<>();
        resolvedVariables.put("packageCode", softPackage == null ? null : softPackage.getPackageCode());
        resolvedVariables.put("packageName", softPackage == null ? null : softPackage.getPackageName());
        resolvedVariables.put("profileCode", profile == null ? null : profile.getProfileCode());
        resolvedVariables.put("versionCode", version == null ? null : version.getVersionCode());
        resolvedVariables.put("versionName", version == null ? null : version.getVersionName());
        resolvedVariables.put("installationName", installationName);
        resolvedVariables.put("installPath", installPath);
        resolvedVariables.put("serviceName", serviceName);
        resolvedVariables.put("baseDirectory", target == null ? null : target.getBaseDirectory());
        resolvedVariables.put("targetType", target == null ? null : target.getTargetType());
        resolvedVariables.put("targetOsType", target == null ? null : target.getOsType());
        resolvedVariables.put("targetHost", target == null ? null : target.getHost());
        resolvedVariables.put("targetPort", target == null ? null : target.getPort());

        for (SoftGuideField field : fields) {
            Map<String, Object> scopeValues = scopeMap(field.getFieldScope(), installValueMap, serviceValueMap, configValueMap);
            Object value = scopeValues.get(field.getFieldKey());
            if (blank(value)) {
                value = renderDefaultValue(field.getDefaultValue(), resolvedVariables);
                if (!blank(value)) {
                    scopeValues.put(field.getFieldKey(), value);
                }
            }
            validateField(field, value);
            resolvedVariables.put(field.getFieldKey(), value);
            resolvedVariables.put(normalizeScope(field.getFieldScope()) + "." + field.getFieldKey(), value);
        }

        if (blank(resolvedVariables.get("installationName"))) {
            resolvedVariables.put("installationName", softPackage == null ? null : softPackage.getPackageName());
        }
        if (blank(resolvedVariables.get("serviceName"))) {
            resolvedVariables.put("serviceName", softPackage == null ? null : softPackage.getPackageCode());
        }
        if (blank(resolvedVariables.get("installPath")) && resolvedVariables.get("baseDirectory") != null && softPackage != null && version != null) {
            resolvedVariables.put("installPath", resolvedVariables.get("baseDirectory") + "/" + softPackage.getPackageCode() + "/" + version.getVersionCode());
        }
        if (version != null) {
            resolvedVariables.put("artifactPath", resolveArtifactPath(version, resolvedVariables));
        }

        Map<String, String> renderedScripts = new LinkedHashMap<>();
        List<SoftRenderedConfigFile> renderedConfigFiles = new ArrayList<>();
        for (SoftGuideTemplate template : templates) {
            String content = SoftCommandSupport.renderTemplate(template.getTemplateContent(), resolvedVariables);
            if (isConfigTemplate(template.getTemplateScope())) {
                renderedConfigFiles.add(SoftRenderedConfigFile.builder()
                        .templateCode(template.getTemplateCode())
                        .templateName(template.getTemplateName())
                        .templatePath(SoftCommandSupport.renderTemplate(template.getTemplatePath(), resolvedVariables))
                        .content(content)
                        .build());
            } else {
                renderedScripts.put(template.getTemplateCode(), content);
            }
        }
        overrideVersionScripts(version, renderedScripts, resolvedVariables);

        List<String> logPaths = renderPathList(resolvePathList(version == null ? null : version.getLogPathsJson(), profile, "logPaths"), resolvedVariables);
        List<String> configPaths = renderPathList(resolvePathList(version == null ? null : version.getConfigPathsJson(), profile, "configPaths"), resolvedVariables);
        renderedConfigFiles.stream().map(SoftRenderedConfigFile::getTemplatePath).filter(Objects::nonNull).forEach(configPaths::add);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("profileCode", profile == null ? null : profile.getProfileCode());
        summary.put("renderedScripts", new ArrayList<>(renderedScripts.keySet()));
        summary.put("renderedConfigPaths", renderedConfigFiles.stream().map(SoftRenderedConfigFile::getTemplatePath).toList());
        summary.put("installOptions", installValueMap);
        summary.put("serviceOptions", serviceValueMap);
        summary.put("configOptions", configValueMap);
        return SoftGuidePreviewResponse.builder()
                .resolvedVariables(resolvedVariables)
                .renderedScripts(renderedScripts)
                .renderedConfigFiles(renderedConfigFiles)
                .logPaths(distinct(logPaths))
                .configPaths(distinct(configPaths))
                .templateSummaryJson(SoftJsons.toJson(summary))
                .build();
    }

    private List<SoftGuideField> buildGuideFields(
            SoftPackageProfile profile,
            Map<String, Object> overrides,
            SoftPackage softPackage,
            SoftPackageVersion version,
            SoftTarget target
    ) {
        if (profile == null) {
            return Collections.emptyList();
        }
        Map<String, SoftGuideField> fields = new LinkedHashMap<>();
        List<SoftPackageProfileField> source = fieldMapper.selectList(Wrappers.<SoftPackageProfileField>lambdaQuery()
                .eq(SoftPackageProfileField::getSoftPackageProfileId, profile.getSoftPackageProfileId())
                .orderByAsc(SoftPackageProfileField::getSortOrder, SoftPackageProfileField::getSoftPackageProfileFieldId));

        Map<String, Object> fieldDefaults = castMap(overrides.get("fieldDefaults"));
        Map<String, Object> fieldOverrides = castMap(overrides.get("fieldOverrides"));
        Map<String, Object> baseVariables = new LinkedHashMap<>();
        baseVariables.put("baseDirectory", target == null ? null : target.getBaseDirectory());
        baseVariables.put("packageCode", softPackage == null ? null : softPackage.getPackageCode());
        baseVariables.put("packageName", softPackage == null ? null : softPackage.getPackageName());
        baseVariables.put("versionCode", version == null ? null : version.getVersionCode());
        baseVariables.put("profileCode", profile.getProfileCode());
        for (SoftPackageProfileField item : source) {
            SoftGuideField field = toGuideField(item);
            if (fieldDefaults.containsKey(field.getFieldKey())) {
                field.setDefaultValue(fieldDefaults.get(field.getFieldKey()));
            }
            Map<String, Object> override = castMap(fieldOverrides.get(field.getFieldKey()));
            applyFieldOverride(field, override);
            if (field.getDefaultValue() instanceof String text) {
                field.setDefaultValue(SoftCommandSupport.renderTemplate(text, baseVariables));
            }
            if (!blank(field.getDefaultValue())) {
                baseVariables.put(field.getFieldKey(), field.getDefaultValue());
                baseVariables.put(normalizeScope(field.getFieldScope()) + "." + field.getFieldKey(), field.getDefaultValue());
            }
            fields.put(field.getFieldKey(), field);
        }
        for (Map<String, Object> extra : castListMap(overrides.get("extraFields"))) {
            SoftGuideField field = mapToGuideField(extra);
            if (field.getFieldKey() != null) {
                if (field.getDefaultValue() instanceof String text) {
                    field.setDefaultValue(SoftCommandSupport.renderTemplate(text, baseVariables));
                }
                if (!blank(field.getDefaultValue())) {
                    baseVariables.put(field.getFieldKey(), field.getDefaultValue());
                    baseVariables.put(normalizeScope(field.getFieldScope()) + "." + field.getFieldKey(), field.getDefaultValue());
                }
                fields.put(field.getFieldKey(), field);
            }
        }
        return fields.values().stream()
                .sorted(Comparator.comparing(SoftGuideField::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SoftGuideField::getFieldKey, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private List<SoftGuideTemplate> buildGuideTemplates(SoftPackageProfile profile, Map<String, Object> overrides) {
        if (profile == null) {
            return Collections.emptyList();
        }
        Map<String, SoftGuideTemplate> templates = new LinkedHashMap<>();
        List<SoftPackageProfileTemplate> source = templateMapper.selectList(Wrappers.<SoftPackageProfileTemplate>lambdaQuery()
                .eq(SoftPackageProfileTemplate::getSoftPackageProfileId, profile.getSoftPackageProfileId())
                .orderByAsc(SoftPackageProfileTemplate::getSortOrder, SoftPackageProfileTemplate::getSoftPackageProfileTemplateId));
        source.forEach(item -> templates.put(item.getTemplateCode(), toGuideTemplate(item)));
        Map<String, Object> templateOverrides = castMap(overrides.get("templateOverrides"));
        templateOverrides.forEach((code, value) -> {
            SoftGuideTemplate template = templates.get(code);
            if (template == null) {
                return;
            }
            if (value instanceof String text) {
                template.setTemplateContent(text);
                return;
            }
            Map<String, Object> override = castMap(value);
            if (override.containsKey("templateContent")) {
                template.setTemplateContent(String.valueOf(override.get("templateContent")));
            }
            if (override.containsKey("templatePath")) {
                template.setTemplatePath(String.valueOf(override.get("templatePath")));
            }
        });
        for (Map<String, Object> extra : castListMap(overrides.get("extraTemplates"))) {
            SoftGuideTemplate template = mapToGuideTemplate(extra);
            if (template.getTemplateCode() != null) {
                templates.put(template.getTemplateCode(), template);
            }
        }
        return templates.values().stream()
                .sorted(Comparator.comparing(SoftGuideTemplate::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SoftGuideTemplate::getTemplateCode, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private List<SoftGuideField> filterScope(List<SoftGuideField> fields, String scope) {
        return fields.stream().filter(item -> normalizeScope(item.getFieldScope()).equals(scope)).toList();
    }

    private SoftPackageProfile resolveProfile(SoftPackage softPackage) {
        if (softPackage == null) {
            return null;
        }
        if (softPackage.getSoftPackageProfileId() != null) {
            SoftPackageProfile value = profileMapper.selectById(softPackage.getSoftPackageProfileId());
            if (value != null) {
                return value;
            }
        }
        String profileCode = Optional.ofNullable(softPackage.getProfileCode())
                .filter(text -> !text.isBlank())
                .orElseGet(() -> builtinProfileCode(softPackage.getPackageCode()));
        SoftPackageProfile value = profileMapper.selectOne(Wrappers.<SoftPackageProfile>lambdaQuery()
                .eq(SoftPackageProfile::getProfileCode, profileCode)
                .last("limit 1"));
        if (value != null) {
            return value;
        }
        return profileMapper.selectOne(Wrappers.<SoftPackageProfile>lambdaQuery()
                .eq(SoftPackageProfile::getProfileCode, DEFAULT_PROFILE_CODE)
                .last("limit 1"));
    }

    private SoftPackage requiredPackage(Integer id) {
        SoftPackage value = packageMapper.selectById(id);
        if (value == null) {
            throw new IllegalStateException("软件不存在: " + id);
        }
        return value;
    }

    private SoftPackageVersion requiredVersion(Integer id) {
        SoftPackageVersion value = versionMapper.selectById(id);
        if (value == null) {
            throw new IllegalStateException("版本不存在: " + id);
        }
        return value;
    }

    private SoftPackageVersion latestVersion(Integer packageId) {
        return versionMapper.selectOne(Wrappers.<SoftPackageVersion>lambdaQuery()
                .eq(SoftPackageVersion::getSoftPackageId, packageId)
                .orderByDesc(SoftPackageVersion::getUpdateTime, SoftPackageVersion::getCreateTime)
                .last("limit 1"));
    }

    private Map<String, Object> readGuideOverrides(SoftPackageVersion version) {
        if (version == null || version.getMetadataJson() == null || version.getMetadataJson().isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, Object> metadata = SoftJsons.toMap(version.getMetadataJson());
        Object value = metadata.get("guide");
        if (value instanceof Map<?, ?> map) {
            return castMap(map);
        }
        return metadata;
    }

    private String builtinProfileCode(String packageCode) {
        if (packageCode == null || packageCode.isBlank()) {
            return DEFAULT_PROFILE_CODE;
        }
        String value = packageCode.trim().toLowerCase();
        if (value.contains("mysql")) return "mysql";
        if (value.contains("redis")) return "redis";
        if (value.contains("nginx")) return "nginx";
        if (value.contains("minio")) return "minio";
        if (value.contains("nacos")) return "nacos";
        return DEFAULT_PROFILE_CODE;
    }

    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                if (key != null) result.put(String.valueOf(key), item);
            });
            return result;
        }
        if (value instanceof String text) {
            return SoftJsons.toMap(text);
        }
        return Collections.emptyMap();
    }

    private SoftGuideField toGuideField(SoftPackageProfileField item) {
        return SoftGuideField.builder()
                .fieldKey(item.getFieldKey())
                .fieldLabel(item.getFieldLabel())
                .fieldScope(item.getFieldScope())
                .componentType(item.getComponentType())
                .groupName(item.getGroupName())
                .fieldDescription(item.getFieldDescription())
                .sortOrder(item.getSortOrder())
                .requiredFlag(item.getRequiredFlag())
                .defaultValue(item.getDefaultValue())
                .options(SoftJsons.toMapList(item.getOptionsJson()))
                .validation(SoftJsons.toMap(item.getValidationJson()))
                .condition(SoftJsons.toMap(item.getConditionJson()))
                .targetPath(item.getTargetPath())
                .metadata(SoftJsons.toMap(item.getMetadataJson()))
                .build();
    }

    private SoftGuideTemplate toGuideTemplate(SoftPackageProfileTemplate item) {
        return SoftGuideTemplate.builder()
                .templateScope(item.getTemplateScope())
                .templateCode(item.getTemplateCode())
                .templateName(item.getTemplateName())
                .templatePath(item.getTemplatePath())
                .templateEngine(item.getTemplateEngine())
                .templateContent(item.getTemplateContent())
                .sortOrder(item.getSortOrder())
                .metadata(SoftJsons.toMap(item.getMetadataJson()))
                .build();
    }

    private SoftGuideField mapToGuideField(Map<String, Object> source) {
        return SoftGuideField.builder()
                .fieldKey(stringValue(source.get("fieldKey")))
                .fieldLabel(stringValue(source.get("fieldLabel")))
                .fieldScope(stringValue(source.get("fieldScope")))
                .componentType(stringValue(source.get("componentType")))
                .groupName(stringValue(source.get("groupName")))
                .fieldDescription(stringValue(source.get("fieldDescription")))
                .sortOrder(intValue(source.get("sortOrder")))
                .requiredFlag(boolValue(source.get("requiredFlag")))
                .defaultValue(source.get("defaultValue"))
                .options(castListMap(source.get("options")))
                .validation(castMap(source.get("validation")))
                .condition(castMap(source.get("condition")))
                .targetPath(stringValue(source.get("targetPath")))
                .metadata(castMap(source.get("metadata")))
                .build();
    }

    private SoftGuideTemplate mapToGuideTemplate(Map<String, Object> source) {
        return SoftGuideTemplate.builder()
                .templateScope(stringValue(source.get("templateScope")))
                .templateCode(stringValue(source.get("templateCode")))
                .templateName(stringValue(source.get("templateName")))
                .templatePath(stringValue(source.get("templatePath")))
                .templateEngine(stringValue(source.get("templateEngine")))
                .templateContent(stringValue(source.get("templateContent")))
                .sortOrder(intValue(source.get("sortOrder")))
                .metadata(castMap(source.get("metadata")))
                .build();
    }

    private List<Map<String, Object>> castListMap(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                result.add(castMap(item));
            }
            return result;
        }
        if (value instanceof String text) {
            return SoftJsons.toMapList(text);
        }
        return Collections.emptyList();
    }

    private void applyFieldOverride(SoftGuideField field, Map<String, Object> override) {
        if (override.isEmpty()) {
            return;
        }
        if (override.containsKey("fieldLabel")) field.setFieldLabel(stringValue(override.get("fieldLabel")));
        if (override.containsKey("groupName")) field.setGroupName(stringValue(override.get("groupName")));
        if (override.containsKey("fieldDescription")) field.setFieldDescription(stringValue(override.get("fieldDescription")));
        if (override.containsKey("defaultValue")) field.setDefaultValue(override.get("defaultValue"));
        if (override.containsKey("requiredFlag")) field.setRequiredFlag(boolValue(override.get("requiredFlag")));
        if (override.containsKey("componentType")) field.setComponentType(stringValue(override.get("componentType")));
        if (override.containsKey("sortOrder")) field.setSortOrder(intValue(override.get("sortOrder")));
        if (override.containsKey("options")) field.setOptions(castListMap(override.get("options")));
        if (override.containsKey("validation")) field.setValidation(castMap(override.get("validation")));
        if (override.containsKey("condition")) field.setCondition(castMap(override.get("condition")));
        if (override.containsKey("targetPath")) field.setTargetPath(stringValue(override.get("targetPath")));
        if (override.containsKey("metadata")) field.setMetadata(castMap(override.get("metadata")));
    }

    private void overrideVersionScripts(SoftPackageVersion version, Map<String, String> renderedScripts, Map<String, Object> variables) {
        if (version == null) {
            return;
        }
        putScript(renderedScripts, "INSTALL_SCRIPT", version.getInstallScript(), variables);
        putScript(renderedScripts, "UNINSTALL_SCRIPT", version.getUninstallScript(), variables);
        putScript(renderedScripts, "START_SCRIPT", version.getStartScript(), variables);
        putScript(renderedScripts, "STOP_SCRIPT", version.getStopScript(), variables);
        putScript(renderedScripts, "RESTART_SCRIPT", version.getRestartScript(), variables);
        putScript(renderedScripts, "STATUS_SCRIPT", version.getStatusScript(), variables);
        putScript(renderedScripts, "SERVICE_REGISTER_SCRIPT", version.getServiceRegisterScript(), variables);
        putScript(renderedScripts, "SERVICE_UNREGISTER_SCRIPT", version.getServiceUnregisterScript(), variables);
    }

    private void putScript(Map<String, String> renderedScripts, String code, String content, Map<String, Object> variables) {
        if (content != null && !content.isBlank()) {
            renderedScripts.put(code, SoftCommandSupport.renderTemplate(normalizeArtifactTemplate(content), variables));
        }
    }

    private String normalizeArtifactTemplate(String content) {
        return content
                .replace("${installPath}/artifact.bin", "${artifactPath}")
                .replace("${installPath}\\artifact.bin", "${artifactPath}")
                .replace("${installPath}/artifact.exe", "${artifactPath}")
                .replace("${installPath}\\artifact.exe", "${artifactPath}");
    }

    private String resolveArtifactPath(SoftPackageVersion version, Map<String, Object> variables) {
        String installPath = stringValue(variables.get("installPath"));
        if (blank(installPath)) {
            return null;
        }
        String fileName = resolveArtifactFileName(version);
        if (blank(fileName)) {
            return installPath + "/artifact.bin";
        }
        return installPath + "/" + fileName;
    }

    private String resolveArtifactFileName(SoftPackageVersion version) {
        Map<String, Object> metadata = SoftJsons.toMap(version == null ? null : version.getMetadataJson());
        Object metadataFileName = metadata.get("artifactFileName");
        if (metadataFileName != null && !String.valueOf(metadataFileName).isBlank()) {
            return String.valueOf(metadataFileName);
        }
        List<String> downloadUrls = version == null ? Collections.emptyList() : version.getDownloadUrls();
        if ((downloadUrls == null || downloadUrls.isEmpty()) && version != null) {
            downloadUrls = SoftJsons.toStringList(version.getDownloadUrlsJson());
        }
        if (downloadUrls != null && !downloadUrls.isEmpty()) {
            try {
                URI uri = URI.create(downloadUrls.getFirst());
                String path = uri.getPath();
                if (path != null && !path.isBlank()) {
                    String fileName = path.substring(path.lastIndexOf('/') + 1);
                    if (!fileName.isBlank()) {
                        return fileName;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return "artifact.bin";
    }

    private List<String> resolvePathList(String json, SoftPackageProfile profile, String metadataKey) {
        if (json != null && !json.isBlank()) {
            return new ArrayList<>(SoftJsons.toStringList(json));
        }
        if (profile == null) {
            return new ArrayList<>();
        }
        Object value = SoftJsons.toMap(profile.getMetadataJson()).get(metadataKey);
        if (value instanceof List<?> list) {
            return new ArrayList<>(list.stream().filter(Objects::nonNull).map(String::valueOf).toList());
        }
        return new ArrayList<>();
    }

    private List<String> renderPathList(List<String> paths, Map<String, Object> variables) {
        return new ArrayList<>(paths.stream()
                .filter(Objects::nonNull)
                .map(item -> SoftCommandSupport.renderTemplate(item, variables))
                .filter(item -> !item.isBlank())
                .toList());
    }

    private Map<String, Object> scopeMap(String scope, Map<String, Object> installValues, Map<String, Object> serviceValues, Map<String, Object> configValues) {
        return switch (normalizeScope(scope)) {
            case "service" -> serviceValues;
            case "config" -> configValues;
            default -> installValues;
        };
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "install";
        }
        String value = scope.trim().toLowerCase();
        if (value.contains("service")) return "service";
        if (value.contains("config")) return "config";
        return "install";
    }

    private boolean isConfigTemplate(String scope) {
        if (scope == null) {
            return false;
        }
        String value = scope.trim().toUpperCase();
        return value.equals("CONFIG_TEMPLATE") || value.equals("ENV_TEMPLATE");
    }

    private Object renderDefaultValue(Object value, Map<String, Object> variables) {
        if (value instanceof String text) {
            return SoftCommandSupport.renderTemplate(text, variables);
        }
        return value;
    }

    private void validateField(SoftGuideField field, Object value) {
        if (Boolean.TRUE.equals(field.getRequiredFlag()) && blank(value)) {
            throw new IllegalStateException("字段必填: " + field.getFieldLabel());
        }
        if (blank(value) || field.getValidation() == null || field.getValidation().isEmpty()) {
            return;
        }
        Map<String, Object> validation = field.getValidation();
        if (validation.containsKey("min") || validation.containsKey("max")) {
            double number = Double.parseDouble(String.valueOf(value));
            if (validation.containsKey("min") && number < Double.parseDouble(String.valueOf(validation.get("min")))) {
                throw new IllegalStateException("字段过小: " + field.getFieldLabel());
            }
            if (validation.containsKey("max") && number > Double.parseDouble(String.valueOf(validation.get("max")))) {
                throw new IllegalStateException("字段过大: " + field.getFieldLabel());
            }
        }
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Collections.emptyMap() : value;
    }

    private List<String> distinct(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private boolean blank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer intValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private Boolean boolValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
