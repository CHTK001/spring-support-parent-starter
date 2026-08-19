package com.chua.starter.mybatis.interceptor;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.chua.common.support.utils.ObjectUtils;
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
    /** Dept_leader */
    private static final String DEPT_LEADER = "DEPT_LEADER";
    /** Managed_dept_tree_ids */
    private static final String MANAGED_DEPT_TREE_IDS = "managedDeptTreeIds";

    /** Methodsecurityinterceptor */
    private final MybatisPlusDataScopeProperties methodSecurityInterceptor;
    /** Auth服务供应商 */
    private final Supplier<AuthService> authServiceSupplier;

    /**
     * 创建 MybatisPlusDataPermissionInterceptor 实例
     * @param dataPermissionHandler dataPermissionHandler
     * @param MybatisPlusDataScopeProperties MybatisPlusDataScopeProperties
     */
    public MybatisPlusDataPermissionInterceptor(DataPermissionHandler dataPermissionHandler, MybatisPlusDataScopeProperties methodSecurityInterceptor) {
        this(dataPermissionHandler, methodSecurityInterceptor, () -> null);
    }

    /**
     * 创建 MybatisPlusDataPermissionInterceptor 实例
     * @param dataPermissionHandler dataPermissionHandler
     * @param methodSecurityInterceptor methodSecurityInterceptor
     * @param authServiceSupplier authServiceSupplier
     */
    public MybatisPlusDataPermissionInterceptor(DataPermissionHandler dataPermissionHandler,
                                                MybatisPlusDataScopeProperties methodSecurityInterceptor,
                                                Supplier<AuthService> authServiceSupplier) {
        super(dataPermissionHandler);
        this.methodSecurityInterceptor = methodSecurityInterceptor;
        this.authServiceSupplier = authServiceSupplier;
    }

    @Override
    /** 处理删除 */
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        if (!shouldApplyDataPermission()) {
            return;
        }
        super.processDelete(delete, index, sql, obj);
    }

    @Override
    /** 处理更新 */
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        if (!shouldApplyDataPermission()) {
            return;
        }
        super.processUpdate(update, index, sql, obj);
    }

    @Override
    /** 处理选择 */
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

    /** 是否拥有ManagedDeptLeaderScope */
    private boolean hasManagedDeptLeaderScope(CurrentUser currentUser) {
        return currentUser.hasRole(DEPT_LEADER)
                && null != currentUser.getExt()
                && null != currentUser.getExt().get(MANAGED_DEPT_TREE_IDS);
    }

    /** 获取CurrentUser */
    private CurrentUser getCurrentUser() {
        if (authServiceSupplier == null) {
            return null;
        }
        AuthService authService = authServiceSupplier.get();
        return authService == null ? null : authService.getCurrentUser();
    }
}

