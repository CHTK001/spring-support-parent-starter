package com.chua.starter.panel.software.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.panel.software.entity.SysProjectContainerSoftwareBinding;
import com.chua.starter.panel.software.pojo.ProjectContainerSoftwareBatchOperationRequest;
import com.chua.starter.panel.software.pojo.ProjectContainerSoftwareImportPresetRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface SysProjectContainerSoftwareBindingService extends IService<SysProjectContainerSoftwareBinding> {

    IPage<SysProjectContainerSoftwareBinding> pageFor(Query<SysProjectContainerSoftwareBinding> query,
                                                      SysProjectContainerSoftwareBinding request,
                                                      Set<String> roles);

    List<SysProjectContainerSoftwareBinding> listByProjectId(Integer sysProjectId);

    void evictProjectBindingCache(Integer sysProjectId);

    ReturnResult<SysProjectContainerSoftwareBinding> importFromPreset(ProjectContainerSoftwareImportPresetRequest request);

    ReturnResult<Boolean> delete(Integer bindingId, String connectionId);

    ReturnResult<Boolean> update(Set<String> roles, SysProjectContainerSoftwareBinding binding);

    ReturnResult<SysProjectContainerSoftwareBinding> save(SysProjectContainerSoftwareBinding binding, Set<String> roles);

    ReturnResult<Map<String, Object>> checkRequirements(Map<String, Object> request);

    ReturnResult<Map<String, Object>> checkDocker(Map<String, Object> request);

    ReturnResult<Map<String, Object>> reconfigure(Integer bindingId, Map<String, Object> configValues);

    ReturnResult<List<String>> fetchRuntimeLogs(Integer bindingId, Integer tailLines, String connectionId);

    ReturnResult<Map<String, Object>> fetchContainerInfo(Integer bindingId, String connectionId);

    ReturnResult<Map<String, Object>> dockerExec(Integer bindingId, String command, String connectionId);

    ReturnResult<Map<String, Object>> getContainerStats(Integer bindingId, String connectionId);

    // ─── 容器状态同步 ────────────────────────────────────────

    /**
     * 同步所有 Docker 绑定的容器实际状态。
     * 检查实际容器是否存在/运行，更新业务表的 installStatus / runtimeStatus。
     */
    ReturnResult<SyncSummary> syncContainerStatus(String connectionId);

    /**
     * 同步单个绑定的容器实际状态。
     */
    ReturnResult<Map<String, Object>> syncSingleContainerStatus(Integer bindingId, String connectionId);

    /** 同步结果摘要 */
    record SyncSummary(int totalChecked, int updated, int missingContainers) {
    }

    /**
     * 批量同步多台服务器的容器状态
     */
    ReturnResult<List<SyncSummary>> syncContainerStatusMulti(List<String> connectionIds);

    /**
     * 批量操作 - 在多台服务器上执行相同操作
     */
    ReturnResult<List<Map<String, Object>>> batchOperate(ProjectContainerSoftwareBatchOperationRequest request);
}
