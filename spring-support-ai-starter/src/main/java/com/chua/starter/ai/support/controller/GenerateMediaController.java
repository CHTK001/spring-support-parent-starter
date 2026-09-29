package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.generate.GenerateMediaStore;
import com.chua.starter.ai.support.generate.GenerateService;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.mapper.SysAiGenerateTaskMapper;
import com.chua.starter.ai.support.manage.store.SysAiGenerateTask;
import com.chua.starter.ai.support.util.AiUserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 生成任务媒体读取：按 taskId + 下标返回图片字节。
 *
 * <p><b>为什么需要这个控制器。</b>部分供应商（OpenAI 兼容系的 {@code b64_json}、本地同步出图）
 * 返回的是整张图的 base64，不是地址。这类结果一度被原样存进任务行的 JSON 列，
 * 于是「历史列表」这一个只读接口每次都要把页内每一条的整张图读回来：
 * 实测 30 条任务 543~680ms，而单行读在有内联图时 28~47ms、无内联图时 11~15ms ——
 * 开销随行数线性增长，库也会一直涨。</p>
 *
 * <p><b>分工。</b>内联字节在入库前先落盘（见 {@link GenerateMediaStore}），
 * 库里只留句柄；列表与查询接口把这句柄换成
 * {@code /v2/ai/generate/tasks/{taskId}/media/{index}}（见
 * {@code GenerateController#toView}），前端 {@code <img src>} 直接指向它，
 * 浏览器自己管懒加载、解码与缓存；供应商本来就给的真实地址原样透传。</p>
 *
 * <p><b>两种来源。</b>新行是磁盘句柄，改造前入库的存量行仍是内联 base64，
 * 本控制器两种都能服务，因此不需要一次性重写历史数据。</p>
 *
 * <p><b>鉴权。</b>{@code <img>} 带不了 Authorization 头，跨站也带不了 cookie，
 * 所以前端按框架既有约定把令牌放在查询参数 {@code x-oauth-token} 里
 * （与 {@code workspace.api.ts} 的资源地址同款）。令牌仍然是登录令牌，
 * 每次请求都回查归属，不存在「地址即凭证」的问题。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/ai/generate/tasks")
@RequiredArgsConstructor
@Tag(name = "AI 生成 - 任务媒体")
public class GenerateMediaController {

    /**
     * 内联媒体地址前缀。
     *
     * <p>{@code GenerateController#toView} 用它把 base64 换成可寻址路径，
     * 本控制器按同一前缀解析，两者必须保持一致。</p>
     */
    public static final String INLINE_PATH_PREFIX = "/v2/ai/generate/tasks/";

    /**
     * data URI 的固定开头
     */
    private static final String DATA_URI_PREFIX = "data:";

    /**
     * base64 载荷分隔符
     */
    private static final String BASE64_MARKER = ";base64,";

    /**
     * 无法识别的媒体类型回落值
     */
    private static final MediaType OCTET_STREAM = MediaType.APPLICATION_OCTET_STREAM;

    /**
     * 任务行映射器
     */
    private final SysAiGenerateTaskMapper taskMapper;

    /**
     * 生成媒体落盘存储：查询命中的句柄从磁盘读回
     */
    private final GenerateMediaStore mediaStore;

    /**
     * 生成服务：存量媒体回填直接用它的业务实现，避免在控制器里重写一遍落盘规则
     */
    private final GenerateService generateService;

    /**
     * 读取任务的第 index 张结果图。
     *
     * @param taskId 任务ID
     * @param index  结果图下标，与列表接口 {@code urls} 里的位置一一对应
     * @return 图片字节流
     */
    @Operation(summary = "读取生成结果图", description = "按任务号与下标返回结果图字节：句柄从磁盘读，存量内联行从库读")
    @GetMapping("/{taskId}/media/{index}")
    public ResponseEntity<byte[]> media(@PathVariable String taskId, @PathVariable int index) {
        SysAiGenerateTask task = requireOwned(taskId);
        List<String> urls = task.resultUrls();
        if (index < 0 || index >= urls.size()) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "结果图不存在");
        }
        String raw = urls.get(index);
        // 供应商本来就给了地址的，浏览器自己去取，这里不重复代理
        if (!GenerateMediaStore.isStored(raw)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "该结果不是本应用托管的媒体，无需经本接口读取");
        }
        return respond(raw, taskId + "#" + index);
    }

    /**
     * 读取任务的参考图。
     *
     * @param taskId 任务ID
     * @return 参考图字节流
     */
    @Operation(summary = "读取生成参考图", description = "返回参考图字节：句柄从磁盘读，存量内联行从库读")
    @GetMapping("/{taskId}/reference")
    public ResponseEntity<byte[]> reference(@PathVariable String taskId) {
        SysAiGenerateTask task = requireOwned(taskId);
        return respond(task.getReferenceImage(), taskId + "#ref");
    }

    /**
     * 执行存量内联媒体回填。
     *
     * <p>落盘改造只对新行生效；改造前入库的结果行与参考图行仍把整张图存在
     * LONGTEXT 里，只读的历史列表每次都要把页内这些行读回来，列表慢的账全在那里。
     * 本接口把它们一次性搬到磁盘，库里换成句柄。幂等，可反复执行。</p>
     *
     * <p>建议先用 {@code dryRun=true} 清点，确认影响行数后再执行。
     * 顺序上先写文件再改库，写失败的行原样保留 base64，不会出现两边都没有的状态。</p>
     *
     * @param dryRun 只清点不写库，默认 true（防误触发）
     * @return 统计报告
     */
    @Operation(summary = "存量内联媒体回填", description = "把历史行里的 base64 搬到磁盘，库里换成句柄；dryRun 默认 true")
    @PostMapping("/media/backfill")
    public ReturnResult<Map<String, Object>> backfillMedia(
            @RequestParam(required = false, defaultValue = "true") boolean dryRun) {
        AiUserContext.requireUserId();
        return ReturnResult.success(generateService.backfillInlineMedia(dryRun));
    }

    /**
     * 取任务行并校验归属。
     *
     * <p>地址里带的是任务号，不是凭证：任何登录用户都能构造出别人的 taskId，
     * 所以这里必须比对 userId，否则等于把全库的图片按可枚举的 UUID 敞开。
     * 查询参数里的令牌由 oauth 过滤器解析，因此 {@code requireUserId} 在此可用。</p>
     *
     * @param taskId 任务ID
     * @return 任务行
     */
    private SysAiGenerateTask requireOwned(String taskId) {
        String userId = AiUserContext.requireUserId();
        SysAiGenerateTask task = taskMapper.selectById(taskId);
        if (task == null || !userId.equals(task.getUserId())) {
            // 不区分「不存在」与「不是你的」：报出后者等于确认该任务号存在
            throw new ApiException(ApiErrorCode.NOT_FOUND, "任务不存在");
        }
        return task;
    }

    /**
     * 把 data URI 拆成媒体类型与字节并组织响应。
     *
     * <p>缓存给 7 天且允许公共缓存：字节一旦落定就不再变化（重新生成会写新的任务行），
     * 任务号又是 UUID，回访同一页面时浏览器直接命中本地缓存，不再回源。</p>
     *
     * <p>两种来源都走这里：新行是磁盘句柄，存量行是库内内联 base64。
     * 对调用方是同一个响应形态，前端只认地址、不需要知道字节在哪。</p>
     *
     * @param raw  媒体值：句柄或 data URI
     * @param etag 缓存标识，用 taskId 与下标拼，不需要另算哈希
     * @return 图片字节流响应
     */
    private ResponseEntity<byte[]> respond(String raw, String etag) {
        if (GenerateMediaStore.isHandle(raw)) {
            GenerateMediaStore.Stored stored = mediaStore.load(raw);
            if (stored == null) {
                throw new ApiException(ApiErrorCode.NOT_FOUND, "媒体文件不存在");
            }
            return build(stored.bytes(), resolveType(stored.contentType()), etag);
        }
        if (!GenerateMediaStore.isInlineData(raw)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "该任务没有可读取的托管媒体");
        }
        int split = raw.indexOf(BASE64_MARKER);
        String declared = raw.substring(DATA_URI_PREFIX.length(), split);
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(raw.substring(split + BASE64_MARKER.length()));
        } catch (IllegalArgumentException e) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "内联图片内容已损坏");
        }
        return build(bytes, resolveType(declared), etag);
    }

    /**
     * 组织字节流响应。
     *
     * @param bytes 字节
     * @param type  媒体类型
     * @param etag  缓存标识
     * @return 字节流响应
     */
    private static ResponseEntity<byte[]> build(byte[] bytes, MediaType type, String etag) {
        if (bytes == null || bytes.length == 0) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "媒体内容为空");
        }
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .eTag("\"" + etag + "\"")
                .body(bytes);
    }

    /**
     * 把 data URI 头部声明的类型转成 MediaType。
     *
     * <p>声明值来自入库原文，但仍是外部写入的文本，交给
     * {@link MediaType#parseMediaType} 解析；解析不了就回落成
     * {@code application/octet-stream}，绝不让一个畸形串变成 500。</p>
     *
     * @param declared data URI 头部声明的类型，如 {@code image/png}
     * @return 媒体类型
     */
    private static MediaType resolveType(String declared) {
        try {
            MediaType type = MediaType.parseMediaType(declared);
            return type.isWildcardType() ? OCTET_STREAM : type;
        } catch (IllegalArgumentException e) {
            return OCTET_STREAM;
        }
    }
}

