package com.chua.starter.mybatis.utils;

import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.function.InitializingAware;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.request.DataFilter;
import com.chua.common.support.request.ItemFilter;
import com.chua.common.support.request.OptOption;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.unit.name.NamingCase;
import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.mybatis.opt.OptHandler;

import java.util.*;
import java.util.function.Consumer;

import static com.chua.common.support.constant.CommonConstant.SYMBOL_COMMA;

/**
 * 包装工具
 *
 * @author CH
 * @since 2023/10/29
 */
public class EntityWrapper<T> implements InitializingAware {
    private final Class<T> type;
    private final DataFilter dataFilter;

    private final Map<String, String> fields = new HashMap<>();

    private final List<ItemFilter> notFields = new LinkedList<>();
    private Map<String, ColumnCache> columnMap;

    public EntityWrapper(Class<T> type, DataFilter dataFilter) {
        this.type = type;
        this.dataFilter = dataFilter;
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
        String value1 = itemFilter.getValue();
        if(StringUtils.isBlank(value1)) {
            return;
        }

        if(!isField(itemFilter)) {
            notFields.add(itemFilter);
            return;
        }
        String key = itemFilter.getKey();
        OptOption option = itemFilter.getOption();
        ServiceProvider.of(OptHandler.class)
                .getNewExtension(option)
                .doInject(key.replaceAll("~", ""), value1, fields, wrapper);

    }


    /**
     * 是字段
     *
     * @param itemFilter 项目筛选器
     * @return boolean
     */
    private boolean isField(ItemFilter itemFilter) {
        String key = itemFilter.getKey();
        if(key.contains(SYMBOL_COMMA)) {
            for (String s : Splitter.on(SYMBOL_COMMA).omitEmptyStrings().trimResults().splitToSet(key)) {
                if(!fields.containsKey(s)) {
                    return false;
                }
            }
            return true;
        }
        return fields.containsKey(itemFilter.getKey().toUpperCase());
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
        for (Map.Entry<String, ColumnCache> entry : columnMap.entrySet()) {
            fields.put(entry.getKey(), entry.getValue().getColumn());
        }
    }
}
