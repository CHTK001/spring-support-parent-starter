package com.chua.starter.ai.support.chat;

import com.chua.starter.ai.support.model.AiMemoryRecallDTO;
import com.chua.starter.ai.support.service.SysAiMemoryService;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 对话轮次与记忆体之间的接线 —— 注入本轮命中的记忆，轮次结束后回抽记忆。
 *
 * <p>记忆能力本身在 {@link SysAiMemoryService}，这里只负责"哪一轮、哪个会话、哪个人"的取值，
 * 让 {@code /api/conversations} 与 {@code /v2/chat} 两套对话出口共用同一条接线，
 * 避免各自抄一份导致口径漂移。</p>
 *
 * <p>降级口径：召回失败只让本轮不带记忆，抽取失败只丢这一轮的沉淀，都不阻断对话回复。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMemorySupport {

    /**
     * 记忆体服务
     */
    private final SysAiMemoryService memoryService;

    /**
     * 记忆片段的固定开头，用于剥离历史里上一轮注入的片段（与 {@code renderPromptBlock} 同源）。
     */
    private static final String BLOCK_PREFIX = "以下是系统为当前用户维护的长期记忆";

    /**
     * 把本轮命中的记忆作为一段 system 消息插入到本轮用户消息之前。
     *
     * @param userId         记忆归属人
     * @param conversationId 会话标识（前端会话主键，与记忆表 {@code conversation_id} 同源）
     * @param userInput      本轮用户输入，相关性打分依据
     * @param msgList        待拼装的消息列表，原地调整
     */
    public void inject(String userId, String conversationId, String userInput, List<Map<String, Object>> msgList) {
        if (userId == null || userId.isBlank() || msgList == null) {
            return;
        }
        try {
            AiMemoryRecallDTO recall = memoryService.recall(userId, conversationId, userInput, true);
            if (recall == null || recall.getPromptBlock() == null || recall.getPromptBlock().isBlank()) {
                return;
            }
            // 历史消息里会带上上一轮注入的片段，重复堆叠会挤占上下文
            msgList.removeIf(msg -> isMemoryBlock(msg.get("content")));
            Map<String, Object> memory = new LinkedHashMap<>();
            memory.put("role", "system");
            memory.put("content", recall.getPromptBlock());
            msgList.add(indexOfCurrentTurnUser(msgList), memory);
            log.info("[ChatMemory] 记忆注入 conversation={} user={} 条数={}",
                    conversationId, userId, recall.getItems() == null ? 0 : recall.getItems().size());
        } catch (Exception e) {
            log.warn("[ChatMemory] 记忆召回失败（本轮不注入）conversation={}: {}", conversationId, e.getMessage());
        }
    }

    /**
     * 是否为本轮之前注入的记忆片段。
     *
     * @param content 消息内容
     * @return 是记忆片段返回 true
     */
    private boolean isMemoryBlock(Object content) {
        return content instanceof String text && text.startsWith(BLOCK_PREFIX);
    }

    /**
     * 本轮用户消息的位置：记忆要落在问题之前，插到末尾会被模型当成答案后的补充。
     *
     * @param msgList 消息列表
     * @return 插入下标，无用户消息时返回列表末尾
     */
    private int indexOfCurrentTurnUser(List<Map<String, Object>> msgList) {
        for (int i = msgList.size() - 1; i >= 0; i--) {
            if ("user".equals(msgList.get(i).get("role"))) {
                return i;
            }
        }
        return msgList.size();
    }

    /**
     * 轮次结束后异步抽取记忆，避免模型抽取拖慢本轮收尾推送。
     *
     * @param userId         记忆归属人
     * @param conversationId 会话标识
     * @param userInput      本轮用户输入
     * @param fullReply      本轮模型完整回复
     * @param model          本轮模型名称
     */
    public void remember(String userId, String conversationId, String userInput, String fullReply, String model) {
        if (userId == null || userId.isBlank() || fullReply == null || fullReply.isBlank()) {
            return;
        }
        CompletableFuture.runAsync(AiUserContext.capturing(() -> {
            try {
                memoryService.extractAndRecord(userId, conversationId, userInput, fullReply, model);
            } catch (Exception e) {
                log.warn("[ChatMemory] 轮次记忆抽取失败 conversation={}: {}", conversationId, e.getMessage());
            }
        }));
    }
}
