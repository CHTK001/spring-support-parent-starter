package com.chua.starter.spider.node;

import com.chua.common.support.task.flow.FlowContext;
import com.chua.common.support.task.flow.FlowNode;
import com.chua.common.support.task.flow.FlowNodeField;
import com.chua.common.support.task.flow.FlowProps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库节点。
 *
 * <p>对数据源执行一条 SQL，支持查询（SELECT）与写入（INSERT/UPDATE/DELETE）两类：
 * 查询结果以 {@code List<Map<String,Object>>} 写入上下文属性；写入类 SQL 返回受影响行数。</p>
 *
 * <p>节点配置说明：</p>
 * <ul>
 *   <li>{@code sql} 待执行的 SQL 文本（必填），可使用 {@code ?} 占位符</li>
 *   <li>{@code params} 占位符取值来源，顺序对应 SQL 中 {@code ?} 的位置，
 *       每一项为上下文属性名（逗号分隔）</li>
 *   <li>{@code saveAs} 结果写入的上下文属性名（必填）</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
public class SpiderDbNode implements FlowNode {

    /**
     * 查询类 SQL 关键字前缀
     */
    private static final String SQL_SELECT = "select";

    /**
     * 数据源
     */
    @Resource
    /** 数据来源 */
    private DataSource dataSource;

    /**
     * 节点类型标识。
     *
     * @return 节点类型
     */
    @Override
    public String type() {
        return "spider.db";
    }

    /**
     * 节点配置表单元信息。
     *
     * @return 表单元信息列表
     */
    @Override
    public List<FlowNodeField> configSchema() {
        List<FlowNodeField> schema = new ArrayList<>();
        schema.add(FlowNodeField.textarea("sql", "SQL 文本", "select * from table where id = ?"));
        schema.add(FlowNodeField.input("params", "占位符参数（属性名，逗号分隔）"));
        schema.add(FlowNodeField.input("saveAs", "写入属性名", true));
        return schema;
    }

    /**
     * 执行数据库节点。
     *
     * @param context 当前流程上下文
     */
    @Override
    public void execute(FlowContext context) {
        FlowProps props = context.currentNodeProps();
        String sql = props.getString("sql");
        String saveAs = props.getString("saveAs");
        if (sql == null || sql.trim().isEmpty()) {
            /**
             * illegal参数异常。
             * @param 参数" 参数"
             * @return illegal参数异常的结果
             */
            throw new IllegalArgumentException("spider.db 节点缺少 sql 参数");
        }
        if (saveAs == null || saveAs.isEmpty()) {
            /**
             * illegal参数异常。
             * @param 参数" 参数"
             * @return illegal参数异常的结果
             */
            throw new IllegalArgumentException("spider.db 节点缺少 saveAs 参数");
        }
        List<Object> params = resolveParams(props.getString("params"), context);
        String trimmed = sql.trim().toLowerCase();
        Object result = trimmed.startsWith(SQL_SELECT)
                /**
                 * 查询for列表。
                 * @param sql SQL
                 * @param params 参数
                 * @return 查询for列表的结果
                 */
                ? queryForList(sql, params) : executeUpdate(sql, params);
        context.setAttribute(saveAs, result);
        log.info("spider.db 执行完成: saveAs={} rows={}", saveAs,
                result instanceof List<?> list ? list.size() : result);
    }

    /**
     * 解析占位符参数值列表。
     *
     * <p>属性名为逗号分隔列表，特别地以 {@code current} 标识当前处理数据。</p>
     *
     * @param paramsText 属性名列表文本
     * @param context    当前流程上下文
     * @return 参数值列表
     */
    private List<Object> resolveParams(String paramsText, FlowContext context) {
        List<Object> result = new ArrayList<>();
        if (paramsText == null || paramsText.trim().isEmpty()) {
            return result;
        }
        for (String token : paramsText.split(",")) {
            String name = token.trim();
            if ("current".equals(name)) {
                result.add(context.getData());
            } else {
                result.add(context.getAttribute(name));
            }
        }
        return result;
    }

    /**
     * 执行查询 SQL 并返回结果行列表。
     *
     * @param sql    SQL 文本
     * @param params 占位参数值
     * @return 结果行列表（每行为列名到值的映射）
     */
    private List<Map<String, Object>> queryForList(String sql, List<Object> params) {
        /**
         * 尝试。
         * @param dataSource.getConnection( 数据源.getconnection(
         */
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            /**
             * bind参数。
             * @param statement 对账单
             * @param params 参数
             */
            bindParams(statement, params);
            /**
             * 尝试。
             * @param statement.executeQuery( 对账单.执行查询(
             */
            try (ResultSet resultSet = statement.executeQuery()) {
                return toRows(resultSet);
            }
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param e.getMessage( e.获取消息(
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("spider.db 查询失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行写入类 SQL 并返回受影响行数。
     *
     * @param sql    SQL 文本
     * @param params 占位参数值
     * @return 受影响行数
     */
    private int executeUpdate(String sql, List<Object> params) {
        /**
         * 尝试。
         * @param dataSource.getConnection( 数据源.getconnection(
         */
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            /**
             * bind参数。
             * @param statement 对账单
             * @param params 参数
             */
            bindParams(statement, params);
            return statement.executeUpdate();
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param e.getMessage( e.获取消息(
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("spider.db 更新失败: " + e.getMessage(), e);
        }
    }

    /**
     * 绑定占位参数。
     *
     * @param statement 预编译语句
     * @param params    参数值列表
     * @throws Exception 绑定异常
     */
    private void bindParams(PreparedStatement statement, List<Object> params) throws Exception {
        for (int i = 0; i < params.size(); i++) {
            statement.setObject(i + 1, params.get(i));
        }
    }

    /**
     * 将结果集转换为行列表。
     *
     * @param resultSet 结果集
     * @return 行列表
     * @throws Exception 解析异常
     */
    private List<Map<String, Object>> toRows(ResultSet resultSet) throws Exception {
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(metaData.getColumnLabel(i), resultSet.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }
}