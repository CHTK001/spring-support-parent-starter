package com.chua.tenant.support.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.tenant.support.common.entity.SysServiceModule;

import java.util.List;

/**
 * 服务模块服务接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/07
 */
public interface SysServiceModuleService extends IService<SysServiceModule> {

    /**
     * 分页查询服务模块
     *
     * @param query           查询对象
     * @param sysServiceModule 服务模块条件
     * @return 分页结果
     */
    IPage<SysServiceModule> pageForSysServiceModule(Query<SysServiceModule> query, SysServiceModule sysServiceModule);

    /**
     * 保存服务模块
     *
     * @param sysServiceModule 服务模块对象
     * @return 保存结果
     */
    ReturnResult<SysServiceModule> saveForSysServiceModule(SysServiceModule sysServiceModule);

    /**
     * 更新服务模块
     *
     * @param sysServiceModule 服务模块对象
     * @return 更新结果
     */
    ReturnResult<Boolean> updateForSysServiceModule(SysServiceModule sysServiceModule);

    /**
     * 删除服务模块
     *
     * @param sysServiceModuleId 服务模块ID
     * @return 删除结果
     */
    ReturnResult<Boolean> deleteForSysServiceModule(Long sysServiceModuleId);

    /**
     * 查询服务模块列表
     *
     * @param sysServiceModule 服务模块条件
     * @return 服务模块列表
     */
    ReturnResult<List<SysServiceModule>> listForSysServiceModule(SysServiceModule sysServiceModule);
}
