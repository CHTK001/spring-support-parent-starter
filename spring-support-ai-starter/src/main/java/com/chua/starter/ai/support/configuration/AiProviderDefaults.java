package com.chua.starter.ai.support.configuration;

import com.chua.starter.ai.support.properties.AiProperties;
import com.chua.starter.ai.support.properties.ProviderProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public final class AiProviderDefaults {

    public static final String DEFAULT_PROVIDER = "siliconflow";
    public static final String DEFAULT_BASE_URL = "https://api.siliconflow.cn/v1";
    public static final String DEFAULT_ENV_KEY = "SILICONFLOW_API_KEY";

    private AiProviderDefaults() {
    }

    public static AiProperties normalize(AiProperties properties) {
        if (properties == null) {
            return null;
        }
        if (!StringUtils.hasText(properties.getDefaultProvider())) {
            properties.setDefaultProvider(DEFAULT_PROVIDER);
        }
        ProviderProperties provider = properties.getProviders().computeIfAbsent(DEFAULT_PROVIDER, key -> new ProviderProperties());
        if (!StringUtils.hasText(provider.getBaseUrl())) {
            provider.setBaseUrl(DEFAULT_BASE_URL);
        }
        if (!StringUtils.hasText(provider.getEnvKey())) {
            provider.setEnvKey(DEFAULT_ENV_KEY);
        }
        if (!StringUtils.hasText(provider.getApiKey()) && !StringUtils.hasText(provider.getAppKey())) {
            String resolved = resolveAppKey(provider);
            if (StringUtils.hasText(resolved)) {
                provider.setApiKey(resolved);
                provider.setAppKey(resolved);
            }
        }
        return properties;
    }

    private static String resolveAppKey(ProviderProperties provider) {
        String envKey = provider.getEnvKey();
        if (StringUtils.hasText(envKey)) {
            String envValue = System.getenv(envKey.trim());
            if (StringUtils.hasText(envValue)) {
                return envValue.trim();
            }
        }
        for (Path path : candidatePaths(provider == null ? null : provider.getAppKeyPath())) {
            try {
                if (Files.isRegularFile(path)) {
                    String text = Files.readString(path, StandardCharsets.UTF_8).trim();
                    if (StringUtils.hasText(text)) {
                        return text;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static List<Path> candidatePaths(String explicitPath) {
        List<Path> paths = new ArrayList<>();
        if (StringUtils.hasText(explicitPath)) {
            paths.add(Paths.get(explicitPath.trim()));
        }
        String userHome = System.getProperty("user.home");
        if (StringUtils.hasText(userHome)) {
            paths.add(Paths.get(userHome, ".siliconflow", "appkey"));
            paths.add(Paths.get(userHome, ".siliconflow", "appkey.txt"));
            paths.add(Paths.get(userHome, ".config", "siliconflow", "appkey"));
            paths.add(Paths.get(userHome, ".config", "siliconflow", "appkey.txt"));
        }
        paths.add(Paths.get("config", "siliconflow.appkey"));
        paths.add(Paths.get("config", "siliconflow.key"));
        return paths;
    }
}
