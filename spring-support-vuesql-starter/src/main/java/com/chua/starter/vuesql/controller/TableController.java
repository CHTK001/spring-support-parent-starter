package com.chua.starter.vuesql.controller;

import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.lang.treenode.CustomTreeNode;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.configuration.CacheConfiguration;
import com.chua.starter.common.support.result.Result;
import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.pojo.*;
import com.chua.starter.vuesql.service.WebsqlConfigService;
import com.chua.starter.vuesql.support.channel.TableChannel;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 數據庫
 *
 * @author CH
 */
@RestController
@RequestMapping("/vuesql/table")
public class TableController {
    @Resource
    private WebsqlConfigService websqlConfigService;

    /**
     * 解释sql
     *
     * @return 数据库
     */
    @PostMapping(value = "/explain/{configId}", consumes = "application/json")
    public Result<SqlResult> getExplainTableInfo(@PathVariable String configId,
                                                 @RequestBody JSONObject jsonObject
    ) {
        WebsqlConfig websqlConfig = websqlConfigService.forById(configId);
        String databaseType = websqlConfig.getConfigType().name().toLowerCase();
        TableChannel tableChannel =  ServiceProvider.of(TableChannel.class).getExtension(databaseType);
        if (null == tableChannel) {
            return Result.failed("数据库类型不支持", databaseType);
        }

        try {
            SqlResult rs = tableChannel.explain(websqlConfig,
                    jsonObject.getString("sql")
            );
            return Result.success(rs);
        } catch (Exception e) {
            return Result.failed(e.getLocalizedMessage().substring(e.getLocalizedMessage().indexOf(":") + 1));
        }

    }

    /**
     * 执行sql
     *
     * @return 数据库
     */
    @PostMapping(value = "/execute/{configId}", consumes = "application/json")
    public Result<SqlResult> getTableInfo(@PathVariable String configId,
                                          @RequestBody JSONObject jsonObject
    ) {
        WebsqlConfig websqlConfig = websqlConfigService.forById(configId);
        String databaseType = websqlConfig.getConfigType().name().toLowerCase();
        TableChannel tableChannel =  ServiceProvider.of(TableChannel.class).getExtension(databaseType);
        if (null == tableChannel) {
            return Result.failed("数据库类型不支持", databaseType);
        }

        try {
            SqlResult rs = tableChannel.execute(websqlConfig,
                    jsonObject.getString("sql"),
                    jsonObject.getIntValue("pageNum", 1),
                    jsonObject.getIntValue("pageSize", 10),
                    jsonObject.getString("orderColumn"),
                    jsonObject.getString("orderType")
            );
            return Result.success(rs);
        } catch (Exception e) {
            return Result.failed(e.getLocalizedMessage().substring(e.getLocalizedMessage().indexOf(":") + 1));
        }

    }

    /**
     * 跟新数据
     *
     * @return 数据库
     */
    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody JSONObject json) {
        WebsqlConfig websqlConfig = websqlConfigService.forById(json.getJSONObject("config").getString("configId"));
        String databaseType = websqlConfig.getConfigType().name().toLowerCase();
        TableChannel tableChannel = ServiceProvider.of(TableChannel.class).getExtension(databaseType);
        if (null == tableChannel) {
            return Result.failed("数据库类型不支持", databaseType);
        }
        try {
            return Result.success(tableChannel.update(websqlConfig,
                    json.getJSONObject("table").getString("realName"),
                    json.getJSONArray("record")));

        } catch (Exception e) {
            String localizedMessage = e.getLocalizedMessage().replace("java.sql.SQLException:", "");
            if(!localizedMessage.contains(":")) {
                return Result.failed(localizedMessage);
            }
            return Result.failed(localizedMessage.substring(localizedMessage.indexOf(":") + 1));
        }
    }

    /**
     * 根据配置获取数据库表,试图等信息
     *
     * @return 数据库
     */
    @GetMapping("/open/{configId}/{tableName}")
    public Result<OpenResult> openTable(
            @PathVariable String configId,
            @PathVariable String tableName,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize
    ) {
        WebsqlConfig websqlConfig = websqlConfigService.forById(configId);
        String databaseType = websqlConfig.getConfigType().name().toLowerCase();
        TableChannel tableChannel = ServiceProvider.of(TableChannel.class).getExtension(databaseType);
        if (null == tableChannel) {
            return Result.failed("数据库类型不支持", databaseType);
        }
        return Result.success(tableChannel.openTable(websqlConfig, tableName, pageNum, pageSize));
    }

    /**
     * 清空表
     *
     * @return 数据库
     */
    @GetMapping("/clear/{configId}/{tableName}")
    public Result<OperatorResult> clearTable(@PathVariable String configId, @PathVariable String tableName) {
        WebsqlConfig websqlConfig = websqlConfigService.forById(configId);
        String databaseType = websqlConfig.getConfigType().name().toLowerCase();
        TableChannel tableChannel = ServiceProvider.of(TableChannel.class).getExtension(databaseType);
        if (null == tableChannel) {
            return Result.failed("数据库类型不支持", databaseType);
        }
        return Result.success(tableChannel.doExecute(websqlConfig, tableName, "clear"));
    }

    /**
     * 根据配置获取数据库表,试图等信息
     *
     * @return 数据库
     */
    @GetMapping("/{configId}")
    public Result<Construct> getTableInfo(@PathVariable String configId) {
        WebsqlConfig websqlConfig = websqlConfigService.forById(configId);
        String databaseType = websqlConfig.getConfigType().name().toLowerCase();
        TableChannel tableChannel =  ServiceProvider.of(TableChannel.class).getExtension(databaseType);
        if (null == tableChannel) {
            return Result.failed("数据库类型不支持", databaseType);
        }
        List<Construct> constructs = null;
        try {
            constructs = tableChannel.getDataBaseConstruct(websqlConfig);
        } catch (Exception e) {
            return Result.failed(e.getLocalizedMessage());
        }
        CustomTreeNode<Construct, String> treeNode = new CustomTreeNode<>(Construct::getId, Construct::getPid, "0");
        treeNode.add(constructs);

        return Result.success(treeNode.transfer());
    }
    /**
     * 根据配置,数据库获取数据库信息
     *
     * @return 数据库
     */
    @GetMapping("/keyword/{configId}")
    @Cacheable(cacheManager = CacheConfiguration.DEFAULT_CACHE_MANAGER,cacheNames = "configId", key = "#configId")
    public Result<List<Keyword>> keyword(@PathVariable String configId) {
        WebsqlConfig websqlConfig = websqlConfigService.forById(configId);
        String databaseType = websqlConfig.getConfigType().name().toLowerCase();
        TableChannel tableChannel =  ServiceProvider.of(TableChannel.class).getExtension(databaseType);
        if (null == tableChannel) {
            return Result.failed("数据库类型不支持", databaseType);
        }
        return Result.success(tableChannel.getKeyword(websqlConfig));

    }
}
