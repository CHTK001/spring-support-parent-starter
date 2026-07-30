package com.chua.starter.project.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.cache.TenantedKeyGenerator;
import com.chua.starter.common.support.constant.CacheConstant;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.project.mapper.SysProjectMapper;
import com.chua.starter.project.service.SysProjectDefaultService;
import com.chua.starter.project.service.SysProjectService;
import com.chua.starter.entity.project.SysProject;
import com.chua.starter.entity.project.SysProjectDefault;
import com.chua.starter.project.pojo.project.ProjectDefaultFunctionV1Request;
import com.chua.spring.support.configuration.SpringBeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;

/**
 * 项目
 *
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class SysProjectServiceImpl extends ServiceImpl<SysProjectMapper, SysProject> implements SysProjectService {

    final TransactionTemplate transactionTemplate;
    final SysProjectDefaultService sysProjectDefaultService;
    @Override
    public IPage<SysProject> pageFor(Query<SysProject> query, SysProject sysProject, Set<String> roles) {
        return baseMapper.selectPageFor(query.createPage(), sysProject);
    }

    @Override
    @Cacheable(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, key = "'sys:project:' + #sysProjectId", keyGenerator = "tenantedKeyGenerator")
    public SysProject getOne(Integer sysProjectId) {
        SysProject sysProject = new SysProject();
        sysProject.setSysProjectId(sysProjectId);
        IPage<SysProject> sysProjectIPage = pageFor(new Query<SysProject>().setPageSize(1), sysProject, Set.of());
        return CollectionUtils.findFirst(sysProjectIPage.getRecords());
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, key = "'sys:project:' + #typeName", keyGenerator = "tenantedKeyGenerator")
    public SysProject getDefaultByType(String typeName) {
        IPage<SysProject> defaultByType = baseMapper.getDefaultByType(new Page<>(1, 1), typeName);
        return CollectionUtils.findFirst(defaultByType.getRecords());
    }


    private void clearDefaultCache(String typeName) {
        StringBuilder keyPref = SpringBeanUtils.getBean(TenantedKeyGenerator.class).getKeyPrefix();
        SpringBeanUtils.getBean(CacheConstant.REDIS_CACHE_ALWAYS, CacheManager.class)
                .getCache(CacheConstant.REDIS_CACHE_ALWAYS)
                .evict(keyPref.toString() + "sys:project:" + typeName);
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, key = "'sys:project:' + #sysProjectId", keyGenerator = "tenantedKeyGenerator")
    public ReturnResult<Boolean> delete(Integer sysProjectId) {
        SysProject sysProject = getOne(sysProjectId);
        if (null == sysProject) {
            return ReturnResult.illegal("项目信息不存在");
        }
        return transactionTemplate.execute(it -> {
            saveOrUpdateDefault(sysProject);
            clearDefaultCacheByProjectId(sysProject.getSysProjectId());
            return ReturnResult.guess(baseMapper.deleteById(sysProjectId) > 0);
        });
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, key = "'sys:project:' + #sysProject.sysProjectId", keyGenerator = "tenantedKeyGenerator")
    public ReturnResult<Boolean> update(Set<String> roles, SysProject sysProject) {
        SysProject sysProject1 = getOne(sysProject.getSysProjectId());
        if (null == sysProject1) {
            return ReturnResult.illegal("项目信息不存在");
        }
        sysProject.setSysProjectCode(null);
        sysProject.setSysProjectDelete(null);
        return transactionTemplate.execute(it -> {
            saveOrUpdateDefault(sysProject);
            clearDefaultCacheByProjectId(sysProject1.getSysProjectId());
            return ReturnResult.guess(baseMapper.updateById(sysProject) > 0);
        });
    }

    @Override
    public ReturnResult<SysProject> updateDefault(ProjectDefaultFunctionV1Request request) {
        if(null == request.getSysProjectDefaultType()) {
            return ReturnResult.illegal("类型不能为空");
        }
        return transactionTemplate.execute(it -> {
            String sysProjectDefaultType = String.valueOf(request.getSysProjectDefaultType());
            sysProjectDefaultService.remove(Wrappers.<SysProjectDefault>lambdaQuery().eq(SysProjectDefault::getSysProjectDefaultType, sysProjectDefaultType));
            Integer sysSetDefault = request.getSysSetDefault();
            clearDefaultCache(sysProjectDefaultType);
            if(null == sysSetDefault || sysSetDefault == 0) {
                return ReturnResult.ok(getDefault(request.getSysProjectId()));
            }
            SysProjectDefault sysProjectDefault = new SysProjectDefault();
            sysProjectDefault.setSysProjectDefaultType(sysProjectDefaultType);
            sysProjectDefault.setSysProjectId(request.getSysProjectId());
            sysProjectDefaultService.save(sysProjectDefault);
            return ReturnResult.guess(getDefault(request.getSysProjectId()));
        });
    }

    /**
     * 获取默认项目
     *
     * @param sysProjectId 项目id
     * @return 项目
     */
    protected SysProject getDefault(Integer sysProjectId) {
        SysProject sysProject = new SysProject();
        sysProject.setSysProjectId(sysProjectId);
        Query<SysProject> query = new Query<>();
        query.setPageSize(1);
        IPage<SysProject> sysProjectIPage = pageFor(query, sysProject, Set.of());
        return CollectionUtils.findFirst(sysProjectIPage.getRecords());
    }

    private void saveOrUpdateDefault(SysProject sysProject) {
        String sysProjectFunctionDefaultIds = sysProject.getSysProjectFunctionDefaultIds();
        if (StringUtils.isNotBlank(sysProjectFunctionDefaultIds)) {
            Set<String> strings = Splitter.on(",").omitEmptyStrings().trimResults().splitToSet(sysProjectFunctionDefaultIds);
            sysProjectDefaultService.remove(Wrappers.<SysProjectDefault>lambdaQuery().eq(SysProjectDefault::getSysProjectId, sysProject.getSysProjectId()));
            sysProjectDefaultService.remove(Wrappers.<SysProjectDefault>lambdaQuery()
                    .in(SysProjectDefault::getSysProjectDefaultType, strings));
            List<SysProjectDefault> sysProjectDefaults = new ArrayList<>(strings.size());
            for (String string : strings) {
                SysProjectDefault sysProjectDefault = new SysProjectDefault();
                sysProjectDefault.setSysProjectDefaultType(string);
                sysProjectDefault.setSysProjectId(sysProject.getSysProjectId());

                sysProjectDefaults.add(sysProjectDefault);
            }
            sysProjectDefaultService.saveBatch(sysProjectDefaults);

        }
    }

    private void clearDefaultCacheByProjectId(Integer sysProjectId) {
        List<SysProjectDefault> defaults = sysProjectDefaultService.list(
                Wrappers.<SysProjectDefault>lambdaQuery()
                        .select(SysProjectDefault::getSysProjectDefaultType)
                        .eq(SysProjectDefault::getSysProjectId, sysProjectId)
        );
        for (SysProjectDefault item : defaults) {
            if (StringUtils.isNotBlank(item.getSysProjectDefaultType())) {
                clearDefaultCache(item.getSysProjectDefaultType());
            }
        }
    }

    @Override
    public ReturnResult<SysProject> save(SysProject sysProject, Set<String> roles) {
        sysProject.setSysProjectDelete(0);
        if (null == sysProject.getSysProjectStatus()) {
            sysProject.setSysProjectStatus(1);
        }
        sysProject.setSysProjectCode(IdUtils.fastUuid());
        return transactionTemplate.execute(it -> {
            baseMapper.insert(sysProject);
            saveOrUpdateDefault(sysProject);
            return ReturnResult.guess(sysProject);
        });
    }
}
