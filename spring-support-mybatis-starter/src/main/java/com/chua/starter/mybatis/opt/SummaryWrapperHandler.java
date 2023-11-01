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
    private final ItemExpression key;
    private final ItemExpression value;
    private final OptOption option;
    private final LinkOption linkOption;
    private final Map<String, String> fields;

    public SummaryWrapperHandler(ItemFilter itemFilter, LinkOption linkOption, Map<String, String> fields) {
        this.itemFilter = itemFilter;
        this.key = itemFilter.getKey();
        this.value = itemFilter.getValue();
        this.option = itemFilter.getOption();
        this.linkOption = linkOption;
        this.fields = fields;
    }

    public <T> void doInject(QueryWrapper<T> wrapper) {
        OptHandler optHandler = ServiceProvider.of(OptHandler.class).getNewExtension(option);
        if(key instanceof ItemValue) {
            String column = ((ItemValue) key).getFilterValue().toString().toUpperCase();
            if (value instanceof ItemValue) {
                registerItemValue(column, (ItemValue)value, wrapper, optHandler);
            }

            registerItemFilter(column, value, wrapper, optHandler);
            return;
        }
        if(linkOption == LinkOption.AND) {
            wrapper.and(t -> new SummaryWrapperHandler((ItemFilter) value, linkOption, fields).doInject(t));
            return;
        }
        wrapper.or(t -> new SummaryWrapperHandler((ItemFilter) value, linkOption, fields).doInject(t));

    }

    private <T> void registerItemFilter(String column, ItemExpression value, QueryWrapper<T> wrapper, OptHandler optHandler) {
        if (linkOption == LinkOption.AND) {
            wrapper.and(t -> new SummaryLinkWrapperHandler(column, option, linkOption, (ItemFilter) value, fields).doInject(t));
            return;
        }
        wrapper.or(w -> optHandler.doInject(column, ((ItemValue) value).getFilterValue().toString(), fields, w));
    }

    /**
     * 注册项目值
     *
     * @param column     柱
     * @param value      值
     * @param wrapper    包装物
     * @param optHandler opt处理程序
     */
    private <T> void registerItemValue(String column, ItemValue value, QueryWrapper<T> wrapper, OptHandler optHandler) {
        if (linkOption == LinkOption.AND) {
            wrapper.and(tQueryWrapper -> optHandler.doInject(column, ((ItemValue) value).getFilterValue().toString(), fields, tQueryWrapper));
            return;
        }
        wrapper.or(w -> optHandler.doInject(column, ((ItemValue) value).getFilterValue().toString(), fields, w));
    }
}
