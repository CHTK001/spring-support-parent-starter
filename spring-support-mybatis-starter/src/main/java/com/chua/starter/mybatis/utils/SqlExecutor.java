package com.chua.starter.mybatis.utils;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 直接执行 SQL 或 XML 映射语句的工具类
 * 提供通过 Statement ID 执行 XML 映射，或直接执行原生 SQL 的能力
 *
 * @author CH
 */
@Component
public class SqlExecutor {

    private final SqlSessionFactory sqlSessionFactory;

    public SqlExecutor(@Qualifier("sqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    /**
     * 根据 Statement ID 执行 XML 映射的查询语句
     *
     * @param statementId 映射语句ID，格式为 "namespace.methodName"
     * @param params      参数对象
     * @param <T>         返回类型
     * @return 查询结果列表
     */
    public <T> List<T> selectList(String statementId, Object params) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return session.selectList(statementId, params);
        }
    }

    /**
     * 根据 Statement ID 执行 XML 映射的查询语句（无参数）
     *
     * @param statementId 映射语句ID
     * @param <T>         返回类型
     * @return 查询结果列表
     */
    public <T> List<T> selectList(String statementId) {
        return selectList(statementId, null);
    }

    /**
     * 根据 Statement ID 执行 XML 映射的单条查询
     *
     * @param statementId 映射语句ID
     * @param params      参数对象
     * @param <T>         返回类型
     * @return 查询结果
     */
    public <T> T selectOne(String statementId, Object params) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return session.selectOne(statementId, params);
        }
    }

    /**
     * 根据 Statement ID 执行 XML 映射的单条查询（无参数）
     *
     * @param statementId 映射语句ID
     * @param <T>         返回类型
     * @return 查询结果
     */
    public <T> T selectOne(String statementId) {
        return selectOne(statementId, null);
    }

    /**
     * 执行原生 SQL 查询
     *
     * @param sql   SQL 语句
     * @param params 参数数组
     * @param <T>   返回类型
     * @return 查询结果列表
     */
    public <T> List<T> query(String sql, Object... params) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return session.selectList(sql, params);
        }
    }

    /**
     * 执行原生 SQL 查询（无参数）
     *
     * @param sql SQL 语句
     * @param <T> 返回类型
     * @return 查询结果列表
     */
    public <T> List<T> query(String sql) {
        return query(sql, new Object[0]);
    }

    /**
     * 执行原生 SQL 更新（INSERT/UPDATE/DELETE）
     *
     * @param sql    SQL 语句
     * @param params 参数数组
     * @return 影响行数
     */
    public int execute(String sql, Object... params) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return session.update(sql, params);
        }
    }

    /**
     * 执行原生 SQL 更新（无参数）
     *
     * @param sql SQL 语句
     * @return 影响行数
     */
    public int execute(String sql) {
        return execute(sql, new Object[0]);
    }

    /**
     * 根据 Statement ID 执行 XML 映射的更新语句
     *
     * @param statementId 映射语句ID
     * @param params      参数对象
     * @return 影响行数
     */
    public int update(String statementId, Object params) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            return session.update(statementId, params);
        }
    }

    /**
     * 根据 Statement ID 执行 XML 映射的更新语句（无参数）
     *
     * @param statementId 映射语句ID
     * @return 影响行数
     */
    public int update(String statementId) {
        return update(statementId, null);
    }
}
