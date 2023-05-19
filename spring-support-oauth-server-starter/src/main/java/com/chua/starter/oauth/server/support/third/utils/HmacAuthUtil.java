package com.chua.starter.oauth.server.support.third.utils;

import javafx.util.Pair;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * hmac
 *
 * @author CH
 */
@Slf4j
public class HmacAuthUtil {

    /**
     * 解析消息头
     *
     * @param urlStr        地址
     * @param requestMethod 类型
     * @param accessKey     ak
     * @param secretKey     sk
     * @return 消息头
     */
    public static HttpHeaders generateHeader(String urlStr, String requestMethod, String accessKey, String secretKey) {
        log.info("params,urlStr={},requestMethod={},accessKey={},secretKey={}", urlStr, requestMethod, accessKey, secretKey);
        HttpHeaders header = new HttpHeaders();
        try {
            DateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            String date = dateFormat.format(new Date());
            URL url = new URL(urlStr);
            URI uri = new URI(url.getProtocol(), url.getHost(), url.getPath(), url.getQuery(), null);
            String canonicalQueryString = getCanonicalQueryString(uri.getQuery());
            String message = requestMethod.toUpperCase() + "\n" + uri.getPath() + "\n" + canonicalQueryString + "\n" + accessKey + "\n" + date + "\n";
            Mac hasher = Mac.getInstance("HmacSHA256");
            hasher.init(new SecretKeySpec(secretKey.getBytes(), "HmacSHA256"));
            byte[] hash = hasher.doFinal(message.getBytes());
            // to lowercase hexits            DatatypeConverter.printHexBinary(hash);
            // to base64
            String sign = DatatypeConverter.printBase64Binary(hash);
            header.set("X-BG-HMAC-SIGNATURE", sign);
            header.set("X-BG-HMAC-ALGORITHM", "hmac-sha256");
            header.set("X-BG-HMAC-ACCESS-KEY", accessKey);
            header.set("X-BG-DATE-TIME", date);
        } catch (Exception e) {
            log.error("generate error", e);
            throw new RuntimeException("generate header error");
        }
        log.info("header info,{}", header);
        return header;
    }

    /**
     * 地址低缓
     *
     * @param query 地址
     * @return 结果
     */
    private static String getCanonicalQueryString(String query) {
        if (query == null || query.trim().length() == 0) {
            return "";
        }
        List<Pair<String, String>> queryParamList = new ArrayList<>();
        String[] params = query.split("&");
        for (String param : params) {
            int eqIndex = param.indexOf("=");
            String key = param.substring(0, eqIndex);
            String value = param.substring(eqIndex + 1);
            Pair<String, String> pair = new Pair<String, String>(key, value);
            queryParamList.add(pair);
        }
        List<Pair<String, String>> sortedParamList = queryParamList.stream().sorted(Comparator.comparing(param -> param.getKey() + "=" + Optional.ofNullable(param.getValue()).orElse(""))).collect(Collectors.toList());
        List<Pair<String, String>> encodeParamList = new ArrayList<>();
        sortedParamList.forEach(param -> {
            try {
                String key = URLEncoder.encode(param.getKey(), "utf-8");
                String value = URLEncoder.encode(Optional.ofNullable(param.getValue()).orElse(""), "utf-8").replaceAll("\\%2B", "%20").replaceAll("\\+", "%20").replaceAll("\\%21", "!").replaceAll("\\%27", "'").replaceAll("\\%28", "(").replaceAll("\\%29", ")").replaceAll("\\%7E", "~").replaceAll("\\%25", "%");
                encodeParamList.add(new Pair<>(key, value));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("encoding error");
            }
        });
        StringBuilder queryParamString = new StringBuilder(64);
        for (Pair<String, String> encodeParam : encodeParamList) {
            queryParamString.append(encodeParam.getKey()).append("=").append(Optional.ofNullable(encodeParam.getValue()).orElse(""));
            queryParamString.append("&");
        }
        return queryParamString.substring(0, queryParamString.length() - 1);
    }
}
