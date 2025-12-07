//package com.chua.starter.common.support.filter;
//
//import com.alibaba.fastjson2.filter.AfterFilter;
//import com.chua.common.support.utils.ClassUtils;
//import com.chua.starter.common.support.annotations.EnumDescribe;
//import org.springframework.util.ReflectionUtils;
//
///**
// * 枚举转换后置处理滤器
// *
// * @author CH
// */
//public class EnumConvertAfterFilter extends AfterFilter {
//    @Override
//    public void writeAfter(Object object) {
//        if (null == object) {
//            return;
//        }
//        Class<?> objectClass = object.getClass();
//        ReflectionUtils.doWithFields(objectClass, field -> {
//            EnumDescribe annotation = field.getAnnotation(EnumDescribe.class);
//            if (annotation == null) {
//                return;
//            }
//            ClassUtils.setAccessible(field);
//            Object fieldValue = ClassUtils.getFieldValue(field, object);
//            if (null == fieldValue) {
//                return;
//            }
//
//            Class<?> aClass = fieldValue.getClass();
//            Class<? extends Enum<?>> clazz = annotation.value();
//            Enum<?>[] enumConstants = clazz.getEnumConstants();
//            Object value = getFieldValue(aClass, fieldValue, enumConstants);
//            if (null == value) {
//                return;
//            }
//            super.writeKeyValue(field.getName(), value);
//        });
//    }
//    /**
//     * 收到领域�?
//     *
//     * @param aClass        一个班
//     * @param fieldValue    领域�?
//     * @param enumConstants 枚举常量
//     * @return {@link Object}
//     */
//    private Object getFieldValue(Class<?> aClass, Object fieldValue, Enum<?>[] enumConstants) {
//        if(aClass.isPrimitive()) {
//            int ordinal = getOrdinal(fieldValue);
//            for (Enum<?> enumConstant : enumConstants) {
//                if (ordinal == enumConstant.ordinal()) {
//                    return enumConstant;
//                }
//            }
//        }
//
//        if(aClass == String.class) {
//            String string = fieldValue.toString();
//            for (Enum<?> enumConstant : enumConstants) {
//                if (enumConstant.name().equalsIgnoreCase(string)) {
//                    return enumConstant;
//                }
//            }
//        }
//
//        return null;
//    }
//
//    private int getOrdinal(Object fieldValue) {
//        return ClassUtils.primitiveInt(fieldValue);
//    }
//}

