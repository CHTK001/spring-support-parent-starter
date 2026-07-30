package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.model.AiChatRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface AiChatFacadeService {

    Map<String, Object> call(AiChatRequest request);

    org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(AiChatRequest request);

    default List<String> models(Integer groupId) {
        return Collections.emptyList();
    }
}
