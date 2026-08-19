package com.chua.starter.software.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.software.support.entity.SoftSoftware;
import com.chua.starter.software.support.entity.SoftSource;
import com.chua.starter.software.support.enums.SourceType;
import com.chua.starter.software.support.mapper.SoftSourceMapper;
import com.chua.starter.software.support.service.SoftSoftwareService;
import com.chua.starter.software.support.service.SoftSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * 软件源表 Service 实现
 *
 * @since 2026/7/25
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class SoftSourceServiceImpl extends ServiceImpl<SoftSourceMapper, SoftSource> implements SoftSourceService {

    /**
     * 软件服务 - 通过 Setter 注入，断开循环依赖
     */
    private SoftSoftwareService softwareService;

    /**
     * 事务模板
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * 通过 Setter 注入，打断与 softwareService 的循环依赖
     *
     * @param softwareService 软件服务
     */
    @Autowired
    public void setSoftwareService(@Lazy SoftSoftwareService softwareService) {
        this.softwareService = softwareService;
    }

    @Override
    /** PageFor */
    public IPage<SoftSource> pageFor(com.chua.starter.mybatis.entity.Query<SoftSource> query, SoftSource request) {
        Page<SoftSource> page = query.createPage();
        LambdaQueryWrapper<SoftSource> w = new LambdaQueryWrapper<>();
        if (request != null) {
            if (StringUtils.isNotBlank(request.getSourceCode())) {
                w.eq(SoftSource::getSourceCode, request.getSourceCode());
            }
            if (StringUtils.isNotBlank(request.getSourceName())) {
                w.like(SoftSource::getSourceName, request.getSourceName());
            }
            if (request.getSourceType() != null) {
                w.eq(SoftSource::getSourceType, request.getSourceType());
            }
            if (request.getSourceIsEnabled() != null) {
                w.eq(SoftSource::getSourceIsEnabled, request.getSourceIsEnabled());
            }
        }
        return baseMapper.selectPage(page, w);
    }

    @Override
    /** 保存Source */
    public ReturnResult<SoftSource> saveSource(SoftSource source) {
        if (StringUtils.isBlank(source.getSourceCode())) {
            return ReturnResult.illegal("源编码不能为空");
        }
        if (StringUtils.isBlank(source.getSourceName())) {
            return ReturnResult.illegal("源名称不能为空");
        }
        source.setSourceCreateTime(LocalDateTime.now());
        source.setSourceUpdateTime(LocalDateTime.now());
        baseMapper.insert(source);
        return ReturnResult.ok(source);
    }

    @Override
    /** 更新Source */
    public ReturnResult<SoftSource> updateSource(SoftSource source) {
        if (source.getSourceId() == null) {
            return ReturnResult.illegal("源ID不能为空");
        }
        SoftSource exist = getById(source.getSourceId());
        if (exist == null) {
            return ReturnResult.illegal("软件源不存在");
        }
        source.setSourceUpdateTime(LocalDateTime.now());
        baseMapper.updateById(source);
        return ReturnResult.ok(source);
    }

    @Override
    /** 删除Source */
    public ReturnResult<Boolean> deleteSource(String sourceId) {
        if (sourceId == null) {
            return ReturnResult.illegal("源ID不能为空");
        }
        SoftSource exist = getById(sourceId);
        if (exist == null) {
            return ReturnResult.illegal("软件源不存在");
        }
        baseMapper.deleteById(sourceId);
        return ReturnResult.ok(true);
    }

    @Override
    /** 获取ByCode */
    public SoftSource getByCode(String code) {
        return baseMapper.selectOne(new LambdaQueryWrapper<SoftSource>()
                .eq(SoftSource::getSourceCode, code)
                .eq(SoftSource::getSourceStatus, 0)
                .orderByDesc(SoftSource::getSourceCreateTime)
                .last("LIMIT 1"));
    }

    @Override
    /** 获取DefaultSource */
    public SoftSource getDefaultSource() {
        return baseMapper.selectOne(new LambdaQueryWrapper<SoftSource>()
                .eq(SoftSource::getSourceIsDefault, true)
                .eq(SoftSource::getSourceIsEnabled, true)
                .eq(SoftSource::getSourceStatus, 0));
    }

    @Override
    /** ListEnabled */
    public java.util.List<SoftSource> listEnabled() {
        return baseMapper.selectList(new LambdaQueryWrapper<SoftSource>()
                .eq(SoftSource::getSourceIsEnabled, true)
                .eq(SoftSource::getSourceStatus, 0)
                .orderByAsc(SoftSource::getSourcePriority)
                .orderByAsc(SoftSource::getSourceCreateTime));
    }

    @Override
    /** SyncSource */
    public ReturnResult<Integer> syncSource(String sourceId) {
        return softwareService.syncFromSource(sourceId);
    }

    /** 启用Source */
    public ReturnResult<Boolean> enableSource(String sourceId) {
        if (sourceId == null) {
            return ReturnResult.illegal("源ID不能为空");
        }
        SoftSource source = getById(sourceId);
        if (source == null) {
            return ReturnResult.illegal("软件源不存在");
        }
        source.setSourceIsEnabled(true);
        updateSource(source);
        return ReturnResult.ok(true);
    }

    @Override
    /** 禁用Source */
    public ReturnResult<Boolean> disableSource(String sourceId) {
        if (sourceId == null) {
            return ReturnResult.illegal("源ID不能为空");
        }
        SoftSource source = getById(sourceId);
        if (source == null) {
            return ReturnResult.illegal("软件源不存在");
        }
        source.setSourceIsEnabled(false);
        updateSource(source);
        return ReturnResult.ok(true);
    }

    @Override
    /** 获取DefaultLocalSource */
    public SoftSource getDefaultLocalSource() {
        return baseMapper.selectOne(new LambdaQueryWrapper<SoftSource>()
                .eq(SoftSource::getSourceType, SourceType.LOCAL.getValue())
                .eq(SoftSource::getSourceIsEnabled, true)
                .eq(SoftSource::getSourceStatus, 0)
                .orderByDesc(SoftSource::getSourceCreateTime)
                .last("LIMIT 1"));
    }
}