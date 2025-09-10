package com.chua.starter.common.support.result;

import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonArray;
import com.chua.common.support.net.UserAgent;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.codec.CodecFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * 请求加密
 * @author CH
 */
@RestControllerAdvice
@Slf4j
@SuppressWarnings("ALL")
public class CodecRequestBodyAdvice implements RequestBodyAdvice {

    private final CodecFactory codecFactory;

    public CodecRequestBodyAdvice(CodecFactory codecFactory) {
        this.codecFactory = codecFactory;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        // 检查是否启用请求解密
        if(!codecFactory.requestCodecOpen()) {
            log.debug("[RequestDecrypt] 请求解密未启用，跳过处理");
            return inputMessage;
        }

        HttpHeaders headers = inputMessage.getHeaders();

        // === 安全验证：检查User-Agent，防止爬虫攻击 ===
        String userAgent = headers.getFirst("user-agent");
        if (UserAgent.isCrawler(userAgent)) {
            log.warn("[RequestDecrypt] 检测到爬虫请求，拒绝处理: {}", userAgent);
            return EmptyHttpInputMessage.getInstance();
        }

        // === 获取关键请求头 ===
        String keyHeader = headers.getFirst(codecFactory.getKeyHeader());
        String timestamp = headers.getFirst("access-control-timestamp-user");
        String nonce = headers.getFirst("access-control-nonce");
        String otkId = headers.getFirst("access-control-otk-id");

        log.debug("[RequestDecrypt] 请求头信息 - keyHeader: {}, timestamp: {}, nonce: {}, otkId: {}",
                StringUtils.isNotEmpty(keyHeader) ? "存在" : "缺失",
                timestamp, nonce, otkId);

        // === 基础验证：检查必要的请求头 ===
        if(StringUtils.isEmpty(keyHeader)) {
            log.debug("[RequestDecrypt] 缺少密钥头，跳过解密处理");
            return inputMessage;
        }

        // === 反重放攻击验证 ===
        if (StringUtils.isNotEmpty(timestamp) && StringUtils.isNotEmpty(nonce)) {
            if (!codecFactory.validateAntiReplay(timestamp, nonce)) {
                log.error("[RequestDecrypt] 反重放攻击验证失败 - timestamp: {}, nonce: {}", timestamp, nonce);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求验证失败：重放攻击检测");
            }
            log.debug("[RequestDecrypt] 反重放攻击验证通过");
        } else {
            log.warn("[RequestDecrypt] 缺少反重放攻击保护参数，存在安全风险");
        }

        // === 读取请求体 ===
        byte[] originByteArray;
        try {
            originByteArray = IoUtils.toByteArray(inputMessage.getBody());
        } catch (IOException e) {
            log.error("[RequestDecrypt] 读取请求体失败", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体读取失败");
        }

        if (originByteArray.length == 0) {
            log.debug("[RequestDecrypt] 请求体为空，跳过解密处理");
            return new ByteInputMessage(originByteArray, headers);
        }
        
        String encodeBody = StringUtils.utf8Str(originByteArray);
        log.debug("[RequestDecrypt] 请求体长度: {} bytes", originByteArray.length);

        // === 提取加密数据 ===
        String encryptedData = null;
        boolean isArrayFormat = false;
        
        try {
            if(encodeBody.startsWith("[")) {
                // 数组格式处理
                JsonArray jsonArray = Json.getJsonArray(encodeBody);
                if(jsonArray.isEmpty()){
                    log.debug("[RequestDecrypt] JSON数组为空，返回原始数据");
                    return new ByteInputMessage(originByteArray, headers);
                }
                encryptedData = jsonArray.getJsonObject(0).getString("data");
                isArrayFormat = true;
            } else {
                // 对象格式处理
                encryptedData = Json.getJsonObject(encodeBody).getString("data");
                isArrayFormat = false;
            }
        } catch (Exception e) {
            log.warn("[RequestDecrypt] JSON解析失败，返回原始数据: {}", e.getMessage());
            return new ByteInputMessage(originByteArray, headers);
        }

        // === 验证加密数据 ===
        if (StringUtils.isBlank(encryptedData)) {
            log.debug("[RequestDecrypt] 未找到加密数据字段，返回原始数据");
            return new ByteInputMessage(originByteArray, headers);
        }

        log.debug("[RequestDecrypt] 提取到加密数据，格式: {}, 长度: {} chars",
                isArrayFormat ? "数组" : "对象", encryptedData.length());

        // === 一次性密钥验证（如果提供） ===
        if (StringUtils.isNotEmpty(otkId)) {
            String otkKey = codecFactory.getAndValidateOtk(otkId);
            if (StringUtils.isEmpty(otkKey)) {
                log.error("[RequestDecrypt] 一次性密钥验证失败 - OTK_ID: {}", otkId);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "一次性密钥验证失败");
            }
            log.debug("[RequestDecrypt] 一次性密钥验证成功 - OTK_ID: {}", otkId);
        }

        // === 执行解密 ===
        byte[] decryptedByteArray;
        try {
            decryptedByteArray = codecFactory.decodeRequest(encryptedData);

            if (decryptedByteArray == null || decryptedByteArray.length == 0) {
                log.warn("[RequestDecrypt] 解密结果为空，返回原始数据");
                return new ByteInputMessage(originByteArray, headers);
            }

            log.info("[RequestDecrypt] 请求解密成功 - 原始长度: {} bytes, 解密后长度: {} bytes, 格式: {}, OTK: {}",
                    originByteArray.length, decryptedByteArray.length,
                    isArrayFormat ? "数组" : "对象",
                    StringUtils.isNotEmpty(otkId) ? "已验证" : "未使用");

            return new ByteInputMessage(decryptedByteArray, headers);

        } catch (Exception e) {
            log.error("[RequestDecrypt] 请求解密失败 - 加密数据长度: {} chars, 错误: {}",
                    encryptedData.length(), e.getMessage(), e);

            // 解密失败时的安全策略：返回原始数据以保证业务连续性
            log.warn("[RequestDecrypt] 解密失败，返回原始数据以保证业务连续性");
            return new ByteInputMessage(originByteArray, headers);
        }
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    static class ByteInputMessage implements HttpInputMessage {
        private final byte[] body;
        private final ByteArrayInputStream inputStream;
        private final HttpHeaders headers;

        ByteInputMessage(byte[] body, HttpHeaders headers) {
            this.body = body;
            this.headers = headers;
            this.inputStream = new ByteArrayInputStream(body);
        }

        @Override
        public InputStream getBody() throws IOException {
            return inputStream;
        }
        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }

     static class EmptyHttpInputMessage implements HttpInputMessage{
        private static final EmptyHttpInputMessage EMPTY_MESSAGE = new EmptyHttpInputMessage();

        static final InputStream EMPTY = new ByteArrayInputStream(new byte[0]);
        static final HttpHeaders EMPTY_HEADER = new HttpHeaders();
         @Override
         public InputStream getBody() throws IOException {
             return EMPTY;
         }

         @Override
         public HttpHeaders getHeaders() {
             return EMPTY_HEADER;
         }

         public static EmptyHttpInputMessage getInstance() {
             return EMPTY_MESSAGE;
         }
     }
}
