package com.chua.starter.common.support.strategies;

import com.chua.starter.common.support.codec.CodecFactory;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.accept.ContentNegotiationStrategy;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.List;

/**
 * @author CH
 * @since 2025/10/10 15:07
 */
public class CustomContentNegotiationStrategy implements ContentNegotiationStrategy {
    private final CodecFactory codecFactory;

    public CustomContentNegotiationStrategy(CodecFactory codecFactory) {
        this.codecFactory = codecFactory;
    }

    @Override
    public List<MediaType> resolveMediaTypes(NativeWebRequest webRequest) throws HttpMediaTypeNotAcceptableException {
        if(codecFactory.isPass()) {
            return MEDIA_TYPE_ALL_LIST;
        }
        return List.of(MediaType.parseMediaType("application/encrypted-data"));
    }
}
