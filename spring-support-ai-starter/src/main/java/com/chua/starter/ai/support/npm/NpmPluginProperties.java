package com.chua.starter.ai.support.npm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * npm 插件能力配置（{@code plugin.ai.npm.*}）。
 *
 * <p>控制 npm 安装型插件的全局开关、Node/npm 可执行文件位置、安装目录
 * 以及 Node 侧车进程的空闲退出与调用超时。全部项都有可用默认值：
 * 未显式配置时，安装目录取 {@code ${user.dir}/npm-plugins}，node/npm 取 PATH 上的命令。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Data
@Component
@ConfigurationProperties(prefix = "plugin.ai.npm")
public class NpmPluginProperties {

    /**
     * 是否启用 npm 插件能力（全局总闸）。
     *
     * <p>关闭时角色配置了 npmPlugins 也不会包装中间件，侧车进程不会启动。</p>
     */
    private boolean enabled = true;

    /**
     * node 可执行文件路径。
     *
     * <p>为空时使用 PATH 上的 {@code node}。194 等未把 Node 写入 PATH 的机器，
     * 指向解压目录下的 node.exe（如 {@code D:\apps\nodejs\node.exe}）。</p>
     */
    private String nodePath = "";

    /**
     * npm 可执行文件路径。
     *
     * <p>为空时 Windows 取 {@code npm.cmd}、其它平台取 {@code npm}。
     * 与 nodePath 同理，可指向解压目录下的 npm.cmd。</p>
     */
    private String npmPath = "";

    /**
     * npm 插件安装根目录。
     *
     * <p>为空时取 {@code ${user.dir}/npm-plugins}。npm 安装在该目录的
     * node_modules 下，Node 侧车从同一目录 require 插件模块。</p>
     */
    private String installDir = "";

    /**
     * npm registry 镜像地址。
     *
     * <p>为空时使用 npm 默认源；内网/弱网环境可配置为
     * {@code https://registry.npmmirror.com} 等镜像。</p>
     */
    private String npmRegistry = "";

    /**
     * Node 侧车空闲自退秒数。
     *
     * <p>侧车进程持续无调用超过该时长后自行退出，释放内存；
     * 下次有插件调用时再由 Java 侧拉起。</p>
     */
    private long hostIdleSeconds = 300;

    /**
     * 单次中间件调用（event/response）超时秒数。
     */
    private long callTimeoutSeconds = 60;

    /**
     * npm install/uninstall 命令超时秒数。
     */
    private long installTimeoutSeconds = 300;
}
