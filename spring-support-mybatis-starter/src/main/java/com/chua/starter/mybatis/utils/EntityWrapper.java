package com.chua.starter.mybatis.utils;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.function.InitializingAware;
import com.chua.common.support.request.*;
import com.chua.starter.mybatis.opt.SummaryWrapperHandler;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 包装工具
 *
 * @author CH
 * @since 2023/10/29
 */
public class EntityWrapper<T> implements InitializingAware {
    private final Class<T> type;
    private final DataFilter dataFilter;

    private final LinkOption linkOption;

    private final Map<String, String> fields = new HashMap<>();

    private final List<ItemFilter> notFields = new LinkedList<>();
    private Map<String, ColumnCache> columnMap;

    public EntityWrapper(Class<T> type, DataFilter dataFilter) {
        this(type, dataFilter, LinkOption.AND);
    }
    public EntityWrapper(Class<T> type, DataFilter dataFilter, LinkOption linkOption) {
        this.type = type;
        this.dataFilter = dataFilter;
        this.linkOption = linkOption;
        afterPropertiesSet();
    }

    /**
     * 获取包装物
     *
     * @return {@link Wrapper}<{@link T}>
     */
    public QueryWrapper<T> getWrapper() {
        QueryWrapper<T> tLambdaQueryWrapper = Wrappers.<T>query();
        for (ItemFilter itemFilter : dataFilter.getFilter()) {
            register(tLambdaQueryWrapper, itemFilter);
        }

        return tLambdaQueryWrapper;
    }

    /**
     * 注册
     *
     * @param wrapper t lambda查询包装
     * @param itemFilter          项目筛选器
     */
    private void register(QueryWrapper<T> wrapper, ItemFilter itemFilter) {
        Object value1 = itemFilter.getValue();
        if(null == value1) {
            return;
        }

        if(!isField(itemFilter)) {
            notFields.add(itemFilter);
            return;
        }
        new SummaryWrapperHandler(itemFilter, linkOption, fields).doInject(wrapper);
    }


    /**
     * 是字段
     *
     * @param itemFilter 项目筛选器
     * @return boolean
     */
    private boolean isField(ItemFilter itemFilter) {
        if(fields.isEmpty()) {
            return true;
        }
        ItemExpression key = itemFilter.getKey();
        String toUpperCase = ((ItemValue) key).getFilterValue().toString().toUpperCase();
        if(toUpperCase.contains(CommonConstant.SYMBOL_COMMA)) {
            for (String s : toUpperCase.split(CommonConstant.SYMBOL_COMMA)) {
                if(fields.containsKey(s.trim())) {
                    return true;
                }
            }
            return false;
        }
        return fields.containsKey(toUpperCase);
    }

    /**
     * 初始化
     *
     * @param type       类型
     * @param dataFilter 数据过滤器
     * @return {@link EntityWrapper}<{@link T}>
     */
    public static <T>EntityWrapper<T> of(Class<T> type, DataFilter dataFilter) {
        return new EntityWrapper<>(type, dataFilter);
    }

    @Override
    public void afterPropertiesSet() {
        this.columnMap = LambdaUtils.getColumnMap(type);
        if(null == this.columnMap) {
            return;
        }
        for (Map.Entry<String, ColumnCache> entry : columnMap.entrySet()) {
            fields.put(entry.getKey(), entry.getValue().getColumn());
        }
    }
}
