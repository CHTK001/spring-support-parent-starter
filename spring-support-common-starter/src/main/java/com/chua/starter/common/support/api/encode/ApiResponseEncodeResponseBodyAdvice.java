package com.chua.starter.common.support.api.encode;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.api.decode.ApiRequestDecodeRegister;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.chua.common.support.utils.StringUtils;


/**
   * 响应加密
   * @author CH
   * @since 4.0.0
   */
@RestControllerAdvice
@SuppressWarnings({"unchecked", "rawtypes"})
public class ApiResponseEncodeResponseBodyAdvice implements ResponseBodyAdvice<Object>  {
      /**
       * 日志
      */
    private static final Logger log = LoggerFactory.getLogger(ApiResponseEncodeResponseBodyAdvice.class);
          /**
           * API响应encoderegister
          */
        private final ApiResponseEncodeRegister apiResponseEncodeRegister;


      /**
       * 创建 api响应encode响应主体advice 实例
       * @param apiResponseEncodeRegister api响应encode注册
       */
    public ApiResponseEncodeResponseBodyAdvice(ApiResponseEncodeRegister apiResponseEncodeRegister) {
        this.apiResponseEncodeRegister = apiResponseEncodeRegister;
    }


    @Override
      /**
       * 支持
       * @param aClass 参数
       * @param methodParameter 参数
       * @return 返回值
      */
    public boolean supports(MethodParameter methodParameter, Class<? extends HttpMessageConverter<?>> aClass) {
        return true;
    }

    @SneakyThrows
    @Override
      /**
       * 之前主体写入
       * @param aClass 参数
       * @param mediaType 参数
       * @param methodParameter 参数
       * @param o 对象
       * @param serverHttpRequest 参数
       * @param serverHttpResponse 参数
       * @return 返回值
      */
    public Object beforeBodyWrite(Object o, MethodParameter methodParameter, MediaType mediaType, Class<? extends HttpMessageConverter<?
        >> aClass, ServerHttpRequest serverHttpRequest, ServerHttpResponse serverHttpResponse) {
        if (o instanceof StreamingResponseBody || o instanceof ResponseBodyEmitter) {
            return o;
        }

        if (mediaType != null) {
            String subtype = mediaType.getSubtype();
            if (subtype != null && (subtype.contains("event-stream") || subtype.contains("octet-stream"))) {
                return o;
            }
        }

        if (!(serverHttpRequest instanceof ServletServerHttpRequest servletServerHttpRequest)) {
            return o;
        }

        HttpServletRequest servletRequest = servletServerHttpRequest.getServletRequest();
        if (apiResponseEncodeRegister.isPass()) {
            return o;
        }

        if (o instanceof ReturnResult returnResult && !returnResult.isOk()) {
            return o;
        }

        if (apiResponseEncodeRegister.isPass(servletRequest.getRequestURI())) {
            return o;
        }


        HttpHeaders headers = serverHttpResponse.getHeaders();

        // keylog 回包：复用请求侧共享密钥（请求帧 attribute 或 x-codec-keylog-pub 头）
        String keylogMaterial = resolveKeylogMaterial(servletRequest);
        ApiResponseEncodeRegister.CodecResult codecResult =
                apiResponseEncodeRegister.encode(Json.toJson(o), keylogMaterial);
        if (null == codecResult) {
            // keylog 请求未携带密钥材料（响应加密绑定请求加密）→ 原样返回
            return o;
        }

          // 设置响应头
        headers.set("access-control-timestamp-user", codecResult.getTimestamp());
        headers.set("access-control-no-data", String.valueOf(true));

   // 设置内容-类型为application/octet-流
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

   // 添加X-内容-类型-期权: nosniff响应头
        headers.set("X-Content-Type-Options", "nosniff");

   // 构建响应数据（Key直接拼接在02后面）
        String responseData = "02" + codecResult.getKey() + "200" + codecResult.getData() + "ffff";

          // 将响应数据转换为字节数组（使用更真实的二进制转换方法）
        byte[] responseBytes = convertToBinary(responseData);

   // 设置内容-长度头，值为加密后数据的实际长度
        headers.setContentLength(responseBytes.length);

          // 返回二进制数据而不是JSON
        log.debug("[CodecResponse] 响应加密完成，数据长度: {}", responseBytes.length);
        return ResponseEntity.<byte[]>ok()
                .headers(headers)
                .body(responseBytes);
    }


    /**
     * 解析 keylog 回包所需的请求侧密钥材料。
     *
     * <p>优先取 decode advice 暂存的请求帧（有 body 的请求），其次取
     * x-codec-keylog-pub 头（无 body 的 keylog 请求也下发临时公钥）。</p>
     *
     * @param request 请求
     * @return 密钥材料；都没有时返回 null
     */
    private String resolveKeylogMaterial(HttpServletRequest request) {
        Object frame = request.getAttribute(ApiRequestDecodeRegister.REQUEST_CODEC_FRAME_ATTRIBUTE);
        if (frame instanceof String frameText && !frameText.isBlank()) {
            return frameText;
        }
        String pubHeader = request.getHeader("x-codec-keylog-pub");
        return null == pubHeader || pubHeader.isBlank() ? null : pubHeader.trim();
    }

      /**
       * 将字符串转换为字节数组（使用更真实的二进制转换方法）
       * @param str 字符串
       * @return 字节数组
       */
    private byte[] convertToBinary(String str) {
        if (StringUtils.isEmpty(str)) {
            return new byte[0];
        }
        
   // 使用字符集编码器将字符串转换为字节缓冲区
        CharBuffer charBuffer = CharBuffer.wrap(str);
        ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
        
          // 创建结果数组并复制数据
        byte[] result = new byte[byteBuffer.remaining()];
        byteBuffer.get(result);
        
        return result;
    }
}
