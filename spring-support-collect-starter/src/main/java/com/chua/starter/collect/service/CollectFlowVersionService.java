package com.chua.starter.collect.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.starter.collect.entity.CollectFlowVersion;

import java.util.List;

/**
 * 流程版本服务。
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface CollectFlowVersionService extends IService<CollectFlowVersion> {

    /**
     * 保存当前版本快照。
     *
     * @param flowId   流程ID
     * @param flowDsl  DSL 内容
     * @param remark   版本说明
     * @return 新版本号
     */
    int saveVersion(Integer flowId, String flowDsl, String remark);

    /**
     * 查询某流程的版本历史。
     *
     * @param flowId 流程ID
     * @return 版本历史
     */
    List<CollectFlowVersion> listByFlow(Integer flowId);

    /**
     * 回滚到指定版本。
     *
     * @param flowId    流程ID
     * @param versionNo 版本号
     * @return 是否成功
     */
    boolean rollback(Integer flowId, Integer versionNo);
}
