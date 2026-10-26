package com.chua.starter.common.support.configuration;

import com.chua.common.support.converter.definition.TypeConverter;
import com.chua.common.support.converter.definition.EnumTypeConverter;
import com.chua.common.support.spi.ServiceProvider;
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
 * 转换器
 *
 * @author CH
 * @since 4.0.0
 */
public class TypeConverterRegisterConfiguration {

    /**
     * 日志
     */
    private static final System.Logger LOG = System.getLogger(TypeConverterRegisterConfiguration.class.getName());

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ConfigurableConversionService.class)
    /**
     * 类型转换器registry
     *
     * @param conversionService 转换服务
     * @return 类型转换器registry的结果
     */
    public TypeConverterRegistry typeConverterRegistry(ConfigurableConversionService conversionService) {
        /**
         * 类型转换器registry。
         * @param conversionService 转换服务
         * @return 类型转换器registry的结果
         */
        return new TypeConverterRegistry(conversionService);
    }

    /**
     * 类型转换器registry类。
     *
     * @author CH
     * @since 4.0.0
     */
    public static class TypeConverterRegistry {

        @SuppressWarnings({"unchecked", "rawtypes"})
        /**
         * 创建 类型转换器registry 实例
         * @param conversionService 转换服务
         */
        public TypeConverterRegistry(ConfigurableConversionService conversionService) {
            conversionService.addConverter(new StringToLongTypeConverter());
            conversionService.addConverter(new StringToDateTypeConverter());
            conversionService.addConverter(new StringToLocalTimeTypeConverter());
            conversionService.addConverter(new StringToLocalDateTypeConverter());
            conversionService.addConverter(new StringToLocalDateTimeTypeConverter());
            conversionService.addConverter(new StringArrayToStringTypeConverter());

            Map<String, com.chua.common.support.converter.definition.TypeConverter> list = ServiceProvider.of(com.chua.common.support.converter.definition.TypeConverter.class).list();
            for (com.chua.common.support.converter.definition.TypeConverter converter : list.values()) {
                try {
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    Class targetType = converter.getType();
                    conversionService.addConverter(Object.class, targetType, source -> converter.convert(source));
                } catch (Exception e) {
                    LOG.log(System.Logger.Level.DEBUG, "[converter] 注册转换器失败，已忽略: " + e.getMessage(), e);
                }
                try {
                    if(converter instanceof EnumTypeConverter) {
                        conversionService.removeConvertible(String.class, Enum.class);
                        conversionService.addConverter(new ConditionalGenericConverter() {
                            @Override
                            /**
                             * Matches
                            */
                            public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
                                return true;
                            }

                            @Override
                            /**
                             * 获取转换类型
                            */
                            public Set<GenericConverter.ConvertiblePair> getConvertibleTypes() {
                                return Collections.singleton(new GenericConverter.ConvertiblePair(String.class, Enum.class));
                            }

                            @Override
                            /**
                             * 转换
                            */
                            public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
                                return converter.convert(source);
                            }
                        });
                    }
                } catch (Exception e) {
                    LOG.log(System.Logger.Level.DEBUG, "[converter] 注册转换器失败，已忽略: " + e.getMessage(), e);
                }
            }
        }
    }
}
