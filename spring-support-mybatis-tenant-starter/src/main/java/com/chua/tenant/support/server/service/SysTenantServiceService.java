package com.chua.tenant.support.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.tenant.support.entity.SysTenantService;

import java.util.List;

/**
 * 租户服务关联接口
 * <p>
 * 管理租户与服务的绑定关系
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
public interface SysTenantServiceService extends IService<SysTenantService> {

    /**
     * 根据租户ID获取菜单ID列表
     *
     * @param sysTenantId 租户ID
     * @return 菜单ID列表
     */
    List<Integer> getMenuIds(Integer sysTenantId);
}
