package com.chua.starter.oauth.client.support.advice;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.google.common.base.Strings;
import lombok.NoArgsConstructor;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * advice
 *
 * @author CH
 * @since 2022/7/29 9:43
 */
@NoArgsConstructor(staticName = "create")
public class Advice {

    private final Map<String, AdviceResolver> resolverMap;

    {
        List<AdviceResolver> adviceResolvers = ServiceProvider.of(AdviceResolver.class).collect();
        resolverMap = new ConcurrentHashMap<>(adviceResolvers.size());
        for (AdviceResolver adviceResolver : adviceResolvers) {
            if(null == adviceResolver) {
                continue;
            }
            String type = adviceResolver.type();
            if(StringUtils.isBlank(type)) {
                continue;
            }
            resolverMap.put(type.toLowerCase(), adviceResolver);
        }
    }

    /**
     * 获取解释器
     *
     * @param mediaType 媒体类型
     * @return 解释器
     */
    public AdviceResolver resolve(MediaType mediaType) {
        AdviceResolver adviceResolver = null;
        if ((adviceResolver = resolverMap.get(mediaType.toString().toLowerCase())) == null) {
            return null;
        }

        return adviceResolver;
    }

    /**
     * 获取解释器
     *
     * @param accept      媒体类型
     * @param contentType 媒体类型
     * @return 解释器
     */
    public AdviceResolver resolve(String accept, String contentType) {
        AdviceResolver adviceResolver = null;
        if (!Strings.isNullOrEmpty(contentType)) {
            if ((adviceResolver = resolverMap.get(contentType)) != null) {
                return adviceResolver;
            }
        }

        if (!Strings.isNullOrEmpty(accept)) {
            String[] split = accept.split(",");
            for (String s : split) {
                if ((adviceResolver = resolverMap.get(s)) != null) {
                    return adviceResolver;
                }
            }
        }

        return null;
    }
}
