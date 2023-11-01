package com.chua.starter.mybatis.opt;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.request.*;
import com.chua.common.support.spi.ServiceProvider;

import java.util.Map;

/**
 * 摘要包装处理程序
 *
 * @author CH
 * @since 2023/11/01
 */
public class SummaryWrapperHandler {
    private final ItemFilter itemFilter;
    private final String key;
    private final ItemExpression value;
    private final OptOption option;
    private final LinkOption linkOption;
    private final Map<String, String> fields;

    public SummaryWrapperHandler(ItemFilter itemFilter, LinkOption linkOption, Map<String, String> fields) {
        this.itemFilter = itemFilter;
        this.key = ((ItemValue)itemFilter.getKey()).getFilterValue().toString();
        this.value = itemFilter.getValue();
        this.option = itemFilter.getOption();
        this.linkOption = linkOption;
        this.fields = fields;
    }

    public <T> void doInject(QueryWrapper<T> wrapper) {
        OptHandler optHandler = ServiceProvider.of(OptHandler.class).getNewExtension(option);
        if(value instanceof ItemValue) {
            if(linkOption == LinkOption.AND) {
                wrapper.and(tQueryWrapper -> optHandler.doInject(key, ((ItemValue) value).getFilterValue().toString(), fields, tQueryWrapper));
                return;
            }
            wrapper.or(w -> optHandler.doInject(key, ((ItemValue) value).getFilterValue().toString(), fields, w));
            return;
        }
        if(linkOption == LinkOption.AND) {
            wrapper.and(t -> new SummaryWrapperHandler((ItemFilter) value, linkOption, fields).doInject(t));
            return;
        }
        wrapper.or(t -> new SummaryWrapperHandler((ItemFilter) value, linkOption, fields).doInject(t));

    }
}
