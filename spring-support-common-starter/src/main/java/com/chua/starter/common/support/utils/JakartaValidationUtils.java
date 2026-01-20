package com.chua.starter.common.support.utils;

import com.chua.common.support.lang.code.ReturnResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Jakarta Validation 工具类
 *
 * @author CH
 * @since 2024/12/20
 */
public class JakartaValidationUtils {

    private static final Validator VALIDATOR;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            VALIDATOR = factory.getValidator();
        }
    }

    /**
     * 验证对象
     *
     * @param object           要验证的对象
     * @param condition        条件判断器，用于决定使用哪个验证组
     * @param updateGroupClass 更新组Class
     * @param addGroupClass    添加组Class
     * @param <T>              对象类型
     * @return 验证错误
     */
    public static <T> Errors validate(T object, Predicate<T> condition, Class<?> updateGroupClass, Class<?> addGroupClass) {
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(object, object.getClass().getSimpleName());
        
        Class<?>[] groups = condition.test(object) 
                ? new Class<?>[]{updateGroupClass} 
                : new Class<?>[]{addGroupClass};
        
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(object, groups);
        
        for (ConstraintViolation<T> violation : violations) {
            String field = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            errors.rejectValue(field, "validation.error", message);
        }
        
        return errors;
    }

    /**
     * 验证对象
     *
     * @param object 要验证的对象
     * @param groups 验证组
     * @param <T>    对象类型
     * @return 验证错误
     */
    public static <T> Errors validate(T object, Class<?>... groups) {
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(object, object.getClass().getSimpleName());
        
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(object, groups);
        
        for (ConstraintViolation<T> violation : violations) {
            String field = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            errors.rejectValue(field, "validation.error", message);
        }
        
        return errors;
    }

    /**
     * 将验证错误转换为 ReturnResult
     *
     * @param errors 验证错误
     * @param <T>    结果类型
     * @return ReturnResult
     */
    public static <T> ReturnResult<T> toReturnResult(Errors errors) {
        if (errors == null || !errors.hasErrors()) {
            return ReturnResult.ok();
        }
        
        List<FieldError> fieldErrors = errors.getFieldErrors();
        if (fieldErrors.isEmpty()) {
            return ReturnResult.error("参数验证失败");
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fieldErrors.size(); i++) {
            FieldError fieldError = fieldErrors.get(i);
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(fieldError.getField()).append(": ").append(fieldError.getDefaultMessage());
        }
        
        return ReturnResult.error(sb.toString());
    }
}
