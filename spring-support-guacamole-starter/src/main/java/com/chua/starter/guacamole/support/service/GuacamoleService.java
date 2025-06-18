package com.chua.starter.guacamole.support.service;

import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.GuacamoleTunnel;

/**
 * Guacamole服务接口
 *
 * @author CH
 * @since 2024/7/24
 */
public interface GuacamoleService {

    /**
     * 创建Guacamole隧道
     *
     * @param protocol 协议类型（rdp, vnc, ssh等）
     * @param host     远程主机地址
     * @param port     远程主机端口
     * @return Guacamole隧道
     * @throws GuacamoleException 如果创建隧道时发生错误
     */
    GuacamoleTunnel createTunnel(String protocol, String host, int port) throws GuacamoleException;

    /**
     * 创建Guacamole隧道（带用户名密码）
     *
     * @param protocol 协议类型（rdp, vnc, ssh等）
     * @param host     远程主机地址
     * @param port     远程主机端口
     * @param username 用户名
     * @param password 密码
     * @return Guacamole隧道
     * @throws GuacamoleException 如果创建隧道时发生错误
     */
    GuacamoleTunnel createTunnel(String protocol, String host, int port, String username, String password) throws GuacamoleException;

    /**
     * 创建RDP隧道
     *
     * @param host     远程主机地址
     * @param port     远程主机端口
     * @param username 用户名
     * @param password 密码
     * @param domain   域名（可选）
     * @param security 安全模式（可选）
     * @param width    屏幕宽度
     * @param height   屏幕高度
     * @return Guacamole隧道
     * @throws GuacamoleException 如果创建隧道时发生错误
     */
    GuacamoleTunnel createRdpTunnel(String host, int port, String username, String password,
                                    String domain, String security, int width, int height) throws GuacamoleException;

    /**
     * 创建VNC隧道
     *
     * @param host     远程主机地址
     * @param port     远程主机端口
     * @param password 密码
     * @param width    屏幕宽度
     * @param height   屏幕高度
     * @return Guacamole隧道
     * @throws GuacamoleException 如果创建隧道时发生错误
     */
    GuacamoleTunnel createVncTunnel(String host, int port, String password,
                                    int width, int height) throws GuacamoleException;

    /**
     * 创建SSH隧道
     *
     * @param host     远程主机地址
     * @param port     远程主机端口
     * @param username 用户名
     * @param password 密码
     * @return Guacamole隧道
     * @throws GuacamoleException 如果创建隧道时发生错误
     */
    GuacamoleTunnel createSshTunnel(String host, int port, String username, String password) throws GuacamoleException;
} 