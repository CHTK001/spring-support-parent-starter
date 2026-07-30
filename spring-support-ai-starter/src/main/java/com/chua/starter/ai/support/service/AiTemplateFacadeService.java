package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiTemplate;

import java.util.List;

public interface AiTemplateFacadeService {

    List<SysAiTemplate> list(Integer moduleId, String templateType, String category, String name, String remark);

    SysAiTemplate save(SysAiTemplate template);

    boolean update(SysAiTemplate template);

    boolean deleteById(Integer id);
}
