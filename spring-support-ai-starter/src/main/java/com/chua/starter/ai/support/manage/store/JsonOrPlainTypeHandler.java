package com.chua.starter.ai.support.manage.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
* 「纯文本或 JSON」混合类型处理器。
*
* <p>用于 {@link SysAiConversationMessage#getContent()} 等 {@code Object} 类型字段：
* <ul>
*   <li>写入时：若值为 {@link String}，直接以纯文本存储（不额外加 JSON 引号，避免前端展示出现双引号）；
* 其他对象（映射/列表/POJO 等）序列化为 JSON 字符串存储。</li>
*   <li>读取时：若列值以 {@code { } 或 [ } 开头，尝试按 JSON 反序列化为结构化对象；
*       否则原样返回字符串。</li>
* </ul>
* </p>
*
* @since 4.0.0.42
* @author CH
 */
public class JsonOrPlainTypeHandler extends BaseTypeHandler<Object> {

    /**
    * Jackson 对象映射器：负责结构化对象与 JSON 字符串的互转
    */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
    * 写入参数：字符串 原样存储，其余对象 JSON 序列化。
    *
    * @param ps        预处理语句
    * @param i         参数下标
    * @param parameter 待写入的值
    * @param jdbcType  JDBC 类型
    * @throws SQLException 参数绑定失败时抛出
    */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        if (parameter instanceof String text) {
            // 纯文本直接存储，避免 JSON 引号污染
            ps.setString(i, text);
            return;
        }
        try {
            ps.setString(i, OBJECT_MAPPER.writeValueAsString(parameter));
        } catch (Exception e) {
            /**
            * SQL异常。
            * @param e.getMessage( e.获取消息(
            * @return sql异常的结果
            */
            throw new SQLException("无法序列化字段为 JSON: " + e.getMessage(), e);
        }
    }

    /**
    * 按列名读取：Word JSON 对象则反序列化，否则返回原字符串。
    *
    * @param rs         结果集
    * @param columnName 列名
    * @return 反序列化后的对象或原字符串
    * @throws SQLException 读取失败时抛出
    */
    @Override
    public Object getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return parse(value);
    }

    /**
    * 按下标读取（无 column标签 场景）。
    *
    * @param rs          结果集
    * @param columnIndex 列下标
    * @return 反序列化后的对象或原字符串
    * @throws SQLException 读取失败时抛出
    */
    @Override
    public Object getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return parse(value);
    }

    /**
    * 存储过程按下标读取。
    *
    * @param cs          可调用语句
    * @param columnIndex 列下标
    * @return 反序列化后的对象或原字符串
    * @throws SQLException 读取失败时抛出
    */
    @Override
    public Object getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return parse(value);
    }

    /**
    * 解析列值：以 {@code { 或 [ } 开头的值尝试 JSON 反序列化，否则原样返回。
    *
    * @param value 数据库列字符串
    * @return 结构化对象（JSON）或原字符串
    */
    private Object parse(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '{' || first == '[') {
            try {
                return OBJECT_MAPPER.readValue(value, Object.class);
            } catch (Exception ignored) {
                // JSON 解析失败时按普通字符串返回
            }
        }
        return value;
    }
}
