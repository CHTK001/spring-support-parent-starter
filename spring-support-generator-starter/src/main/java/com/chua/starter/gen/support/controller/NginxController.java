package com.chua.starter.gen.support.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.gen.support.entity.*;
import com.chua.starter.gen.support.service.*;
import com.chua.starter.mybatis.entity.RequestPage;
import com.chua.starter.mybatis.utils.PageResultUtils;
import lombok.AllArgsConstructor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * @author CH
 */
@RestController
@SuppressWarnings("ALL")
@AllArgsConstructor
@RequestMapping("v1/nginx")
public class NginxController {

    private final SysGenNginxHttpConfigService sysGenNginxHttpConfigService;
    private final SysGenNginxServerService sysGenNginxServerService;
    private final SysGenNginxServerItemService sysGenNginxServerItemService;
    private final SysGenNginxUpstreamService sysGenNginxUpstreamService;
    private final SysGenNginxUpstreamItemService sysGenNginxUpstreamItemService;


    private final TransactionTemplate transactionTemplate;
    /**
     * 查询基础配置
     * @param page 分页
     * @return
     */
    @GetMapping("config/page")
    public ReturnPageResult<SysGenNginxHttpConfig> configPage(RequestPage<SysGenNginxHttpConfig> page) {
        return PageResultUtils.ok(sysGenNginxHttpConfigService.page(page.createPage()));
    }

    /**
     * 保存基础配置
     * @param config 分页
     * @return
     */
    @PostMapping("config/save")
    public ReturnResult<SysGenNginxHttpConfig> configSave(@RequestBody SysGenNginxHttpConfig config) {
        sysGenNginxHttpConfigService.save(config);
        return ReturnResult.ok(config);
    }

    /**
     * 修改基础配置
     * @param config 分页
     * @return
     */
    @PutMapping("config/update")
    public ReturnResult<SysGenNginxHttpConfig> configUpdate(@RequestBody SysGenNginxHttpConfig config) {
        sysGenNginxHttpConfigService.updateById(config);
        return ReturnResult.ok(config);
    }
    /**
     * 删除基础配置
     * @param config 分页
     * @return
     */
    @DeleteMapping("config/delete")
    public ReturnResult<Boolean> configUpdate(String ids) {
        if(StringUtils.isEmpty(ids)) {
            return ReturnResult.illegal("信息不存在");
        }
        sysGenNginxHttpConfigService.removeBatchByIds(Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(ids));
        return ReturnResult.ok(true);
    }
    //*********************************************************************************代理
    /**
     * 查询代理配置
     * @param page 分页
     * @return
     */
    @GetMapping("server/page")
    public ReturnPageResult<SysGenNginxServer> serverPage(RequestPage<SysGenNginxServer> page) {
        return PageResultUtils.ok(sysGenNginxServerService.page(page.createPage()));
    }

    /**
     * 保存代理配置
     * @param config 分页
     * @return
     */
    @PostMapping("server/save")
    public ReturnResult<SysGenNginxServer> serverSave(@RequestBody SysGenNginxServer config) {
        transactionTemplate.execute(new TransactionCallback<Boolean>() {
            @Override
            public Boolean doInTransaction(TransactionStatus status) {
                sysGenNginxServerService.save(config);
                List<SysGenNginxServerItem> configItem = config.getItem();
                if(!CollectionUtils.isEmpty(configItem)) {
                    configItem.forEach(it -> it.setServerId(config.getServerId()));
                    sysGenNginxServerItemService.saveBatch(configItem);
                }
                return true;
            }
        });
        return ReturnResult.ok(config);
    }

    /**
     * 修改代理配置
     * @param config 分页
     * @return
     */
    @PutMapping("server/update")
    public ReturnResult<SysGenNginxServer> serverUpdate(@RequestBody SysGenNginxServer config) {
        sysGenNginxServerService.updateById(config);
        return ReturnResult.ok(config);
    }
    /**
     * 删除代理配置
     * @param config 分页
     * @return
     */
    @DeleteMapping("server/delete")
    public ReturnResult<Boolean> serverDelete(String ids) {
        if(StringUtils.isEmpty(ids)) {
            return ReturnResult.illegal("信息不存在");
        }
        transactionTemplate.execute(new TransactionCallback<Boolean>() {
            @Override
            public Boolean doInTransaction(TransactionStatus status) {
                Set<String> idLists = Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(ids);
                sysGenNginxServerService.removeBatchByIds(idLists);
                sysGenNginxServerItemService.remove(Wrappers.<SysGenNginxServerItem>lambdaUpdate().in(SysGenNginxServerItem::getServerId, idLists));
                return true;
            }
        });
        return ReturnResult.ok(true);
    }
    //*********************************************************************************代理子项

    /**
     * 查询代理配置
     * @param page 分页
     * @return
     */
    @GetMapping("server/item/page")
    public ReturnPageResult<SysGenNginxServerItem> serverItemPage(RequestPage<SysGenNginxServerItem> page, String serverId) {
        if(StringUtils.isEmpty(serverId)) {
            return ReturnPageResult.illegal("服务不存在");
        }
        return PageResultUtils.ok(sysGenNginxServerItemService.page(page.createPage(), Wrappers.<SysGenNginxServerItem>lambdaQuery().eq(SysGenNginxServerItem::getServerId, serverId)));
    }

    /**
     * 保存代理配置
     * @param config 分页
     * @return
     */
    @PostMapping("server/item/save")
    public ReturnResult<SysGenNginxServerItem> serverItemSave(@RequestBody SysGenNginxServerItem config) {
        if(null == config.getServerId()) {
            return ReturnResult.illegal("服务不存在");
        }
        sysGenNginxServerItemService.save(config);
        return ReturnResult.ok(config);
    }

    /**
     * 修改代理配置
     * @param config 分页
     * @return
     */
    @PutMapping("server/item/update")
    public ReturnResult<SysGenNginxServerItem> serverItemUpdate(@RequestBody SysGenNginxServerItem config) {
        if(null == config.getServerId()) {
            return ReturnResult.illegal("服务不存在");
        }
        sysGenNginxServerItemService.updateById(config);
        return ReturnResult.ok(config);
    }
    /**
     * 删除代理配置
     * @param config 分页
     * @return
     */
    @DeleteMapping("server/item/delete")
    public ReturnResult<Boolean> serverItemUpdate(String ids) {
        if(StringUtils.isEmpty(ids)) {
            return ReturnResult.illegal("信息不存在");
        }
        sysGenNginxServerItemService.removeBatchByIds(Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(ids));
        return ReturnResult.ok(true);
    }
    //*********************************************************************************负载均衡
    /**
     * 查询代理配置
     * @param page 分页
     * @return
     */
    @GetMapping("upstream/page")
    public ReturnPageResult<SysGenNginxUpstream> upstreamPage(RequestPage<SysGenNginxUpstream> page) {
        return PageResultUtils.ok(sysGenNginxUpstreamService.page(page.createPage()));
    }

    /**
     * 保存代理配置
     * @param config 分页
     * @return
     */
    @PostMapping("upstream/save")
    public ReturnResult<SysGenNginxUpstream> upstreamSave(@RequestBody SysGenNginxUpstream config) {
        transactionTemplate.execute(new TransactionCallback<Boolean>() {
            @Override
            public Boolean doInTransaction(TransactionStatus status) {
                sysGenNginxUpstreamService.save(config);
                List<SysGenNginxUpstreamItem> configItem = config.getItem();
                if(!CollectionUtils.isEmpty(configItem)) {
                    configItem.forEach(it -> it.setUpstreamId(config.getUpstreamId() + ""));
                    sysGenNginxUpstreamItemService.saveBatch(configItem);
                }
                return true;
            }
        });
        return ReturnResult.ok(config);
    }

    /**
     * 修改代理配置
     * @param config 分页
     * @return
     */
    @PutMapping("upstream/update")
    public ReturnResult<SysGenNginxUpstream> upstreamUpdate(@RequestBody SysGenNginxUpstream config) {
        sysGenNginxUpstreamService.updateById(config);
        return ReturnResult.ok(config);
    }
    /**
     * 删除代理配置
     * @param config 分页
     * @return
     */
    @DeleteMapping("upstream/delete")
    public ReturnResult<Boolean> upstreamUpdate(String ids) {
        if(StringUtils.isEmpty(ids)) {
            return ReturnResult.illegal("信息不存在");
        }
        transactionTemplate.execute(new TransactionCallback<Boolean>() {
            @Override
            public Boolean doInTransaction(TransactionStatus status) {
                Set<String> idLists = Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(ids);
                sysGenNginxUpstreamService.removeBatchByIds(idLists);
                sysGenNginxUpstreamItemService.remove(Wrappers.<SysGenNginxUpstreamItem>lambdaUpdate().in(SysGenNginxUpstreamItem::getUpstreamId, idLists));
                return true;
            }
        });
        return ReturnResult.ok(true);
    }
    //*********************************************************************************负载均衡子项

    /**
     * 查询代理配置
     * @param page 分页
     * @return
     */
    @GetMapping("upstream/item/page")
    public ReturnPageResult<SysGenNginxUpstreamItem> upstreamItemPage(RequestPage<SysGenNginxUpstreamItem> page, String upstreamId) {
        if(StringUtils.isEmpty(upstreamId)) {
            return ReturnPageResult.illegal("代理不存在");
        }
        return PageResultUtils.ok(sysGenNginxUpstreamItemService.page(page.createPage(), Wrappers.<SysGenNginxUpstreamItem>lambdaQuery().eq(SysGenNginxUpstreamItem::getUpstreamId, upstreamId)));
    }

    /**
     * 保存代理配置
     * @param config 分页
     * @return
     */
    @PostMapping("upstream/item/save")
    public ReturnResult<SysGenNginxUpstreamItem> upstreamItemSave(@RequestBody SysGenNginxUpstreamItem config) {
        if(null == config.getUpstreamId()) {
            return ReturnResult.illegal("代理不存在");
        }
        sysGenNginxUpstreamItemService.save(config);
        return ReturnResult.ok(config);
    }

    /**
     * 修改代理配置
     * @param config 分页
     * @return
     */
    @PutMapping("upstream/item/update")
    public ReturnResult<SysGenNginxUpstreamItem> upstreamItemUpdate(@RequestBody SysGenNginxUpstreamItem config) {
        if(null == config.getUpstreamId()) {
            return ReturnResult.illegal("代理不存在");
        }
        sysGenNginxUpstreamItemService.updateById(config);
        return ReturnResult.ok(config);
    }
    /**
     * 删除代理配置
     * @param config 分页
     * @return
     */
    @DeleteMapping("upstream/item/delete")
    public ReturnResult<Boolean> upstreamItemUpdate(String ids) {
        if(StringUtils.isEmpty(ids)) {
            return ReturnResult.illegal("信息不存在");
        }
        sysGenNginxUpstreamItemService.removeBatchByIds(Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(ids));
        return ReturnResult.ok(true);
    }
}
