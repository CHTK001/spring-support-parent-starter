package com.chua.starter.mybatis.interceptor;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.chua.common.support.core.utils.ObjectUtils;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

import java.util.function.Supplier;

/**
 * 数据权限拦截器
 *
 * @author CH
 */
public class MybatisPlusDataPermissionInterceptor extends DataPermissionInterceptor {
    private static final String DEPT_LEADER = "DEPT_LEADER";
    private static final String MANAGED_DEPT_TREE_IDS = "managedDeptTreeIds";

    private final MybatisPlusDataScopeProperties methodSecurityInterceptor;
    private final Supplier<AuthService> authServiceSupplier;

    public MybatisPlusDataPermissionInterceptor(DataPermissionHandler dataPermissionHandler, MybatisPlusDataScopeProperties methodSecurityInterceptor) {
        this(dataPermissionHandler, methodSecurityInterceptor, () -> null);
    }

    public MybatisPlusDataPermissionInterceptor(DataPermissionHandler dataPermissionHandler,
                                                MybatisPlusDataScopeProperties methodSecurityInterceptor,
                                                Supplier<AuthService> authServiceSupplier) {
        super(dataPermissionHandler);
        this.methodSecurityInterceptor = methodSecurityInterceptor;
        this.authServiceSupplier = authServiceSupplier;
    }

    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        if (!shouldApplyDataPermission()) {
            return;
        }
        super.processDelete(delete, index, sql, obj);
    }

    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        if (!shouldApplyDataPermission()) {
            return;
        }
        super.processUpdate(update, index, sql, obj);
    }

    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
        if (!shouldApplyDataPermission()) {
            return;
        }
        super.processSelect(select, index, sql, obj);
    }

    /**
     * 判断是否应该应用数据权限
     *
     * @return true-应用, false-不应用
     */
    private boolean shouldApplyDataPermission() {
        // 检查是否开启数据权限
        if (!methodSecurityInterceptor.isEnable()) {
            return false;
        }

        // 获取当前用户
        CurrentUser currentUser = getCurrentUser();
        if (ObjectUtils.isEmpty(currentUser)) {
            return false;
        }
        return true;
    }

    private boolean hasManagedDeptLeaderScope(CurrentUser currentUser) {
        return currentUser.hasRole(DEPT_LEADER)
                && null != currentUser.getExt()
                && null != currentUser.getExt().get(MANAGED_DEPT_TREE_IDS);
    }

    private CurrentUser getCurrentUser() {
        if (authServiceSupplier == null) {
            return null;
        }
        AuthService authService = authServiceSupplier.get();
        return authService == null ? null : authService.getCurrentUser();
    }
}

