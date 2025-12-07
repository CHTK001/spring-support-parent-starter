package com.chua.tenant.support.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.tenant.support.common.entity.SysService;
import com.chua.tenant.support.server.pojo.SysServiceBindV1Request;

import java.util.List;

/**
 * 服务服务接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/07
 */
public interface SysServiceService extends IService<SysService> {

    /**
     * 分页查询服务
     *
     * @param query      查询对象
     * @param sysService 服务条件
     * @return 分页结果
     */
    IPage<SysService> pageForSysService(Query<SysService> query, SysService sysService);

    /**
     * 保存服务
     *
     * @param sysService 服务对象
     * @return 保存结果
     */
    ReturnResult<SysService> saveForSysService(SysService sysService);

    /**
     * 更新服务
     *
     * @param sysService 服务对象
     * @return 更新结果
     */
    ReturnResult<Boolean> updateForSysService(SysService sysService);

    /**
     * 删除服务
     *
     * @param sysServiceId 服务ID
     * @return 删除结果
     */
    ReturnResult<Boolean> deleteForSysService(Long sysServiceId);

    /**
     * 绑定服务模块
     *
     * @param request 绑定请求
     * @return 绑定结果
     */
    ReturnResult<Boolean> bindForSysService(SysServiceBindV1Request request);

    /**
     * 查询服务列表
     *
     * @param sysService 服务条件
     * @return 服务列表
     */
    ReturnResult<List<SysService>> listForSysService(SysService sysService);
}
