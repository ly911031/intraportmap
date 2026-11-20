# 0.2.7 验证

- 本地 APK 编译和 v1/v2/v3 签名验证通过，版本号 0.2.7 / 11，最低 API 21。
- 页面顺序为端口映射、Shadowsocks、设置；保留原有两个服务页索引和配置存储，第三页沿用原有开机设置、日志导出及系统设置处理。
- 无连接设备，未进行三页布局及系统设置入口的实机视觉验收。

# 0.2.6 验证

- 本地三 ABI 构建与 APK v1/v2/v3 签名验证通过，版本号 0.2.6 / 10，最低 API 21。
- 从最终 APK 的二进制 Manifest 检查到 RECEIVE_BOOT_COMPLETED 权限、BootReceiver 及 BOOT_COMPLETED 过滤器。
- 主机回归测试通过：API21/23/24/26 网络分支、DNS 格式、日志轮转/导出。
- 开机参数包含映射密钥/SS 密码，保存至应用私有偏好设置；接收器逐模块检查开关，分别启动并独立记录失败，不记录参数明文。
- 无连接设备，尚未实测系统重启、首次解锁及厂商自启动策略。

# 0.2.4 验证

- 三种 ABI 构建、资源打包、APK v2/v3 签名验证通过，版本号 0.2.4 / 8。
- 自适应图标、主题、通知图标作为 Android 资源打包；Java 样式代码编译通过。
- 两服务使用同一通知 ID 和渠道；停止一个服务后由剩余服务更新通知。扩充设备测试检查同时运行及停止 SS 后均只有一条通知，测试代码已编译。
- 没有连接 Android 设备，尚未实机检查界面布局、Toast、桌面图标和通知启停表现。

# 0.2.3 验证

- 三种 ABI 构建、APK v2/v3 签名验证通过，版本号 0.2.3 / 7。
- 网络回调模拟回归通过：SS DNS-only 模式忽略网络/IP 变化、DNS 地址顺序变化和断网后恢复相同 DNS；DNS 地址集合改变触发一次恢复。原端口映射模式测试保持通过。
- 手动 DNS 参数使 SS 选择不注册网络监听；再次点击启动会按新参数关闭旧监听并重新选择策略。
- 本次未连接设备，未进行手机切网实测。

# 0.2.2 验证

- 三种 ABI 编译和 APK v2/v3 签名验证通过，版本号 0.2.2 / 6。
- 主机端网络回调模拟测试通过：初始网络不重复重启、切网事件合并、DNS 和地址变化、断网恢复、停止后注销并取消恢复。测试位于 `tests-host/network`，与生产 `NetworkWatcher.java` 一起用 JDK 编译执行；测试替身只用于主机端，不进入 APK。
- 原生 SS 在完成 DNS、所选 TCP/UDP 监听初始化后输出就绪标记；Java 收到标记才显示启动成功，进程退出或重建时重置状态。三种 ABI 均已构建该代码。
- 设备测试已更新配置保存和就绪状态断言，并编译通过；无连接设备，未执行设备测试，也未进行真实 Wi-Fi/移动网络切换验收。

# 0.2.1 验证

- 三种 ABI 的 APK 编译、签名验证通过，版本号 0.2.1 / 5。
- JVM DNS 回归测试通过：IPv4、裸 IPv6、方括号 IPv6、重复规范化、非法地址。源文件为 `tests-host/DnsServersTest.java`，与 `src/org/intraportmap/android/DnsServers.java` 一起编译运行。
- 扩充设备测试：明文输入、运行中更换 Shadowsocks 端口及密码、旧端口关闭、新配置加密转发、端口映射运行中更换监听端口、两服务互不影响。测试代码编译通过；本次没有连接设备，尚未执行这些设备检查。
- 用户提供日志包含 Shadowsocks `failed to set nameservers` 后退出、映射 UDP 注册超时及 `from connect error`；IPv6 DNS 格式存在确定的 c-ares 兼容问题，本版修复该格式。日志不足以确认远端 UDP 与目标连接失败的具体原因。

# 本次构建验证

日期：2026-11-18

## 0.2.0 端口映射 + Shadowsocks 服务端（当前 APK）

- 固定 Shadowsocks 源码为 `ly911031/shadowsocks-libev` 的 `6c3d281d228c5f9de210015124e18e77e2f89c60`，依赖版本和归档 SHA-256 记录于 `prepare-shadowsocks.py`。
- ARM64、ARMv7、x86_64 的两个原生核心均编译成功；APK 包含六个对应的原生可执行文件。
- Java/DEX 构建、v2/v3 签名校验、ZIP 对齐、Git 差异空白检查通过。APK 内包含依赖许可证和源码版本记录。
- 在 Android 17 测试镜像直接启动 ss-server，正常完成加密初始化和 TCP/UDP 监听。
- 主机 JCE 独立客户端对 Android 原生 ss-server 的五种 AEAD 算法全部互操作通过：chacha20-ietf-poly1305、aes-128-gcm、aes-192-gcm、aes-256-gcm、xchacha20-ietf-poly1305。
- 每种算法验证 TCP 8192 字节回显及 UDP 32/1400/8192 字节回显，逐字节校验。UDP 使用 MTU 9000，经测试专用 TCP/UDP 桥传输，桥只封装数据报、不执行加解密；测试不依赖公网。
- 默认 MTU 1500 时大 UDP 包会受上游接收缓冲区限制，因此已在设置页暴露 MTU。上述大包结果仅对应显式 MTU 9000。
- 新增的双页面/独立服务 instrumentation 测试源码已编译，但因本地模拟器包管理器持续报告 `device is still booting`，尚未执行完毕；新版界面、服务启停隔离和长期保活仍需手机验收。
- 未进行公网连通性、域名查询或真实网络切换测试。
- APK：`../dist/intraportmap-shadowsocks-android.apk`，1,001,287 字节，versionCode=4，沿用之前的本地测试签名。
- SHA-256：`16EC211A9DA91DEF60E36DB20D1F1407547A2C46A26076DE927AD020C305859E`。

## 0.1.3 最近任务隐藏与文件日志历史记录

- 三架构原生编译、Java/DEX 打包、v2/v3 签名与 ZIP 对齐校验通过，versionCode=3，沿用之前的测试签名。
- Android 14 模拟器安装及冷启动通过。
- 独立 instrumentation 测试通过：最近任务隐藏/恢复标志生效；本地 TCP 服务正常启动并把 Java/原生输出写入文件；主动停止后停止事件写入文件；文件中没有 DEBUG 或构建机器路径。测试监听 `127.0.0.1:32120`，未连接公网。
- 独立 JVM 日志测试通过：UTF-8 持久化、重建读取、旧 DEBUG 过滤、文件大小/历史数量上限、导出时间顺序、多实例写入/导出并发、导出失败清理与后续继续写入。
- 检查三个 ABI 的原生二进制，`client_tunnel on_fail`、`tunnel from event` 等 DEBUG 专用字符串均已移除。
- 文件保存选择器和厂商最近任务界面的视觉表现未逐项验收；实际手机 Doze/省电下长期保活仍需设备实测。
- APK：512,817 字节。
- SHA-256：`FFAB594A2F4081C8935A6FE3D48555DAA486AB68523EC78139D5B89CE29406A2`。

## 0.1.2 保活增强包历史记录

- ARM64、ARMv7、x86_64 编译及 Java/DEX 打包通过。
- v2/v3 签名校验、ZIP 对齐检查通过；versionCode=2，与前包同一本地测试密钥，可覆盖升级。
- Manifest 已确认包含电池优化豁免、Wi-Fi 锁权限及 `stopWithTask=false`。
- 已添加系统 Intent 重投递恢复、原生核心退避重启、父进程死亡信号、通知停止按钮和保活设置入口。
- 当前没有连接的测试设备；Doze/省电/系统回收/主动停止行为尚未经过设备验收。验收步骤见 README。
- 当时 APK：521,009 字节。
- SHA-256：`DCD8A5917619146C47E5FE15D3E1614FEFA0482DF7058E920FCEA12BE5346E9D`。

## 0.1.1 初始包历史记录

- ARM64、ARMv7、x86_64 原生核心均已成功编译，静态链接 libevent 和 C++ 运行库。
- Java 编译、DEX 转换、APK 打包成功。
- APK Signature Scheme v2、v3 校验通过，ZIP 对齐检查通过。
- APK 最低 API 26，目标 API 32，包含三个 ABI。
- 模拟器原生测试确认客户端可经 Android 系统解析 `localhost`，并完成 TCP 控制通道连接和 UDP 映射注册。
- 完整 TCP/UDP 数据回环未通过：测试时模拟器尚未完成启动，网卡未配置地址，无法连接主机回显服务。
- APK 安装尝试返回 `device is still booting`，尚未验证安装后的界面、后台服务和手机实际映射行为。

初始 APK（已由 0.1.2 覆盖）：516,913 字节。

SHA-256：`9CE5267BB45E206B6A7AAEF707FC498F75EB35D997D65AC325B5D05DDFE38AAC`

本地测试密钥签名；不属于已完成设备验收的正式发布包。
