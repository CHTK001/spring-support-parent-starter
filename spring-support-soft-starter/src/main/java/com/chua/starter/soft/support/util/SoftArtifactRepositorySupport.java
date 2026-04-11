package com.chua.starter.soft.support.util;

import com.chua.starter.soft.support.entity.SoftPackage;
import com.chua.starter.soft.support.entity.SoftPackageVersion;
import com.chua.starter.soft.support.entity.SoftRepository;
import com.chua.starter.soft.support.spi.SoftRepositorySyncProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SoftArtifactRepositorySupport {

    private static final List<String> ARTIFACT_EXTENSIONS = List.of(
            ".tar.gz", ".tgz", ".tar", ".zip", ".7z", ".rpm", ".deb", ".exe", ".msi", ".bin"
    );
    private static final Pattern VERSION_PATTERN = Pattern.compile("(?i)(?:^|[-_.])((?:v)?\\d+(?:[._-]\\d+){1,})(?=$|[-_.])");
    private static final Set<String> OS_TOKENS = Set.of(
            "windows", "win", "linux", "mac", "macos", "darwin", "osx"
    );
    private static final Set<String> ARCH_TOKENS = Set.of(
            "amd64", "x64", "x86_64", "x86", "arm64", "aarch64"
    );

    private SoftArtifactRepositorySupport() {
    }

    public static boolean isArtifactFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return ARTIFACT_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
    }

    public static SoftRepositorySyncProvider.RepositorySyncPayload parseArtifact(
            SoftRepository repository,
            String fileName,
            String artifactPath,
            String downloadUrl,
            String artifactKind
    ) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        String extension = ARTIFACT_EXTENSIONS.stream()
                .filter(lowerName::endsWith)
                .findFirst()
                .orElse("");
        String baseName = fileName.substring(0, fileName.length() - extension.length());
        List<String> tokens = new ArrayList<>();
        for (String token : baseName.split("[-_.]+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        String osType = detectOs(tokens);
        String architecture = detectArchitecture(tokens);
        VersionMatch versionMatch = detectVersion(baseName);
        String versionCode = versionMatch == null ? "latest" : versionMatch.versionCode();
        String packageCode = normalizeArtifactCode(baseName, versionMatch);
        String softwareKey = normalizeSoftwareKey(packageCode);
        boolean standaloneExecutable = ".exe".equals(extension)
                && !lowerName.contains("setup")
                && !lowerName.contains("install");

        SoftPackage softPackage = new SoftPackage();
        softPackage.setSoftRepositoryId(repository.getSoftRepositoryId());
        softPackage.setPackageCode(packageCode);
        softPackage.setPackageName(buildPackageName(packageCode));
        softPackage.setPackageCategory(categoryOf(extension));
        softPackage.setOsType(osType);
        softPackage.setArchitecture(architecture);
        softPackage.setDescription("由安装包仓库自动解析: " + fileName);
        softPackage.setSoftwareKey(softwareKey);

        SoftPackageVersion version = new SoftPackageVersion();
        version.setPackageCode(packageCode);
        version.setVersionCode(versionCode);
        version.setVersionName(versionCode);
        version.setDownloadUrlsJson(SoftJsons.toJson(List.of(downloadUrl)));
        version.setInstallScript(defaultInstallScript(extension, osType, standaloneExecutable));
        version.setEnabled(Boolean.TRUE);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("artifactFileName", fileName);
        metadata.put("artifactPath", artifactPath);
        metadata.put("artifactExtension", extension);
        metadata.put("artifactKind", normalizeArtifactKind(artifactKind));
        metadata.put("softwareKey", softwareKey);
        metadata.put("packageOsType", normalizeOsType(osType));
        metadata.put("packageArchitecture", normalizeArchitecture(architecture));
        metadata.put("standaloneExecutable", standaloneExecutable);
        version.setMetadataJson(SoftJsons.toJson(metadata));
        return new SoftRepositorySyncProvider.RepositorySyncPayload(List.of(softPackage), List.of(version));
    }

    public static String normalizeSoftwareKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = normalizeArtifactCode(value, detectVersion(value));
        return normalizeCode(normalized);
    }

    public static String buildPackageIdentity(String packageCode, String osType, String architecture) {
        String normalizedPackageCode = normalizeCode(packageCode);
        if (normalizedPackageCode == null || normalizedPackageCode.isBlank()) {
            normalizedPackageCode = "artifact";
        }
        String normalizedOsType = normalizeOsType(osType);
        String normalizedArchitecture = normalizeArchitecture(architecture);
        return normalizedPackageCode
                + "#"
                + (normalizedOsType == null ? "*" : normalizedOsType)
                + "#"
                + (normalizedArchitecture == null ? "*" : normalizedArchitecture);
    }

    public static String normalizeOsType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("win")) {
            return "windows";
        }
        if (normalized.contains("linux")) {
            return "linux";
        }
        if (normalized.contains("mac") || normalized.contains("darwin") || normalized.contains("osx")) {
            return "macos";
        }
        return normalized;
    }

    public static String normalizeArchitecture(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (List.of("amd64", "x64", "x86_64").contains(normalized)) {
            return "amd64";
        }
        if (List.of("arm64", "aarch64").contains(normalized)) {
            return "arm64";
        }
        return normalized;
    }

    private static String detectOs(List<String> tokens) {
        for (String token : tokens) {
            String value = token.toLowerCase(Locale.ROOT);
            if (value.contains("win")) {
                return "windows";
            }
            if (value.contains("linux")) {
                return "linux";
            }
            if (value.contains("mac") || value.contains("darwin") || value.contains("osx")) {
                return "macos";
            }
        }
        return null;
    }

    private static String detectArchitecture(List<String> tokens) {
        for (String token : tokens) {
            String value = token.toLowerCase(Locale.ROOT);
            if (List.of("amd64", "x64", "x86_64").contains(value)) {
                return "amd64";
            }
            if (List.of("arm64", "aarch64").contains(value)) {
                return "arm64";
            }
        }
        return null;
    }

    private static VersionMatch detectVersion(String baseName) {
        Matcher matcher = VERSION_PATTERN.matcher(baseName);
        VersionMatch matched = null;
        while (matcher.find()) {
            String versionCode = matcher.group(1);
            if (versionCode == null || versionCode.isBlank()) {
                continue;
            }
            matched = new VersionMatch(versionCode, matcher.start(1), matcher.end(1));
        }
        return matched;
    }

    private static String normalizeCode(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private static String normalizeArtifactCode(String baseName, VersionMatch versionMatch) {
        String normalizedBase = baseName;
        if (versionMatch != null) {
            normalizedBase = (baseName.substring(0, versionMatch.start())
                    + "-"
                    + baseName.substring(versionMatch.end()))
                    .replaceAll("[-_.]{2,}", "-");
        }
        List<String> parts = new ArrayList<>();
        for (String token : normalizedBase.split("[-_.]+")) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String normalizedToken = token.toLowerCase(Locale.ROOT);
            if (OS_TOKENS.contains(normalizedToken) || ARCH_TOKENS.contains(normalizedToken)) {
                continue;
            }
            parts.add(token);
        }
        String packageCode = normalizeCode(parts.isEmpty() ? normalizedBase : String.join("-", parts));
        return packageCode == null || packageCode.isBlank() ? "artifact" : packageCode;
    }

    private static String readableName(String packageCode) {
        if (packageCode == null || packageCode.isBlank()) {
            return "未命名软件";
        }
        StringBuilder builder = new StringBuilder();
        for (String word : packageCode.split("-")) {
            if (word.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    private static String buildPackageName(String packageCode) {
        return readableName(packageCode);
    }

    private static String normalizeArtifactKind(String artifactKind) {
        if (artifactKind == null || artifactKind.isBlank()) {
            return "LOCAL_SCAN";
        }
        return artifactKind.trim().toUpperCase(Locale.ROOT);
    }

    private static String categoryOf(String extension) {
        if (List.of(".rpm", ".deb").contains(extension)) {
            return "系统安装包";
        }
        if (List.of(".zip", ".tar.gz", ".tgz", ".tar", ".7z").contains(extension)) {
            return "压缩归档";
        }
        return "安装程序";
    }

    private static String defaultInstallScript(String extension, String osType, boolean standaloneExecutable) {
        boolean windows = osType != null && osType.toLowerCase(Locale.ROOT).contains("win");
        if (windows) {
            if (".msi".equals(extension)) {
                return "Start-Process msiexec.exe -Wait -ArgumentList @('/i', ${artifactPath}, '/qn', '/norestart')";
            }
            if (".exe".equals(extension)) {
                return standaloneExecutable
                        ? "Write-Output ('standalone executable ready: ' + ${artifactPath})"
                        : "Start-Process -Wait -FilePath ${artifactPath} -ArgumentList @('/S', '/quiet', '/norestart')";
            }
            if (".zip".equals(extension)) {
                return "Expand-Archive -Force -Path ${artifactPath} -DestinationPath ${installPath}";
            }
            return "Write-Output ('artifact ready: ' + ${artifactPath})";
        }
        if (".rpm".equals(extension)) {
            return "rpm -Uvh --force ${artifactPath} || dnf install -y ${artifactPath} || yum localinstall -y ${artifactPath}";
        }
        if (".deb".equals(extension)) {
            return "dpkg -i ${artifactPath} || apt-get install -f -y";
        }
        if (".tar.gz".equals(extension) || ".tgz".equals(extension)) {
            return "tar -xzf ${artifactPath} -C ${installPath}";
        }
        if (".tar".equals(extension)) {
            return "tar -xf ${artifactPath} -C ${installPath}";
        }
        if (".zip".equals(extension) || ".7z".equals(extension)) {
            return "unzip -o ${artifactPath} -d ${installPath}";
        }
        return "chmod +x ${artifactPath} || true";
    }

    private record VersionMatch(String versionCode, int start, int end) {
    }
}
