package com.chua.starter.unified.server.support.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.starter.mybatis.entity.DelegatePage;
import com.chua.starter.unified.server.support.entity.UnifiedExecuter;

/**
 * 统一执行器服务
 *
 * @author CH
 */
public interface UnifiedExecuterService extends IService<UnifiedExecuter>{

    /**
     * 创建执行器
     *
     * @param request 请求
     */
    void createExecutor(BootRequest request);

    /**
     * 保存或更新执行器
     *
     * @param t t
     * @return {@link Boolean}
     */
    Boolean saveOrUpdateExecuter(UnifiedExecuter t);

    /**
     * 更新通过id执行器
     *
     * @param t t
     * @return {@link Boolean}
     */
    Boolean updateByIdExecuter(UnifiedExecuter t);

    /**
     * 去除通过id执行器
     *
     * @param id id
     * @return {@link Boolean}
     */
    Boolean removeByIdExecuter(String id);

    /**
     * 分页执行器
     *
     * @param page   分页
     * @param entity 实体
     * @return {@link IPage}<{@link UnifiedExecuter}>
     */
    IPage<UnifiedExecuter> pageExecuter(DelegatePage<UnifiedExecuter> page, UnifiedExecuter entity);

    /**
     * 按名称获取id
     *
     * @param appName 应用程序名称
     * @return {@link Integer}
     */
    Integer getIdByName(String appName);
}
