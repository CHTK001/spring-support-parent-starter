// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.model;

import com.chua.starter.ai.support.entity.SysAiBotBinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 验收 {@code BeanUtils.copyProperties} 是否能把 record 的属性喂给 POJO。
 *
 * <p>录：AiBotBindingController.create/update() 里 {@code BeanUtils.copyProperties(body, entity)}
 * 实际上执行为记录体赋值，如果 record 的访问器不被 BeanWrapper 识别，这里会静默丢数据。</p>
 */
@DisplayName("BeanUtils.copyProperties 对 record 的财产移植性")
class RecordCopyPropertiesTest {

    @Test
    @DisplayName("record 源属性的字段可以完整复制给目标 POJO")
    void copyPropertiesRecordsSoucre() {
        BotBindingCreateRequest src = new BotBindingCreateRequest(
                "b1", "tk", "WX", "昵称", 8000, "ON", "app", "qwen",
                true, "low", "sys", true, true, true, 5, 100.0);
        SysAiBotBinding target = new SysAiBotBinding();
        BeanUtils.copyProperties(src, target);

        assertEquals("b1", target.getBotId());
        assertEquals("tk", target.getBotToken());
        assertEquals("WX", target.getPlatform());
        assertEquals("昵称", target.getNickname());
        assertEquals(Integer.valueOf(8000), target.getGatewayPort());
        assertEquals("ON", target.getStatus());
        assertEquals("app", target.getBindSource());
        assertEquals("qwen", target.getModel());
        assertEquals(true, target.getThinking());
        assertEquals("low", target.getThinkingEffort());
        assertEquals("sys", target.getSystemPrompt());
        assertEquals(true, target.getRecordUsage());
        assertEquals(true, target.getMentionOnly());
        assertEquals(true, target.getRandomReplyEnabled());
        assertEquals(Integer.valueOf(5), target.getRandomContextCount());
        assertEquals(Double.valueOf(100.0), target.getRandomProbability());
    }

    @Test
    @DisplayName("record 源属性的过空也正常复制")
    void copyPropertiesSparseRecord() {
        BotBindingCreateRequest src = new BotBindingCreateRequest(null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);
        SysAiBotBinding target = new SysAiBotBinding();
        BeanUtils.copyProperties(src, target);
        assertNull(target.getBotId());
    }
}