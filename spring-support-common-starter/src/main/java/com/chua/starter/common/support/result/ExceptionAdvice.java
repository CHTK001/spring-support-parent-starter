package com.chua.starter.common.support.result;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.exception.AuthenticationException;
import com.chua.common.support.lang.exception.RemoteExecutionException;
import com.chua.common.support.unit.name.NamingCase;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.validator.Validator;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.common.support.exception.RuntimeMessageException;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.BadSqlGrammarException;
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
 *
 * @author CH
 * @since 2023-08-01
 */
@RestControllerAdvice
@Slf4j
public class ExceptionAdvice {

    /**
     * 匹配数据库字段过长错误信息的正则表达式
     */
    static final Pattern DATA_TOO_LONG_PATTERN = Pattern.compile("Data too long for column '([^']*)' at row");

    /**
     * 处理参数绑定异常
     *
     * @param e 参数绑定异常对象，包含详细的错误信息
     * @param <T> 泛型类型
     * @return 返回错误结果，包含具体的错误信息
     * 
     * 示例：
     * 当请求参数不符合验证规则时，会抛出此异常
     * 如：@NotNull(message = "用户名不能为空") String username
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> processException(BindException e) {
        e.printStackTrace();
        String msg = e.getAllErrors().stream().map(DefaultMessageSourceResolvable::getDefaultMessage).collect(Collectors.joining("；"));
        return Result.failed(REQUEST_PARAM_ERROR, msg);
    }

    /**
     * 处理远程执行异常
     *
     * @param e 远程执行异常对象，包含远程服务调用的错误信息
     * @param <T> 泛型类型
     * @return 返回错误结果，根据异常类型返回不同的错误信息
     * 
     * 示例：
     * 当调用远程服务失败时抛出此异常
     * 如：远程服务认证失败、超时等
     */
    @ExceptionHandler(RemoteExecutionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> remoteExecutionException(RemoteExecutionException e) {
        String message = e.getMessage();
        log.error("远程调用异常");
        e.printStackTrace();
        if(null != message && message.contains("Auth fail")) {
            return Result.failed(e.getType() + "登录认证失败");
        }
        return Result.failed(REMOTE_EXECUTION_TIMEOUT, REMOTE_EXECUTION_TIMEOUT.getMsg());
    }

    /**
     * 处理HTTP请求方法不支持异常
     *
     * @param e HTTP请求方法不支持异常对象，包含请求方法和允许的方法信息
     * @param <T> 泛型类型
     * @return 返回错误结果，包含详细的错误描述
     * 
     * 示例：
     * 当客户端使用GET方法访问只支持POST的接口时抛出此异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> processException(HttpRequestMethodNotSupportedException e) {
        e.printStackTrace();
        ProblemDetail body = e.getBody();
        return Result.failed(REQUEST_PARAM_ERROR, body.getDetail());
    }

    /**
     * 处理方法参数验证异常
     *
     * @param e 方法参数验证异常对象，包含参数验证的错误信息
     * @param <T> 泛型类型
     * @return 返回错误结果，包含具体的验证错误信息
     * 
     * 示例：
     * 当使用@RequestBody接收参数且参数不符合验证规则时抛出此异常
     * 如：@Valid @RequestBody User user
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> processException(MethodArgumentNotValidException e) {
        e.printStackTrace();
        String msg = e.getBindingResult().getAllErrors().stream().map(DefaultMessageSourceResolvable::getDefaultMessage).collect(Collectors.joining("；"));
        return Result.failed(REQUEST_PARAM_ERROR, msg);
    }

    /**
     * 处理找不到处理器异常
     *
     * @param e 找不到处理器异常对象，表示请求的资源不存在
     * @param <T> 泛型类型
     * @return 返回错误结果，提示资源未找到
     * 
     * 示例：
     * 当访问一个不存在的URL路径时抛出此异常
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public <T> Result<T> processException(NoHandlerFoundException e) {
        e.printStackTrace();
        return Result.failed(RESOURCE_NOT_FOUND);
    }

    /**
     * 处理缺少请求参数异常
     *
     * @param e 缺少请求参数异常对象，包含缺失参数的名称
     * @param <T> 泛型类型
     * @return 返回错误结果，提示具体缺失的参数名
     * 
     * 示例：
     * 当请求需要参数id但未提供时抛出此异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> ReturnResult<T> processException(MissingServletRequestParameterException e) {
        e.printStackTrace();
        return Result.illegal(REQUEST_PARAM_ERROR, REQUEST_PARAM_ERROR.getMsg() + "(" + e.getParameterName() + ")缺失");
    }

    /**
     * 处理方法参数类型不匹配异常
     *
     * @param e 方法参数类型不匹配异常对象，包含参数类型错误信息
     * @param <T> 泛型类型
     * @return 返回错误结果，提示参数类型错误
     * 
     * 示例：
     * 当请求参数期望是Integer类型但传入了字符串时抛出此异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> processException(MethodArgumentTypeMismatchException e) {
        e.printStackTrace();
        return Result.failed(REQUEST_PARAM_ERROR, "类型错误");
    }

    /**
     * 处理Servlet异常
     *
     * @param e Servlet异常对象，包含具体的Servlet错误信息
     * @param <T> 泛型类型
     * @return 返回错误结果，根据异常类型返回不同的错误信息
     * 
     * 示例：
     * 当请求的Content-Type不支持时抛出HttpMediaTypeNotSupportedException
     */
    @ExceptionHandler(ServletException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> processException(ServletException e) {
        e.printStackTrace();
        if(e instanceof HttpMediaTypeNotSupportedException httpMediaTypeNotSupportedException) {
            return Result.failed("当前不支持: {}, 支持: {}",
                    httpMediaTypeNotSupportedException.getContentType(),
                    httpMediaTypeNotSupportedException.getSupportedMediaTypes()
                    );
        }
        return Result.failed("当前请求方法不支持{}", e.getMessage());
    }

    /**
     * 处理未知主机异常
     *
     * @param e 未知主机异常对象，表示无法解析的主机名
     * @param <T> 泛型类型
     * @return 返回错误结果，提示服务不可用
     * 
     * 示例：
     * 当尝试连接一个不存在的域名时抛出此异常
     */
    @ExceptionHandler(UnknownHostException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public <T> Result<T> unknow(UnknownHostException e) {
        e.printStackTrace();
        return Result.failed(e.getMessage());
    }

    /**
     * 处理非法参数异常
     *
     * @param e 非法参数异常对象，包含参数错误的具体信息
     * @param <T> 泛型类型
     * @return 返回错误结果，根据错误信息返回相应的提示
     * 
     * 示例：
     * 当传入无效的URL参数时抛出此异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("非法参数异常，异常原因：{}", e.getMessage(), e);
        e.printStackTrace();
        String message = e.getMessage();
        if(message.contains("Unable to parse url")) {
            return Result.failed(message.replace("Unable to parse url", "无法解析地址"));
        }
        return Result.failed(e.getMessage());
    }

    /**
     * 处理JSON处理异常
     *
     * @param e JSON处理异常对象，包含JSON转换的错误信息
     * @param <T> 泛型类型
     * @return 返回错误结果，提示JSON格式错误
     * 
     * 示例：
     * 当请求体中的JSON格式不正确时抛出此异常
     */
    @ExceptionHandler(JsonProcessingException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> handleJsonProcessingException(JsonProcessingException e) {
        log.error("Json转换异常，异常原因：{}", e.getMessage(), e);
        e.printStackTrace();
        return Result.failed(e.getMessage());
    }

    /**
     * 处理HTTP消息不可读异常
     *
     * @param e HTTP消息不可读异常对象，通常表示请求体格式错误
     * @param <T> 泛型类型
     * @return 返回错误结果，提示请求体错误信息
     * 
     * 示例：
     * 当请求体为空或格式不正确时抛出此异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> processException(HttpMessageNotReadableException e) {
        e.printStackTrace();
        String errorMessage = "请求体不可为空";
        Throwable cause = e.getCause();
        if (cause != null) {
            errorMessage = convertMessage(cause);
        }
        return Result.failed(errorMessage);
    }

    /**
     * 处理类型不匹配异常
     *
     * @param e 类型不匹配异常对象，包含类型转换错误信息
     * @param <T> 泛型类型
     * @return 返回错误结果，提示类型错误
     * 
     * 示例：
     * 当请求参数类型与期望类型不匹配时抛出此异常
     */
    @ExceptionHandler(TypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> processException(TypeMismatchException e) {
        e.printStackTrace();
        return Result.failed(e.getMessage());
    }

    /**
     * 处理SQL语法错误异常
     *
     * @param e SQL语法错误异常对象，表示SQL语句存在语法问题
     * @param <T> 泛型类型
     * @return 返回错误结果，提示无权限操作
     * 
     * 示例：
     * 当执行的SQL语句语法不正确时抛出此异常
     */
    @ExceptionHandler(SQLSyntaxErrorException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public <T> Result<T> processSQLSyntaxErrorException(SQLSyntaxErrorException e) {
        e.printStackTrace();
        return Result.failed("无权限操作");
    }

    /**
     * 处理认证异常
     *
     * @param e 认证异常对象，表示用户认证失败
     * @param <T> 泛型类型
     * @return 返回错误结果，提示无权限操作
     * 
     * 示例：
     * 当用户未登录或登录凭证无效时抛出此异常
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public <T> Result<T> authenticationException(AuthenticationException e) {
        e.printStackTrace();
        return Result.failed(RESULT_ACCESS_UNAUTHORIZED, "无权限操作");
    }

    /**
     * 处理业务异常
     *
     * @param e 业务异常对象，包含业务逻辑错误信息
     * @param <T> 泛型类型
     * @return 返回错误结果，根据异常码返回相应提示
     * 
     * 示例：
     * 当业务逻辑检查失败时抛出此异常
     * 如：用户不存在、余额不足等业务相关错误
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> handleBizException(BusinessException e) {
        e.printStackTrace();
        if (e.getResultCode() != null) {
            return Result.failed(e.getLocalizedMessage());
        }
        return Result.failed("系统繁忙");
    }

    /**
     * 文件大小格式化器
     */
    static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.##");

    /**
     * 处理文件上传大小超限异常
     *
     * @param e 文件上传大小超限异常对象，包含最大允许的文件大小
     * @param <T> 泛型类型
     * @return 返回错误结果，提示文件过大及限制大小
     * 
     * 示例：
     * 当上传的文件超过配置的最大大小时抛出此异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> maxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.error("文件过大", e);
        e.printStackTrace();
        if(e.getMaxUploadSize() > 0) {
            return Result.failed("文件过大, 当前服务器支支持{}大小文件", StringUtils.getNetFileSizeDescription(e.getMaxUploadSize(), DECIMAL_FORMAT));
        }
        return Result.failed("文件过大");
    }

    /**
     * 处理通用异常
     *
     * @param e 通用异常对象，捕获所有未被特定处理的异常
     * @param <T> 泛型类型
     * @return 返回错误结果，提示请求失败
     * 
     * 示例：
     * 当发生未预期的运行时异常时被捕获
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> handleException(Exception e) {
        log.error("handleException exception: {}", e.getMessage());
        e.printStackTrace();
        return Result.failed("请求失败,请稍后重试");
    }

    /**
     * 处理SQL异常
     *
     * @param e SQL异常对象，包含数据库操作的错误信息
     * @param <T> 泛型类型
     * @return 返回错误结果，根据错误信息返回相应提示
     * 
     * 示例：
     * 当数据库操作失败时抛出此异常
     * 如：违反唯一约束、连接超时等
     */
    @ExceptionHandler(SQLException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public <T> Result<T> sqlException(SQLException e) {
        log.error("SQLException: {}", e.getMessage());
        e.printStackTrace();
        if(Validator.hasChinese(e.getMessage())) {
            return Result.failed(e);
        }
        return Result.failed(e.getSQLState());
    }

    /**
     * 处理SQL语法异常
     *
     * @param ex SQL语法异常对象，包含错误的SQL语句和错误信息
     * @param <T> 泛型类型
     * @return 返回错误结果，提示SQL语法错误
     * 
     * 示例：
     * 当执行的SQL语句存在语法错误时抛出此异常
     */
    @ExceptionHandler(BadSqlGrammarException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> Result<T> badSqlGrammarException(BadSqlGrammarException ex) {
        ex.printStackTrace();
        // 提取异常中的关键词信息
        log.error("错误SQL: " + ex.getSql());
        log.error("错误代码: " + ex.getSQLException().getErrorCode());
        log.error("错误信息: " + ex.getSQLException().getMessage());
        String sql = ex.getSql().toLowerCase(); // 获取有问题的SQL语句

        // 提取INSERT语句中的表名
        if (sql.startsWith("insert")) {
            Pattern insertPattern = Pattern.compile("insert\\s+into\\s+([\\w\\.]+)", Pattern.CASE_INSENSITIVE);
            Matcher m = insertPattern.matcher(sql);
            if (m.find()) {
                System.out.println("INSERT表名: " + m.group(1));
            }
        }

        // 提取SELECT语句中的表名
       else if (sql.startsWith("select")) {
            Pattern selectPattern = Pattern.compile("from\\s+([\\w\\.]+)", Pattern.CASE_INSENSITIVE);
            Matcher m = selectPattern.matcher(sql);
            if (m.find()) {
                System.out.println("SELECT表名: " + m.group(1));
            }
        }

        // 提取UPDATE语句中的表名
        else  if (sql.startsWith("update")) {
            Pattern updatePattern = Pattern.compile("update\\s+([\\w\\.]+)", Pattern.CASE_INSENSITIVE);
            Matcher m = updatePattern.matcher(sql);
            if (m.find()) {
                System.out.println("UPDATE表名: " + m.group(1));
            }
        }

        // 提取DELETE语句中的表名
        else  if (sql.startsWith("delete")) {
            // 处理DELETE语句
            Pattern deletePattern = Pattern.compile(
                    "delete\\s+(?:from\\s+)?([\\w\\.]+)",
                    Pattern.CASE_INSENSITIVE
            );
            Matcher m = deletePattern.matcher(sql);
            if (m.find()) {
                System.out.println("DELETE操作表名: " + m.group(1));
            }
        }


        return Result.failed(ex.getRootCause());
    }

    /**
     * 处理运行时异常
     *
     * @param e 运行时异常对象，包含运行时错误信息
     * @param response HTTP响应对象，用于设置响应头信息
     * @param <T> 泛型类型
     * @return 返回错误结果，根据异常类型返回不同提示
     * 
     * 示例：
     * 当发生各种运行时错误时被捕获，如空指针异常、数组越界等
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public <T> Result<T> handleRuntimeException(RuntimeException e, HttpServletResponse response) {
        e.printStackTrace();
        response.setContentType("application/json");
        if("org.apache.ibatis.exceptions.TooManyResultsException".equals(e.getClass().getName())) {
            log.error("SQL只允许返回一条数据, 但是查询到多条数据", e);
        } else {
            log.error("handleRuntimeException exception", e);
        }

        String message = e.getMessage();
        Throwable cause = e.getCause();
        if (cause instanceof Exception e1 && !(e1 instanceof NullPointerException)) {
            message = e1.getMessage();
        }

        if (Validator.hasChinese(message)) {
            return Result.failed(e);
        }

        if (e instanceof RuntimeMessageException) {
            return Result.failed(e.getMessage());
        }

        if(cause instanceof UnsupportedOperationException) {
            return Result.failed("当前系统版本/软件不支持该功能");
        }
        if(cause instanceof RemoteExecutionException) {
            return remoteExecutionException((RemoteExecutionException) cause);
        }

        if(cause instanceof IllegalArgumentException) {
            return handleIllegalArgumentException((IllegalArgumentException) cause);
        }

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

    /**
     * 参数转换错误信息匹配模式
     */
    static Pattern CONVERTER_PATTERN = Pattern.compile("\\[\"(.*?)\"]+");

    /**
     * 传参类型错误时，用于消息转换
     *
     * @param throwable 异常对象，包含类型转换错误的详细信息
     * @return 错误信息字符串，描述具体的字段类型错误
     * 
     * 示例：
     * 当请求参数类型不匹配时，提取并格式化错误信息
     * 如：将"Cannot convert value of type 'java.lang.String' to required type 'java.lang.Integer'"转换为更友好的提示
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
