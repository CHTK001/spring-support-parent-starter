package com.chua.starter.vuesql.support.channel;

import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.pojo.Construct;
import com.chua.starter.vuesql.pojo.Keyword;
import com.chua.starter.vuesql.pojo.OpenResult;
import com.chua.starter.vuesql.pojo.SqlResult;

import java.util.List;

/**
 * 表
 *
 * @author CH
 */
public interface TableChannel {
    /**
     * 创建url
     *
     * @param config 配置
     * @return url
     */
    String createUrl(WebsqlConfig config);

    /**
     * 获取数据库结构
     *
     * @param config 配置
     * @return 获取所有表
     */
    List<Construct> getDataBaseConstruct(WebsqlConfig config);

    /**
     * 获取所有表
     *
     * @param config 配置
     * @return 获取所有表
     */
    List<Keyword> getKeyword(WebsqlConfig config);

    /**
     * 分页查询
     *
     * @param websqlConfig 配置
     * @param sql          sql
     * @param pageNum      页码
     * @param pageSize     每页数量
     * @param sortColumn   排序字段
     * @param sortType     排序方式
     * @return 结果
     */
    SqlResult execute(WebsqlConfig websqlConfig,
                      String sql,
                      Integer pageNum,
                      Integer pageSize,
                      String sortColumn,
                      String sortType
    );

    /**
     * 解释sql
     *
     * @param websqlConfig 配置
     * @param sql          sql
     * @return 结果
     */
    SqlResult explain(WebsqlConfig websqlConfig, String sql);

    /**
     * 打开表
     *
     * @param websqlConfig 配置
     * @param tableName    表名
     * @param pageNum      页码
     * @param pageSize     每页数量
     * @return 结果
     */
    OpenResult openTable(WebsqlConfig websqlConfig, String tableName, Integer pageNum, Integer pageSize);
}
