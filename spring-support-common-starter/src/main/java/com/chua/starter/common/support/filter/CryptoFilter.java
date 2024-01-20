//package com.chua.starter.common.support.filter;
//
//import com.alibaba.fastjson2.filter.ValueFilter;
//import com.chua.common.support.crypto.Codec;
//import com.chua.common.support.crypto.CryptoModule;
//import com.chua.common.support.crypto.CryptoType;
//import com.chua.common.support.utils.StringUtils;
//import com.chua.starter.common.support.annotations.Crypto;
//import com.google.common.base.Strings;
//
//import java.lang.reflect.Field;
//
///**
// * 加解密过滤器
// *
// * @author CH
// */
//public class CryptoFilter implements ValueFilter {
//    @Override
//    public Object apply(Object object, String name, Object value) {
//        if (!(value instanceof String) || StringUtils.isEmpty(((String) value).trim())) {
//            return value;
//        }
//
//        try {
//            Field field = object.getClass().getDeclaredField(name);
//            Crypto crypto;
//            if (String.class != field.getType() || (crypto = field.getAnnotation(Crypto.class)) == null) {
//                return value;
//            }
//
//            CryptoType cryptoType = crypto.cryptoType();
//            CryptoModule cryptoModule = crypto.cryptoModule();
//            String key = crypto.key();
//            String origin = value.toString();
//            if (!Strings.isNullOrEmpty(origin) && null != cryptoType && StringUtils.isNotEmpty(key) && null != cryptoModule) {
//                if(cryptoModule == CryptoModule.DECODE) {
//                    return Codec.build(cryptoModule.name(), key).encodeHex(origin);
//                }
//                return Codec.build(cryptoModule.name(), key).decodeHex(origin);
//            }
//            return origin;
//        } catch (Exception ignored) {
//        }
//        return value;
//    }
//}
