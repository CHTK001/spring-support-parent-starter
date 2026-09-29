package com.chua.starter.software.support.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.software.support.entity.SoftSoftware;
import com.chua.starter.software.support.pojo.software.SoftwarePageRequest;
import com.chua.starter.software.support.spi.SoftwareSource;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.Collection;
import java.util.List;

/**
 * 软件信息表 服务 接口
 *
 * @since 2026/7/25
 * @author CH
 */
public interface SoftSoftwareService extends IService<SoftSoftware> {

    /**
     * 分页查询软件列表
     *
     * @param query 查询条件对象，用于分页和排序
     * @param request 软件查询请求对象
     * @param installedSoftwareIds 已安装软件标识集合，用于排序（已安装的排前面）
     * @return 返回分页查询结果
     */
    IPage<SoftSoftware> pageFor(Query<SoftSoftware> query, SoftwarePageRequest request, Collection<String> installedSoftwareIds);

    /**
     * 保存软件信息
     *
     * @param software 软件信息
     * @return 返回保存结果
     */
    ReturnResult<SoftSoftware> saveSoftware(SoftSoftware software);

    /**
     * 更新软件信息
     *
     * @param software 软件信息
     * @return 返回更新结果
     */
    ReturnResult<SoftSoftware> updateSoftware(SoftSoftware software);

    /**
     * 删除软件信息
     *
     * @param softwareId 软件标识
     * @return 返回删除结果
     */
    ReturnResult<Boolean> deleteSoftware(String softwareId);

    /**
     * 根据软件编码获取软件信息
     *
     * @param code 软件编码
     * @return 软件信息
     */
    SoftSoftware getByCode(String code);

    /**
     * 根据源标识获取软件列表
     *
     * @param sourceId 源标识
     * @return 软件列表
     */
    java.util.List<SoftSoftware> listBySourceId(String sourceId);

    /**
     * 从软件源同步软件信息
     *
     * @param sourceId 源标识
     * @return 返回同步数量
     */
    ReturnResult<Integer> syncFromSource(String sourceId);

    /**
     * 将软件信息同步到 Elasticsearch
     *
     * @param software 软件信息
     */
    void indexToElasticsearch(SoftSoftware software);

    /**
     * 批量将软件信息同步到 Elasticsearch
     *
     * @param softwareList 软件信息列表
     */
    void indexBatchToElasticsearch(java.util.List<SoftSoftware> softwareList);

    /**
     * 从 Elasticsearch 中删除软件信息
     *
     * @param softwareId 软件标识
     */
    void deleteFromElasticsearch(String softwareId);

    /**
     * 从 Elasticsearch 搜索软件信息
     *
     * @param keyword 搜索关键词
     * @return 软件列表
     */
    java.util.List<SoftSoftware> searchFromElasticsearch(String keyword);

    /**
     * 从 Elasticsearch 搜索软件信息（分页）
     *
     * @param keyword 搜索关键词
     * @param from 开始位置
     * @param size 每页大小
     * @return 软件列表
     */
    java.util.List<SoftSoftware> searchFromElasticsearch(String keyword, int from, int size);
}
