package com.chua.starter.mybatis.handler;

import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.NumberUtils;
import com.chua.common.support.utils.StringUtils;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * MyBatis 类型处理器：实现 {@code List<Long>} 与 VARCHAR 之间的相互转换。
 *
 * <p>将 {@code List<Long>} 序列化为 {@code Long,Long,Long} 形式的字符串写入数据库；
 * 读取时按英文逗号拆分为 {@code List<Long>}。空值统一返回不可变的空列表。</p>
 *
 * <h2>使用示例</h2>
 * <pre>
 *   {@code @MappedTypes(List.class)}
 *   {@code @MappedJdbcTypes(JdbcType.VARCHAR)}
 *   public class LongListTypeHandler extends BaseTypeHandler&lt;List&lt;Long&gt;&gt; { ... }
 * </pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class LongListTypeHandler extends BaseTypeHandler<List<Long>> {

    /**
     * 列表元素分隔符（英文逗号）
     */
    private static final String SEPARATOR = ",";

    /**
     * 写入参数：把 {@code List<Long>} 序列化为字符串后绑定到 JDBC 参数。
     *
     * @param ps        JDBC 预编译语句
     * @param i         参数位置（从 1 开始）
     * @param parameter 待写入的 Long 列表，不为 null
     * @param jdbcType  JDBC 类型，本实现固定为 VARCHAR
     * @throws SQLException 当 JDBC 绑定失败时抛出
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps,
                                    int i,
                                    List<Long> parameter,
                                    JdbcType jdbcType) throws SQLException {
        // 复用 StringUtils.join 完成拼接，避免手写循环与 StringBuilder
        String value = StringUtils.join(parameter, SEPARATOR);
        ps.setString(i, value);
    }

    /**
     * 按列名读取并解析为 {@code List<Long>}。
     *
     * @param rs         JDBC 结果集
     * @param columnName 列名
     * @return 解析后的 Long 列表；列为 null 或空字符串时返回空列表
     * @throws SQLException 当 JDBC 读取失败时抛出
     */
    @Override
    public List<Long> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseList(rs.getString(columnName));
    }

    /**
     * 按列下标读取并解析为 {@code List<Long>}。
     *
     * @param rs          JDBC 结果集
     * @param columnIndex 列下标（从 1 开始）
     * @return 解析后的 Long 列表；列为 null 或空字符串时返回空列表
     * @throws SQLException 当 JDBC 读取失败时抛出
     */
    @Override
    public List<Long> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseList(rs.getString(columnIndex));
    }

    /**
     * 从存储过程调用结果中按列下标读取并解析为 {@code List<Long>}。
     *
     * @param cs          存储过程调用语句
     * @param columnIndex 列下标（从 1 开始）
     * @return 解析后的 Long 列表；列为 null 或空字符串时返回空列表
     * @throws SQLException 当 JDBC 读取失败时抛出
     */
    @Override
    public List<Long> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseList(cs.getString(columnIndex));
    }

    /**
     * 把逗号分隔的字符串解析为 {@code List<Long>}。
     *
     * @param value 待解析的字符串，可能为 null 或空
     * @return 解析后的 Long 列表；非法元素被忽略；空入参返回空列表
     */
    private List<Long> parseList(String value) {
        if (StringUtils.isEmpty(value)) {
            return Collections.emptyList();
        }

        // 复用 StringUtils.split + NumberUtils.toLong 完成解析
        List<Long> result = CollectionUtils.newArrayList();
        for (String part : StringUtils.split(value, SEPARATOR)) {
            // 跳过空白片段，避免空字符串进 Long.parseLong 抛异常
            if (StringUtils.isBlank(part)) {
                continue;
            }

            // NumberUtils.toLong 内部封装了 try/catch，非法值返回默认值 0L
            long parsed = NumberUtils.toLong(part, 0L);
            // 过滤非法解析项，避免 0L 污染业务数据
            if (parsed != 0L) {
                result.add(parsed);
            }
        }
        return result;
    }
}
