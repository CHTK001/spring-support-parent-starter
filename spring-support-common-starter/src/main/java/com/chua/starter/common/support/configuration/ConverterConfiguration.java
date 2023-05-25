package com.chua.starter.common.support.configuration;

import com.chua.common.support.converter.definition.TypeConverter;
import com.chua.common.support.spi.ServiceProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterRegistry;

import java.util.Map;

/**
 * converter
 *
 * @author CH
 */
public class ConverterConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TypeConverterRegistry typeConverterRegistry(ConverterRegistry converterRegistry) {
        return new TypeConverterRegistry(converterRegistry);
    }

    public static class TypeConverterRegistry {

        @SuppressWarnings("ALL")
        public TypeConverterRegistry(ConverterRegistry converterRegistry) {
            Map<String, TypeConverter> list = ServiceProvider.of(TypeConverter.class).list();
            for (TypeConverter converter : list.values()) {
                converterRegistry.addConverter(Object.class, converter.getType(), new Converter<Object, Object>() {
                    @Override
                    public Object convert(Object source) {
                        return converter.convert(source);
                    }
                });
            }
        }
    }
}
