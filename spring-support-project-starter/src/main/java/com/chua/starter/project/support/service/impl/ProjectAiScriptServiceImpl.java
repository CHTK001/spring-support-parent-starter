package com.chua.starter.project.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.starter.project.support.entity.MsProject;
import com.chua.starter.project.support.service.ProjectAiScriptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * AI 脚本生成服务实现（反射降级版）
 * <p>
 * 宿主装配 spring-support-ai-starter 时，通过运行时反射查找
 * {@code ChatClientRegistry} bean 与 {@code ChatClient} 接口调用 LLM；
 * 本模块与 AI starter 之间零编译期依赖（AI starter 打包产物为 fat-jar
 * 不可作普通依赖），未装配时接口降级返回提示，不影响部署/守护主流程。
 * 生成结果直接回填表单，由用户确认后再保存，避免未审核脚本直接落库执行。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectAiScriptServiceImpl implements ProjectAiScriptService {

    /** AI 注册表 bean 类型（全限定名，反射查找） */
    private static final String REGISTRY_CLASS = "com.chua.starter.ai.support.registry.ChatClientRegistry";

    /** Spring 应用上下文（运行时反射查找 AI bean，与 AI starter 零编译期依赖） */
    private final ApplicationContext applicationContext;

    @Override
    public ReturnResult<String> generate(MsProject project, String kind) {
        if (project == null) {
            return ReturnResult.error("项目上下文不能为空");
        }
        if (kind == null || kind.isBlank()) {
            return ReturnResult.error("脚本类型不能为空");
        }
        String kindUpper = kind.trim().toUpperCase(Locale.ROOT);
        if (!KIND_START.equals(kindUpper) && !KIND_STOP.equals(kindUpper)
                && !KIND_RESTART.equals(kindUpper) && !KIND_CHECK.equals(kindUpper)) {
            return ReturnResult.error("不支持的脚本类型: " + kind + "（START/STOP/RESTART/CHECK）");
        }
        Object registry = findRegistryBean();
        if (registry == null) {
            return ReturnResult.error("宿主未装配 AI 能力（spring-support-ai-starter），请改用内置模板或手动编写");
        }
        boolean linux = isLinux(project);
        try {
            // [P3C 1.10 豁免] 跨模块解耦反射调用 AI 注册表 API
            Method getDefault = registry.getClass().getMethod("getDefault");
            Object client = getDefault.invoke(registry);
            if (client == null) {
                return ReturnResult.error("AI 客户端不可用：请在 sys_ai_global_setting.runtime 配置 provider/api_key/base_url/model");
            }
            String prompt = buildPrompt(project, kindUpper);
            Object response = invokeClient(client, systemPrompt(linux), prompt);
            String text = response == null ? null : extractText(response);
            if (text == null || text.isBlank()) {
                return ReturnResult.error("AI 返回为空，请重试");
            }
            return ReturnResult.ok(stripFence(text));
        } catch (Exception e) {
            log.warn("[project-ai] 脚本生成失败: {}", e.getMessage());
            return ReturnResult.error("AI 生成失败: " + rootMsg(e));
        }
    }

    /**
     * 反射查找 AI 注册表 bean
     *
     * @return 注册表实例，未装配返回 空
     */
    private Object findRegistryBean() {
        Class<?> registryClass = ReflectUtils.forName(REGISTRY_CLASS);
        if (registryClass == null) {
            return null;
        }
        try {
            String[] names = applicationContext.getBeanNamesForType(registryClass);
            if (names.length == 0) {
                return null;
            }
            return applicationContext.getBean(names[0]);
        } catch (Exception e) {
            log.warn("[project-ai] 查找 AI 注册表 bean 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 调用客户端阻塞生成
     *
     * @param client  客户端
     * @param system  系统提示词
     * @param prompt  用户提示词
     * @return 同步响应对象
     * @throws Exception 反射调用异常
     */
    private Object invokeClient(Object client, String system, String prompt) throws Exception {
        // [P3C 1.10 豁免] 跨模块解耦反射调用 AI 客户端 API，保持原生反射以避免编译期强依赖
        Method systemMethod = client.getClass().getMethod("system", String.class);
        Object configured = systemMethod.invoke(client, system);
        Method tempMethod = configured.getClass().getMethod("temperature", double.class);
        Object tuned = tempMethod.invoke(configured, 0.2);
        Method chatMethod = tuned.getClass().getMethod("chatSync", String.class);
        return chatMethod.invoke(tuned, prompt);
    }

    /**
     * 提取同步响应文本
     *
     * @param response 响应对象
     * @return 文本
     * @throws Exception 反射调用异常
     */
    private String extractText(Object response) throws Exception {
        if (response instanceof String s) {
            return s;
        }
        // [P3C 1.10 豁免] 同上，跨模块解耦反射调用
        Method getText = response.getClass().getMethod("getText");
        Object text = getText.invoke(response);
        return text == null ? null : text.toString();
    }

    /**
     * 系统提示词：约束 LLM 只输出可执行脚本
     *
     * @param linux 是否 Linux
     * @return 系统提示词
     */
    private String systemPrompt(boolean linux) {
        return linux
                ? "你是一名 Linux 系统运维专家。只输出可直接执行的 bash 脚本，不要输出任何解释、标题或 Markdown 围栏；脚本需支持 {{dir}} 占位符表示部署目录。"
                : "你是一名 Windows 系统运维专家。只输出可直接执行的 PowerShell 脚本，不要输出任何解释、标题或 Markdown 围栏；脚本需支持 {{dir}} 占位符表示部署目录。";
    }

    /**
     * 构造用户提示词（携带项目上下文）
     *
     * @param project 项目
     * @param kind    脚本类型
     * @return 提示词
     */
    private String buildPrompt(MsProject project, String kind) {
        boolean linux = isLinux(project);
        String osName = linux ? "Linux" : "Windows";
        String dir = project.getMsProjectDeployDir() == null ? "" : project.getMsProjectDeployDir();
        String healthTarget = project.getMsProjectHealthTarget() == null ? "" : project.getMsProjectHealthTarget();
        String healthType = project.getMsProjectHealthType() == null ? "PORT" : project.getMsProjectHealthType();
        return switch (kind) {
            case KIND_START -> "为以下应用生成【启动脚本】，目标 OS=" + osName + "，项目类型="
                    + project.getMsProjectType() + "，部署目录={{dir}}（实际值 " + dir + "）。"
                    + "要求：后台运行、重定向输出、幂等（已启动时不重复启动）。";
            case KIND_STOP -> "为以下应用生成【停止脚本】，目标 OS=" + osName + "，项目类型="
                    + project.getMsProjectType() + "，部署目录={{dir}}（实际值 " + dir + "）。"
                    + "要求：按进程特征精确停止、不误杀其它 java/node 进程、退出码 0 表示成功。";
            case KIND_RESTART -> "为以下应用生成【重启脚本】，目标 OS=" + osName + "，项目类型="
                    + project.getMsProjectType() + "，部署目录={{dir}}（实际值 " + dir + "）。"
                    + "要求：先停止旧进程再启动，旧进程停止失败时强制 kill 后继续启动。";
            case KIND_CHECK -> "为以下应用生成【状态检测脚本】（守护进程用），目标 OS=" + osName + "，"
                    + "项目类型=" + project.getMsProjectType() + "，健康检查类型=" + healthType
                    + "，检查目标=" + healthTarget + "，部署目录={{dir}}（实际值 " + dir + "）。"
                    + "要求：服务健康时 exit 0，不健康时非 0 退出；探测要快（<10s）。";
            default -> throw new IllegalStateException("未知脚本类型: " + kind);
        };
    }

    /**
     * 去除 LLM 输出中的 Markdown 围栏
     *
     * @param text 原始文本
     * @return 纯脚本文本
     */
    private String stripFence(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline > 0) {
                t = t.substring(firstNewline + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.trim();
    }

    /**
     * 项目目标 OS 是否 Linux
     *
     * @param project 项目
     * @return 是否 Linux
     */
    private boolean isLinux(MsProject project) {
        String os = project.getMsProjectHostOs() == null ? "LINUX" : project.getMsProjectHostOs();
        return os.trim().toUpperCase(Locale.ROOT).startsWith("LINUX");
    }

    /**
     * 异常根因信息
     *
     * @param e 异常
     * @return 根因消息
     */
    private String rootMsg(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}
