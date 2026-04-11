package com.chua.starter.soft.support.spi;

import com.chua.starter.soft.support.model.SoftExecutionContext;
import com.chua.starter.soft.support.model.SoftOperationResult;

public interface SoftInstallExecutor {

    boolean supports(String targetType);

    SoftOperationResult install(SoftExecutionContext context) throws Exception;

    default SoftOperationResult install(SoftExecutionContext context, SoftCommandObserver observer) throws Exception {
        return install(context);
    }

    SoftOperationResult uninstall(SoftExecutionContext context) throws Exception;

    default SoftOperationResult uninstall(SoftExecutionContext context, SoftCommandObserver observer) throws Exception {
        return uninstall(context);
    }
}
