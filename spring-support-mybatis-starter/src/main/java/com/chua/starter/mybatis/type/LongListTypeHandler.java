package com.chua.starter.mybatis.type;

import com.chua.starter.common.support.function.Joiner;
import com.google.common.base.Splitter;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeHandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 列表<Long> 的类型转换器实现类，对应数据库的 varchar 类型
 *
 * @author 芋道源码
 * @author CH
 * @since 4.0.0
 */
@MappedJdbcTypes(JdbcType.VARCHAR)
@MappedTypes(List.class)
public class LongListTypeHandler implements TypeHandler<List<Long>> {

    /**
     * Comma
    */
    private static final String COMMA = ",";

    @Override
    /**
     * 设置参数
    */
    public void setParameter(PreparedStatement ps, int i, List<Long> strings, JdbcType jdbcType) throws SQLException {
        // 设置占位符
        ps.setString(i, Joiner.on(COMMA).join(strings));
    }

    @Override
    /**
     * 获取结果
    */
    public List<Long> getResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return getResult(value);
    }

    @Override
    /**
     * 获取结果
    */
    public List<Long> getResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return getResult(value);
    }

    @Override
    /**
     * 获取结果
    */
    public List<Long> getResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return getResult(value);
    }

    /**
     * 获取结果
     *
     * @param value 值
     * @return 获取结果的结果
     */
    private List<Long> getResult(String value) {
        if (value == null) {
            return null;
        }
        return Splitter.on(COMMA).trimResults().omitEmptyStrings().splitToList(value).stream()
                .map(Long::valueOf).toList();
    }
}

