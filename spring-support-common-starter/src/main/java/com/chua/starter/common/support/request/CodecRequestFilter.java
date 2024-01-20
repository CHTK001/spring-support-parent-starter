package com.chua.starter.common.support.request;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.crypto.CodecKeyPair;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.utils.Hex;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.filter.CustomHttpServletRequestWrapper;
import com.chua.starter.common.support.properties.CodecProperties;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.security.PrivateKey;

/**
 * 编解码器请求处理程序
 *
 * @author CH
 */
public class CodecRequestFilter implements Filter {
    private final CodecProperties codecProperties;

    private PrivateKey privateKey;

    public CodecRequestFilter(CodecProperties codecProperties) {
        this.codecProperties = codecProperties;
        privateKey = createPrivateKey();
    }

    private PrivateKey createPrivateKey() {
        if(codecProperties.isEnable()) {
            try {
                Codec codec = Codec.build(codecProperties.getCodecType());
                if(codec instanceof CodecKeyPair) {
                    return ((CodecKeyPair) codec).getPrivateKey(Hex.decodeHex(codecProperties.getPrivateKey()));
                }
            } catch (Exception e) {
            }
        }
        return null;

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if(!codecProperties.isEnable()) {
            chain.doFilter(request, response);
            return;
        }

// form-data不校验
        String contentType = request.getContentType();
        if (!"application/json".equals(contentType)) {
            chain.doFilter(request, response);
            return;
        }


        // 拿到加密串
        String body = new CustomHttpServletRequestWrapper((HttpServletRequest) request).getBody();
        if (StringUtils.isEmpty(body)) {
            request = new CustomHttpServletRequestWrapper((HttpServletRequest) request, body);
            chain.doFilter(request, response);
            return;
        }

        JsonObject jsonObject = Json.getJsonObject(body);
        if(jsonObject.size() != 1 || StringUtils.isEmpty(jsonObject.getString("data"))) {
            request = new CustomHttpServletRequestWrapper((HttpServletRequest) request, body);
            chain.doFilter(request, response);
            return;
        }

        Codec codec = Codec.build(codecProperties.getCodecType());
        CodecKeyPair codecKeyPair = (CodecKeyPair) codec;
        codecKeyPair.setPrivateKey(privateKey);
        // 解析
        body = codec.decode(body);
        request = new CustomHttpServletRequestWrapper((HttpServletRequest) request, body);
        chain.doFilter(request, response);
    }
}
