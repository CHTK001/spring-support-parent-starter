package com.chua.tenant.support.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.tenant.support.common.entity.SysServiceModule;
import com.chua.tenant.support.server.mapper.SysServiceModuleMapper;
import com.chua.tenant.support.server.service.SysServiceModuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 服务模块服务实现类
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/07
 */
@Slf4j
@Service
public class SysServiceModuleServiceImpl extends ServiceImpl<SysServiceModuleMapper, SysServiceModule>
        implements SysServiceModuleService {

    @Override
    public IPage<SysServiceModule> pageForSysServiceModule(Query<SysServiceModule> query, SysServiceModule sysServiceModule) {
        log.debug("分页查询服务模块, 条件: {}", sysServiceModule);
        return baseMapper.selectPage(query.createPage(),
                Wrappers.<SysServiceModule>lambdaQuery()
                        .likeRight(StringUtils.isNotBlank(sysServiceModule.getSysServiceModuleName()),
                                SysServiceModule::getSysServiceModuleName, sysServiceModule.getSysServiceModuleName())
                        .likeRight(StringUtils.isNotBlank(sysServiceModule.getSysServiceModuleCode()),
                                SysServiceModule::getSysServiceModuleCode, sysServiceModule.getSysServiceModuleCode())
                        .orderByAsc(SysServiceModule::getSysServiceModuleSort)
        );
    }

    @Override
    public ReturnResult<SysServiceModule> saveForSysServiceModule(SysServiceModule sysServiceModule) {
        log.debug("保存服务模块: {}", sysServiceModule);
        if (exists(Wrappers.<SysServiceModule>lambdaQuery()
                .eq(SysServiceModule::getSysServiceModuleCode, sysServiceModule.getSysServiceModuleCode()))) {
            log.warn("服务模块编码已存在: {}", sysServiceModule.getSysServiceModuleCode());
            return ReturnResult.error("服务模块编码已存在");
        }
        sysServiceModule.setSysServiceModuleDelete(0);
        sysServiceModule.setSysServiceModuleStatus(0);
        save(sysServiceModule);
        log.info("服务模块保存成功, ID: {}", sysServiceModule.getSysServiceModuleId());
        return ReturnResult.success(sysServiceModule);
    }

    @Override
    public ReturnResult<Boolean> updateForSysServiceModule(SysServiceModule sysServiceModule) {
        log.debug("更新服务模块: {}", sysServiceModule);
        if (exists(Wrappers.<SysServiceModule>lambdaQuery()
                .eq(SysServiceModule::getSysServiceModuleCode, sysServiceModule.getSysServiceModuleCode())
                .ne(SysServiceModule::getSysServiceModuleId, sysServiceModule.getSysServiceModuleId()))) {
            log.warn("服务模块编码已存在: {}", sysServiceModule.getSysServiceModuleCode());
            return ReturnResult.error("服务模块编码已存在");
        }
        sysServiceModule.setSysServiceModuleDelete(null);
        boolean result = updateById(sysServiceModule);
        log.info("服务模块更新结果: {}", result);
        return ReturnResult.preconditioning(result).toResult();
    }

    @Override
    public ReturnResult<Boolean> deleteForSysServiceModule(Long sysServiceModuleId) {
        log.debug("删除服务模块, ID: {}", sysServiceModuleId);
        boolean result = removeById(sysServiceModuleId);
        log.info("服务模块删除结果: {}", result);
        return ReturnResult.preconditioning(result).toResult();
    }

    @Override
    public ReturnResult<List<SysServiceModule>> listForSysServiceModule(SysServiceModule sysServiceModule) {
        log.debug("查询服务模块列表, 条件: {}", sysServiceModule);
        return ReturnResult.ok(baseMapper.selectList(
                Wrappers.<SysServiceModule>lambdaQuery()
                        .likeRight(StringUtils.isNotBlank(sysServiceModule.getSysServiceModuleName()),
                                SysServiceModule::getSysServiceModuleName, sysServiceModule.getSysServiceModuleName())
                        .likeRight(StringUtils.isNotBlank(sysServiceModule.getSysServiceModuleCode()),
                                SysServiceModule::getSysServiceModuleCode, sysServiceModule.getSysServiceModuleCode())
                        .orderByAsc(SysServiceModule::getSysServiceModuleSort)
        ));
    }
}
