package com.chua.tenant.support.client.handler;

import com.chua.tenant.support.entity.SysTenant;

/**
 * 租户处理器接�?
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
public interface TenantHandler {

    /**
     * 保存或更新租�?
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
