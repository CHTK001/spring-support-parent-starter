package com.chua.starter.mybatis.interceptor;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.chua.starter.common.support.oauth.CurrentUser;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.PlainSelect;

/**
 * 数据处理器
 *
 * @author CH
 */
public interface SelectDataPermissionHandler extends DataPermissionHandler {
    /**
     * sql片段
     *
     * @param plainSelect       select
     * @param where             where
     * @param mappedStatementId 片段ID
     * @param currentUser       用户信息
     */
    void processSelect(PlainSelect plainSelect, Expression where, String mappedStatementId, CurrentUser currentUser);

}
