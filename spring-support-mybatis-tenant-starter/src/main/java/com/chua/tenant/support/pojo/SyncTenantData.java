package com.chua.tenant.support.pojo;

import com.chua.common.support.constant.Action;
import com.chua.tenant.support.entity.SysTenant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 同步租户数据
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SyncTenantData {

    /**
     * 操作类型
     */
    private Action action;

    /**
     * 租户信息
     */
    private SysTenant tenant;
}
