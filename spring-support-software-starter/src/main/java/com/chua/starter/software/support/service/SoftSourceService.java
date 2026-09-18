package com.chua.starter.software.support.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.software.support.entity.SoftSource;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* 软件源表 服务 接口
*
* @since 2026/7/25
* @author CH
 */
public interface SoftSourceService extends IService<SoftSource> {

    /**
    * 分页查询软件源列表
    *
    * @param query 查询条件对象，用于分页和排序
    * @param request 软件源查询请求对象
    * @return 返回分页查询结果
    */
    IPage<SoftSource> pageFor(Query<SoftSource> query, SoftSource request);

    /**
    * 保存软件源
    *
    * @param source 软件源信息
    * @return 返回保存结果
    */
    ReturnResult<SoftSource> saveSource(SoftSource source);

    /**
    * 更新软件源
    *
    * @param source 软件源信息
    * @return 返回更新结果
    */
    ReturnResult<SoftSource> updateSource(SoftSource source);

    /**
    * 删除软件源
    *
    * @param sourceId 软件源标识
    * @return 返回删除结果
    */
    ReturnResult<Boolean> deleteSource(String sourceId);

    /**
    * 根据软件源编码获取软件源
    *
    * @param code 软件源编码
    * @return 软件源信息
    */
    SoftSource getByCode(String code);

    /**
    * 获取默认软件源
    *
    * @return 默认软件源
    */
    SoftSource getDefaultSource();

    /**
    * 获取所有启用的软件源
    *
    * @return 启用的软件源列表
    */
    List<SoftSource> listEnabled();

    /**
    * 同步软件源
    *
    * @param sourceId 软件源标识
    * @return 返回同步的软件数量
    */
    ReturnResult<Integer> syncSource(String sourceId);

    /**
    * 启用软件源
    *
    * @param sourceId 软件源标识
    * @return 返回启用结果
    */
    ReturnResult<Boolean> enableSource(String sourceId);

    /**
    * 禁用软件源
    *
    * @param sourceId 软件源标识
    * @return 返回禁用结果
    */
    ReturnResult<Boolean> disableSource(String sourceId);

    /**
    * 获取默认本地软件源
    *
    * @return 本地软件源（source_type=3）
    */
    SoftSource getDefaultLocalSource();
}
