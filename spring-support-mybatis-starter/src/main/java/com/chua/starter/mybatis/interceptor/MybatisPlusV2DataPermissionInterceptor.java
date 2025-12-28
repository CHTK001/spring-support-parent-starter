package com.chua.starter.mybatis.interceptor;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

/**
 * 数据权限
 *
 * @author CH
 */
public class MybatisPlusV2DataPermissionInterceptor extends DataPermissionInterceptor {

    private final MybatisPlusDataScopeProperties methodSecurityInterceptor;

    public MybatisPlusV2DataPermissionInterceptor(DataPermissionHandler dataPermissionHandler, MybatisPlusDataScopeProperties methodSecurityInterceptor) {
        super(dataPermissionHandler);
        this.methodSecurityInterceptor = methodSecurityInterceptor;
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
        AuthService authService = SpringBeanUtils.getBean(AuthService.class);
        if (null == authService) {
            return false;
        }

        CurrentUser currentUser = authService.getCurrentUser();
        if (ObjectUtils.isEmpty(currentUser)) {
            return false;
        }

        // 检查用户是否需要应用数据权限
        return currentUser.isNeedDataPermission();
    }
}
