package com.chua.starter.soft.support.spi.impl;

import com.chua.starter.soft.support.entity.SoftPackage;
import com.chua.starter.soft.support.entity.SoftPackageVersion;
import com.chua.starter.soft.support.entity.SoftRepository;
import com.chua.starter.soft.support.model.SoftRepositorySource;
import com.chua.starter.soft.support.spi.SoftRepositorySyncProvider;
import com.chua.starter.soft.support.util.SoftArtifactRepositorySupport;
import com.chua.starter.soft.support.util.SoftJsons;
import com.chua.starter.soft.support.util.SoftRepositoryPayloadParser;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class LocalDirSoftRepositorySyncProvider implements SoftRepositorySyncProvider {

    @Override
    public boolean supports(String repositoryType) {
        return "LOCAL_DIR".equalsIgnoreCase(repositoryType);
    }

    @Override
    public RepositorySyncPayload sync(SoftRepository repository, SoftRepositorySource source) throws Exception {
        String localDirectory = source == null ? repository.getLocalDirectory() : source.getLocalDirectory();
        if (localDirectory == null || localDirectory.isBlank()) {
            return RepositorySyncPayload.empty();
        }
        Path directory = Path.of(localDirectory);
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            throw new IllegalStateException("本地仓库目录不存在: " + localDirectory);
        }
        Path indexFile = directory.resolve("index.json");
        if (Files.isRegularFile(indexFile)) {
            JsonNode root = SoftJsons.readTree(Files.readString(indexFile));
            return SoftRepositoryPayloadParser.parse(repository, root);
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            Map<String, SoftPackage> packages = new LinkedHashMap<>();
            Map<String, SoftPackageVersion> versions = new LinkedHashMap<>();
            for (Path file : stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList()) {
                String fileName = file.getFileName().toString();
                if (fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
                    JsonNode root = SoftJsons.readTree(Files.readString(file));
                    RepositorySyncPayload payload = SoftRepositoryPayloadParser.parse(repository, root);
                    payload.packages().forEach(item -> packages.put(item.getPackageCode(), item));
                    payload.versions().forEach(item -> versions.put(item.getPackageCode() + "#" + item.getVersionCode(), item));
                    continue;
                }
                if (!SoftArtifactRepositorySupport.isArtifactFile(fileName)) {
                    continue;
                }
                RepositorySyncPayload payload = SoftArtifactRepositorySupport.parseArtifact(
                        repository,
                        fileName,
                        file.toString(),
                        file.toUri().toString(),
                        "LOCAL_SCAN"
                );
                payload.packages().forEach(item -> packages.put(item.getPackageCode(), item));
                payload.versions().forEach(item -> versions.put(item.getPackageCode() + "#" + item.getVersionCode(), item));
            }
            return new RepositorySyncPayload(new ArrayList<>(packages.values()), new ArrayList<>(versions.values()));
        }
    }
}
