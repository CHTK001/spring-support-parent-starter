package com.chua.starter.soft.support.util;

import com.chua.starter.soft.support.entity.SoftPackage;
import com.chua.starter.soft.support.entity.SoftPackageVersion;
import com.chua.starter.soft.support.entity.SoftRepository;
import com.chua.starter.soft.support.spi.SoftRepositorySyncProvider;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SoftRepositoryPayloadParser {

    private SoftRepositoryPayloadParser() {
    }

    public static SoftRepositorySyncProvider.RepositorySyncPayload parse(SoftRepository repository, JsonNode root) {
        JsonNode packagesNode = root.isArray() ? root : root.path("packages");
        List<SoftPackage> packages = new ArrayList<>();
        List<SoftPackageVersion> versions = new ArrayList<>();
        if (!packagesNode.isArray()) {
            return new SoftRepositorySyncProvider.RepositorySyncPayload(packages, versions);
        }
        for (JsonNode item : packagesNode) {
            SoftPackage softPackage = new SoftPackage();
            softPackage.setSoftRepositoryId(repository.getSoftRepositoryId());
            String rawPackageCode = text(item, "packageCode", "code");
            softPackage.setProfileCode(text(item, "profileCode", "profile"));
            softPackage.setPackageName(text(item, "packageName", "name"));
            softPackage.setPackageCategory(text(item, "packageCategory", "category"));
            softPackage.setOsType(text(item, "osType"));
            softPackage.setArchitecture(text(item, "architecture"));
            softPackage.setDescription(text(item, "description"));
            softPackage.setIconUrl(text(item, "iconUrl"));
            String softwareKey = SoftArtifactRepositorySupport.normalizeSoftwareKey(
                    text(item, "softwareKey", "softwareKeyCode", "software")
            );
            if (softwareKey == null) {
                softwareKey = SoftArtifactRepositorySupport.normalizeSoftwareKey(rawPackageCode);
            }
            softPackage.setSoftwareKey(softwareKey);
            softPackage.setPackageCode(softwareKey == null ? rawPackageCode : softwareKey);
            packages.add(softPackage);

            JsonNode versionNodes = item.path("versions");
            if (!versionNodes.isArray()) {
                continue;
            }
            for (JsonNode versionNode : versionNodes) {
                SoftPackageVersion version = new SoftPackageVersion();
                version.setPackageCode(softPackage.getPackageCode());
                version.setVersionCode(text(versionNode, "versionCode", "code"));
                version.setVersionName(text(versionNode, "versionName", "name"));
                version.setDownloadUrlsJson(readStringArray(versionNode.path("downloadUrls"), versionNode.path("downloadUrl")));
                version.setMd5(text(versionNode, "md5"));
                version.setSha256(text(versionNode, "sha256"));
                version.setInstallScript(text(versionNode, "installScript"));
                version.setUninstallScript(text(versionNode, "uninstallScript"));
                version.setStartScript(text(versionNode, "startScript"));
                version.setStopScript(text(versionNode, "stopScript"));
                version.setRestartScript(text(versionNode, "restartScript"));
                version.setStatusScript(text(versionNode, "statusScript"));
                version.setServiceRegisterScript(text(versionNode, "serviceRegisterScript"));
                version.setServiceUnregisterScript(text(versionNode, "serviceUnregisterScript"));
                version.setLogPathsJson(readStringArray(versionNode.path("logPaths"), null));
                version.setConfigPathsJson(readStringArray(versionNode.path("configPaths"), null));
                version.setCapabilityFlagsJson(readStringArray(versionNode.path("capabilityFlags"), null));
                version.setMetadataJson(mergeMetadata(
                        versionNode.path("metadata"),
                        softPackage.getSoftwareKey(),
                        softPackage.getOsType(),
                        softPackage.getArchitecture()
                ));
                version.setEnabled(versionNode.path("enabled").asBoolean(true));
                versions.add(version);
            }
        }
        return new SoftRepositorySyncProvider.RepositorySyncPayload(packages, versions);
    }

    private static String text(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static String readStringArray(JsonNode arrayNode, JsonNode singleValueNode) {
        List<String> values = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                String value = item.asText(null);
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            }
        }
        if (values.isEmpty() && singleValueNode != null && !singleValueNode.isMissingNode() && !singleValueNode.isNull()) {
            String value = singleValueNode.asText(null);
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        return SoftJsons.toJson(values);
    }

    private static String jsonText(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.toString();
    }

    private static String mergeMetadata(
            JsonNode node,
            String softwareKey,
            String packageOsType,
            String packageArchitecture
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>(SoftJsons.toMap(jsonText(node)));
        if (softwareKey != null && !softwareKey.isBlank()) {
            metadata.put("softwareKey", softwareKey);
        }
        if (packageOsType != null && !packageOsType.isBlank()) {
            metadata.put("packageOsType", SoftArtifactRepositorySupport.normalizeOsType(packageOsType));
        }
        if (packageArchitecture != null && !packageArchitecture.isBlank()) {
            metadata.put("packageArchitecture", SoftArtifactRepositorySupport.normalizeArchitecture(packageArchitecture));
        }
        return metadata.isEmpty() ? null : SoftJsons.toJson(metadata);
    }
}
