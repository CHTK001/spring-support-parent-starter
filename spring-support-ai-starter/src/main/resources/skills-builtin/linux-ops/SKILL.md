---
name: linux-ops
icon: WindowConsole20Regular
title: Linux 运维手册
description: Linux 服务器运维常用操作：进程与服务、文件与磁盘、网络排查、性能分析、安全基线。
version: 1.0.0
author: system
category: 运维部署
tags:
  - linux
  - ops
  - shell
---

# Linux 运维手册

## 一、进程与服务

- 查进程：`ps aux | grep java`、`pgrep -af <name>`；按端口找 PID：`ss -ltnp | grep 8080`。
- systemd 服务：`systemctl status/start/stop/enable <svc>`；开机自启用 `enable`，别用 rc.local 硬拼。
- 手工拉起的后端要用 `nohup ... &` 或 `setsid` 脱离会话，日志重定向到文件；重要服务写 unit 文件托管。
- 结束进程先 `kill <pid>`（SIGTERM）给清理机会，僵死再 `kill -9`；不要 `pkill -9 -f` 宽泛匹配误杀。

## 二、文件与磁盘

- 磁盘水位：`df -h` 看容量、`df -i` 看 inode（小文件爆 inode）；`du -sh *` 逐层找大目录。
- 大文件定位：`find / -xdev -size +500M -type f`；日志轮转用 logrotate，别让单文件无限增长。
- 删除被占用文件后空间不释放：进程仍握着 fd，`lsof | grep deleted` 找到后重启该进程。
- 挂载检查：`mount | column -t`；NFS 卡顿先看 `dmesg` 与网络。

## 三、网络排查

- 连通性：`ping`（可达性）、`mtr`（路径丢包）、`curl -v --noproxy '*'`（HTTP 层，注意系统代理劫持）。
- 端口监听：`ss -ltnp`；连接状态分布：`ss -s`，大量 TIME_WAIT 检查短连接与端口耗尽。
- DNS：`dig +short host`；解析慢/失败先查 `/etc/resolv.conf`。
- 抓包：`tcpdump -i any port 3306 -w a.pcap`，配合 wireshark 分析握手与重置。

## 四、性能分析

- 整体：`top` / `htop`（CPU、load、内存）、`iostat -x 1`（磁盘）、`vmstat 1`（换页）。
- load 高先看 CPU 型还是 IO 型：`us` 高查应用，`wa` 高查磁盘，`si/so` 频繁说明内存不足。
- CPU 火焰图：`perf record -F 99 -g -p <pid>` 或 async-profiler；现场排查 JVM 用 `jstat`/`jcmd`。
- 交换分区频繁活动 = 物理内存吃紧，先查泄漏与大缓存，别急着加 swap。

## 五、安全基线

- SSH：禁 root 直登、禁密码改密钥、改默认端口可选；`fail2ban` 防爆破。
- 防火墙最小开放：`firewalld`/`iptables` 只放行业务端口与堡垒机网段。
- 账号与 sudo 审计；关键目录权限收敛（配置文件 600，含密钥的绝不 644）。
- 时间同步（chrony/ntp）必须开——证书、日志、分布式锁都依赖时钟。

## 六、检查清单

- [ ] 服务有 systemd unit 或可靠守护，断电重启可自愈
- [ ] 日志有轮转，磁盘水位有告警
- [ ] 端口最小开放，SSH 走密钥
- [ ] 排查会用 ss/iostat/perf 组合拳
- [ ] 时钟同步与备份任务在跑
