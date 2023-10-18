package com.chua.starter.common.support.configuration;

import com.chua.common.support.converter.definition.EnumTypeConverter;
import com.chua.common.support.converter.definition.TypeConverter;
import com.chua.common.support.spi.ServiceProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterRegistry;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

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
                if(converter instanceof EnumTypeConverter) {
                    converterRegistry.removeConvertible(String.class, Enum.class);
                    converterRegistry.addConverter(new ConditionalGenericConverter() {
                        @Override
                        public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
                            return true;
                        }

                        @Override
                        public Set<ConvertiblePair> getConvertibleTypes() {
                            return Collections.singleton(new ConvertiblePair(String.class, Enum.class));
                        }

                        @Override
                        public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
                            return com.chua.common.support.converter.Converter.convertIfNecessary(source, targetType.getType());
                        }
                    });
                }
            }
        }
    }
}
