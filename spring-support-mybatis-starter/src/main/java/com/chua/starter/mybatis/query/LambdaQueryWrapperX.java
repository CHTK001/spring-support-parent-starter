package com.chua.starter.mybatis.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Collection;

/**
* 拓展 MyBatis Plus 查询包装器 类，主要增加如下功能：
* <p>
* 1. 拼接条件的方法，增加 xxxifpresent 方法，用于判断值不存在的时候，不要拼接到条件中。
*
* @param <T> 数据类型
* @author CH
* @since 4.0.0
 */
public class LambdaQueryWrapperX<T> extends LambdaQueryWrapper<T> {

    /**
    * likeifpresent
    *
    * @param column column
    * @param val val
    * @return likeIfPresent的结果
    */
    public LambdaQueryWrapperX<T> likeIfPresent(SFunction<T, ?> column, String val) {
        if (StringUtils.hasText(val)) {
            return (LambdaQueryWrapperX<T>) super.like(column, val);
        }
        return this;
    }

    /**
    * 入ifpresent
    *
    * @param column column
    * @param values 价值观
    * @return 入ifpresent的结果
    */
    public LambdaQueryWrapperX<T> inIfPresent(SFunction<T, ?> column, Collection<?> values) {
        if (ObjectUtils.isAllNotEmpty(values) && !CollectionUtils.isEmpty(values)) {
            return (LambdaQueryWrapperX<T>) super.in(column, values);
        }
        return this;
    }

    /**
    * 入ifpresent
    *
    * @param column column
    * @param values 价值观
    * @return 入ifpresent的结果
    */
    public LambdaQueryWrapperX<T> inIfPresent(SFunction<T, ?> column, Object... values) {
        if (ObjectUtils.isAllNotEmpty(values) && !ArrayUtils.isEmpty(values)) {
            return (LambdaQueryWrapperX<T>) super.in(column, values);
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
    public LambdaQueryWrapperX<T> eqIfPresent(SFunction<T, ?> column, Object val) {
        if (ObjectUtils.isNotEmpty(val)) {
            return (LambdaQueryWrapperX<T>) super.eq(column, val);
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
    public LambdaQueryWrapperX<T> neIfPresent(SFunction<T, ?> column, Object val) {
        if (ObjectUtils.isNotEmpty(val)) {
            return (LambdaQueryWrapperX<T>) super.ne(column, val);
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
    public LambdaQueryWrapperX<T> gtIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (LambdaQueryWrapperX<T>) super.gt(column, val);
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
    public LambdaQueryWrapperX<T> geIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (LambdaQueryWrapperX<T>) super.ge(column, val);
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
    public LambdaQueryWrapperX<T> ltIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (LambdaQueryWrapperX<T>) super.lt(column, val);
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
    public LambdaQueryWrapperX<T> leIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (LambdaQueryWrapperX<T>) super.le(column, val);
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
    public LambdaQueryWrapperX<T> betweenIfPresent(SFunction<T, ?> column, Object val1, Object val2) {
        if (val1 != null && val2 != null) {
            return (LambdaQueryWrapperX<T>) super.between(column, val1, val2);
        }
        if (val1 != null) {
            return (LambdaQueryWrapperX<T>) ge(column, val1);
        }
        if (val2 != null) {
            return (LambdaQueryWrapperX<T>) le(column, val2);
        }
        return this;
    }

    /**
    * betweenifpresent
    *
    * @param column column
    * @param values 价值观
    * @return betweenIfPresent的结果
    */
    public LambdaQueryWrapperX<T> betweenIfPresent(SFunction<T, ?> column, Object[] values) {
        Object val1 = ArrayUtils.getIndex(values, 0);
        Object val2 = ArrayUtils.getIndex(values, 1);
        return betweenIfPresent(column, val1, val2);
    }

    // ========== 重写父类方法，方便链式调用 ==========

    @Override
    /** Eq */
    public LambdaQueryWrapperX<T> eq(boolean condition, SFunction<T, ?> column, Object val) {
        super.eq(condition, column, val);
        return this;
    }

    @Override
    /** Eq */
    public LambdaQueryWrapperX<T> eq(SFunction<T, ?> column, Object val) {
        super.eq(column, val);
        return this;
    }

    @Override
    /** 订单bydesc */
    public LambdaQueryWrapperX<T> orderByDesc(SFunction<T, ?> column) {
        super.orderByDesc(true, column);
        return this;
    }

    @Override
    /** 最后一个 */
    public LambdaQueryWrapperX<T> last(String lastSql) {
        super.last(lastSql);
        return this;
    }

    @Override
    /** 入 */
    public LambdaQueryWrapperX<T> in(SFunction<T, ?> column, Collection<?> coll) {
        super.in(column, coll);
        return this;
    }

}

