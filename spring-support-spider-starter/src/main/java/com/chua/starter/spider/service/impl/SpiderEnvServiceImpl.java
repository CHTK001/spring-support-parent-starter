package com.chua.starter.spider.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.spider.entity.SpiderEnv;
import com.chua.starter.spider.mapper.SpiderEnvMapper;
import com.chua.starter.spider.service.SpiderEnvService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 环境变量服务实现。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Service
public class SpiderEnvServiceImpl
        extends SpiderBaseServiceImpl<SpiderEnvMapper, SpiderEnv>
        implements SpiderEnvService {

    @Override
    /** 映射已启用 */
    public Map<String, String> mapEnabled() {
        List<SpiderEnv> list = list(Wrappers.<SpiderEnv>lambdaQuery()
                .eq(SpiderEnv::getEnabled, Integer.valueOf(1)));
        Map<String, String> result = new LinkedHashMap<>(list.size() * 2);
        for (SpiderEnv env : list) {
            result.put(env.getEnvKey(), env.getEnvValue());
        }
        return result;
    }
}