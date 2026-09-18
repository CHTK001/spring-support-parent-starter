package com.chua.starter.project.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.project.support.entity.MsProject;

/**
 * AI 脚本生成服务
 * <p>
 * 依据项目类型 / 部署目录 / 目标 OS / 健康目标，调用
 * spring-support-ai-starter 的 LLM 通道自动生成启动/停止/重启/状态检测脚本，
 * 供前端表单「AI 生成脚本」一键填充。宿主未装配 AI 时降级返回提示。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface ProjectAiScriptService {

    /** 脚本类型：启动 */
    String KIND_START = "START";

    /** 脚本类型：停止 */
    String KIND_STOP = "STOP";

    /** 脚本类型：重启 */
    String KIND_RESTART = "RESTART";

    /** 脚本类型：状态检测（守护用，exit 0=健康） */
    String KIND_CHECK = "CHECK";

    /**
    * 生成指定类型的运维脚本
    *
    * @param project 项目（类型/目录/OS/健康目标等上下文）
    * @param kind    脚本类型 START/STOP/RESTART/CHECK
    * @return 生成的脚本文本；AI 未装配或失败返回错误提示
    */
    ReturnResult<String> generate(MsProject project, String kind);
}
