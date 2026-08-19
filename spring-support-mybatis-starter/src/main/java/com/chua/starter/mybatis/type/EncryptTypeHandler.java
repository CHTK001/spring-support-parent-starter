package com.chua.starter.mybatis.type;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import com.chua.common.support.lang.algorithm.cipher.AesCipher;
import com.chua.common.support.lang.algorithm.crypto.Hex;
import com.chua.common.support.utils.StringUtils;
import com.google.common.base.Preconditions;
import com.chua.starter.common.support.algorithm.crypto.Codec;

import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 字段字段的 TypeHandler 实现类，基于 实现
 * 可通过 jasypt.encryptor.password 配置项，设置密钥
 *
 * @author 芋道源码
 * @author CH
 */
public class EncryptTypeHandler extends BaseTypeHandler<String> {

    /** Encryptor_property_name */
    private static final String ENCRYPTOR_PROPERTY_NAME = "mybatis-plus.encryptor.password";
    /** Encryptor_mode_property_name */
    private static final String ENCRYPTOR_MODE_PROPERTY_NAME = "mybatis-plus.encryptor.mode";

    /** AEScodec */
    private static AesCipher aesCodec;
    /** 密码 */
    private static String password;

    @Override
    /** 设置NonNullParameter */
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, encrypt(parameter));
    }

    @Override
    /** 获取NullableResult */
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return decrypt(value);
    }

    @Override
    /** 获取NullableResult */
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return decrypt(value);
    }

    @Override
    /** 获取NullableResult */
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return decrypt(value);
    }

    /** Decrypt */
    private static String decrypt(String value) {
        if (value == null) {
            return null;
        }
        EncryptMode encryptMode = resolveEncryptMode();
        if (encryptMode == EncryptMode.PLAIN) {
            return value;
        }
        try {
            byte[] decoded = com.chua.common.support.lang.algorithm.crypto.Hex.decodeHex(value);
            return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
        } catch (RuntimeException ex) {
            if (encryptMode == EncryptMode.AUTO) {
                return value;
            }
            throw ex;
        }
    }

    /** Encrypt */
    public static String encrypt(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (resolveEncryptMode() == EncryptMode.PLAIN) {
            return rawValue;
        }
        try {
            byte[] key = password.getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = getEncryptor().encrypt(key, rawValue.getBytes(StandardCharsets.UTF_8));
            return Hex.encodeHexString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    /** 获取Encryptor */
    private static AesCipher getEncryptor() {
        if (aesCodec != null) {
            return aesCodec;
        }
        // 构建 AES
        password = System.getProperty(ENCRYPTOR_PROPERTY_NAME);
        Preconditions.checkArgument(!password.isEmpty(), "配置项(%s)不能为空", ENCRYPTOR_PROPERTY_NAME);
        aesCodec = new AesCipher();
        return aesCodec;
    }

    /** 解析EncryptMode */
    private static EncryptMode resolveEncryptMode() {
        String mode = System.getProperty(ENCRYPTOR_MODE_PROPERTY_NAME);
        if (!StringUtils.hasText(mode)) {
            return EncryptMode.ENCRYPT;
        }
        try {
            return EncryptMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return EncryptMode.ENCRYPT;
        }
    }

    private enum EncryptMode {
        ENCRYPT,
        PLAIN,
        AUTO
    }

}
