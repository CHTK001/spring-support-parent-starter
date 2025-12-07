//package com.chua.starter.common.support.filter;
//
//import com.alibaba.fastjson2.filter.ValueFilter;
//import com.chua.common.support.utils.PrivacyUtils;
//import com.chua.common.support.utils.StringUtils;
//import com.chua.starter.common.support.annotations.PrivacyEncrypt;
//import com.chua.starter.common.support.api.rule.PrivacyTypeEnum;
//
//import java.lang.reflect.Field;
//
///**
// * 敏感信息过滤�?
// *
// * @author CH
// */
//public class PrivacyEncryptFilter implements ValueFilter {
//    @Override
//    public Object apply(Object object, String name, Object value) {
//        if (!(value instanceof String) || StringUtils.isEmpty(((String) value).trim())) {
//            return value;
//        }
//
//        try {
//            Field field = object.getClass().getDeclaredField(name);
//            PrivacyEncrypt privacyEncrypt;
//            if (String.class != field.getType() || (privacyEncrypt = field.getAnnotation(PrivacyEncrypt.class)) == null) {
//                return value;
//            }
//
//            String origin = value.toString();
//            int prefixNoMaskLen = privacyEncrypt.prefixNoMaskLen();
//            String symbol = privacyEncrypt.symbol();
//            int suffixNoMaskLen = privacyEncrypt.suffixNoMaskLen();
//            PrivacyTypeEnum privacyTypeEnum = privacyEncrypt.type();
//
//            switch (privacyTypeEnum) {
//                case CUSTOMER:
//                    return PrivacyUtils.desValue(origin, prefixNoMaskLen, suffixNoMaskLen, symbol);
//                case NAME:
//                    return PrivacyUtils.hideChineseName(origin);
//                case ID_CARD:
//                    return PrivacyUtils.hideCard(origin);
//                case PHONE:
//                    return PrivacyUtils.hidePhone(origin);
//                case EMAIL:
//                    return PrivacyUtils.hideEmail(origin);
//                case ADDRESS:
//                    return PrivacyUtils.hideAddress(origin, 8);
//                case BANK_CARD:
//                    return PrivacyUtils.hideBankCard(origin);
//                case PASSWORD:
//                    return PrivacyUtils.hidePassword(origin);
//                case CAR_NUMBER:
//                    return PrivacyUtils.hideCarNumber(origin);
//                case NONE:
//                    return origin;
//                default:
//                    return value;
//            }
//        } catch (Exception ignored) {
//        }
//        return value;
//    }
//}

