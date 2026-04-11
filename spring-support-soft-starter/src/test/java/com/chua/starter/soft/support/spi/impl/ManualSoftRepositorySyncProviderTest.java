package com.chua.starter.soft.support.spi.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chua.starter.soft.support.entity.SoftRepository;
import com.chua.starter.soft.support.model.SoftRepositorySource;
import org.junit.jupiter.api.Test;

class ManualSoftRepositorySyncProviderTest {

    private final ManualSoftRepositorySyncProvider provider = new ManualSoftRepositorySyncProvider();

    @Test
    void shouldReturnEmptyPayloadForManualRepository() {
        SoftRepository repository = new SoftRepository();
        repository.setRepositoryType("MANUAL");
        SoftRepositorySource source = SoftRepositorySource.builder()
                .sourceType("MANUAL")
                .enabled(Boolean.TRUE)
                .build();

        var payload = provider.sync(repository, source);

        assertTrue(provider.supports("MANUAL"));
        assertEquals(0, payload.packages().size());
        assertEquals(0, payload.versions().size());
    }
}
