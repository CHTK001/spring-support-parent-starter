package com.chua.starter.panel.software.service;

import com.chua.common.support.lang.code.ReturnResult;

import java.util.List;

public interface SysProjectContainerSoftwareLogService {
    void appendLog(Integer bindingId, String action, String line);
    void appendLog(Integer bindingId, String action, String operationId, String line);
    ReturnResult<List<String>> getLogs(Integer bindingId, String action);
    ReturnResult<List<String>> getLogs(Integer bindingId, String action, String operationId);
}
