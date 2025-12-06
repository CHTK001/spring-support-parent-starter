package com.chua.tenant.support.pojo;

import com.chua.common.support.constant.Action;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 同步租户服务数据
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SyncTenantServiceData {

    /**
     * 操作类型
     */
    private Action action;

    /**
     * 租户ID
     */
    private Integer sysTenantId;

    /**
     * 菜单ID列表
     */
    private List<Integer> menuIds;
}
