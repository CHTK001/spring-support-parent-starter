package com.chua.starter.unified.client.support.mybatis;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 支撑注射器
 *
 * @author CH
 * @since 2023/11/20
 */
public class SupportInjector extends DefaultSqlInjector {

    @Override
    public List<AbstractMethod> getMethodList(Class<?> mapperClass, TableInfo tableInfo) {
        List<AbstractMethod> list = new ArrayList<>();

        List<AbstractMethod> methodList = super.getMethodList(mapperClass, tableInfo);
        list.addAll(methodList);

        return list;
    }
}
