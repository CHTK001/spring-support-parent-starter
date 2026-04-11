package com.chua.starter.soft.support.spi.impl;

import com.chua.starter.soft.support.config.SoftManagementProperties;
import com.chua.starter.soft.support.model.SoftExecutionContext;
import com.chua.starter.soft.support.model.SoftLogWatchHandle;
import com.chua.starter.soft.support.service.SoftTargetCommandExecutor;
import com.chua.starter.soft.support.spi.SoftLogStreamProvider;
import com.chua.starter.soft.support.util.SoftCommandSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class DefaultSoftLogStreamProvider implements SoftLogStreamProvider {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final SoftManagementProperties properties;
    private final LocalSoftTargetCommandExecutor localExecutor;
    private final SshSoftTargetCommandExecutor sshExecutor;
    private final WinRmSoftTargetCommandExecutor winRmExecutor;

    public DefaultSoftLogStreamProvider(
            SoftManagementProperties properties,
            LocalSoftTargetCommandExecutor localExecutor,
            SshSoftTargetCommandExecutor sshExecutor,
            WinRmSoftTargetCommandExecutor winRmExecutor
    ) {
        this.properties = properties;
        this.localExecutor = localExecutor;
        this.sshExecutor = sshExecutor;
        this.winRmExecutor = winRmExecutor;
    }

    @Override
    public List<String> readRecent(SoftExecutionContext context, String logPath, int lines) throws Exception {
        if (logPath == null || logPath.isBlank()) {
            return Collections.emptyList();
        }
        if ("LOCAL".equalsIgnoreCase(context.getTarget().getTargetType())) {
            if (!Files.exists(Path.of(logPath))) {
                return Collections.emptyList();
            }
            List<String> allLines = Files.readAllLines(Path.of(logPath), StandardCharsets.UTF_8);
            int fromIndex = Math.max(0, allLines.size() - lines);
            return allLines.subList(fromIndex, allLines.size());
        }
        SoftTargetCommandExecutor executor = executor(context);
        String command = SoftCommandSupport.isWindows(context.getTarget().getOsType())
                ? "Get-Content -Tail " + lines + " -Path " + SoftCommandSupport.powershellQuote(logPath)
                : "tail -n " + lines + " " + SoftCommandSupport.bashQuote(logPath);
        String output = executor.execute(context.getTarget(), command).getOutput();
        return output == null ? Collections.emptyList() : List.of(output.split("\\R"));
    }

    @Override
    public SoftLogWatchHandle startWatch(
            Long watchId,
            SoftExecutionContext context,
            String logPath,
            Consumer<String> onLine,
            Consumer<Throwable> onError,
            Consumer<String> onClose
    ) {
        List<String>[] previousLines = new List[]{new ArrayList<>()};
        var future = scheduler.scheduleAtFixedRate(() -> {
            try {
                List<String> current = new ArrayList<>(readRecent(context, logPath, properties.getDefaultLogTailLines()));
                for (String line : diffLines(previousLines[0], current)) {
                    if (!line.isBlank()) {
                        onLine.accept(line);
                    }
                }
                previousLines[0] = current;
            } catch (Throwable throwable) {
                onError.accept(throwable);
            }
        }, 0L, properties.getLogPollIntervalMillis(), TimeUnit.MILLISECONDS);
        return new SoftLogWatchHandle(watchId, () -> {
            future.cancel(true);
            onClose.accept("stopped");
        });
    }

    private List<String> diffLines(List<String> previous, List<String> current) {
        if (previous == null || previous.isEmpty()) {
            return current;
        }
        if (current.isEmpty()) {
            return Collections.emptyList();
        }
        int overlap = Math.min(previous.size(), current.size());
        for (int size = overlap; size > 0; size--) {
            List<String> previousSuffix = previous.subList(previous.size() - size, previous.size());
            List<String> currentPrefix = current.subList(0, size);
            if (previousSuffix.equals(currentPrefix)) {
                return current.subList(size, current.size());
            }
        }
        if (current.size() >= previous.size() && current.subList(0, previous.size()).equals(previous)) {
            return current.subList(previous.size(), current.size());
        }
        return current;
    }

    private SoftTargetCommandExecutor executor(SoftExecutionContext context) {
        String type = context.getTarget().getTargetType();
        if (localExecutor.supports(type)) {
            return localExecutor;
        }
        if (sshExecutor.supports(type)) {
            return sshExecutor;
        }
        return winRmExecutor;
    }
}
