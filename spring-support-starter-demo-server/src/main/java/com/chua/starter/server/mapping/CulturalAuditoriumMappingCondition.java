package com.chua.starter.server.mapping;//package com.chua.starter.server.mapping;
//
//import cn.hutool.crypto.digest.DigestUtil;
//import com.alibaba.fastjson.JSON;
//import com.chua.starter.common.support.crypto.mac.HmacAlgorithm;
//import com.chua.starter.common.support.crypto.utils.DigestUtils;
//import com.chua.starter.common.support.placeholder.PropertyResolver;
//import com.chua.starter.common.support.utils.RandomUtils;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.lang3.StringUtils;
//import org.apache.commons.lang3.math.NumberUtils;
//
//import java.util.HashMap;
//import java.util.LinkedHashMap;
//import java.util.Map;
//
//import static java.nio.charset.StandardCharsets.UTF_8;
//
///**
// * sign
// *
// * @author CH
// */
//@Slf4j
//public class CulturalAuditoriumMappingCondition implements MappingCondition {
//
//    private static final String PLACE_LIST = "/provide/open/place/place-list";
//    private static final String ASSESS_RANKING = "/provide/open/place/assess-ranking";
//    private static final String ASSESS_LIST = "/provide/open/place/assess-list";
//    private static final String TOPIC = "/provide/open/datav/topic";
//    private static final String ASSESS_HOT_CATS = "/provide/open/datav/assess-hot-cats";
//    private static final String ASSESS = "/provide/open/datav/assess";
//    private static final String ARTICLE_LIST = "/provide/open/article/article-list";
//    private static final String ARTICLE_DETAIL = "/provide/open/article/article-detail";
//
//    private static final String IOT_INFO = "/provide/open/datav/iot-info";
//    private static final String IOT_PUSH_LATEST = "/provide/open/datav/iot-push-latest";
//    private static final String IOT_PUSH_DETAIL = "/provide/open/datav/iot-push-detail";
//
//    private static final Map<Character, String> SPE = new HashMap<Character, String>() {
//        {
//            put(' ', "%20");
//            put('!', "%21");
//            put('\"', "%22");
//            put('#', "%23");
//            put('$', "%24");
//            put('%', "%25");
//            put('&', "%26");
//            put('’', "%27");
//            put('(', "%28");
//            put(')', "%29");
//            put('*', "%2a");
////            put('+', "%2b");
//            put('+', "%2d");
//            put(',', "%2c");
////            put('-', "%2d");
//            put('-', "%2b");
////            put('/', "%2f");
//            put('_', "%2f");
//            put(':', "%3a");
//            put(';', "%3b");
//            put('<', "%3c");
//            put('=', "%3d");
//            put('>', "%3e");
//            put('?', "%3f");
//            put('@', "%40");
//            put('[', "%5b");
//            put('\\', "%5c");
//            put(']', "%5d");
//            put('^', "%5e");
////            put('_', "%5f");
//            put('/', "%5f");
//            put('{', "%7b");
//            put('|', "%7c");
//            put('}', "%7d");
//            put('~', "%7e");
//        }
//    };
//
//    @Override
//    public String resolve(PropertyResolver placeholderResolver, String name, String path) {
//        if (log.isDebugEnabled()) {
//            log.debug("======================================sign-body=====================================");
//        }
//        // 13位随机数
//        String random = RandomUtils.randomString(13);
//        //13位时间戳
//        String timestamp = placeholderResolver.resolvePlaceholders("${Timestamp}");
//        //appSecret
//        String appSecret = placeholderResolver.resolvePlaceholders("${secret}");
//        // 接口地址
//        // JSON格式请求参数
//        String body = getBody(path, placeholderResolver);
//        // 待签名的字符
//        String srcStr = StringUtils.prependIfMissing(path, "/") + '&' + body + '&' + timestamp + '&' + random;
//
//        String signStr = DigestUtils.hmac(HmacAlgorithm.HMAC_SHA1, appSecret.getBytes(UTF_8)).digestBase64(srcStr, true) + "=";
//        if (log.isDebugEnabled()) {
//            System.out.println("signStr: " + signStr);
//        }
//
//        StringBuilder sb = new StringBuilder();
//        for (int i = 0; i < signStr.length(); i++) {
//            char si = signStr.charAt(i);
//            if (SPE.containsKey(si)) {
//                sb.append(SPE.get(si));
//                continue;
//            }
//
//            sb.append(si);
//        }
//
//        signStr = sb.toString();
//        if (log.isDebugEnabled()) {
//            System.out.println("random: " + random);
//            System.out.println("timestamp: " + timestamp);
//            System.out.println("appSecret: " + appSecret);
//            System.out.println("body: " + body);
//            System.out.println("srcStr: " + srcStr);
//            System.out.println("signStr: " + signStr);
//            System.out.println("path: " + path);
//        }
//        signStr = '0' + random + signStr;
//        return signStr;
//    }
//
//    /**
//     * path
//     *
//     * @param path
//     * @param placeholderResolver
//     * @return
//     */
//    private String getBody(String path, PropertyResolver placeholderResolver) {
//        Map<String, Object> jsonObject = new LinkedHashMap<>();
//        switch (path) {
//            case PLACE_LIST: {
//                jsonObject.put("page", Integer.parseInt(placeholderResolver.resolvePlaceholders("${page}")));
//                jsonObject.put("size", Integer.parseInt(placeholderResolver.resolvePlaceholders("${size}")));
//                return JSON.toJSONString(jsonObject);
//            }
//            case ASSESS_RANKING: {
//                try {
//                    jsonObject.put("startDate", NumberUtils.createInteger(placeholderResolver.resolvePlaceholders("${startDate:}")));
//                } catch (Exception ignored) {
//                }
//                try {
//                    jsonObject.put("endDate", NumberUtils.createInteger(placeholderResolver.resolvePlaceholders("${endDate:}")));
//                } catch (Exception ignored) {
//                }
//                jsonObject.put("size", Integer.parseInt(placeholderResolver.resolvePlaceholders("${size}")));
//                return JSON.toJSONString(jsonObject);
//            }
//            case ASSESS_LIST: {
//
//                jsonObject.put("placeId", placeholderResolver.resolvePlaceholders("${placeId}"));
//                try {
//                    jsonObject.put("before", Integer.valueOf(placeholderResolver.resolvePlaceholders("${before}")));
//                } catch (Throwable e) {
//                }
//                jsonObject.put("size", Integer.parseInt(placeholderResolver.resolvePlaceholders("${size}")));
//                try {
//                    jsonObject.put("startDate", NumberUtils.createInteger(placeholderResolver.resolvePlaceholders("${startDate:}")));
//                } catch (Exception ignored) {
//                }
//
//                try {
//                    jsonObject.put("endDate", NumberUtils.createInteger(placeholderResolver.resolvePlaceholders("${endDate:}")));
//                } catch (Exception ignored) {
//                }
//                jsonObject.put("status", Integer.parseInt(placeholderResolver.resolvePlaceholders("${status:0}")));
//
//                return JSON.toJSONString(jsonObject);
//            }
//            case IOT_INFO:
//            case TOPIC: {
//                return "{}";
//            }
//            case ASSESS_HOT_CATS: {
//                jsonObject.put("type", Integer.parseInt(placeholderResolver.resolvePlaceholders("${type:1}")));
//                return JSON.toJSONString(jsonObject);
//            }
//            case ASSESS: {
//                jsonObject.put("type", Integer.parseInt(placeholderResolver.resolvePlaceholders("${type:1}")));
//                return JSON.toJSONString(jsonObject);
//            }
//            case ARTICLE_LIST: {
//                jsonObject.put("page", Integer.parseInt(placeholderResolver.resolvePlaceholders("${page:1}")));
//                jsonObject.put("size", Integer.parseInt(placeholderResolver.resolvePlaceholders("${size:90}")));
//                try {
//                    jsonObject.put("catId", placeholderResolver.resolvePlaceholders("${catId}"));
//                } catch (NumberFormatException ignored) {
//                }
//
//                try {
//                    jsonObject.put("feature", placeholderResolver.resolvePlaceholders("${feature}"));
//                } catch (Exception ignored) {
//                }
//                return JSON.toJSONString(jsonObject);
//            }
//            case IOT_PUSH_DETAIL:
//            case ARTICLE_DETAIL: {
//                jsonObject.put("id", placeholderResolver.resolvePlaceholders("${id}"));
//                return JSON.toJSONString(jsonObject);
//            }
//            case IOT_PUSH_LATEST: {
//                jsonObject.put("after", placeholderResolver.resolvePlaceholders("${after:0}"));
//                jsonObject.put("size", placeholderResolver.resolvePlaceholders("${size:20}"));
//                return JSON.toJSONString(jsonObject);
//            }
//            default:
//                return "";
//        }
//    }
//}
