package com.chua.starter.common.support.result;

import com.chua.common.support.converter.Converter;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.RandomUtils;
import com.chua.starter.common.support.codec.CodecFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

/**
 * 响应加密
 * @author CH
 */
@RestControllerAdvice
@Slf4j
@SuppressWarnings("ALL")

public class CodecResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final CodecFactory codecFactory;


    public CodecResponseBodyAdvice(CodecFactory codecFactory) {
        this.codecFactory = codecFactory;
    }


    @Override
    public boolean supports(MethodParameter methodParameter, Class<? extends HttpMessageConverter<?>> aClass) {
        return true;
    }

    @SneakyThrows
    @Override
    public Object beforeBodyWrite(Object o, MethodParameter methodParameter, MediaType mediaType, Class<? extends HttpMessageConverter<?>> aClass, ServerHttpRequest serverHttpRequest, ServerHttpResponse serverHttpResponse) {
        HttpServletRequest servletRequest = ((ServletServerHttpRequest) serverHttpRequest).getServletRequest();
        if(codecFactory.isPass()) {
            return o;
        }

        if( methodParameter.hasMethodAnnotation(com.chua.common.support.annotations.Ignore.class)) {
            return o;
        }

        if(!(serverHttpRequest instanceof ServletServerHttpRequest)) {
            return o;
        }

        if(o instanceof ReturnResult returnResult && !returnResult.isOk()) {
            return o;
        }

        if(codecFactory.isPass(servletRequest.getRequestURI())) {
            return o;
        }


        HttpHeaders headers = serverHttpResponse.getHeaders();

        // 使用主密钥加密（去除OTK）
        CodecFactory.CodecResult codecResult = codecFactory.encode(Json.toJson(o));

        // 设置响应头
        headers.set("access-control-timestamp-user", codecResult.getTimestamp());
        headers.set("access-control-no-data", String.valueOf(true));

        // 设置Content-Type为application/octet-stream
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        // 添加X-Content-Type-Options: nosniff响应头
        headers.set("X-Content-Type-Options", "nosniff");

        // 构建响应数据（key直接拼接到02后面）
        String responseData = "02" + codecResult.getKey() + "200" + codecResult.getData() + "ffff";

        // 将响应数据转换为字节数组（使用更真实的二进制转换方法）
        byte[] responseBytes = convertToBinary(responseData);

        // 设置Content-Length头，值为加密后数据的实际长度
        headers.setContentLength(responseBytes.length);

        // 返回二进制数据而不是JSON
        log.debug("[CodecResponse] 响应加密完成，数据长度: {}", responseBytes.length);
        return ResponseEntity.<byte[]>ok()
                .headers(headers)
                .body(responseBytes);
    }


    /**
     * 将字符串转换为字节数组（使用更真实的二进制转换方法）
     * @param str 字符串
     * @return 字节数组
     */
    private byte[] convertToBinary(String str) {
        if (str == null || str.isEmpty()) {
            return new byte[0];
        }
        
        // 使用CharsetEncoder将字符串转换为字节缓冲区
        CharBuffer charBuffer = CharBuffer.wrap(str);
        ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
        
        // 创建结果数组并复制数据
        byte[] result = new byte[byteBuffer.remaining()];
        byteBuffer.get(result);
        
        return result;
    }
}