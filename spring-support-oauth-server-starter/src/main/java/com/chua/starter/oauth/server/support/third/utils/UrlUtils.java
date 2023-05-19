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
 * url工具类
 *
 * @author CH
 */
@Slf4j
public class UrlUtils {
    /**
     * 获取url
     *
     * @param url    url
     * @param params 参数
     * @return 结果
     */
    public static String getUrl(String url, Map<String, Object> params) {
        StringBuilder s = new StringBuilder();
        s.append(url);
        if (null != params && !params.isEmpty()) {
            s.append("?");
            Set<String> keys = params.keySet();
            int i = 0;

            for (Iterator<String> iterator = keys.iterator(); iterator.hasNext(); ++i) {
                String key = iterator.next();
                s.append(key).append("=").append(params.get(key).toString());
                if (i < keys.size() - 1) {
                    s.append("&");
                }
            }
        }

        return s.toString();
    }

    /**
     * 获取请求头
     *
     * @param urlPath       地址
     * @param requestMethod 类型
     * @param accessKey     ak
     * @param secretKey     sk
     * @return 消息头
     */
    public static HttpHeaders generateHeader(String urlPath, String requestMethod, String accessKey, String secretKey) {
        HttpHeaders headers = new HttpHeaders();
        log.info("params,urlStr={},requestMethod={},accessKey={},secretKey={}", urlPath, requestMethod, accessKey, secretKey);

        try {
            DateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            String date = dateFormat.format(new Date());
            URL url = new URL(urlPath);
            URI uri = new URI(url.getProtocol(), url.getHost(), url.getPath(), url.getQuery(), (String) null);
            String canonicalQueryString = getCanonicalQueryString(uri.getQuery());
            String message = requestMethod.toUpperCase() + "\n" + uri.getPath() + "\n" + canonicalQueryString + "\n" + accessKey + "\n" + date + "\n";
            Mac sha256 = Mac.getInstance("HmacSHA256");
            sha256.init(new SecretKeySpec(secretKey.getBytes(), "HmacSHA256"));
            byte[] hash = sha256.doFinal(message.getBytes());
            DatatypeConverter.printHexBinary(hash);
            String sign = DatatypeConverter.printBase64Binary(hash);
            headers.add("X-BG-HMAC-SIGNATURE", sign);
            headers.add("X-BG-HMAC-ALGORITHM", "hmac-sha256");
            headers.add("X-BG-HMAC-ACCESS-KEY", accessKey);
            headers.add("X-BG-DATE-TIME", date);
            headers.add("Content-Type", "application/x-www-form-urlencoded");
        } catch (Exception e) {
            log.error("generate error", e);
            throw new RuntimeException("generate header error");
        }

        log.info("header info,{}", headers);
        return headers;
    }

    /**
     * 获取请求头
     *
     * @param apiCode       api
     * @param urlPath       地址
     * @param requestMethod 类型
     * @param accessKey     ak
     * @param secretKey     sk
     * @return 消息头
     */
    public static HttpHeaders generateHeader(String apiCode, String urlPath, String requestMethod, String accessKey, String secretKey) {
        HttpHeaders headers = new HttpHeaders();
        log.info("params,urlStr={},requestMethod={},accessKey={},secretKey={}", urlPath, requestMethod, accessKey, secretKey);
        try {
            DateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            String date = dateFormat.format(new Date());
            URL url = new URL(urlPath);
            URI uri = new URI(url.getProtocol(), url.getHost(), url.getPath(), url.getQuery(), null);
            String canonicalQueryString = getCanonicalQueryString(uri.getQuery());
            String message = requestMethod.toUpperCase() + "\n" + uri.getPath() + "\n" + canonicalQueryString + "\n" + accessKey + "\n" + date + "\n";
            Mac hasher = Mac.getInstance("HmacSHA256");
            hasher.init(new SecretKeySpec(secretKey.getBytes(), "HmacSHA256"));
            byte[] hash = hasher.doFinal(message.getBytes());
            // to lowercase hexits
            DatatypeConverter.printHexBinary(hash);
            // to base64
            String sign = DatatypeConverter.printBase64Binary(hash);
            headers.add("X-BG-HMAC-SIGNATURE", sign);
            headers.add("X-BG-HMAC-ALGORITHM", "hmac-sha256");
            headers.add("X-BG-HMAC-ACCESS-KEY", accessKey);
            headers.add("X-BG-DATE-TIME", date);
            headers.add("zjgxfwxt-interface-code", apiCode);
            headers.add("zjgxfwxt-access-key", accessKey);
            headers.add("zjgxfwxt-sign", sign);
            headers.add("zjgxfwxt-time", date);
            headers.add("Content-Type", "application/x-www-form-urlencoded");
        } catch (Exception e) {
            log.error("generate error", e);
            throw new RuntimeException("generate header error");
        }
        log.info("header info,{}", headers);
        return headers;
    }

    /**
     * url查询参数
     *
     * @param query url
     * @return 结果
     */
    private static String getCanonicalQueryString(String query) {
        if (query != null && query.trim().length() != 0) {
            List<Pair<String, String>> queryParamList = new ArrayList<>();
            String[] params = query.split("&");
            String[] strings = params;
            int length = params.length;

            for (int var5 = 0; var5 < length; ++var5) {
                String param = strings[var5];
                int eqIndex = param.indexOf("=");
                String key = param.substring(0, eqIndex);
                String value = param.substring(eqIndex + 1);
                Pair<String, String> pair = new Pair(key, value);
                queryParamList.add(pair);
            }

            List<Pair<String, String>> sortedParamList = queryParamList.stream().sorted(Comparator.comparing((it) -> it.getKey() + "=" + Optional.ofNullable(it.getValue()).orElse(""))).collect(Collectors.toList());
            List<Pair<String, String>> encodeParamList = new ArrayList<>();
            sortedParamList.forEach((it) -> {
                try {
                    String key = URLEncoder.encode(it.getKey(), "utf-8");
                    String value = URLEncoder.encode(Optional.ofNullable(it.getValue()).orElse(""), "utf-8").replaceAll("\\%2B", "%20").replaceAll("\\+", "%20").replaceAll("\\%21", "!").replaceAll("\\%27", "'").replaceAll("\\%28", "(").replaceAll("\\%29", ")").replaceAll("\\%7E", "~").replaceAll("\\%25", "%");
                    encodeParamList.add(new Pair<>(key, value));
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException("encoding error");
                }
            });
            StringBuilder queryParamString = new StringBuilder(64);
            Iterator<Pair<String, String>> iterator = encodeParamList.iterator();

            while (iterator.hasNext()) {
                Pair<String, String> encodeParam = iterator.next();
                queryParamString.append(encodeParam.getKey()).append("=").append(Optional.ofNullable(encodeParam.getValue()).orElse(""));
                queryParamString.append("&");
            }

            return queryParamString.substring(0, queryParamString.length() - 1);
        } else {
            return "";
        }
    }
}
