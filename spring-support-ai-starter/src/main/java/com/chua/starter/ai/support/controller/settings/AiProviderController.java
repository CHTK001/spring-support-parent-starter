package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型供应商控制器
 */
@Tag(name = "AI 设置 - 模型供应商")
@RestController
@RequestMapping("/v2/providers")
@RequiredArgsConstructor
public class AiProviderController {

    private final SysAiProviderMapper sysAiProviderMapper;

    /**
     * 获取可添加的供应商模板
     * @return 结果列表，无数据时为空列表
     */
    @Operation(summary = "获取可添加的供应商模板")
    @GetMapping("/templates")
    public List<Map<String, Object>> templates() {
        List<Map<String, Object>> list = new ArrayList<>(16);

        // OpenAI
        Map<String, Object> openai = new HashMap<>(8);
        openai.put("id", "openai");
        openai.put("name", "OpenAI");
        openai.put("description", "OpenAI GPT系列模型，包括GPT-4o、GPT-4、GPT-3.5等");
        openai.put("protocol", "openai");
        openai.put("defaultApiUrl", "https://api.openai.com/v1");
        list.add(openai);

        // Anthropic
        Map<String, Object> anthropic = new HashMap<>(8);
        anthropic.put("id", "anthropic");
        anthropic.put("name", "Anthropic");
        anthropic.put("description", "Claude系列模型，包括Claude 3 Opus、Sonnet、Haiku等");
        anthropic.put("protocol", "anthropic");
        anthropic.put("defaultApiUrl", "https://api.anthropic.com");
        list.add(anthropic);

        // Google Gemini
        Map<String, Object> gemini = new HashMap<>(8);
        gemini.put("id", "google");
        gemini.put("name", "Google Gemini");
        gemini.put("description", "Google Gemini系列模型，包括Gemini 1.5 Pro、Flash等");
        gemini.put("protocol", "openai");
        gemini.put("defaultApiUrl", "https://generativelanguage.googleapis.com/v1beta/openai/");
        list.add(gemini);

        // DeepSeek
        Map<String, Object> deepseek = new HashMap<>(8);
        deepseek.put("id", "deepseek");
        deepseek.put("name", "DeepSeek");
        deepseek.put("description", "深度求索，DeepSeek-V3、R1等开源模型");
        deepseek.put("protocol", "openai");
        deepseek.put("defaultApiUrl", "https://api.deepseek.com/v1");
        list.add(deepseek);

        // 通义千问
        Map<String, Object> qwen = new HashMap<>(8);
        qwen.put("id", "aliyun-bailian");
        qwen.put("name", "通义千问");
        qwen.put("description", "阿里云通义千问系列模型");
        qwen.put("protocol", "openai");
        qwen.put("defaultApiUrl", "https://dashscope.aliyuncs.com/compatible-mode/v1");
        list.add(qwen);

        // 智谱AI
        Map<String, Object> zhipu = new HashMap<>(8);
        zhipu.put("id", "zhipu");
        zhipu.put("name", "智谱AI");
        zhipu.put("description", "智谱GLM系列模型，包括GLM-4、ChatGLM等");
        zhipu.put("protocol", "openai");
        zhipu.put("defaultApiUrl", "https://open.bigmodel.cn/api/paas/v4");
        list.add(zhipu);

        // Moonshot
        Map<String, Object> moonshot = new HashMap<>(8);
        moonshot.put("id", "moonshot");
        moonshot.put("name", "Moonshot");
        moonshot.put("description", "月之暗面Kimi系列模型");
        moonshot.put("protocol", "openai");
        moonshot.put("defaultApiUrl", "https://api.moonshot.cn/v1");
        list.add(moonshot);

        // Ollama（本地）
        Map<String, Object> ollama = new HashMap<>(8);
        ollama.put("id", "ollama");
        ollama.put("name", "Ollama");
        ollama.put("description", "本地运行的开源模型，支持Llama、Qwen等");
        ollama.put("protocol", "openai");
        ollama.put("defaultApiUrl", "http://localhost:11434/v1");
        list.add(ollama);

        // ONNX（本地）
        Map<String, Object> onnx = new HashMap<>(8);
        onnx.put("id", "onnx");
        onnx.put("name", "ONNX Runtime");
        onnx.put("description", "本地ONNX模型推理，支持自定义模型部署");
        onnx.put("protocol", "openai");
        onnx.put("defaultApiUrl", "http://localhost:8080/v1");
        list.add(onnx);

        // SiliconFlow 硅基流动
        Map<String, Object> siliconflow = new HashMap<>(8);
        siliconflow.put("id", "siliconflow");
        siliconflow.put("name", "SiliconFlow 硅基流动");
        siliconflow.put("description", "国内AI模型平台，支持DeepSeek、Qwen、GLM等");
        siliconflow.put("protocol", "openai");
        siliconflow.put("defaultApiUrl", "https://api.siliconflow.cn/v1");
        list.add(siliconflow);

        // OpenRouter
        Map<String, Object> openrouter = new HashMap<>(8);
        openrouter.put("id", "openrouter");
        openrouter.put("name", "OpenRouter");
        openrouter.put("description", "聚合API，支持GPT、Claude、Gemini等数百个模型");
        openrouter.put("protocol", "openai");
        openrouter.put("defaultApiUrl", "https://openrouter.ai/api/v1");
        list.add(openrouter);

        // 魔搭 ModelScope
        Map<String, Object> modelscope = new HashMap<>(8);
        modelscope.put("id", "modelscope");
        modelscope.put("name", "魔搭 ModelScope");
        modelscope.put("description", "阿里达摩院模型社区，免费使用多种开源模型");
        modelscope.put("protocol", "openai");
        modelscope.put("defaultApiUrl", "https://api-inference.modelscope.cn/v1");
        list.add(modelscope);

        // 千帆 Qianfan
        Map<String, Object> qianfan = new HashMap<>(8);
        qianfan.put("id", "qianfan");
        qianfan.put("name", "百度千帆");
        qianfan.put("description", "百度智能云大模型平台，支持文心一言、DeepSeek等");
        qianfan.put("protocol", "openai");
        qianfan.put("defaultApiUrl", "https://qianfan.baidubce.com/v2");
        list.add(qianfan);

        // SenseNova 商汤
        Map<String, Object> sensenova = new HashMap<>(8);
        sensenova.put("id", "sensenova");
        sensenova.put("name", "SenseNova 商汤");
        sensenova.put("description", "商汤科技大模型平台");
        sensenova.put("protocol", "openai");
        sensenova.put("defaultApiUrl", "https://token.sensenova.cn/v1");
        list.add(sensenova);

        // NVIDIA
        Map<String, Object> nvidia = new HashMap<>(8);
        nvidia.put("id", "nvidia");
        nvidia.put("name", "NVIDIA NIM");
        nvidia.put("description", "英伟达AI推理平台，免费使用多种模型");
        nvidia.put("protocol", "openai");
        nvidia.put("defaultApiUrl", "https://integrate.api.nvidia.com/v1");
        list.add(nvidia);

        // 自定义
        Map<String, Object> custom = new HashMap<>(8);
        custom.put("id", "custom");
        custom.put("name", "自定义供应商");
        custom.put("description", "自定义OpenAI兼容协议的供应商，支持任何兼容OpenAI API的服务");
        custom.put("protocol", "openai");
        custom.put("defaultApiUrl", "");
        list.add(custom);

        return list;
    }

    /**
     * 获取已添加的供应商列表
     * @return 结果列表，无数据时为空列表
     */
    @Operation(summary = "获取已添加的供应商列表")
    @GetMapping
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<SysAiProvider> providers = sysAiProviderMapper.selectList(null);
        for (SysAiProvider provider : providers) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", provider.getProviderId());
            item.put("name", provider.getName());
            item.put("platform", provider.getPlatform());
            item.put("baseUrl", provider.getBaseUrl());
            result.add(item);
        }
        return result;
    }
}
