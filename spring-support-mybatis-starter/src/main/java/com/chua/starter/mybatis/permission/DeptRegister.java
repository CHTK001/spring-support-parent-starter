package com.chua.starter.mybatis.permission;

import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import net.sf.jsqlparser.expression.Expression;

/**
 * 注入机构ID
 *
 * @author CH
 */
public interface DeptRegister {

    /**
     * 注册
     *
     * @return Expression
     */
    Expression register();


    /**
     * 获取表信息
     *
     * @param tableName 表名
     * @return 表信息
     */
    default TableInfo getTableInfo(String tableName) {
        return TableInfoHelper.getTableInfos().stream().filter(
                it -> {
                    String tableName1 = it.getTableName();
                    if (tableName.equalsIgnoreCase(tableName1)) {
                        return true;
                    }
                    return tableName1.contains(".") && tableName1.contains("." + tableName);
                }
        ).findFirst().get();
    }
}
