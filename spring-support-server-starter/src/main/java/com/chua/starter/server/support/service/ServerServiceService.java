package com.chua.starter.server.support.service;

import com.chua.starter.server.support.entity.ServerService;
import com.chua.starter.server.support.entity.ServerServiceOperationLog;
import com.chua.starter.server.support.model.ServerServiceAiDraft;
import com.chua.starter.server.support.model.ServerAiTaskTicket;
import com.chua.starter.server.support.model.ServerServiceConfigWriteRequest;
import com.chua.starter.server.support.enums.ServerServiceOperationType;
import com.chua.starter.server.support.model.ServerServiceCommandResult;
import com.chua.starter.server.support.model.ServerServiceUpsertRequest;
import java.util.List;

public interface ServerServiceService {

    List<ServerService> listServices(Integer serverId, Boolean enabled);

    ServerService getService(Integer id);

    ServerService saveService(ServerService service);

    void deleteService(Integer id);

    ServerService saveManagedService(ServerServiceUpsertRequest request);

    ServerService getBySoftInstallationId(Integer installationId);

    boolean deleteBySoftInstallationId(Integer installationId);

    List<ServerService> detectServices(Integer serverId) throws Exception;

    List<ServerServiceOperationLog> listOperationLogs(Integer serverServiceId, Integer limit);

    ServerAiTaskTicket generateAiDraft(Integer id);

    ServerServiceCommandResult writeConfig(Integer id, ServerServiceConfigWriteRequest request) throws Exception;

    ServerServiceCommandResult operate(Integer id, ServerServiceOperationType operationType) throws Exception;

    ServerServiceCommandResult operateBySoftInstallationId(Integer installationId, ServerServiceOperationType operationType) throws Exception;
}
