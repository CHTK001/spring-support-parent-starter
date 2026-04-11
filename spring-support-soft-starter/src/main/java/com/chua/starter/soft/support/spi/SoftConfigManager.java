package com.chua.starter.soft.support.spi;

import com.chua.starter.soft.support.model.SoftExecutionContext;

public interface SoftConfigManager {

    String read(SoftExecutionContext context, String configPath) throws Exception;

    void write(SoftExecutionContext context, String configPath, String content) throws Exception;
}
