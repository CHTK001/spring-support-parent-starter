package com.chua.starter.vuesql.controller;

import com.chua.common.support.context.factory.ApplicationContext;
import com.chua.starter.common.support.result.Result;
import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.service.WebsqlConfigService;
import com.chua.starter.vuesql.support.channel.TableChannel;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

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
     * 获取新增/更新数据库
     *
     * @return 数据库
     */
    @PostMapping("/{databaseName}/{databaseConfigId}")
    public Result<Boolean> saveDatabase(@PathVariable String databaseName, @PathVariable String databaseConfigId) {
        WebsqlConfig websqlConfig = websqlConfigService.getById(databaseConfigId);
        String databaseType = websqlConfig.getConfigType().name().toLowerCase();
        TableChannel tableChannel = applicationContext.getBean(databaseType, TableChannel.class);
        if (null == tableChannel) {
            return Result.failed("数据库类型不支持", databaseType);
        }


    }
}
