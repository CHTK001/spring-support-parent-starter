package com.chua.starter.mybatis.opt;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.request.*;
import com.chua.common.support.request.sql.SqlHandler;
import com.chua.common.support.request.sql.SummarySqlHandler;
import com.chua.common.support.spi.ServiceProvider;

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

    public SummaryWrapperHandler(ItemFilter itemFilter, LinkOption linkOption) {
        this.itemFilter = itemFilter;
        this.key = ((ItemValue)itemFilter).getFilterValue().toString();
        this.value = itemFilter.getValue();
        this.option = itemFilter.getOption();
        this.linkOption = linkOption;
    }

    public <T> void doInject(QueryWrapper<T> wrapper) {
        SqlHandler sqlHandler = ServiceProvider.of(SqlHandler.class).getNewExtension(option);
        if(value instanceof ItemValue) {
            if(linkOption == LinkOption.AND) {
                wrapper.and(tQueryWrapper -> sqlHandler.create(key, ((ItemValue) value).getFilterValue()));
                return;
            }
            wrapper.or(w -> sqlHandler.create(key, ((ItemValue) value).getFilterValue()));
            return;
        }
        if(linkOption == LinkOption.AND) {
            wrapper.and(t -> new SummaryWrapperHandler((ItemFilter) value, linkOption).doInject(t));
            return;
        }
        wrapper.or(t -> new SummaryWrapperHandler((ItemFilter) value, linkOption).doInject(t));

    }
}
