package com.chua.starter.common.support.algorithm.crypto;

import com.chua.common.support.lang.algorithm.crypto.Hex;

import java.nio.charset.StandardCharsets;

public class Codec {

    public static Codec build(String type, String key) {
        return new Codec(type, key);
    }

    private Codec(String type, String key) {
    }

    public String encodeHex(String data) {
        if (data == null) return null;
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        return Hex.encodeHexString(bytes);
    }

    public String decodeHex(String hex) {
        if (hex == null) return null;
        byte[] bytes = Hex.decodeHex(hex);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
