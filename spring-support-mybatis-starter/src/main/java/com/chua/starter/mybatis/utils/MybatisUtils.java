package com.chua.starter.mybatis.utils;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.utils.BeanUtils;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.entity.Sorting;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MyBatis 工具类
 * 提供分页构建、数据拷贝等工具方法
 *
 * @author CH
 * @since 4.0.0
 */
public class MybatisUtils {

    /**
     * MyBatis工具。
     */
    private MybatisUtils() {
    }

    /**
     * MySQL 转义字符
     */
    private static final String MYSQL_ESCAPE_CHARACTER = "`";

    /**
     * 构建分页对象
     *
     * @param pageParam 分页参数
     * @param <T>       实体类型
     * @return 分页对象
     */
    public static <T> Page<T> buildPage(Query pageParam) {
        return buildPage(pageParam, null);
    }

    /**
     * 构建分页对象（包含排序）
     *
     * @param pageParam     分页参数
     * @param sortingFields 排序字段集合
     * @param <T>           实体类型
     * @return 分页对象
     */
    public static <T> Page<T> buildPage(Query pageParam, Collection<Sorting> sortingFields) {
        Page<T> page = new Page<>(pageParam.getPage(), pageParam.getPageSize());
        if (!CollectionUtils.isEmpty(sortingFields)) {
            List<OrderItem> orderItems = sortingFields.stream()
                    .map(sortingField -> Sorting.ORDER_ASC.equals(sortingField.getOrder())
                            ? OrderItem.asc(sortingField.getField())
                            : OrderItem.desc(sortingField.getField()))
                    .collect(Collectors.toList());
            page.addOrder(orderItems);
        }
        return page;
    }

    /**
     * 分页数据拷贝
     * 将源分页对象的数据拷贝到目标类型的分页对象中
     *
     * @param result 源分页对象
     * @param type   目标类型
     * @param <T>    目标类型
     * @return 目标类型的分页对象
     */
    public static <T> Page<T> copy(Page<?> result, Class<T> type) {
        Page<T> page = new Page<>();
        BeanUtils.copyProperties(result, page);

        List<?> records = result.getRecords();
        List<T> ts = BeanUtils.copyPropertiesList(records, type);
        page.setRecords(ts);
        return page;
    }

    /**
     * 根据 对账单 标识 执行 XML 映射的查询，返回列表
     *
     * @param sqlSessionFactory SQL会话工厂
     * @param statementId       映射语句标识，格式为 "namespace.方法名称"
     * @param params            参数对象
     * @param <T>               返回类型
     * @return 查询结果列表
     */
    public static <T> List<T> selectList(SqlSessionFactory sqlSessionFactory, String statementId, Object params) {
        /**
         * 尝试。
         * @param sqlSessionFactory.openSession(true SQL会话工厂.opensession(true
         */
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return session.selectList(statementId, params);
        }
    }

    /**
     * 根据 对账单 标识 执行 XML 映射的查询，返回单条记录
     *
     * @param sqlSessionFactory SQL会话工厂
     * @param statementId       映射语句标识
     * @param params            参数对象
     * @param <T>               返回类型
     * @return 查询结果
     */
    public static <T> T selectOne(SqlSessionFactory sqlSessionFactory, String statementId, Object params) {
        /**
         * 尝试。
         * @param sqlSessionFactory.openSession(true SQL会话工厂.opensession(true
         */
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return session.selectOne(statementId, params);
        }
    }

    /**
     * 执行原生 SQL 查询
     *
     * @param sqlSessionFactory SQL会话工厂
     * @param sql               SQL 语句
     * @param params            参数数组
     * @param <T>               返回类型
     * @return 查询结果列表
     */
    public static <T> List<T> query(SqlSessionFactory sqlSessionFactory, String sql, Object... params) {
        /**
         * 尝试。
         * @param sqlSessionFactory.openSession(true SQL会话工厂.opensession(true
         */
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return session.selectList(sql, params);
        }
    }

    /**
     * 执行原生 SQL 更新（插入/更新/删除）
     *
     * @param sqlSessionFactory SQL会话工厂
     * @param sql               SQL 语句
     * @param params            参数数组
     * @return 影响行数
     */
    public static int execute(SqlSessionFactory sqlSessionFactory, String sql, Object... params) {
        /**
         * 尝试。
         * @param sqlSessionFactory.openSession(true SQL会话工厂.opensession(true
         */
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return session.update(sql, params);
        }
    }
}
