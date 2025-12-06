package com.chua.tenant.support.common.configuration;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.chua.tenant.support.common.properties.TenantProperties;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

/**
 * 租户行处理器实现
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
public class TenantLineHandlerImpl implements TenantLineHandler {

    private final TenantProperties tenantProperties;

    /**
     * 当前租户ID（线程本地变量）
     */
    private static final ThreadLocal<Long> CURRENT_TENANT_ID = new ThreadLocal<>();

    public TenantLineHandlerImpl(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties;
    }

    /**
     * 设置当前租户ID
     *
     * @param tenantId 租户ID
     */
    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT_ID.set(tenantId);
    }

    /**
     * 获取当前租户ID
     *
     * @return 租户ID
     */
    public static Long getTenantId() {
        return CURRENT_TENANT_ID.get();
    }

    /**
     * 清除当前租户ID
     */
    public static void clear() {
        CURRENT_TENANT_ID.remove();
    }

    @Override
    public Expression getTenantId() {
        Long tenantId = CURRENT_TENANT_ID.get();
        if (tenantId == null) {
            // 尝试从配置获取默认租户ID（客户端配置）
            String defaultTenantId = tenantProperties.getClient().getTenantId();
            if (defaultTenantId != null && !defaultTenantId.isEmpty()) {
                tenantId = Long.parseLong(defaultTenantId);
            }
        }
        return new LongValue(tenantId != null ? tenantId : 0L);
    }

    @Override
    public String getTenantIdColumn() {
        return tenantProperties.getTenantIdColumn();
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return tenantProperties.getIgnoreTable().contains(tableName);
    }
}
