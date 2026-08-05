package com.chua.starter.spider.service.impl;

import com.chua.starter.spider.entity.SpiderDataRecord;
import com.chua.starter.spider.mapper.SpiderDataRecordMapper;
import com.chua.starter.spider.service.SpiderDataRecordService;
import org.springframework.stereotype.Service;

/**
 * 采集数据记录服务实现。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Service
public class SpiderDataRecordServiceImpl
        extends SpiderBaseServiceImpl<SpiderDataRecordMapper, SpiderDataRecord>
        implements SpiderDataRecordService {
}