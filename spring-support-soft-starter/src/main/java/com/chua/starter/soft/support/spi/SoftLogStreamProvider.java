package com.chua.starter.soft.support.spi;

import com.chua.starter.soft.support.model.SoftExecutionContext;
import com.chua.starter.soft.support.model.SoftLogWatchHandle;
import java.util.List;
import java.util.function.Consumer;

public interface SoftLogStreamProvider {

    List<String> readRecent(SoftExecutionContext context, String logPath, int lines) throws Exception;

    SoftLogWatchHandle startWatch(
            Long watchId,
            SoftExecutionContext context,
            String logPath,
            Consumer<String> onLine,
            Consumer<Throwable> onError,
            Consumer<String> onClose
    );
}
