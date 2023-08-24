package com.chua.starter.common.support.result;

import com.chua.common.support.lang.exception.AuthenticationException;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.annotations.Ignore;
import com.chua.starter.common.support.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import javax.servlet.ServletException;
import javax.validation.ConstraintViolation;
import java.sql.SQLSyntaxErrorException;
import java.text.DecimalFormat;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.chua.starter.common.support.result.ReturnCode.*;

/**
 * @author CH
 */
@RestControllerAdvice
@Slf4j
public class ResponseAdvice implements ResponseBodyAdvice<Object> {


    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> processException(BindException e) {
        log.error("BindException:{}", e);
        String msg = e.getAllErrors().stream().map(DefaultMessageSourceResolvable::getDefaultMessage).collect(Collectors.joining("；"));
        return Result.failed(PARAM_ERROR, msg);
    }

    /**
     * RequestParam参数的校验
     *
     * @param e ConstraintViolationException
     * @param <T> Result
     * @return
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> processException(javax.validation.ConstraintViolationException e) {
        log.error("ConstraintViolationException:{}", e);
        String msg = e.getConstraintViolations().stream().map(ConstraintViolation::getMessage).collect(Collectors.joining("；"));
        return Result.failed(PARAM_ERROR, msg);
    }

    /**
     * RequestBody参数的校验
     *
     * @param e
     * @param <T>
     * @return
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> processException(MethodArgumentNotValidException e) {
        log.error("MethodArgumentNotValidException:{}", e);
        String msg = e.getBindingResult().getAllErrors().stream().map(DefaultMessageSourceResolvable::getDefaultMessage).collect(Collectors.joining("；"));
        return Result.failed(PARAM_ERROR, msg);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public <T> Result<T> processException(NoHandlerFoundException e) {
        log.error(e.getMessage(), e);
        return Result.failed(RESOURCE_NOT_FOUND);
    }

    /**
     * MissingServletRequestParameterException
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> processException(MissingServletRequestParameterException e) {
        log.error(e.getMessage(), e);
        return Result.failed(PARAM_IS_NULL);
    }

    /**
     * MethodArgumentTypeMismatchException
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> processException(MethodArgumentTypeMismatchException e) {
        log.error(e.getMessage(), e);
        return Result.failed(PARAM_ERROR, "类型错误");
    }

    /**
     * ServletException
     */
    @ExceptionHandler(ServletException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> processException(ServletException e) {
        log.error(e.getMessage(), e);
        return Result.failed(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("非法参数异常，异常原因：{}", e.getMessage(), e);
        return Result.failed(e.getMessage());
    }

    @ExceptionHandler(JsonProcessingException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> handleJsonProcessingException(JsonProcessingException e) {
        log.error("Json转换异常，异常原因：{}", e.getMessage(), e);
        return Result.failed(e.getMessage());
    }

    /**
     * HttpMessageNotReadableException
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> processException(HttpMessageNotReadableException e) {
        log.error(e.getMessage(), e);
        String errorMessage = "请求体不可为空";
        Throwable cause = e.getCause();
        if (cause != null) {
            errorMessage = convertMessage(cause);
        }
        return Result.failed(errorMessage);
    }

    @ExceptionHandler(TypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> processException(TypeMismatchException e) {
        log.error(e.getMessage(), e);
        return Result.failed(e.getMessage());
    }

//    @ExceptionHandler(BadSqlGrammarException.class)
//    @ResponseStatus(HttpStatus.FORBIDDEN)
//    public <T> Result<T> handleBadSqlGrammarException(BadSqlGrammarException e) {
//        log.error(e.getMessage(), e);
//        String errorMsg = e.getMessage();
//        if (StrUtil.isNotBlank(errorMsg) && errorMsg.contains("denied to user")) {
//            return Result.failed(FORBIDDEN_OPERATION);
//        } else {
//            return Result.failed(e.getMessage());
//        }
//    }

    @ExceptionHandler(SQLSyntaxErrorException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public <T> Result<T> processSQLSyntaxErrorException(SQLSyntaxErrorException e) {
        log.error(e.getMessage(), e);
        return Result.failed("无权限操作");
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public <T> Result<T> authenticationException(AuthenticationException e) {
        log.error(e.getMessage(), e);
        return Result.failed(RESULT_ACCESS_UNAUTHORIZED, "无权限操作");
    }


    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> handleBizException(BusinessException e) {
        e.printStackTrace();
        if (e.getResultCode() != null) {
            return Result.failed(e.getLocalizedMessage());
        }
        return Result.failed("系统繁忙");
    }

    static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.##");

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> maxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.error("文件过大", e);
        if(e.getMaxUploadSize() > 0) {
            return Result.failed("文件过大, 当前服务器支支持{}大小文件", StringUtils.getNetFileSizeDescription(e.getMaxUploadSize(), DECIMAL_FORMAT));
        }
        return Result.failed("文件过大");
    }
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> handleException(Exception e) {
        log.error("unknown exception: {}", e);
        return Result.failed("请求失败,请稍后重试");
    }
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> handleRuntimeException(RuntimeException e) {
        log.error("unknown exception: {}", e);
        return Result.failed("当前系统不支持该功能/操作");
    }

    /**
     * 传参类型错误时，用于消息转换
     *
     * @param throwable 异常
     * @return 错误信息
     */
    private String convertMessage(Throwable throwable) {
        String error = throwable.toString();
        String regulation = "\\[\"(.*?)\"]+";
        Pattern pattern = Pattern.compile(regulation);
        Matcher matcher = pattern.matcher(error);
        String group = "";
        if (matcher.find()) {
            String matchString = matcher.group();
            matchString = matchString.replace("[", "").replace("]", "");
            matchString = String.format("%s字段类型错误", matchString.replaceAll("\\\"", ""));
            group += matchString;
        }
        return group;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Class<? extends HttpMessageConverter<?>> aClass) {
        return true;
    }

    @SneakyThrows
    @Override
    public Object beforeBodyWrite(Object o, MethodParameter methodParameter, MediaType mediaType, Class<? extends HttpMessageConverter<?>> aClass, ServerHttpRequest serverHttpRequest, ServerHttpResponse serverHttpResponse) {
        Class<?> declaringClass = methodParameter.getDeclaringClass();
        String typeName = declaringClass.getTypeName();
        if (typeName.contains("swagger")) {
            return o;
        }


        if(mediaType.getSubtype().contains("spring-boot.actuator")) {
            return o;
        }

        if(mediaType.getSubtype().contains("event-stream")) {
            return o;
        }

        if(AnnotationUtils.isAnnotationDeclaredLocally(Ignore.class, declaringClass)) {
            return o;
        }

        if(null != methodParameter.getMethodAnnotation(Ignore.class)) {
            return o;
        }

        if (o instanceof ResponseBodyAdvice || o instanceof byte[] || o instanceof Callable) {
            return o;
        }

        if (o instanceof ReturnPageResult) {
            return o;
        }
        if (o instanceof ResultData) {
            return o;
        }

        if (o instanceof ReturnResult || (null != o && (o.getClass().getTypeName().endsWith("result.PageResult")))) {
            return o;
        }

//        if (aClass == ResultDataHttpMessageConverter.class) {
//            return ResultData.success(o);
//        }
        return Result.success(o);
    }
}
