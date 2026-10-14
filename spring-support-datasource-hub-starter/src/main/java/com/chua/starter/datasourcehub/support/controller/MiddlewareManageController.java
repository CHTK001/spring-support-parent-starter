package com.chua.starter.datasourcehub.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.datasource.support.middleware.MiddlewareCapability;
import com.chua.datasource.support.middleware.MiddlewareClientProvider;
import com.chua.datasource.support.middleware.MiddlewareConnectRequest;
import com.chua.datasource.support.middleware.MiddlewareDocument;
import com.chua.datasource.support.middleware.MiddlewareNotInstalledException;
import com.chua.datasource.support.middleware.MiddlewareQueryResult;
import com.chua.datasource.support.middleware.MiddlewareTestResult;
import com.chua.datasource.support.middleware.MiddlewareTreeNode;
import com.chua.starter.datasourcehub.support.middleware.MiddlewareClientRegistry;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 中间件管理面 REST 接口（SPI 驱动，类型无关）。
 *
 * <p><b>为什么是一个通用 Controller 而不是每种类型一个</b>：宿主不写死任何中间件语义
 * （不知道 Kafka 有 Topic、InfluxDB 有 Bucket）。所有操作归一成
 * 「连接测试 / 顶层节点 / 子节点 / 只读查询 / 详情查看」五个动作，
 * 由 {@link MiddlewareClientRegistry} 按 {@code type} 分发到具体 SPI 实现。</p>
 *
 * <p><b>新增一种中间件</b>：实现方注册 SPI 即可，本接口与前端无需任何改动。</p>
 *
 * <p><b>未装配对应实现时</b>：返回「功能未开放」的业务结果，
 * 不抛 500，前端据此提示「请安装 XX 能力包」。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Tag(name = "监控管理", description = "中间件管理面（SPI 驱动）")
@RestController
@RequestMapping("/ms/monitor/middleware")
@RequiredArgsConstructor
public class MiddlewareManageController {

    /** 默认查询返回行数 */
    private static final int DEFAULT_ROW_LIMIT = 50;

    /** 单次查询最大返回行数 */
    private static final int MAX_ROW_LIMIT = 500;

    private final MiddlewareClientRegistry registry;
    private final DatasourceService datasourceService;

    /**
     * 列出已开放功能的中间件类型（前端下拉用）。
     *
     * @return 类型名到展示名的映射
     */
    @Operation(summary = "列出已开放的中间件类型")
    @GetMapping("/types")
    public ReturnResult<Map<String, String>> types() {
        return ReturnResult.ok(registry.displayNames());
    }

    /**
     * 测试连接。
     *
     * @param type    数据源类型
     * @param request 测试请求，可为空
     * @return 测试结果
     */
    @Operation(summary = "测试中间件连接")
    @PostMapping("/test")
    public ReturnResult<MiddlewareTestResult> test(
            @RequestParam String type,
            @RequestBody(required = false) MiddlewareTestDTO request) {
        return call("测试 " + type + " 连接",
                () -> registry.require(type).test(toRequest(type, request)));
    }

    /**
     * 能力声明：宿主前端据此决定渲染哪些按钮。
     *
     * @param type 数据源类型
     * @return 能力描述
     */
    @Operation(summary = "查询中间件能力声明")
    @GetMapping("/capability")
    public ReturnResult<MiddlewareCapability> capability(@RequestParam String type) {
        return call("查询 " + type + " 能力",
                () -> registry.require(type).capability());
    }

    /**
     * 列出顶层节点（逻辑库 / Bucket / Topic / 命名空间）。
     *
     * @param type 数据源类型
     * @return 节点列表
     */
    @Operation(summary = "列出顶层节点")
    @GetMapping("/nodes")
    public ReturnResult<List<MiddlewareTreeNode>> nodes(@RequestParam String type) {
        return call("列出 " + type + " 顶层节点",
                () -> registry.require(type).listTopNodes(toRequest(type, null)));
    }

    /**
     * 列出顶层节点下的子节点（集合 / 度量 / 分区）。
     *
     * @param type      数据源类型
     * @param parentKey 父节点标识
     * @return 子节点列表
     */
    @Operation(summary = "列出子节点")
    @GetMapping("/children")
    public ReturnResult<List<MiddlewareTreeNode>> children(
            @RequestParam String type,
            @RequestParam String parentKey) {
        return call("列出 " + type + " 子节点",
                () -> registry.require(type).listChildren(toRequest(type, null), parentKey));
    }

    /**
     * 执行只读查询。
     *
     * @param type       数据源类型
     * @param expression 查询表达式，语法由实现方定义
     * @param limit      期望最大行数，缺省 50、上限 500
     * @return 查询结果
     */
    @Operation(summary = "执行只读查询")
    @PostMapping("/query")
    public ReturnResult<MiddlewareQueryResult> query(
            @RequestParam String type,
            @RequestParam String expression,
            @RequestParam(required = false) Integer limit) {
        int n = limit == null || limit <= 0 ? DEFAULT_ROW_LIMIT : Math.min(limit, MAX_ROW_LIMIT);
        return call("执行 " + type + " 查询",
                () -> registry.require(type).query(toRequest(type, null), expression, n));
    }

    /**
     * 查看单条详情（文档 / 消息 / 键值）。
     *
     * @param type  数据源类型
     * @param scope 作用域（库名 / 桶名 / 主题名）
     * @param key   标识，取自结果行
     * @return 详情，不存在时返回 null
     */
    @Operation(summary = "查看单条详情")
    @GetMapping("/document")
    public ReturnResult<MiddlewareDocument> document(
            @RequestParam String type,
            @RequestParam String scope,
            @RequestParam String key) {
        return call("查看 " + type + " 详情",
                () -> registry.require(type).document(toRequest(type, null), scope, key));
    }

    /**
     * 把数据源行（或前端显式传入的连接参数）转成 SPI 的不可变连接请求。
     *
     * <p>SPI 实现可能跑在 OSGi bundle 里，看不到宿主的 Spring 容器，
     * 因此必须把连接参数在这个方法里一次性备齐，不能让实现方反查宿主。</p>
     */
    private MiddlewareConnectRequest toRequest(String type, MiddlewareTestDTO dto) {
        if (dto != null && dto.datasourceId() != null && dto.datasourceId() > 0) {
            DataSourceVO ds = datasourceService.get(dto.datasourceId()).getData();
            if (ds == null) {
                throw new IllegalArgumentException("数据源不存在: id=" + dto.datasourceId());
            }
            return toRequest(ds);
        }
        // 没有 datasourceId 时退化为「该类型」的第一个数据源。
        // 注意：不能回退到其它类型的第一个数据源 —— 那会让 Kafka 拿 nginx 的连接参数
        // 去探测，属于静默串号（验收实测过）。
        List<DataSourceVO> all = datasourceService.list().getData();
        DataSourceVO ds = all == null ? null : all.stream()
                .filter(d -> type.equalsIgnoreCase(String.valueOf(d.type())))
                .findFirst()
                .orElse(null);
        if (ds == null) {
            throw new IllegalArgumentException("尚未登记 " + type + " 类型的数据源");
        }
        return toRequest(ds);
    }

    /**
     * 数据源行转 SPI 连接请求。
     *
     * <p>{@code DataSourceVO} 是 record，取值走访问器而不是 getter。</p>
     */
    private MiddlewareConnectRequest toRequest(DataSourceVO ds) {
        return new MiddlewareConnectRequest(
                ds.id() == null ? 0L : ds.id(),
                ds.name() == null ? "" : ds.name(),
                ds.host() == null ? "" : ds.host(),
                ds.port() == null ? 0 : ds.port(),
                ds.database() == null ? "" : ds.database(),
                ds.username() == null ? "" : ds.username(),
                ds.password() == null ? "" : ds.password());
    }

    /**
     * 统一调用包装：把 SPI 侧的「功能未开放」与参数错误转成业务结果，不打 ERROR 栈。
     *
     * @param <T>      返回数据类型
     * @param action   动作描述
     * @param supplier 实际调用
     * @return 结果对象
     */
    private <T> ReturnResult<T> call(String action, Supplier<T> supplier) {
        try {
            return ReturnResult.ok(supplier.get());
        } catch (MiddlewareNotInstalledException e) {
            // 功能未开放不是故障：INFO 级留痕即可，前端据此引导安装能力包
            log.info("[middleware] {} -> 功能未开放: type={}", action, e.type());
            return ReturnResult.error(notInstalledMessage(e.type()));
        } catch (IllegalArgumentException e) {
            log.info("[middleware] {} -> 参数不合法: {}", action, e.getMessage());
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[middleware] {} 失败", action, e);
            return ReturnResult.error(action + "失败: " + e.getMessage());
        }
    }

    /**
     * 「功能未开放」的提示文案：前端用来引导用户安装能力包。
     *
     * @param type 数据源类型
     * @return 可读提示
     */
    public static String notInstalledMessage(String type) {
        return "「" + type + "」功能未开放：未安装对应的中间件能力实现（SPI）。"
                + "请安装对应能力包后重启，或通过 OSGi 控制台热部署。";
    }

    /**
     * 功能未开放的兜底结果（供其它 Controller 复用，避免各处重复拼文案）。
     *
     * @param <T> 数据类型
     * @param e   异常
     * @return 携带提示的结果对象
     */
    public static <T> ReturnResult<T> notInstalled(MiddlewareNotInstalledException e) {
        return ReturnResult.error(notInstalledMessage(e.type()));
    }
}
