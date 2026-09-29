package com.chua.starter.software.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.software.support.entity.SoftInstallRecord;
import com.chua.starter.software.support.entity.SoftSoftware;
import com.chua.starter.software.support.entity.SoftSource;
import com.chua.starter.software.support.enums.InstallStatus;
import com.chua.starter.software.support.mapper.SoftSoftwareMapper;
import com.chua.starter.software.support.pojo.install.InstallDetailResponse;
import com.chua.starter.software.support.pojo.install.InstallRequest;
import com.chua.starter.software.support.pojo.software.SoftwarePageRequest;
import com.chua.starter.software.support.service.SoftInstallRecordService;
import com.chua.starter.software.support.service.SoftSoftwareService;
import com.chua.starter.software.support.service.SoftSourceService;
import com.chua.starter.software.support.spi.SoftwareSource;
import com.chua.starter.software.support.spi.SoftwareSourceResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 软件信息表 服务 实现
 *
 * @since 2026/7/25
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class SoftSoftwareServiceImpl extends ServiceImpl<SoftSoftwareMapper, SoftSoftware> implements SoftSoftwareService {

    /**
     * 事务模板
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * 软件源服务
     */
    private final SoftSourceService sourceService;

    /**
     * 安装记录服务 - 使用 Setter + @Lazy 打断循环依赖
     */
    private SoftInstallRecordService installRecordService;

    /**
     * 软件源 SPI 列表
     */
    private final List<SoftwareSource> softwareSources;

    /**
     * 日志
    */
    private static final Logger log = LoggerFactory.getLogger(SoftSoftwareServiceImpl.class);

    /**
     * Setter 注入，支持 @Lazy
     *
     * @param installRecordService 安装记录服务
     */
    @Autowired
    public void setInstallRecordService(@Lazy SoftInstallRecordService installRecordService) {
        this.installRecordService = installRecordService;
    }

    @Override
    /**
     * pagefor
    */
    public IPage<SoftSoftware> pageFor(Query<SoftSoftware> query, SoftwarePageRequest request, Collection<String> installedSoftwareIds) {
        Page<SoftSoftware> page = query.createPage();
        return baseMapper.selectPageFor(page, request, installedSoftwareIds);
    }

    @Override
    /**
     * 保存Software
    */
    public ReturnResult<SoftSoftware> saveSoftware(SoftSoftware software) {
        if (StringUtils.isBlank(software.getSoftwareCode())) {
            return ReturnResult.illegal("软件编码不能为空");
        }
        SoftSoftware exist = getByCode(software.getSoftwareCode());
        if (exist != null) {
            return ReturnResult.illegal("软件编码已存在");
        }
        if (software.getSoftwareStatus() == null) {
            software.setSoftwareStatus(0);
        }
        software.setSoftwareCreateTime(LocalDateTime.now());
        software.setSoftwareUpdateTime(LocalDateTime.now());
        baseMapper.insert(software);
        /**
         * 索引转为Elasticsearch。
         * @param software software
         */
        indexToElasticsearch(software);
        return ReturnResult.ok(software);
    }

    @Override
    /**
     * 更新Software
    */
    public ReturnResult<SoftSoftware> updateSoftware(SoftSoftware software) {
        if (software.getSoftwareId() == null) {
            return ReturnResult.illegal("软件ID不能为空");
        }
        SoftSoftware exist = getById(software.getSoftwareId());
        if (exist == null) {
            return ReturnResult.illegal("软件不存在");
        }
        software.setSoftwareUpdateTime(LocalDateTime.now());
        baseMapper.updateById(software);
        /**
         * 索引转为Elasticsearch。
         * @param software software
         */
        indexToElasticsearch(software);
        return ReturnResult.ok(software);
    }

    @Override
    /**
     * 删除Software
    */
    public ReturnResult<Boolean> deleteSoftware(String softwareId) {
        SoftSoftware exist = getById(softwareId);
        if (exist == null) {
            return ReturnResult.illegal("软件不存在");
        }
        exist.setSoftwareStatus(1);
        exist.setSoftwareUpdateTime(LocalDateTime.now());
        baseMapper.updateById(exist);
        /**
         * 删除从Elasticsearch。
         * @param softwareId softwareid
         */
        deleteFromElasticsearch(softwareId);
        return ReturnResult.ok(true);
    }

    @Override
    /**
     * 获取by编码
    */
    public SoftSoftware getByCode(String code) {
        return baseMapper.selectOne(new LambdaQueryWrapper<SoftSoftware>().eq(SoftSoftware::getSoftwareCode, code));
    }

    @Override
    /**
     * 列表by源标识
    */
    public List<SoftSoftware> listBySourceId(String sourceId) {
        return baseMapper.selectList(new LambdaQueryWrapper<SoftSoftware>().eq(SoftSoftware::getSoftwareSourceId, sourceId));
    }

    @Override
    /**
     * 同步从源
    */
    public ReturnResult<Integer> syncFromSource(String sourceId) {
        SoftSource source = sourceService.getById(sourceId);
        if (source == null) {
            return ReturnResult.illegal("软件源不存在");
        }
        return transactionTemplate.execute(status -> {
            int count = 0;
            for (SoftwareSource softwareSource : softwareSources) {
                if (softwareSource.support(source)) {
                    List<SoftSoftware> list = softwareSource.fetchSoftwareList(source);
                    if (CollectionUtils.isNotEmpty(list)) {
                        for (SoftSoftware software : list) {
                            software.setSoftwareSourceId(sourceId);
                            software.setSoftwareStatus(0);
                            software.setSoftwareCreateTime(LocalDateTime.now());
                            software.setSoftwareUpdateTime(LocalDateTime.now());
                            SoftSoftware exist = getByCode(software.getSoftwareCode());
                            if (exist != null) {
                                software.setSoftwareId(exist.getSoftwareId());
                                baseMapper.updateById(software);
                            } else {
                                baseMapper.insert(software);
                            }
                            count++;
                        }
                    }
                }
            }
            return ReturnResult.ok(count);
        });
    }

    @Override
    /**
     * 索引转为Elasticsearch
    */
    public void indexToElasticsearch(SoftSoftware software) {
        log.info("索引软件到ES: {}", software.getSoftwareName());
    }

    @Override
    /**
     * 索引批量转为Elasticsearch
    */
    public void indexBatchToElasticsearch(List<SoftSoftware> softwareList) {
        if (CollectionUtils.isEmpty(softwareList)) {
            return;
        }
        for (SoftSoftware software : softwareList) {
            /**
             * 索引转为Elasticsearch。
             * @param software software
             */
            indexToElasticsearch(software);
        }
    }

    @Override
    /**
     * 删除从Elasticsearch
    */
    public void deleteFromElasticsearch(String softwareId) {
        log.info("从ES删除软件: {}", softwareId);
    }

    @Override
    /**
     * 搜索从Elasticsearch
    */
    public List<SoftSoftware> searchFromElasticsearch(String keyword) {
        return searchFromElasticsearch(keyword, 0, 20);
    }

    @Override
    /**
     * 搜索从Elasticsearch
    */
    public List<SoftSoftware> searchFromElasticsearch(String keyword, int from, int size) {
        log.info("从ES搜索软件: keyword={}, from={}, size={}", keyword, from, size);
        return new ArrayList<>();
    }

    /**
     * 获取installedsoftware标识
     *
     * @param userId 用户标识
     * @return 获取installedsoftware标识的结果
     */
    public List<String> getInstalledSoftwareIds(String userId) {
        List<SoftInstallRecord> records = installRecordService.listInstalledByUserId(userId);
        if (CollectionUtils.isEmpty(records)) {
            return Collections.emptyList();
        }
        return records.stream()
                .map(SoftInstallRecord::getInstallRecordSoftwareId)
                .distinct()
                .collect(Collectors.toList());
    }
}
