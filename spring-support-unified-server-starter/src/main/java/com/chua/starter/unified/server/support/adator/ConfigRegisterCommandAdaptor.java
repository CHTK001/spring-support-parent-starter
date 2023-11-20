package com.chua.starter.unified.server.support.adator;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.unified.server.support.entity.UnifiedConfig;
import com.chua.starter.unified.server.support.service.UnifiedConfigService;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

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
        ThreadUtils.newStaticThreadPool()
                .execute(() -> {
                    try {
                        String content = request.getContent();
                        JSONObject jsonObject = JSON.parseObject(content);
                        List<UnifiedConfig> configList = new LinkedList<>();

                        JSONObject transfer = jsonObject.getJSONObject("transfer");
                        Optional.ofNullable(jsonObject.getJSONObject("data")).orElse(new JSONObject())
                                .forEach((k, v) -> {
                                    if(v instanceof JSONObject) {
                                        ((JSONObject) v).forEach((k1 , v1) -> {
                                            UnifiedConfig item = new UnifiedConfig();
                                            if(null != k) {
                                                item.setUnfiedConfigFrom(k.length() > 500 ? k.substring(0, 490) : k);
                                            }
                                            item.setCreateTime(new Date());
                                            item.setUnifiedConfigStatus(1);
                                            item.setUnifiedAppname(request.getAppName());
                                            item.setUnifiedConfigProfile(request.getProfile());
                                            item.setUnifiedConfigName(ObjectUtils.defaultIfNull(k1, ""));
                                            item.setUnifiedConfigValue(ObjectUtils.defaultIfNull(v1, "").toString());
                                            item.setUnifiedConfigDesc(MapUtils.getString(transfer, item.getUnifiedConfigName()));
                                            configList.add(item);
                                        });
                                    }
                                });

                        transactionTemplate.execute(status -> {
                            unifiedConfigService.remove(Wrappers.<UnifiedConfig>lambdaQuery()
                                    .eq(UnifiedConfig::getUnifiedAppname, request.getAppName())
                                    .eq(UnifiedConfig::getUnifiedConfigProfile, request.getProfile())
                            );
                            unifiedConfigService.saveBatch(configList );
                            return true;
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                });
        return BootResponse.empty();
    }
}
