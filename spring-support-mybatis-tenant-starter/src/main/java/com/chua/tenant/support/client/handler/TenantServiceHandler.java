package com.chua.tenant.support.client.handler;

import java.util.List;

/**
 * 租户服务处理器接口
 * <p>
 * 客户端实现此接口以处理从服务端同步的服务/菜单数据
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
public interface TenantServiceHandler {

    /**
     * 保存或更新租户服务
     *
     * @param sysTenantId 租户ID
     * @param menuIds     菜单ID列表
     */
    void saveOrUpdate(Integer sysTenantId, List<Integer> menuIds);

    /**
     * 删除租户服务
     *
     * @param sysTenantId 租户ID
     * @param menuIds     菜单ID列表
     */
    void delete(Integer sysTenantId, List<Integer> menuIds);
}
