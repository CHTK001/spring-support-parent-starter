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
 * 字段字段的 类型处理器 实现类，基于 实现
 * 可通过 jasypt.encryptor.密码 配置项，设置密钥
 *
 * @author 芋道源码
 * @author CH
 * @since 4.0.0
 */
public class EncryptTypeHandler extends BaseTypeHandler<String> {

    /**
     * Encryptor_财产_名称
    */
    private static final String ENCRYPTOR_PROPERTY_NAME = "mybatis-plus.encryptor.password";
    /**
     * Encryptor_mode_财产_名称
    */
    private static final String ENCRYPTOR_MODE_PROPERTY_NAME = "mybatis-plus.encryptor.mode";

    /**
     * aescodec
    */
    private static AesCipher aesCodec;
    /**
     * 密码
    */
    private static String password;

    @Override
    /**
     * 设置non空参数
     * @param i 整数
     * @param jdbcType 参数
     * @param parameter 字符串
     * @param ps 参数
     * @return 返回值
     * @throws SQLException 异常
    */
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, encrypt(parameter));
    }

    @Override
    /**
     * 获取空结果
     * @param columnName 字符串
     * @param rs 参数
     * @return 返回值
     * @throws SQLException 异常
    */
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return decrypt(value);
    }

    @Override
    /**
     * 获取空结果
     * @param columnIndex 整数
     * @param rs 参数
     * @return 返回值
     * @throws SQLException 异常
    */
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return decrypt(value);
    }

    @Override
    /**
     * 获取空结果
     * @param columnIndex 整数
     * @param cs 参数
     * @return 返回值
     * @throws SQLException 异常
    */
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return decrypt(value);
    }

    /**
     * Decrypt
     *
     * @param value 值
     * @return decrypt的结果
     */
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
            /**
             * 字符串。
             * @param decoded decoded
             * @param java.nio.charset.StandardCharsets.UTF_8 Java.nio.字符集.标准字符集.UTF_8
             * @return 字符串的结果
             */
            return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
        } catch (RuntimeException ex) {
            if (encryptMode == EncryptMode.AUTO) {
                return value;
            }
            throw ex;
        }
    }

    /**
     * Encrypt
     *
     * @param rawValue raw值
     * @return encrypt的结果
     */
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
            /**
             * runtime异常。
             * @param "加密失败" "加密失败"
             * @param e e
             * @return runtime异常的结果
             */
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 获取Encryptor
     *
     * @return 获取encryptor的结果
     */
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

    /**
     * 解析encryptmode
     *
     * @return resolveEncryptMode的结果
     */
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

    /**
     * encryptmode枚举。
     *
     * @author CH
     * @since 4.0.0
     */
    private enum EncryptMode {
        ENCRYPT,
        PLAIN,
        AUTO
    }

}
