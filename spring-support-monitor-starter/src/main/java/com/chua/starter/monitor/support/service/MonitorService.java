package com.chua.starter.monitor.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsMonitor;

import java.util.List;

/**
* 监控 服务
*
* @since 2026/8/4
* @author CH
 */
public interface MonitorService {

    /**
    * 创建
    *
    * @param monitor 监控实体
    * @return 创建结果
     */
    ReturnResult<MsMonitor> create(MsMonitor monitor);

    /**
    * 更新
    *
    * @param monitor 监控实体
    * @return 更新结果
     */
    ReturnResult<MsMonitor> update(MsMonitor monitor);

    /**
    * 删除
    *
    * @param id 主键
    * @return 删除结果
     */
    ReturnResult<Void> delete(Long id);

    /**
    * 按 标识 查询
    *
    * @param id 主键
    * @return 查询结果
     */
    ReturnResult<MsMonitor> findById(Long id);

    /**
    * 查询全部
    *
    * @return 列表
     */
    ReturnResult<List<MsMonitor>> listAll();

    /**
    * 按服务类型查询
    *
    * @param serviceType 服务类型
    * @return 列表
     */
    ReturnResult<List<MsMonitor>> listByServiceType(String serviceType);

    /**
    * 分页查询
    *
    * @param pageNum  页码
    * @param pageSize 每页大小
    * @param keyword  关键词
    * @return 分页结果
     */
    ReturnResult<Page<MsMonitor>> page(long pageNum, long pageSize, String keyword);
}