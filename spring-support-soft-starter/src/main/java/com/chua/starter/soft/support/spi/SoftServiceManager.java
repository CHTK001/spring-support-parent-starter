package com.chua.starter.soft.support.spi;

import com.chua.starter.soft.support.model.SoftExecutionContext;
import com.chua.starter.soft.support.model.SoftOperationResult;

public interface SoftServiceManager {

    SoftOperationResult register(SoftExecutionContext context) throws Exception;

    SoftOperationResult unregister(SoftExecutionContext context) throws Exception;

    SoftOperationResult start(SoftExecutionContext context) throws Exception;

    SoftOperationResult stop(SoftExecutionContext context) throws Exception;

    SoftOperationResult restart(SoftExecutionContext context) throws Exception;

    SoftOperationResult status(SoftExecutionContext context) throws Exception;
}
