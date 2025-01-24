package com.chua.starter.common.support.result;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.exception.AuthenticationException;
import com.chua.common.support.lang.exception.RemoteExecutionException;
import com.chua.common.support.lang.file.adaptor.univocity.parsers.conversions.Validator;
import com.chua.common.support.unit.name.NamingCase;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.common.support.exception.RuntimeMessageException;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.ServletException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.net.UnknownHostException;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.text.DecimalFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.chua.common.support.lang.code.ReturnCode.*;

/**
 * 统一异常处理
 * @author CH
 */
@RestControllerAdvice
@Slf4j
public class ExceptionAdvice  {

    static final Pattern DATA_TOO_LONG_PATTERN = Pattern.compile("Data too long for column '([^']*)' at row");

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> processException(BindException e) {
        log.error("BindException:{}", e.getMessage());
        String msg = e.getAllErrors().stream().map(DefaultMessageSourceResolvable::getDefaultMessage).collect(Collectors.joining("；"));
        return Result.failed(REQUEST_PARAM_ERROR, msg);
    }
    @ExceptionHandler(RemoteExecutionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> remoteExecutionException(RemoteExecutionException e) {
        String message = e.getMessage();
        if(null != message && message.contains("Auth fail")) {
            return Result.failed(e.getType() + "登录认证失败");
        }
        return Result.failed(REMOTE_EXECUTION_TIMEOUT, REMOTE_EXECUTION_TIMEOUT.getMsg());
    }


    /**
     * RequestBody参数的校验
     *
     * @param e
     * @param <T>
     * @return
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> processException(HttpRequestMethodNotSupportedException e) {
        log.error("HttpRequestMethodNotSupportedException:{}", e.getMessage());
        ProblemDetail body = e.getBody();
        return Result.failed(REQUEST_PARAM_ERROR, body.getDetail());
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
        log.error("MethodArgumentNotValidException:{}", e.getMessage());
        String msg = e.getBindingResult().getAllErrors().stream().map(DefaultMessageSourceResolvable::getDefaultMessage).collect(Collectors.joining("；"));
        return Result.failed(REQUEST_PARAM_ERROR, msg);
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
    public <T> ReturnResult<T> processException(MissingServletRequestParameterException e) {
        log.error(e.getMessage(), e);
        return Result.illegal(REQUEST_PARAM_ERROR, REQUEST_PARAM_ERROR.getMsg() + "(" + e.getParameterName() + ")缺失");
    }

    /**
     * MethodArgumentTypeMismatchException
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> processException(MethodArgumentTypeMismatchException e) {
        log.error(e.getMessage(), e);
        return Result.failed(REQUEST_PARAM_ERROR, "类型错误");
    }

    /**
     * ServletException
     */
    @ExceptionHandler(ServletException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> processException(ServletException e) {
        log.error(e.getMessage(), e);
        if(e instanceof HttpMediaTypeNotSupportedException httpMediaTypeNotSupportedException) {
            return Result.failed("当前不支持: {}, 支持: {}",
                    httpMediaTypeNotSupportedException.getContentType(),
                    httpMediaTypeNotSupportedException.getSupportedMediaTypes()
                    );
        }
        return Result.failed("当前请求方法不支持{}", e.getMessage());
    }
    /**
     * ServletException
     */
    @ExceptionHandler(UnknownHostException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public <T> Result<T> unknow(UnknownHostException e) {
        log.error(e.getMessage(), e);
        return Result.failed(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("非法参数异常，异常原因：{}", e.getMessage(), e);
        String message = e.getMessage();
        if(message.contains("Unable to parse url")) {
            return Result.failed(message.replace("Unable to parse url", "无法解析地址"));
        }
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
        log.error("-->", e);
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
        log.error("handleException exception: {}", e.getMessage());
        return Result.failed("请求失败,请稍后重试");
    }
    @ExceptionHandler(SQLException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> sqlException(SQLException e) {
        log.error("SQLException: {}", e.getMessage());
        if(Validator.hasChinese(e.getMessage())) {
            return Result.failed(e);
        }
        return Result.failed(e.getSQLState());
    }
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> Result<T> handleRuntimeException(RuntimeException e) {
        if("org.apache.ibatis.exceptions.TooManyResultsException".equals(e.getClass().getName())) {
            log.error("SQL只允许返回一条数据, 但是查询到多条数据", e);
        } else {
            log.error("handleRuntimeException exception", e);
        }


        if(Validator.hasChinese(e.getMessage())) {
            return Result.failed(e);
        }

        if(e instanceof RuntimeMessageException ) {
            return Result.failed(e.getMessage());
        }

        Throwable cause = e.getCause();
        if(cause instanceof UnsupportedOperationException) {
            return Result.failed("当前系统版本/软件不支持该功能");
        }
        if(cause instanceof RemoteExecutionException) {
            return remoteExecutionException((RemoteExecutionException) cause);
        }

        if(cause instanceof IllegalArgumentException) {
            return handleIllegalArgumentException((IllegalArgumentException) cause);
        }

        String message = cause.getMessage();
        if (message != null && message.contains("Data truncation: Data too long for column")) {
            Matcher matcher = DATA_TOO_LONG_PATTERN.matcher(message);
            if (matcher.find()) {
                if (matcher.groupCount() == 1) {
                    return Result.failed("选项%s长度过长".formatted(NamingCase.toCamelCase(matcher.group(1))));
                }
            }
            return Result.failed("数据长度过长");
        }
        return Result.failed("当前系统版本不支持或者系统不开放");
    }

    static Pattern CONVERTER_PATTERN = Pattern.compile("\\[\"(.*?)\"]+");
    /**
     * 传参类型错误时，用于消息转换
     *
     * @param throwable 异常
     * @return 错误信息
     */
    private String convertMessage(Throwable throwable) {
        String error = throwable.toString();
        Matcher matcher = CONVERTER_PATTERN.matcher(error);
        String group = "";
        if (matcher.find()) {
            String matchString = matcher.group();
            matchString = matchString.replace("[", "").replace("]", "");
            matchString = String.format("%s字段类型错误", matchString.replaceAll("\\\"", ""));
            group += matchString;
        }
        return group;
    }
}
