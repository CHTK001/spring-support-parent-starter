package com.chua.starter.mybatis.type;

import com.chua.common.support.crypto.AesCodec;
import com.chua.common.support.core.utils.Preconditions;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.springframework.util.StringUtils;

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
 */
public class EncryptTypeHandler extends BaseTypeHandler<String> {

    private static final String ENCRYPTOR_PROPERTY_NAME = "mybatis-plus.encryptor.password";
    private static final String ENCRYPTOR_MODE_PROPERTY_NAME = "mybatis-plus.encryptor.mode";

    private static AesCodec aesCodec;
    private static String password;

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return decrypt(value);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return decrypt(value);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return decrypt(value);
    }

    private static String decrypt(String value) {
        if (value == null) {
            return null;
        }
        EncryptMode encryptMode = resolveEncryptMode();
        if (encryptMode == EncryptMode.PLAIN) {
            return value;
        }
        try {
            return getEncryptor().decodeHex(value);
        } catch (RuntimeException ex) {
            if (encryptMode == EncryptMode.AUTO) {
                return value;
            }
            throw ex;
        }
    }

    public static String encrypt(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (resolveEncryptMode() == EncryptMode.PLAIN) {
            return rawValue;
        }
        return getEncryptor().encodeHex(rawValue);
    }

    private static AesCodec getEncryptor() {
        if (aesCodec != null) {
            return aesCodec;
        }
        // 构建 AES
        password = System.getProperty(ENCRYPTOR_PROPERTY_NAME);
        Preconditions.notEmpty(password, "配置项({}) 不能为空", ENCRYPTOR_PROPERTY_NAME);
        aesCodec = new AesCodec(password.getBytes(StandardCharsets.UTF_8));
        return aesCodec;
    }

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
