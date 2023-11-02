package com.chua.starter.mybatis.opt;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.request.*;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.unit.name.NamingCase;

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
            column = fields.getOrDefault(column, NamingCase.toCamelUnderscore(column));
            if (value instanceof ItemValue) {
                registerItemValue(column, (ItemValue)value, wrapper, optHandler);
                return;
            }

            registerItemFilter(column, value, wrapper);
            return;
        }

        if (value instanceof ItemValue) {
            registerItemKeyValue((ItemFilter) key, (ItemValue)value, wrapper);
            return;
        }
        registerItemKeyValueItemFilter((ItemFilter) key, (ItemFilter)value, wrapper);
    }

    /**
     * 注册项目钥匙值项目滤器
     *
     * @param key        钥匙
     * @param value      值
     * @param wrapper    包装物
     */
    private <T> void registerItemKeyValueItemFilter(ItemFilter key, ItemFilter value, QueryWrapper<T> wrapper) {
        if(linkOption == LinkOption.AND) {
            wrapper.and(t -> {
                new SummaryWrapperHandler(key, linkOption, fields).doInject(t);
                if(option == OptOption.AND) {
                    t.and(t1 ->  new SummaryWrapperHandler(value, linkOption, fields).doInject(t1));
                    return;
                }
                t.or(t1 ->  new SummaryWrapperHandler(value, linkOption, fields).doInject(t1));
            });
            return;
        }

        wrapper.or(t -> {
            new SummaryWrapperHandler(key, linkOption, fields).doInject(t);
            if(option == OptOption.AND) {
                t.and(t1 ->  new SummaryWrapperHandler(value, linkOption, fields).doInject(t1));
                return;
            }
            t.or(t1 ->  new SummaryWrapperHandler(value, linkOption, fields).doInject(t1));
        });
    }

    /**
     * 注册项目钥匙值
     *
     * @param key        钥匙
     * @param value      值
     * @param wrapper    包装物
     */
    private <T> void registerItemKeyValue(ItemFilter key, ItemValue value, QueryWrapper<T> wrapper) {
        if(linkOption == LinkOption.AND) {
            wrapper.and(t -> {
                new SummaryWrapperHandler(key, linkOption, fields).doInject(t);
            });
            return;
        }

        wrapper.or(t -> {
            new SummaryWrapperHandler(key, linkOption, fields).doInject(t);
        });
    }

    private <T> void registerItemFilter(String column, ItemExpression value, QueryWrapper<T> wrapper) {
        if (linkOption == LinkOption.AND) {
            wrapper.and(t -> new SummaryLinkWrapperHandler(column, linkOption, (ItemFilter) value, fields).doInject(t));
            return;
        }
        wrapper.or(w -> new SummaryLinkWrapperHandler(column, linkOption, (ItemFilter) value, fields).doInject(w));
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
            wrapper.and(tQueryWrapper -> optHandler.doInject(column, value.getFilterValue().toString(), fields, tQueryWrapper));
            return;
        }
        wrapper.or(w -> optHandler.doInject(column, value.getFilterValue().toString(), fields, w));
    }
}
