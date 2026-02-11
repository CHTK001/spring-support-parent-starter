package com.chua.starter.common.support.configuration;

import com.chua.common.support.base.converter.definition.EnumTypeConverter;
import com.chua.common.support.base.converter.definition.TypeConverter;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.starter.common.support.converter.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.convert.support.ConfigurableConversionService;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * converter
 *
 * @author CH
 */
public class TypeConverterRegisterConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ConfigurableConversionService.class)
    public TypeConverterRegistry typeConverterRegistry(ConfigurableConversionService conversionService) {
        return new TypeConverterRegistry(conversionService);
    }

    public static class TypeConverterRegistry {

        @SuppressWarnings("ALL")
        public TypeConverterRegistry(ConfigurableConversionService conversionService) {
            conversionService.addConverter(new StringToLongTypeConverter());
            conversionService.addConverter(new StringToDateTypeConverter());
            conversionService.addConverter(new StringToLocalTimeTypeConverter());
            conversionService.addConverter(new StringToLocalDateTypeConverter());
            conversionService.addConverter(new StringToLocalDateTimeTypeConverter());
            conversionService.addConverter(new StringToDateTypeConverter());
            conversionService.addConverter(new StringArrayToStringTypeConverter());

            Map<String, TypeConverter> list = ServiceProvider.of(TypeConverter.class).list();
            for (TypeConverter converter : list.values()) {
                try {
                    conversionService.addConverter(Object.class, converter.getType(), new Converter<Object, Object>() {
                        @Override
                        public Object convert(Object source) {
                            return converter.convert(source);
                        }
                    });
                } catch (Exception e) {
                }
                try {
                    if(converter instanceof EnumTypeConverter) {
                        conversionService.removeConvertible(String.class, Enum.class);
                        conversionService.addConverter(new ConditionalGenericConverter() {
                            @Override
                            public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
                                return true;
                            }

                            @Override
                            public Set<GenericConverter.ConvertiblePair> getConvertibleTypes() {
                                return Collections.singleton(new GenericConverter.ConvertiblePair(String.class, Enum.class));
                            }

                            @Override
                            public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
                                // 使用 TypeConverter 进行转换
                                if (converter instanceof EnumTypeConverter) {
                                    return ((EnumTypeConverter) converter).convert(source);
                                }
                                return converter.convert(source);
                            }
                        });
                    }
                } catch (Exception e) {
                }
            }
        }
    }
}

