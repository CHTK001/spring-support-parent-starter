package com.chua.starter.plugin.store;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询条件
 * 
 * @author CH
 * @since 2025/1/16
 */
@Data
public class QueryCondition {

    /**
     * 查询条件列表
     */
    private List<Condition> conditions = new ArrayList<>();

    /**
     * 排序条件列表
     */
    private List<OrderBy> orderBys = new ArrayList<>();

    /**
     * 参数映射
     */
    private Map<String, Object> parameters = new HashMap<>();

    /**
     * 添加等于条件
     * 
     * @param field 字段名
     * @param value 值
     * @return 当前对象
     */
    public QueryCondition eq(String field, Object value) {
        conditions.add(new Condition(field, Operator.EQ, value));
        return this;
    }

    /**
     * 添加不等于条件
     * 
     * @param field 字段名
     * @param value 值
     * @return 当前对象
     */
    public QueryCondition ne(String field, Object value) {
        conditions.add(new Condition(field, Operator.NE, value));
        return this;
    }

    /**
     * 添加大于条件
     * 
     * @param field 字段名
     * @param value 值
     * @return 当前对象
     */
    public QueryCondition gt(String field, Object value) {
        conditions.add(new Condition(field, Operator.GT, value));
        return this;
    }

    /**
     * 添加大于等于条件
     * 
     * @param field 字段名
     * @param value 值
     * @return 当前对象
     */
    public QueryCondition ge(String field, Object value) {
        conditions.add(new Condition(field, Operator.GE, value));
        return this;
    }

    /**
     * 添加小于条件
     * 
     * @param field 字段名
     * @param value 值
     * @return 当前对象
     */
    public QueryCondition lt(String field, Object value) {
        conditions.add(new Condition(field, Operator.LT, value));
        return this;
    }

    /**
     * 添加小于等于条件
     * 
     * @param field 字段名
     * @param value 值
     * @return 当前对象
     */
    public QueryCondition le(String field, Object value) {
        conditions.add(new Condition(field, Operator.LE, value));
        return this;
    }

    /**
     * 添加LIKE条件
     * 
     * @param field 字段名
     * @param value 值
     * @return 当前对象
     */
    public QueryCondition like(String field, Object value) {
        conditions.add(new Condition(field, Operator.LIKE, value));
        return this;
    }

    /**
     * 添加IN条件
     * 
     * @param field 字段名
     * @param values 值列表
     * @return 当前对象
     */
    public QueryCondition in(String field, List<?> values) {
        conditions.add(new Condition(field, Operator.IN, values));
        return this;
    }

    /**
     * 添加IS NULL条件
     * 
     * @param field 字段名
     * @return 当前对象
     */
    public QueryCondition isNull(String field) {
        conditions.add(new Condition(field, Operator.IS_NULL, null));
        return this;
    }

    /**
     * 添加IS NOT NULL条件
     * 
     * @param field 字段名
     * @return 当前对象
     */
    public QueryCondition isNotNull(String field) {
        conditions.add(new Condition(field, Operator.IS_NOT_NULL, null));
        return this;
    }

    /**
     * 添加升序排序
     * 
     * @param field 字段名
     * @return 当前对象
     */
    public QueryCondition orderByAsc(String field) {
        orderBys.add(new OrderBy(field, OrderDirection.ASC));
        return this;
    }

    /**
     * 添加降序排序
     * 
     * @param field 字段名
     * @return 当前对象
     */
    public QueryCondition orderByDesc(String field) {
        orderBys.add(new OrderBy(field, OrderDirection.DESC));
        return this;
    }

    /**
     * 添加参数
     * 
     * @param key 参数名
     * @param value 参数值
     * @return 当前对象
     */
    public QueryCondition param(String key, Object value) {
        parameters.put(key, value);
        return this;
    }

    /**
     * 创建空条件
     * 
     * @return 查询条件
     */
    public static QueryCondition empty() {
        return new QueryCondition();
    }

    /**
     * 查询条件
     */
    @Data
    public static class Condition {
        private String field;
        private Operator operator;
        private Object value;

        public Condition(String field, Operator operator, Object value) {
            this.field = field;
            this.operator = operator;
            this.value = value;
        }
    }

    /**
     * 排序条件
     */
    @Data
    public static class OrderBy {
        private String field;
        private OrderDirection direction;

        public OrderBy(String field, OrderDirection direction) {
            this.field = field;
            this.direction = direction;
        }
    }

    /**
     * 操作符枚举
     */
    public enum Operator {
        EQ, NE, GT, GE, LT, LE, LIKE, IN, IS_NULL, IS_NOT_NULL
    }

    /**
     * 排序方向枚举
     */
    public enum OrderDirection {
        ASC, DESC
    }
}
