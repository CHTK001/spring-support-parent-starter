package com.chua.starter.monitor.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.monitor.support.entity.MsMonitor;

import java.util.List;

/**
 * 监控资产 Service
 *
 * @author CH
 * @since 2026/8/4
 */
public interface MonitorService {

    /**
     * 创建资产
     *
     * @param monitor 实体
     * @return 创建后的实体
     */
    MsMonitor create(MsMonitor monitor);

    /**
     * 更新资产
     *
     * @param monitor 实体
     * @return 更新后的实体
     */
    MsMonitor update(MsMonitor monitor);

    /**
     * 删除资产
     *
     * @param id 主键
     */
    void delete(Long id);

    /**
     * 查询单个
     *
     * @param id 主键
     * @return 实体
     */
    MsMonitor findById(Long id);

    /**
     * 列出全部
     *
     * @return 列表
     */
    List<MsMonitor> listAll();

    /**
     * 按服务类型列出
     *
     * @param serviceType 服务类型
     * @return 列表
     */
    List<MsMonitor> listByServiceType(String serviceType);

    /**
     * 分页查询
     *
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param keyword 关键字(可选)
     * @return 分页结果
     */
    Page<MsMonitor> page(long pageNum, long pageSize, String keyword);
}