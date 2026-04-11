package com.chua.starter.soft.support.spi;

import com.chua.starter.soft.support.entity.SoftPackage;
import com.chua.starter.soft.support.entity.SoftPackageVersion;
import com.chua.starter.soft.support.entity.SoftRepository;
import com.chua.starter.soft.support.model.SoftRepositorySource;
import java.util.List;

public interface SoftRepositorySyncProvider {

    boolean supports(String repositoryType);

    RepositorySyncPayload sync(SoftRepository repository, SoftRepositorySource source) throws Exception;

    record RepositorySyncPayload(List<SoftPackage> packages, List<SoftPackageVersion> versions) {
        public static RepositorySyncPayload empty() {
            return new RepositorySyncPayload(List.of(), List.of());
        }
    }
}
