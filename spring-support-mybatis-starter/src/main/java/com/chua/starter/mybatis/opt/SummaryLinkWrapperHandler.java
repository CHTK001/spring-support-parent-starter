package com.chua.starter.mybatis.opt;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.request.ItemFilter;
import com.chua.common.support.request.LinkOption;
import com.chua.common.support.request.OptOption;

import java.util.Map;

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
    private final ItemFilter value;
    private final Map<String, String> fields;

    public SummaryLinkWrapperHandler(String column, OptOption option, LinkOption linkOption, ItemFilter value, Map<String, String> fields) {
        this.column = column;
        this.option = option;
        this.linkOption = linkOption;
        this.value = value;
        this.fields = fields;
    }


    public <T> void doInject(QueryWrapper<T> t) {
        if(option == OptOption.EQ) {
            new SummaryWrapperHandler(value, linkOption, fields).doInject(t);
            t.eq(column, new )
        }
    }
}
