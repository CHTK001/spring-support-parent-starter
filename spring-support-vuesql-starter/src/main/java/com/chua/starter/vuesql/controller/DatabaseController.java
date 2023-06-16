package com.chua.starter.vuesql.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.result.Result;
import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.enums.DatabaseType;
import com.chua.starter.vuesql.service.WebsqlConfigService;
import com.chua.starter.vuesql.support.channel.TableChannel;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
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
    @GetMapping("/type")
    @Cacheable(cacheNames = "websql", key = "'type'")
    public Result<DatabaseType[]> getDatabaseType() {
        return Result.success(DatabaseType.values());
    }

    /**
     * 获取所有数据库
     *
     * @return 数据库
     */
    @ResponseBody
    @GetMapping("/list")
    @Cacheable(cacheNames = "websql", key = "'database'")
    public Result<List<WebsqlConfig>> getDatabase() {
        return Result.success(websqlConfigService.list());
    }

    /**
     * 获取新增/更新数据库
     * @return 数据库
     */
    @ResponseBody
    @PostMapping("/save")
    @CacheEvict(cacheNames = "websql", key = "'database'")
    public Result<Boolean> saveDatabase(HttpServletRequest request, @RequestParam(value = "file", required = false)MultipartFile file) {
        WebsqlConfig websqlConfig = BeanUtils.copyProperties(request.getParameterMap(), WebsqlConfig.class);
        String databaseType = websqlConfig.getConfigType().name().toLowerCase();
        TableChannel tableChannel = ServiceProvider.of(TableChannel.class).getExtension(databaseType);
        if (null == tableChannel) {
            return Result.failed("{}数据库类型不支持", databaseType);
        }

        long count = 0;
        if(websqlConfig.getConfigId() == null) {
            count = websqlConfigService.count(Wrappers.<WebsqlConfig>lambdaQuery()
                    .eq(WebsqlConfig::getConfigName, websqlConfig.getConfigName()));
        } else {
            count = websqlConfigService.count(Wrappers.<WebsqlConfig>lambdaQuery()
                    .eq(WebsqlConfig::getConfigName, websqlConfig.getConfigName())
                    .ne(WebsqlConfig::getConfigId, websqlConfig.getConfigId())
            );
        }
        if(count > 0) {
            return Result.failed("{}配置名称已存在");
        }

        String msg = null;
        if(StringUtils.isNotEmpty(msg = tableChannel.check(websqlConfig, file))) {
            return Result.failed(msg);
        }
        websqlConfig.setConfigUrl(tableChannel.createUrl(websqlConfig));
        return Result.success(websqlConfigService.saveOrUpdate(websqlConfig));
    }

    /**
     * 删除数据库
     * @return 数据库
     */
    @DeleteMapping("/delete/{configId}")
    @CacheEvict(cacheNames = "websql", key = "'database'")
    public Result<Boolean> saveDatabase(@PathVariable String configId) {
        WebsqlConfig websqlConfig = websqlConfigService.getById(configId);
        if(null ==websqlConfig) {
            return Result.success(true);
        }
        String configFile = websqlConfig.getConfigFile();
        if(StringUtils.isNotEmpty(configFile)) {
            try {
                FileUtils.delete(configFile);
            } catch (Exception ignored) {
            }
        }
        return Result.success(websqlConfigService.removeById(configId));
    }
}