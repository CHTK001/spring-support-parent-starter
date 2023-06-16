package com.chua.starter.vuesql.support.channel;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.utils.FileUtils;
import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.pojo.Construct;
import com.chua.starter.vuesql.pojo.Keyword;
import com.chua.starter.vuesql.pojo.OpenResult;
import com.chua.starter.vuesql.pojo.SqlResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 表
 *
 * @author CH
 */
public interface TableChannel {
    static final String PATH = System.getProperty("vuesql.home", "./vuesql");

    /**
     * 生成目录
     *
     * @param subPath 子目录
     * @return 结果
     */
    static String create(String subPath) {
        FileUtils.mkdir(PATH);
        String s = PATH + subPath;
        FileUtils.mkdir(s);
        return s;
    }

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

    /**
     * 检测参数
     *
     * @param websqlConfig 配置
     * @param file         文件
     * @return 结果
     */
    default String check(WebsqlConfig websqlConfig, MultipartFile file) {
        return null;
    }

    /**
     * 更新数据
     *
     * @param websqlConfig 配置
     * @param table        表
     * @param data         数据
     * @return
     */
    Boolean update(WebsqlConfig websqlConfig, String table, JSONArray data);
}
