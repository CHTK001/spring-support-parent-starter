package com.chua.starter.vuesql.support.channel;

import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.pojo.Construct;
import com.chua.starter.vuesql.pojo.Keyword;

import java.util.List;

/**
 * 表
 *
 * @author CH
 */
public interface TableChannel {
    /**
     * 创建url
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
}
