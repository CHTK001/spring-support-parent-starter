package com.chua.starter.unified.server.support.adator;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.starter.unified.server.support.entity.UnifiedConfig;
import com.chua.starter.unified.server.support.service.UnifiedConfigService;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * config-Register命令适配器
 *
 * @author CH
 * @since 2023/11/16
 */
@Spi("Register")
public class ConfigRegisterCommandAdaptor implements ConfigCommandAdaptor{

    @Resource
    private UnifiedConfigService unifiedConfigService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public BootResponse resolve(BootRequest request) {
        String content = request.getContent();
        JSONObject jsonObject = JSON.parseObject(content);
        List<UnifiedConfig> configList = new LinkedList<>();
        jsonObject.forEach((k, v) -> {
            UnifiedConfig item = new UnifiedConfig();
            item.setCreateTime(new Date());
            item.setUnifiedAppname(request.getAppName());
            item.setUnifiedConfigName(ObjectUtils.defaultIfNull(k, ""));
            item.setUnifiedConfigValue(ObjectUtils.defaultIfNull(v, "").toString());
            configList.add(item);
        });

        transactionTemplate.execute(status -> {
            unifiedConfigService.remove(Wrappers.<UnifiedConfig>lambdaQuery().eq(UnifiedConfig::getUnifiedAppname, request.getAppName()));
            unifiedConfigService.saveBatch(configList );
            return true;
        });

        return BootResponse.empty();
    }
}
