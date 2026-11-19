package com.chua.starter.ai.support.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.entity.SysAiSession;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.mapper.SysAiSessionMapper;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话管理 Controller
 *
 * @author CH
 * @since 4.0.0.51
 */
@RestController
@RequestMapping("/v2/sessions")
@RequiredArgsConstructor
public class SysAiSessionController {

    private final SysAiSessionMapper sessionMapper;

    /**
     * 查询归档会话列表。
     *
     * @param keyword 标题关键字过滤（可选）
     * @param groupId 分组标识（可选，按模块编码过滤）
     * @param skip    跳过的条数（可选）
     * @param limit   每页条数（可选）
     * @return 归档会话列表与总数
     */
    @GetMapping("/archived")
    public ReturnResult<Map<String, Object>> listArchived(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Integer skip,
            @RequestParam(required = false) Integer limit) {
        LambdaQueryWrapper<SysAiSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiSession::getIsArchived, true);  // 只查询已归档的会话
        // 仅本人归档（管理员在业务数据侧与普通账号同权）
        if (!AiUserContext.canAccessOthersData()) {
            wrapper.eq(SysAiSession::getSysUserId, AiUserContext.requireUserId());
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(SysAiSession::getTitle, keyword);
        }
        if (groupId != null) {
            wrapper.eq(SysAiSession::getSysAiModuleCode, groupId);
        }
        wrapper.orderByDesc(SysAiSession::getUpdateTime);

        List<SysAiSession> sessions = sessionMapper.selectList(wrapper);

        List<Map<String, Object>> items = new ArrayList<>();
        for (SysAiSession session : sessions) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", String.valueOf(session.getId()));
            item.put("title", session.getTitle());
            item.put("createdAt", session.getCreateTime());
            item.put("updatedAt", session.getUpdateTime());
            item.put("lastActiveAt", session.getUpdateTime());
            items.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", items.size());
        return ReturnResult.ok(result);
    }

    /**
     * 归档/取消归档会话。
     *
     * @param sessionId 会话标识（数字 id 或 UUID）
     * @param body      请求体，其中 archived 为要设置的归档状态
     * @return 操作结果（success 与变更后的会话）
     */
    @PutMapping("/{sessionId}/archive")
    public Map<String, Object> archive(
            @PathVariable String sessionId,
            @RequestBody Map<String, Boolean> body) {
        Boolean archived = body.get("archived");
        
        // 解析 sessionId：数字直接用，UUID 通过标题匹配查找
        Long sessionIdLong = resolveSessionId(sessionId);
        if (sessionIdLong == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }

        SysAiSession session = requireWritableSession(sessionIdLong);
        session.setIsArchived(archived);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
        return Map.of("success", true, "session", session);
    }
    
    /**
     * 解析前端 sessionId 到后端 Long ID。
     *
     * @param sessionId 前端会话 id，数字串或前缀编码串，可为空白
     * @return 后端数值 id，无法解析时返回 null
     */
    private Long resolveSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        // 数字 ID 直接转换
        if (sessionId.matches("\\d+")) {
            return Long.parseLong(sessionId);
        }
        // UUID 通过标题匹配查找（标题格式：guada-xxxxxxxx）
        String titlePrefix = "guada-" + sessionId.substring(0, Math.min(8, sessionId.length()));
        LambdaQueryWrapper<SysAiSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(SysAiSession::getTitle, titlePrefix);
        wrapper.orderByDesc(SysAiSession::getCreateTime);
        wrapper.last("LIMIT 1");
        SysAiSession session = sessionMapper.selectOne(wrapper);
        return session != null ? session.getId() : null;
    }

    /**
     * 批量归档/取消归档会话。
     *
     * <p><b>为什么同时收 POST。</b>前端 {@code batchArchiveSessions} 用的是 POST，
     * 而后端此前只有 PUT，浏览器侧表现为 405/走静态资源兜底；两侧方法必须对齐。</p>
     *
     * <p><b>为什么不再强转 {@code List<Long>}。</b>JSON 反序列化后的数字是 Integer，
     * 直接 {@code (List<Long>) body.get(...)} 在增强 for 拆箱时抛
     * {@code ClassCastException: Integer cannot be cast to Long}，接口 500。
     * 这里按 {@code Number}/{@code String} 两种真实形状逐个归一。</p>
     *
     * @param body 请求体，含 sessionIds 与 archived
     * @return 结果映射：success 与 skipped（无权/不存在而跳过的 id）
     */
    @RequestMapping(value = "/batch-archive", method = {RequestMethod.PUT, RequestMethod.POST})
    public Map<String, Object> batchArchive(@RequestBody Map<String, Object> body) {
        List<Long> sessionIds = longIdsOf(body.get("sessionIds"));
        Boolean archived = archiveFlagOf(body.get("archived"));
        List<Long> skipped = new ArrayList<>();
        for (Long id : sessionIds) {
            try {
                SysAiSession session = requireWritableSession(id);
                session.setIsArchived(archived);
                session.setUpdateTime(LocalDateTime.now());
                sessionMapper.updateById(session);
            } catch (ApiException e) {
                skipped.add(id);
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("updated", sessionIds.size() - skipped.size());
        result.put("skipped", skipped);
        return result;
    }

    /**
     * 把前端传来的 id 数组归一成 {@code List<Long>}。
     *
     * <p>JSON 数字可能是 Integer/Long，字符串形态也一并接受；非法项直接跳过，
     * 不让一个坏 id 打断整批。</p>
     *
     * @param raw 请求体里的原始值，可为 null
     * @return 归一后的 id 列表，永不为 null
     */
    private static List<Long> longIdsOf(Object raw) {
        List<Long> ids = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return ids;
        }
        for (Object item : list) {
            if (item instanceof Number number) {
                ids.add(number.longValue());
            } else if (item instanceof String text && !text.isBlank()) {
                try {
                    ids.add(Long.parseLong(text.trim()));
                } catch (NumberFormatException ignored) {
                    // 非数字 id（如前端 UUID）无法定位会话，直接忽略
                }
            }
        }
        return ids;
    }

    /**
     * 取归档标记：缺省视为归档，避免「少传字段 = 取消归档」把数据改坏。
     *
     * @param raw 请求体里的原始值，可为 null
     * @return 归档标记
     */
    private static Boolean archiveFlagOf(Object raw) {
        if (raw instanceof Boolean flag) {
            return flag;
        }
        if (raw instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text.trim());
        }
        return Boolean.TRUE;
    }

    /**
     * 写出口归属校验：只认归属人本身，管理员对他人数据只读。
     *
     * @param sessionId 会话标识
     * @return 校验通过的会话
     */
    private SysAiSession requireWritableSession(Long sessionId) {
        SysAiSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        if (AiUserContext.isReadonly()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "只读账号不允许执行写操作");
        }
        if (!AiUserContext.requireUserId().equals(session.getSysUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "管理员对他人数据只读，不可修改或删除");
        }
        return session;
    }
}
