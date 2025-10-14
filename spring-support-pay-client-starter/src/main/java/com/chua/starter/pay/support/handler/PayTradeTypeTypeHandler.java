package com.chua.starter.pay.support.handler;

import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.enums.PayTradeType;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author CH
 * @since 2025/10/14 13:14
 */
public class PayTradeTypeTypeHandler extends BaseTypeHandler<PayTradeType> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, PayTradeType parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.getCode());
    }

    @Override
    public PayTradeType getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String ordinal = rs.getString(columnName);
        if (ordinal == null && rs.wasNull()) {
            return null;
        }
        return PayTradeType.parse(ordinal);
    }

    @Override
    public PayTradeType getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String ordinal = rs.getString(columnIndex);
        if (ordinal == null && rs.wasNull()) {
            return null;
        }
        return PayTradeType.parse(ordinal);
    }

    @Override
    public PayTradeType getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String ordinal = cs.getString(columnIndex);
        if (ordinal == null && cs.wasNull()) {
            return null;
        }
        return PayTradeType.parse(ordinal);
    }
}
