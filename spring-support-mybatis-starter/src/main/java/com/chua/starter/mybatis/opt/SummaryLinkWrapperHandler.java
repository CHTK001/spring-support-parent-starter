package com.chua.starter.mybatis.opt;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.request.ItemExpression;
import com.chua.common.support.request.ItemFilter;
import com.chua.common.support.request.LinkOption;
import com.chua.common.support.request.OptOption;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 摘要链接包装处理程序
 *
 * @author CH
 * @since 2023/11/01
 */
public class SummaryLinkWrapperHandler {
    private final String column;
    private final OptOption option;
    private final LinkOption linkOption;
    private final ItemFilter itemFilter;
    private final Map<String, String> fields;
    private final ItemExpression key;
    private final ItemExpression value;

    public SummaryLinkWrapperHandler(String column, LinkOption linkOption, ItemFilter itemFilter, Map<String, String> fields) {
        this.column = column;
        this.linkOption = linkOption;
        this.itemFilter = itemFilter;
        this.key = itemFilter.getKey();
        this.value = itemFilter.getValue();
        this.option = itemFilter.getOption();
        this.fields = fields;
    }


    public <T> void doInject(QueryWrapper<T> t) {
       new SummaryWrapperHandler(itemFilter, linkOption, fields).doInject(t);
    }
}
