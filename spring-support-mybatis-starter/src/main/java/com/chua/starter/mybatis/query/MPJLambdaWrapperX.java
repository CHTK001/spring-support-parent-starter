package com.chua.starter.mybatis.query;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.ObjectUtils;
import com.github.yulichang.toolkit.MPJWrappers;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.chua.common.support.utils.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * 拓展 MyBatis Plus 连接 查询包装器 类，主要增加如下功能：
 * <p>
 * 1. 拼接条件的方法，增加 xxxifpresent 方法，用于判断值不存在的时候，不要拼接到条件中。
 *
 * @param <T> 数据类型
 * @author CH
 * @since 4.0.0
 */
public class MPJLambdaWrapperX<T> extends MPJLambdaWrapper<T> {

    /**
     * likeifpresent
     *
     * @param column column
     * @param val val
     * @return likeIfPresent的结果
     */
    public MPJLambdaWrapperX<T> likeIfPresent(SFunction<T, ?> column, String val) {
        MPJWrappers.lambdaJoin().like(column, val);
        if (StringUtils.hasText(val)) {
            return (MPJLambdaWrapperX<T>) super.like(column, val);
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
    public MPJLambdaWrapperX<T> inIfPresent(SFunction<T, ?> column, Collection<?> values) {
        if (ObjectUtils.isAllNotEmpty(values) && !CollectionUtils.isEmpty(values)) {
            return (MPJLambdaWrapperX<T>) super.in(column, values);
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
    public MPJLambdaWrapperX<T> inIfPresent(SFunction<T, ?> column, Object... values) {
        if (ObjectUtils.isAllNotEmpty(values) && !ArrayUtils.isEmpty(values)) {
            return (MPJLambdaWrapperX<T>) super.in(column, values);
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
    public MPJLambdaWrapperX<T> eqIfPresent(SFunction<T, ?> column, Object val) {
        if (ObjectUtils.isNotEmpty(val)) {
            return (MPJLambdaWrapperX<T>) super.eq(column, val);
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
    public MPJLambdaWrapperX<T> neIfPresent(SFunction<T, ?> column, Object val) {
        if (ObjectUtils.isNotEmpty(val)) {
            return (MPJLambdaWrapperX<T>) super.ne(column, val);
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
    public MPJLambdaWrapperX<T> gtIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (MPJLambdaWrapperX<T>) super.gt(column, val);
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
    public MPJLambdaWrapperX<T> geIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (MPJLambdaWrapperX<T>) super.ge(column, val);
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
    public MPJLambdaWrapperX<T> ltIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (MPJLambdaWrapperX<T>) super.lt(column, val);
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
    public MPJLambdaWrapperX<T> leIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (MPJLambdaWrapperX<T>) super.le(column, val);
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
    public MPJLambdaWrapperX<T> betweenIfPresent(SFunction<T, ?> column, Object val1, Object val2) {
        if (val1 != null && val2 != null) {
            return (MPJLambdaWrapperX<T>) super.between(column, val1, val2);
        }
        if (val1 != null) {
            return (MPJLambdaWrapperX<T>) ge(column, val1);
        }
        if (val2 != null) {
            return (MPJLambdaWrapperX<T>) le(column, val2);
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
    public MPJLambdaWrapperX<T> betweenIfPresent(SFunction<T, ?> column, Object[] values) {
        Object val1 = ArrayUtils.getIndex(values, 0);
        Object val2 = ArrayUtils.getIndex(values, 1);
        return betweenIfPresent(column, val1, val2);
    }

    // ========== 重写父类方法，方便链式调用 ==========

    /**
     * Eq
    */
    @Override
    public <X> MPJLambdaWrapperX<T> eq(boolean condition, SFunction<X, ?> column, Object val) {
        super.eq(condition, column, val);
        return this;
    }

    /**
     * Eq
    */
    @Override
    public <X> MPJLambdaWrapperX<T> eq(SFunction<X, ?> column, Object val) {
        super.eq(column, val);
        return this;
    }

    /**
     * 订单bydesc
    */
    @Override
    public <X> MPJLambdaWrapperX<T> orderByDesc(SFunction<X, ?> column) {
        //noinspection unchecked
        super.orderByDesc(true, column);
        return this;
    }

    /**
     * 最后一个
     * @param lastSql 字符串
     * @return 返回值
    */
    @Override
    public MPJLambdaWrapperX<T> last(String lastSql) {
        super.last(lastSql);
        return this;
    }

    /**
     * 入
    */
    @Override
    public <X> MPJLambdaWrapperX<T> in(SFunction<X, ?> column, Collection<?> coll) {
        super.in(column, coll);
        return this;
    }

    /**
     * 选择全部
     * @param clazz 参数
     * @return 返回值
    */
    @Override
    public MPJLambdaWrapperX<T> selectAll(Class<?> clazz) {
        super.selectAll(clazz);
        return this;
    }

    /**
     * 选择全部
     * @param clazz 参数
     * @param prefix 字符串
     * @return 返回值
    */
    @Override
    public MPJLambdaWrapperX<T> selectAll(Class<?> clazz, String prefix) {
        super.selectAll(clazz, prefix);
        return this;
    }

    /**
     * 选择As
    */
    @Override
    public <S> MPJLambdaWrapperX<T> selectAs(SFunction<S, ?> column, String alias) {
        super.selectAs(column, alias);
        return this;
    }

    /**
     * 选择As
    */
    @Override
    public <E> MPJLambdaWrapperX<T> selectAs(String column, SFunction<E, ?> alias) {
        super.selectAs(column, alias);
        return this;
    }

    /**
     * 选择As
    */
    @Override
    public <S, X> MPJLambdaWrapperX<T> selectAs(SFunction<S, ?> column, SFunction<X, ?> alias) {
        super.selectAs(column, alias);
        return this;
    }

    /**
     * 选择As
    */
    @Override
    public <E, X> MPJLambdaWrapperX<T> selectAs(String index, SFunction<E, ?> column, SFunction<X, ?> alias) {
        super.selectAs(index, column, alias);
        return this;
    }

    /**
     * 选择as类
    */
    @Override
    public <E> MPJLambdaWrapperX<T> selectAsClass(Class<E> source, Class<?> tag) {
        super.selectAsClass(source, tag);
        return this;
    }

    /**
     * 选择Sub
    */
    @Override
    public <E, F> MPJLambdaWrapperX<T> selectSub(Class<E> clazz, Consumer<MPJLambdaWrapper<E>> consumer, SFunction<F, ?> alias) {
        super.selectSub(clazz, consumer, alias);
        return this;
    }

    /**
     * 选择Sub
    */
    @Override
    public <E, F> MPJLambdaWrapperX<T> selectSub(Class<E> clazz, String st, Consumer<MPJLambdaWrapper<E>> consumer, SFunction<F, ?> alias) {
        super.selectSub(clazz, st, consumer, alias);
        return this;
    }

    /**
     * 选择计算数量
    */
    @Override
    public <S> MPJLambdaWrapperX<T> selectCount(SFunction<S, ?> column) {
        super.selectCount(column);
        return this;
    }

    /**
     * 选择计算数量
     * @param alias 字符串
     * @param column 对象
     * @return 返回值
    */
    @Override
    public MPJLambdaWrapperX<T> selectCount(Object column, String alias) {
        super.selectCount(column, alias);
        return this;
    }

    /**
     * 选择计算数量
    */
    @Override
    public <X> MPJLambdaWrapperX<T> selectCount(Object column, SFunction<X, ?> alias) {
        super.selectCount(column, alias);
        return this;
    }


    /**
     * 选择计算数量
    */
    @Override
    public <S, X> MPJLambdaWrapperX<T> selectCount(SFunction<S, ?> column, SFunction<X, ?> alias) {
        super.selectCount(column, alias);
        return this;
    }

    /**
     * 选择求和
    */
    @Override
    public <S> MPJLambdaWrapperX<T> selectSum(SFunction<S, ?> column) {
        super.selectSum(column);
        return this;
    }


    /**
     * 选择求和
    */
    @Override
    public <S, X> MPJLambdaWrapperX<T> selectSum(SFunction<S, ?> column, SFunction<X, ?> alias) {
        super.selectSum(column, alias);
        return this;
    }

    /**
     * 选择最大值
    */
    @Override
    public <S> MPJLambdaWrapperX<T> selectMax(SFunction<S, ?> column) {
        super.selectMax(column);
        return this;
    }


    /**
     * 选择最大值
    */
    @Override
    public <S, X> MPJLambdaWrapperX<T> selectMax(SFunction<S, ?> column, SFunction<X, ?> alias) {
        super.selectMax(column, alias);
        return this;
    }

    /**
     * 选择最小值
    */
    @Override
    public <S> MPJLambdaWrapperX<T> selectMin(SFunction<S, ?> column) {
        super.selectMin(column);
        return this;
    }


    /**
     * 选择最小值
    */
    @Override
    public <S, X> MPJLambdaWrapperX<T> selectMin(SFunction<S, ?> column, SFunction<X, ?> alias) {
        super.selectMin(column, alias);
        return this;
    }

    /**
     * 选择平均值
    */
    @Override
    public <S> MPJLambdaWrapperX<T> selectAvg(SFunction<S, ?> column) {
        super.selectAvg(column);
        return this;
    }


    /**
     * 选择平均值
    */
    @Override
    public <S, X> MPJLambdaWrapperX<T> selectAvg(SFunction<S, ?> column, SFunction<X, ?> alias) {
        super.selectAvg(column, alias);
        return this;
    }

    /**
     * 选择Len
    */
    @Override
    public <S> MPJLambdaWrapperX<T> selectLen(SFunction<S, ?> column) {
        super.selectLen(column);
        return this;
    }


    /**
     * 选择Len
    */
    @Override
    public <S, X> MPJLambdaWrapperX<T> selectLen(SFunction<S, ?> column, SFunction<X, ?> alias) {
        super.selectLen(column, alias);
        return this;
    }

}

