package com.chua.starter.mybatis.handler;

import com.google.common.base.Splitter;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * string -> list long
 * @author CH
 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class LongListTypeHandler extends BaseTypeHandler<List<Long>> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<Long> parameter, JdbcType jdbcType) throws SQLException {
        StringBuilder sb = new StringBuilder();
        for (int idx = 0; idx < parameter.size(); idx++) {
            if (idx > 0) sb.append(",");
            sb.append(parameter.get(idx));
        }
        ps.setString(i, sb.toString());
    }

    @Override
    public List<Long> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String string = rs.getString(columnName);
        return parseList(string);
    }

    @Override
    public List<Long> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String string = rs.getString(columnIndex);
        return parseList(string);
    }

    @Override
    public List<Long> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String string = cs.getString(columnIndex);
        return parseList(string);
    }

    private List<Long> parseList(String string) {
        List<Long> result = new ArrayList<>();
        if (string == null || string.isEmpty()) return result;
        List<String> parts = Splitter.on(",").trimResults().omitEmptyStrings().splitToList(string);
        for (String part : parts) {
            try {
                result.add(Long.parseLong(part));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }
}
