package com.chua.starter.soft.support.service;

import com.chua.starter.soft.support.constants.SoftSocketEvents;
import com.chua.starter.soft.support.entity.SoftOperationLog;
import com.chua.starter.soft.support.entity.SoftPackage;
import com.chua.starter.soft.support.entity.SoftPackageVersion;
import com.chua.starter.soft.support.entity.SoftTarget;
import com.chua.starter.soft.support.enums.SoftOperationStage;
import com.chua.starter.soft.support.model.SoftRealtimePayload;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SoftExecutionReporter {

    private final SoftRealtimePublisher publisher;
    private final SoftOperationLog log;
    private final SoftPackage softPackage;
    private final SoftPackageVersion version;
    private final SoftTarget target;
    private final Integer installationId;
    private final Consumer<SoftOperationLog> logUpdater;
    private final List<String> outputLines = new ArrayList<>();

    public SoftExecutionReporter(
            SoftRealtimePublisher publisher,
            SoftOperationLog log,
            Integer installationId,
            SoftPackage softPackage,
            SoftPackageVersion version,
            SoftTarget target,
            Consumer<SoftOperationLog> logUpdater
    ) {
        this.publisher = publisher;
        this.log = log;
        this.installationId = installationId;
        this.softPackage = softPackage;
        this.version = version;
        this.target = target;
        this.logUpdater = logUpdater;
    }

    public void progress(SoftOperationStage stage, String message, String detail) {
        log.setOperationStage(stage.name());
        log.setProgressPercent(stage.progressPercent());
        if (message != null) {
            log.setOperationMessage(message);
        }
        log.setDetailMessage(detail);
        logUpdater.accept(log);
        publish(SoftSocketEvents.OPERATION_UPDATE, null, false);
        if ("INSTALL".equalsIgnoreCase(log.getOperationType()) || "UNINSTALL".equalsIgnoreCase(log.getOperationType())) {
            publish(SoftSocketEvents.INSTALL_PROGRESS, null, false);
        }
    }

    public void line(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        outputLines.add(line);
        publish(SoftSocketEvents.INSTALL_LOG, line, false);
    }

    public void finish(boolean success, String message, String detail, String output) {
        if (outputLines.isEmpty() && output != null && !output.isBlank()) {
            outputLines.add(output);
        }
        log.setOperationStatus(success ? "SUCCESS" : "FAILED");
        log.setOperationStage(SoftOperationStage.FINISH.name());
        log.setProgressPercent(SoftOperationStage.FINISH.progressPercent());
        log.setOperationMessage(message);
        log.setDetailMessage(detail);
        log.setOperationOutput(String.join(System.lineSeparator(), outputLines));
        log.setEndTime(LocalDateTime.now());
        logUpdater.accept(log);
        publish(SoftSocketEvents.OPERATION_UPDATE, null, true);
        if ("INSTALL".equalsIgnoreCase(log.getOperationType()) || "UNINSTALL".equalsIgnoreCase(log.getOperationType())) {
            publish(SoftSocketEvents.INSTALL_PROGRESS, null, true);
        }
    }

    public String aggregatedOutput() {
        return String.join(System.lineSeparator(), outputLines);
    }

    private void publish(String event, String line, boolean finished) {
        publisher.publish(
                event,
                SoftSocketEvents.RUNTIME_LOG.equals(event) ? installationId : log.getSoftOperationLogId(),
                SoftRealtimePayload.builder()
                        .operationId(log.getSoftOperationLogId())
                        .installationId(installationId)
                        .operationType(log.getOperationType())
                        .status(log.getOperationStatus())
                        .stage(log.getOperationStage())
                        .progressPercent(log.getProgressPercent())
                        .message(log.getOperationMessage())
                        .detail(log.getDetailMessage())
                        .line(line)
                        .targetType(target == null ? null : target.getTargetType())
                        .packageCode(softPackage == null ? null : softPackage.getPackageCode())
                        .versionCode(version == null ? null : version.getVersionCode())
                        .finished(finished)
                        .build()
        );
    }
}
