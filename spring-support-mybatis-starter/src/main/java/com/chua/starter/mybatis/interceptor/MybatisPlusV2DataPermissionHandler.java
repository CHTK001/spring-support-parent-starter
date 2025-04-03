package com.chua.starter.mybatis.interceptor;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.mybatis.permission.TableDeptV2Register;
import com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.schema.Table;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.util.Map;

import static com.chua.starter.mybatis.interceptor.MybatisPlusPermissionHandler.NO_DATA;

/**
 * 多数据权限
 *
 * @author CH
 */
public class MybatisPlusV2DataPermissionHandler implements MultiDataPermissionHandler {
    static final Map<String, Expression> MAPPED_STATEMENT_ID = new ConcurrentReferenceHashMap<>(4096);
    private final MybatisPlusDataScopeProperties metaDataScopeProperties;

    public MybatisPlusV2DataPermissionHandler(MybatisPlusDataScopeProperties metaDataScopeProperties) {
        this.metaDataScopeProperties = metaDataScopeProperties;
    }

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        if (!metaDataScopeProperties.isEnable()) {
            return null;
        }
        try {
            CurrentUser currentUser = SpringBeanUtils.getBean(AuthService.class).getCurrentUser();
            return dataScopeFilter(table, currentUser, metaDataScopeProperties, where, currentUser.getDataPermission());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return createNoData(where);
    }

    private Expression dataScopeFilter(Table table,
                                       CurrentUser currentUser,
                                       MybatisPlusDataScopeProperties dataScopeProperties,
                                       Expression where,
                                       DataFilterTypeEnum dataPermission) {
        if (null == dataPermission || dataPermission == DataFilterTypeEnum.ALL) {
            return null;
        }

        return new TableDeptV2Register(table, where, currentUser, dataPermission, dataScopeProperties).register();
    }

    /**
     * 创建没有数据的条件
     *
     * @param where where
     * @return 条件
     */
    private Expression createNoData(Expression where) {
        if (null == where) {
            return NO_DATA;
        }
        return new AndExpression(where, NO_DATA);
    }
}
