package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.TransactionRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 交易流水Mapper接口
 *
 * @since 2026-03-18
 * @author CH
 */
@Mapper
public interface TransactionRecordMapper extends BaseMapper<TransactionRecord> {
}
