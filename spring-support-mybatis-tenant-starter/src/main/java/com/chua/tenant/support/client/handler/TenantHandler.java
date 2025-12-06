package com.chua.tenant.support.client.handler;

import com.chua.tenant.support.common.entity.SysTenant;

/**
 * 租户处理器接口
 * <p>
 * 客户端实现此接口以处理从服务端同步的租户数据
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
public interface TenantHandler {

    /**
     * 保存或更新租户
     *
     * @param tenant 租户信息
     */
    void saveOrUpdate(SysTenant tenant);

    /**
     * 删除租户
     *
     * @param tenant 租户信息
     */
    void delete(SysTenant tenant);
}
