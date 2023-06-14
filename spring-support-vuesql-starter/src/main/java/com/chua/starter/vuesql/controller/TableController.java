package com.chua.starter.vuesql.controller;

import com.chua.common.support.lang.treenode.CustomTreeNode;
import com.chua.starter.common.support.result.Result;
import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.pojo.Construct;
import com.chua.starter.vuesql.pojo.Keyword;
import com.chua.starter.vuesql.service.WebsqlConfigService;
import com.chua.starter.vuesql.support.channel.TableChannel;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @Resource
    private ApplicationContext applicationContext;
    /**
     * 根据配置获取数据库表,试图等信息
     *
     * @return 数据库
     */
    @GetMapping("/{configId}")
    public Result<Construct> getTableInfo(@PathVariable String configId) {
        WebsqlConfig websqlConfig = websqlConfigService.getById(configId);
        String databaseType = websqlConfig.getConfigType().name().toLowerCase();
        TableChannel tableChannel = applicationContext.getBean(databaseType, TableChannel.class);
        if (null == tableChannel) {
            return Result.failed("数据库类型不支持", databaseType);
        }
        List<Construct> constructs = null;
        try {
            constructs = tableChannel.getDataBaseConstruct(websqlConfig);
        } catch (Exception e) {
            return Result.failed("请检查数据源配置");
        }
        CustomTreeNode<Construct, Integer> treeNode = new CustomTreeNode<>(Construct::getId, Construct::getPid);
        treeNode.add(constructs);

        return Result.success(treeNode.transfer());

    }
    /**
     * 根据配置,数据库获取数据库信息
     *
     * @return 数据库
     */
    @GetMapping("/keyword/{configId}")
    public Result<List<Keyword>> keyword(@PathVariable String configId) {
        WebsqlConfig websqlConfig = websqlConfigService.getById(configId);
        String databaseType = websqlConfig.getConfigType().name().toLowerCase();
        TableChannel tableChannel = applicationContext.getBean(databaseType, TableChannel.class);
        if (null == tableChannel) {
            return Result.failed("数据库类型不支持", databaseType);
        }
        return Result.success(tableChannel.getKeyword(websqlConfig));

    }
}
