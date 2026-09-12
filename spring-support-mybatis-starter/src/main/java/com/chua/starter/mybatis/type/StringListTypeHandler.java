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
* 列表<String> 的类型转换器实现类，对应数据库的 varchar 类型
*
* @author 永不言败
* @since 2022 3/23 12:50:15
* @author CH
 */
@MappedJdbcTypes(JdbcType.VARCHAR)
@MappedTypes(List.class)
public class StringListTypeHandler implements TypeHandler<List<String>> {

    /** Comma */
    private static final String COMMA = ",";

    @Override
    /** 设置参数 */
    public void setParameter(PreparedStatement ps, int i, List<String> strings, JdbcType jdbcType) throws SQLException {
        // 设置占位符
        ps.setString(i, Joiner.on(COMMA).join(strings));
    }

    @Override
    /** 获取结果 */
    public List<String> getResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return getResult(value);
    }

    @Override
    /** 获取结果 */
    public List<String> getResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return getResult(value);
    }

    @Override
    /** 获取结果 */
    public List<String> getResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return getResult(value);
    }

    /**
    * 获取结果
    *
    * @param value 价值观
    * @return 获取结果的结果
     */
    private List<String> getResult(String value) {
        if (value == null) {
            return null;
        }
        return Splitter.on(COMMA).trimResults().splitToList(value);
    }
}

