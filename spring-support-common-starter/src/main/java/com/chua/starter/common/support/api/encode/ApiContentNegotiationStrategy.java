package com.chua.starter.common.support.api.encode;

import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.accept.ContentNegotiationStrategy;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.List;

/**
 * API响应内容协商策略
 * <p>
 * 根据响应编码开关决定返回的媒体类型。
 * 当响应编码开启时，返回 application/encrypted-data 类型。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025/10/10
 */
public class ApiContentNegotiationStrategy implements ContentNegotiationStrategy {

    private final ApiResponseEncodeRegister apiResponseEncodeRegister;

    public ApiContentNegotiationStrategy(ApiResponseEncodeRegister apiResponseEncodeRegister) {
        this.apiResponseEncodeRegister = apiResponseEncodeRegister;
    }

    @Override
    public List<MediaType> resolveMediaTypes(NativeWebRequest webRequest) throws HttpMediaTypeNotAcceptableException {
        if (apiResponseEncodeRegister.isPass()) {
            return MEDIA_TYPE_ALL_LIST;
        }
        return List.of(MediaType.parseMediaType("application/encrypted-data"));
    }
}

