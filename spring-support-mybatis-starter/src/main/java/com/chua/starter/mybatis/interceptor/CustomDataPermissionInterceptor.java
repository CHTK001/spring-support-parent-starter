package com.chua.starter.mybatis.interceptor;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import lombok.*;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.PlainSelect;

/**
 * 数据拦截器
 * @author CH
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class CustomDataPermissionInterceptor extends DataPermissionInterceptor {

    private DataPermissionHandler dataPermissionHandler;

    @Override
    protected void setWhere(PlainSelect plainSelect, String whereSegment) {
        if (dataPermissionHandler instanceof MultiDataPermissionHandler) {
            processPlainSelect(plainSelect, whereSegment);
            return;
        }

        Expression sqlSegment;
        if(dataPermissionHandler instanceof SelectDataPermissionHandler) {
            sqlSegment = ((SelectDataPermissionHandler) dataPermissionHandler).getSqlSegment(plainSelect, plainSelect.getWhere(), whereSegment);
        } else {
            // 兼容旧版的数据权限处理
            sqlSegment = dataPermissionHandler.getSqlSegment(plainSelect.getWhere(), whereSegment);
        }
        if (null != sqlSegment) {
            plainSelect.setWhere(sqlSegment);
        }
    }
}
