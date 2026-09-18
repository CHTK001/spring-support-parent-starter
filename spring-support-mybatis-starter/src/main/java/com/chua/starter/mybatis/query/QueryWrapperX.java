package com.chua.starter.mybatis.query;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ArrayUtils;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.chua.starter.mybatis.entity.SqlConstants;
import com.google.common.base.Preconditions;
import org.springframework.util.StringUtils;

import java.util.Collection;

/**
* 拓展 MyBatis Plus 查询包装器 类，主要增加如下功能：
*
* 1. 拼接条件的方法，增加 xxxifpresent 方法，用于判断值不存在的时候，不要拼接到条件中。
*
* @param <T> 数据类型
* @author CH
* @since 4.0.0
 */
public class QueryWrapperX<T> extends QueryWrapper<T> {

    /**
    * likeifpresent
    *
    * @param column column
    * @param val val
    * @return likeIfPresent的结果
    */
    public QueryWrapperX<T> likeIfPresent(String column, String val) {
        if (StringUtils.hasText(val)) {
            return (QueryWrapperX<T>) super.like(column, val);
        }
        return this;
    }

    /**
    * 入ifpresent
    *
    * @param column column
    * @param values 值
    * @return 入ifpresent的结果
    */
    public QueryWrapperX<T> inIfPresent(String column, Collection<?> values) {
        if (!CollectionUtils.isEmpty(values)) {
            return (QueryWrapperX<T>) super.in(column, values);
        }
        return this;
    }

    /**
    * 入ifpresent
    *
    * @param column column
    * @param values 值
    * @return 入ifpresent的结果
    */
    public QueryWrapperX<T> inIfPresent(String column, Object... values) {
        if (!ArrayUtils.isEmpty(values)) {
            return (QueryWrapperX<T>) super.in(column, values);
        }
        return this;
    }

    /**
    * eqifpresent
    *
    * @param column column
    * @param val val
    * @return eqIfPresent的结果
    */
    public QueryWrapperX<T> eqIfPresent(String column, Object val) {
        if (val != null) {
            return (QueryWrapperX<T>) super.eq(column, val);
        }
        return this;
    }

    /**
    * neifpresent
    *
    * @param column column
    * @param val val
    * @return neIfPresent的结果
    */
    public QueryWrapperX<T> neIfPresent(String column, Object val) {
        if (val != null) {
            return (QueryWrapperX<T>) super.ne(column, val);
        }
        return this;
    }

    /**
    * gtifpresent
    *
    * @param column column
    * @param val val
    * @return gtIfPresent的结果
    */
    public QueryWrapperX<T> gtIfPresent(String column, Object val) {
        if (val != null) {
            return (QueryWrapperX<T>) super.gt(column, val);
        }
        return this;
    }

    /**
    * geifpresent
    *
    * @param column column
    * @param val val
    * @return geIfPresent的结果
    */
    public QueryWrapperX<T> geIfPresent(String column, Object val) {
        if (val != null) {
            return (QueryWrapperX<T>) super.ge(column, val);
        }
        return this;
    }

    /**
    * ltifpresent
    *
    * @param column column
    * @param val val
    * @return ltIfPresent的结果
    */
    public QueryWrapperX<T> ltIfPresent(String column, Object val) {
        if (val != null) {
            return (QueryWrapperX<T>) super.lt(column, val);
        }
        return this;
    }

    /**
    * leifpresent
    *
    * @param column column
    * @param val val
    * @return leIfPresent的结果
    */
    public QueryWrapperX<T> leIfPresent(String column, Object val) {
        if (val != null) {
            return (QueryWrapperX<T>) super.le(column, val);
        }
        return this;
    }

    /**
    * betweenifpresent
    *
    * @param column column
    * @param val1 val1
    * @param val2 val2
    * @return betweenIfPresent的结果
    */
    public QueryWrapperX<T> betweenIfPresent(String column, Object val1, Object val2) {
        if (val1 != null && val2 != null) {
            return (QueryWrapperX<T>) super.between(column, val1, val2);
        }
        if (val1 != null) {
            return (QueryWrapperX<T>) ge(column, val1);
        }
        if (val2 != null) {
            return (QueryWrapperX<T>) le(column, val2);
        }
        return this;
    }

    /**
    * betweenifpresent
    *
    * @param column column
    * @param values 值
    * @return betweenIfPresent的结果
    */
    public QueryWrapperX<T> betweenIfPresent(String column, Object[] values) {
        if (values!= null && values.length != 0 && values[0] != null && values[1] != null) {
            return (QueryWrapperX<T>) super.between(column, values[0], values[1]);
        }
        if (values!= null && values.length != 0 && values[0] != null) {
            return (QueryWrapperX<T>) ge(column, values[0]);
        }
        if (values!= null && values.length != 0 && values[1] != null) {
            return (QueryWrapperX<T>) le(column, values[1]);
        }
        return this;
    }

    // ========== 重写父类方法，方便链式调用 ==========

    @Override
    /** Eq */
    public QueryWrapperX<T> eq(boolean condition, String column, Object val) {
        super.eq(condition, column, val);
        return this;
    }

    @Override
    /** Eq */
    public QueryWrapperX<T> eq(String column, Object val) {
        super.eq(column, val);
        return this;
    }

    @Override
    /** 订单bydesc */
    public QueryWrapperX<T> orderByDesc(String column) {
        super.orderByDesc(true, column);
        return this;
    }

    @Override
    /** 最后一个 */
    public QueryWrapperX<T> last(String lastSql) {
        super.last(lastSql);
        return this;
    }

    @Override
    /** 入 */
    public QueryWrapperX<T> in(String column, Collection<?> coll) {
        super.in(column, coll);
        return this;
    }

    /**
    * 设置只返回最后一条
    *
    * TODO 芋艿：不是完美解，需要在思考下。如果使用多数据源，并且数据源是多种类型时，可能会存在问题：实现之返回一条的语法不同
    *
    * @return this
    * @param n n
    */
    public QueryWrapperX<T> limitN(int n) {
        Preconditions.checkNotNull(SqlConstants.DB_TYPE, "获取不到数据库的类型");
        switch (SqlConstants.DB_TYPE) {
            case ORACLE:
            case ORACLE_12C:
                super.eq("ROWNUM", n);
                break;
            case SQL_SERVER:
            case SQL_SERVER2005:
 // 由于 SQL 服务端 是通过 选择 TOP 1 实现限制一条，所以只好使用 * 查询剩余字段
                super.select("TOP " + n + " *");
                break;
            default:
                super.last("LIMIT " + n);
        }
        return this;
    }

}
