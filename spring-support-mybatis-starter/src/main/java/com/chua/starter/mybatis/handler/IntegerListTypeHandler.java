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
 * 字符串 -> 列表 integer
 * @author CH
 * @since 4.0.0
 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class IntegerListTypeHandler extends BaseTypeHandler<List<Integer>> {
    @Override
    /** 设置non空参数 */
    public void setNonNullParameter(PreparedStatement ps, int i, List<Integer> parameter, JdbcType jdbcType) throws SQLException {
        StringBuilder sb = new StringBuilder();
        for (int idx = 0; idx < parameter.size(); idx++) {
            if (idx > 0) {
                sb.append(",");
            }
            sb.append(parameter.get(idx));
        }
        ps.setString(i, sb.toString());
    }

    @Override
    /** 获取空结果 */
    public List<Integer> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String string = rs.getString(columnName);
        return parseList(string);
    }

    @Override
    /** 获取空结果 */
    public List<Integer> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String string = rs.getString(columnIndex);
        return parseList(string);
    }

    @Override
    /** 获取空结果 */
    public List<Integer> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String string = cs.getString(columnIndex);
        return parseList(string);
    }

    /**
     * 解析列表
     *
     * @param string 字符串
     * @return 解析列表的结果
     */
    private List<Integer> parseList(String string) {
        List<Integer> result = new ArrayList<>();
        if (string == null || string.isEmpty()) {
            return result;
        }
        List<String> parts = Splitter.on(",").trimResults().omitEmptyStrings().splitToList(string);
        for (String part : parts) {
            try {
                result.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }
}
