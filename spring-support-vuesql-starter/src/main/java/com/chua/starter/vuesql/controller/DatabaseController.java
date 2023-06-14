package com.chua.starter.vuesql.controller;

import com.chua.starter.common.support.result.Result;
import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.service.WebsqlConfigService;
import com.chua.starter.vuesql.support.channel.TableChannel;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 數據庫
 * @author CH
 */
@RestController
@RequestMapping("/vuesql/database")
public class DatabaseController {


    @Resource
    private WebsqlConfigService websqlConfigService;

    @Resource
    private ApplicationContext applicationContext;

    /**
     * 获取所有数据库
     *
     * @return 数据库
     */
    @ResponseBody
    @GetMapping("/list")
    public Result<List<WebsqlConfig>> getDatabase() {
        return Result.success(websqlConfigService.list());
    }

    /**
     * 获取新增/更新数据库
     * @return 数据库
     */
    @ResponseBody
    @PostMapping("/save")
    public Result<Boolean> saveDatabase(@RequestBody WebsqlConfig websqlConfig) {
        String databaseType = websqlConfig.getConfigType().name().toLowerCase();
        TableChannel tableChannel = applicationContext.getBean(databaseType, TableChannel.class);
        if (null == tableChannel) {
            return Result.failed("数据库类型不支持", databaseType);
        }
        websqlConfig.setConfigUrl(tableChannel.createUrl(websqlConfig));
        return Result.success(websqlConfigService.saveOrUpdate(websqlConfig));
    }

    /**
     * 删除数据库
     * @return 数据库
     */
    @DeleteMapping("/delete/{configId}")
    public Result<Boolean> saveDatabase(@PathVariable String configId) {
        return Result.success(websqlConfigService.removeById(configId));
    }
}
