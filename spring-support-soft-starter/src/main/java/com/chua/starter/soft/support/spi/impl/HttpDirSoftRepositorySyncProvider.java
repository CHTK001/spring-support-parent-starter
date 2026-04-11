package com.chua.starter.soft.support.spi.impl;

import com.chua.starter.soft.support.entity.SoftPackage;
import com.chua.starter.soft.support.entity.SoftPackageVersion;
import com.chua.starter.soft.support.entity.SoftRepository;
import com.chua.starter.soft.support.model.SoftRepositorySource;
import com.chua.starter.soft.support.spi.SoftRepositorySyncProvider;
import com.chua.starter.soft.support.util.SoftArtifactRepositorySupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class HttpDirSoftRepositorySyncProvider implements SoftRepositorySyncProvider {

    private static final Pattern HREF_PATTERN = Pattern.compile("href\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Set<String> SUPPORTED_TYPES = Set.of("HTTP_DIR", "RPM_REPO", "MIRROR_REPO");

    @Override
    public boolean supports(String repositoryType) {
        return repositoryType != null
                && SUPPORTED_TYPES.contains(repositoryType.trim().toUpperCase());
    }

    @Override
    public RepositorySyncPayload sync(SoftRepository repository, SoftRepositorySource source) throws Exception {
        String sourceUrl = source == null ? repository.getRepositoryUrl() : source.getSourceUrl();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return RepositorySyncPayload.empty();
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(sourceUrl))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8");
        applyAuthorization(repository, requestBuilder);
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        URI baseUri = URI.create(sourceUrl.endsWith("/") ? sourceUrl : sourceUrl + "/");
        Map<String, SoftPackage> packages = new LinkedHashMap<>();
        Map<String, SoftPackageVersion> versions = new LinkedHashMap<>();
        for (URI artifactUri : resolveArtifactUris(baseUri, response.body())) {
            String fileName = artifactUri.getPath() == null ? "" : artifactUri.getPath().substring(artifactUri.getPath().lastIndexOf('/') + 1);
            if (!SoftArtifactRepositorySupport.isArtifactFile(fileName)) {
                continue;
            }
            RepositorySyncPayload payload = SoftArtifactRepositorySupport.parseArtifact(
                    repository,
                    fileName,
                    artifactUri.toString(),
                    artifactUri.toString(),
                    source == null ? "HTTP_DIR" : source.getSourceType()
            );
            payload.packages().forEach(item -> packages.put(item.getPackageCode(), item));
            payload.versions().forEach(item -> versions.put(item.getPackageCode() + "#" + item.getVersionCode(), item));
        }
        if (packages.isEmpty() && SoftArtifactRepositorySupport.isArtifactFile(sourceUrl)) {
            String fileName = sourceUrl.substring(sourceUrl.lastIndexOf('/') + 1);
            RepositorySyncPayload payload = SoftArtifactRepositorySupport.parseArtifact(
                    repository,
                    fileName,
                    sourceUrl,
                    sourceUrl,
                    source == null ? "HTTP_DIR" : source.getSourceType()
            );
            return payload;
        }
        return new RepositorySyncPayload(new ArrayList<>(packages.values()), new ArrayList<>(versions.values()));
    }

    private void applyAuthorization(SoftRepository repository, HttpRequest.Builder requestBuilder) {
        if ("BASIC".equalsIgnoreCase(repository.getAuthType())) {
            String raw = repository.getUsername() + ":" + repository.getPassword();
            requestBuilder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(raw.getBytes()));
        }
        if ("BEARER".equalsIgnoreCase(repository.getAuthType()) && repository.getToken() != null) {
            requestBuilder.header("Authorization", "Bearer " + repository.getToken());
        }
    }

    private List<URI> resolveArtifactUris(URI baseUri, String body) {
        Set<URI> results = new LinkedHashSet<>();
        Matcher matcher = HREF_PATTERN.matcher(body == null ? "" : body);
        while (matcher.find()) {
            String href = matcher.group(1);
            if (href == null || href.isBlank() || href.startsWith("#") || href.startsWith("?") || href.startsWith("../")) {
                continue;
            }
            URI resolved = baseUri.resolve(href);
            String path = resolved.getPath();
            if (path == null || path.endsWith("/")) {
                continue;
            }
            results.add(resolved);
        }
        return new ArrayList<>(results);
    }
}
