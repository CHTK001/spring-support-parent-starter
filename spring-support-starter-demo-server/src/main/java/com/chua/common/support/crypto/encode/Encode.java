package com.chua.common.support.crypto.encode;//package com.chua.common.support.crypto.encode;
//
//import com.chua.common.support.crypto.Hex;
//import com.chua.common.support.utils.StringUtils;
//
///**
// * 加密
// *
// * @author CH
// */
//public interface Encode {
//
//    /**
//     * 加密
//     *
//     * @param bytes 内容
//     * @return 加密结果
//     */
//    byte[] encode(byte[] bytes);
//
//    /**
//     * 加密
//     *
//     * @param bytes 内容
//     * @return 加密结果
//     */
//    default byte[] encode(String bytes) {
//        return encode(StringUtils.utf8Bytes(bytes));
//    }
//
//    /**
//     * 加密
//     *
//     * @param content 内容
//     * @return 加密结果
//     */
//    default String encodeHex(String content) {
//        return Hex.encodeHexString(encode(content));
//    }
//}
