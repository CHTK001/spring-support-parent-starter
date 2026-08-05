package com.chua.starter.spider.service.impl;

import com.chua.starter.spider.entity.SpiderFlow;
import com.chua.starter.spider.mapper.SpiderFlowMapper;
import com.chua.starter.spider.service.SpiderFlowService;
import org.springframework.stereotype.Service;

/**
 * 流程编排服务实现。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Service
public class SpiderFlowServiceImpl
        extends SpiderBaseServiceImpl<SpiderFlowMapper, SpiderFlow>
        implements SpiderFlowService {
}