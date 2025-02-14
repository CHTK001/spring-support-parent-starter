package com.chua.starter.mybatis.interceptor;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

/**
 * 数据权限
 *
 * @author CH
 */
public class MybatisPlusV2DataPermissionInterceptor extends DataPermissionInterceptor {

    public MybatisPlusV2DataPermissionInterceptor(DataPermissionHandler dataPermissionHandler) {
        super(dataPermissionHandler);
    }

    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        CurrentUser currentUser = SpringBeanUtils.getBean(AuthService.class).getCurrentUser();
        if (ObjectUtils.isEmpty(currentUser)) {
            return;
        }

        if (!currentUser.isDept()) {
            return;
        }
        super.processDelete(delete, index, sql, obj);
    }

    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        CurrentUser currentUser = SpringBeanUtils.getBean(AuthService.class).getCurrentUser();
        if (ObjectUtils.isEmpty(currentUser)) {
            return;
        }

        if (!currentUser.isDept()) {
            return;
        }
        super.processUpdate(update, index, sql, obj);
    }

    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
        CurrentUser currentUser = SpringBeanUtils.getBean(AuthService.class).getCurrentUser();
        if (ObjectUtils.isEmpty(currentUser)) {
            return;
        }

        if (!currentUser.isDept()) {
            return;
        }
        super.processSelect(select, index, sql, obj);
    }
}
