package com.chua.starter.soft.support.spi.impl;

import com.chua.starter.soft.support.entity.SoftRepository;
import com.chua.starter.soft.support.model.SoftRepositorySource;
import com.chua.starter.soft.support.spi.SoftRepositorySyncProvider;
import org.springframework.stereotype.Component;

@Component
public class ManualSoftRepositorySyncProvider implements SoftRepositorySyncProvider {

    @Override
    public boolean supports(String repositoryType) {
        return "MANUAL".equalsIgnoreCase(repositoryType);
    }

    @Override
    public RepositorySyncPayload sync(SoftRepository repository, SoftRepositorySource source) {
        return RepositorySyncPayload.empty();
    }
}
