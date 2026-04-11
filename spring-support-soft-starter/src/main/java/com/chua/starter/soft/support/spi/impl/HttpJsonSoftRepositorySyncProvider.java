package com.chua.starter.soft.support.spi.impl;

import com.chua.starter.soft.support.entity.SoftPackage;
import com.chua.starter.soft.support.entity.SoftPackageVersion;
import com.chua.starter.soft.support.entity.SoftRepository;
import com.chua.starter.soft.support.model.SoftRepositorySource;
import com.chua.starter.soft.support.spi.SoftRepositorySyncProvider;
import com.chua.starter.soft.support.util.SoftJsons;
import com.chua.starter.soft.support.util.SoftRepositoryPayloadParser;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class HttpJsonSoftRepositorySyncProvider implements SoftRepositorySyncProvider {

    @Override
    public boolean supports(String repositoryType) {
        return "HTTP_JSON".equalsIgnoreCase(repositoryType);
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
                .header("Accept", "application/json");
        if ("BASIC".equalsIgnoreCase(repository.getAuthType())) {
            String raw = repository.getUsername() + ":" + repository.getPassword();
            requestBuilder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(raw.getBytes()));
        }
        if ("BEARER".equalsIgnoreCase(repository.getAuthType()) && repository.getToken() != null) {
            requestBuilder.header("Authorization", "Bearer " + repository.getToken());
        }
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode root = SoftJsons.readTree(response.body());
        return SoftRepositoryPayloadParser.parse(repository, root);
    }
}
