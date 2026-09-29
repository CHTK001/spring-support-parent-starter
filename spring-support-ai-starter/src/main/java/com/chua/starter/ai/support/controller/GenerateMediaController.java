package com.chua.starter.ai.support.controller;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 生成任务媒体读取：按 taskId + 下标返回库内以 base64 保存的图片字节。
 *
 * <p><b>为什么需要这个控制器。</b>部分供应商（OpenAI 兼容系的 {@code b64_json}、本地同步出图）
 * 返回的是整张图的 base64，不是地址。这类结果被原样存进任务行的 JSON 列，
 * 于是「历史列表」这一个只读接口每次都要吐出几十 MB 纯文本：
 * 实测 30 条任务、其中 1 条带 28 张图 + 1 张 2.38MB 参考图时，
 * 单次响应 <b>77.5MB / 5.2 秒</b>，而这 77MB 里有 77MB 都不该出现在列表里。</p>
 *
 * <p><b>分工。</b>列表与查询接口只回「地址」——内联 base64 换成
 * {@code /v2/ai/generate/tasks/{taskId}/media/{index}}（见
 * {@code GenerateController#toView}），前端 {@code <img src>} 直接指向它，
 * 浏览器自己管懒加载、解码与缓存；真实地址原样透传，前端不需要区分两者。
 * 字节流只在这里按需吐一次，且不参与列表体积。</p>
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
     * 读取任务的第 index 张结果图。
     *
     * @param taskId 任务ID
     * @param index  结果图下标，与列表接口 {@code urls} 里的位置一一对应
     * @return 图片字节流
     */
    @Operation(summary = "读取生成结果图", description = "按任务号与下标返回库内以 base64 保存的结果图字节")
    @GetMapping("/{taskId}/media/{index}")
    public ResponseEntity<byte[]> media(@PathVariable String taskId, @PathVariable int index) {
        SysAiGenerateTask task = requireOwned(taskId);
        List<String> urls = task.resultUrls();
        if (index < 0 || index >= urls.size()) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "结果图不存在");
        }
        String raw = urls.get(index);
        // 非内联项（供应商本来就给了地址）由浏览器自己去取，这里不重复代理
        if (!isInline(raw)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "该结果不是内联图片，无需经本接口读取");
        }
        return respond(raw, taskId + "#" + index);
    }

    /**
     * 读取任务的参考图。
     *
     * @param taskId 任务ID
     * @return 参考图字节流
     */
    @Operation(summary = "读取生成参考图", description = "返回库内以 base64 保存的参考图字节")
    @GetMapping("/{taskId}/reference")
    public ResponseEntity<byte[]> reference(@PathVariable String taskId) {
        SysAiGenerateTask task = requireOwned(taskId);
        String raw = task.getReferenceImage();
        if (!isInline(raw)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "该任务没有内联参考图");
        }
        return respond(raw, taskId + "#ref");
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
     * <p>缓存给 7 天且允许公共缓存：字节一旦入库就不再变化（重新生成会写新的任务行），
     * 任务号又是 UUID，回访同一页面时浏览器直接命中本地缓存，不再回源。</p>
     *
     * @param dataUri data URI 原文
     * @param etag   缓存标识，用 taskId 与下标拼，不需要另算哈希
     * @return 图片字节流响应
     */
    private static ResponseEntity<byte[]> respond(String dataUri, String etag) {
        int split = dataUri.indexOf(BASE64_MARKER);
        if (split < 0) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "内联图片格式无法解析");
        }
        String declared = dataUri.substring(DATA_URI_PREFIX.length(), split);
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(dataUri.substring(split + BASE64_MARKER.length()));
        } catch (IllegalArgumentException e) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "内联图片内容已损坏");
        }
        if (bytes.length == 0) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "内联图片内容为空");
        }
        return ResponseEntity.ok()
                .contentType(resolveType(declared))
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

    /**
     * 判断一个媒体值是不是内联 base64。
     *
     * <p>这就是「怎么区分」的唯一判据：内联的以 {@code data:} 开头且带
     * {@code ;base64,} 分隔符；供应商本来就给地址的（http/https、对象存储路径）
     * 一律原样透传，不走本接口。两类在前端都是同一个字符串塞进
     * {@code <img src>}，浏览器都能加载，因此前端不需要做这个判断。</p>
     *
     * @param value 媒体值
     * @return true 表示内联 base64
     */
    public static boolean isInline(String value) {
        return value != null && value.startsWith(DATA_URI_PREFIX) && value.contains(BASE64_MARKER);
    }
}
