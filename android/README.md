# IntraPortMap Android

Android 5.0（API 21）及以上的独立应用，复用仓库中的 C++ / libevent TCP、UDP 核心。无需 root。Android 5 真机运行尚待验证。

0.2.5：Java 与原生库最低版本均降至 API 21，保留新版系统功能的版本分支。Android 5 使用旧版前台服务通知、网络回调与默认网络轮询；Android 6 起支持 Doze 豁免，Android 8 起使用通知渠道和新版前台启动接口。旧内核缺少 getrandom 时回退 /dev/urandom。启动图标包含 API21 兼容矢量资源。

0.2.4：统一浅色界面、圆角表单和蓝色操作按钮，更新自适应启动图标与通知图标。两个前台服务共用一条通知，通知内分别停止对应模块；任一模块停止后保留另一个模块的常驻通知。保存并启动后立即显示 Toast 和页面提示，SS 实际监听就绪后继续显示启动成功。

0.2.3：Shadowsocks DNS 留空时，仅在非空 DNS 地址集合发生变化后重启并读取新 DNS。仅网络/IP 变化、DNS 顺序变化、短暂断网后 DNS 相同均不重启。手动填写 DNS 时不注册网络变化监听，不因换网或 DNS 变化重启 SS。端口映射保留原来的网络恢复策略。

0.2.2 起：Shadowsocks 启动时保存全部配置（包括密码），重新打开应用会回填；原生 TCP/UDP 监听初始化完成才显示“启动成功”。

0.2.1：两个页面的密码、密钥输入框直接显示明文。服务运行时再次点击“保存并启动”，会结束并等待旧原生进程退出，再按当前页面配置启动；连续点击使用最新配置，自动恢复等待期间也可立即重新启动。两个模块互不影响。

修复自动读取 IPv6 DNS 后 Shadowsocks 报 `failed to set nameservers` 的问题：向内置 c-ares 传入带显式端口的 `[IPv6]:53`，并校验手动输入的 DNS 地址。

## 安装与使用

GitHub Actions：master 上推送 Android 目录或发布工作流改动、推送 `android-v*` 标签，以及手动运行，均会在主机回归、三 ABI 编译与签名验证成功后自动发布预发布 Release，附 APK 与 SHA-256 文件。非标签运行自动创建 `android-v版本-build.运行号.尝试号` 标签。按当前选择，每次 CI 独立生成测试签名，不上传本地私钥，因此不能覆盖安装本地版或其他 CI 签名版本。卸载前请备份配置和导出日志。

安装 `../dist/intraportmap-shadowsocks-android.apk`（`intraportmap-android.apk` 为相同内容的兼容文件名）。这是本地调试密钥签名的安装包，适合直接安装测试，未配置商店发布签名。

## 双页面与 Shadowsocks 服务端（0.2.0）

顶部“端口映射”和“Shadowsocks”分别配置两个模块，页面切换保留当前输入。两个模块可同时运行，拥有独立进程、保活锁和停止按钮，共用一条常驻通知。公共的电池优化、最近任务隐藏、日志导出入口位于端口映射页面，Shadowsocks 页面提供跳转按钮。点击通知可打开应用设置页面。

Shadowsocks 页面在手机上运行 **ss-server 服务端**，不是 SOCKS5 客户端，也不创建 Android VPN。填写监听地址、1024–65535 监听端口、密码、加密方式、TCP/UDP 模式和超时。密码与客户端一致，点击“保存并启动”时全部配置保存至应用私有偏好设置；系统服务恢复会重新投递启动 Intent 中的参数。

服务端集成自 [ly911031/shadowsocks-libev](https://github.com/ly911031/shadowsocks-libev)，固定 `slim-static` 分支提交 `6c3d281d228c5f9de210015124e18e77e2f89c60`。提供五种传统 AEAD：`chacha20-ietf-poly1305`、`aes-128-gcm`、`aes-192-gcm`、`aes-256-gcm`、`xchacha20-ietf-poly1305`。不提供 SS2022、VPN、客户端或 SIP003 插件设置。

局域网客户端使用手机 IP 和监听端口。若要借助端口映射从公网访问，例如：

1. Shadowsocks 页面监听 `127.0.0.1:8388`，设置密码/加密方式并启动。
2. 端口映射页面选客户端，填公网中转服务地址，公网映射地址例如 `[::]:18388`，内网目标 `127.0.0.1:8388`，然后启动。
3. Shadowsocks 客户端连接公网中转地址的 `18388`，使用相同密码/加密方式。需要 UDP 时，两模块以及公网中转服务都应启用 TCP + UDP，并放行相应 UDP 端口。

DNS 留空时，ss-server 启动/重启时读取当前网络提供的 DNS 服务器 IP，也可指定 IP（多个逗号分隔）。它通过 c-ares 发起普通 DNS 查询，不等同于 Android 系统 Private DNS。网络没有提供 DNS 时需手动填写。自动 DNS 地址集合变化后重启 SS，现有连接和 UDP 会话会清除，客户端需重连。手动指定 DNS 时不检测网络 DNS、不因网络变化重启；用户主动点击启动及进程异常退出后的恢复仍保留。

换网时建议使用默认 `0.0.0.0` IPv4 通配监听或 `::` IPv6 通配监听；固定某个旧网卡 IP 可能导致换网后绑定失败。局域网客户端需使用手机的新 IP，移动网络上的外部直连仍取决于运营商是否提供可达地址，也可沿用端口映射入口。

当前映射核心 TCP keepalive 是空闲 40 秒、探测间隔 20 秒、5 次失败；UDP 心跳空闲 15 秒、未应答时每 2 秒重试。网络切换主动重建，不等待这些超时；本版未改动协议心跳间隔。

UDP MTU 默认 1500，沿用上游的缓冲区约束。超过接收缓冲区的数据报可能被截断并拒绝；需要更大的 UDP 包时应同时调整服务端和客户端 MTU，并确认网络支持相应的分片/传输。大包回环测试使用 MTU 9000，不表示默认 MTU 可以接收 8192 字节的 UDP 载荷。

源码准备：先运行 `python android/prepare-shadowsocks.py`，再运行 `./android/build.ps1`。源码与依赖缓存到 `.build-tools/ss-deps`；全部固定提交/版本并校验 SHA-256。依赖为上游选用的 libev 4.33、PCRE 8.45、c-ares 1.18.1 和固定提交的 libcork/libipset/libbloom；AWS-LC 裁剪代码随该仓库提供。构建使用自有 Android CMake 适配，不修改下载源码。许可证及源码版本记录随 APK 打包，脚本可重新获取对应源码。

合并设备测试：`./android/test-device.ps1 -Serial <测试模拟器序列号> -Combined`。测试通过 Android JCE 独立实现 SIP004 帧格式，检查五种 AEAD 的加密 TCP、UDP 回环，以及两个模块互不影响的启停；只在独立测试模拟器运行。

无界面核心互操作测试：`python android/test-shadowsocks-native.py <adb路径> <模拟器序列号> <JDK目录>`，使用主机 JCE 独立加密客户端、ADB 转发和测试专用 UDP 桥验证 Android 原生核心的五种算法。辅助桥仅用于测试，不进入交付 APK。所有回环均使用本地测试端口。

客户端配置示例：

| 配置 | 示例 |
| --- | --- |
| 模式 | 客户端 |
| 服务端 | `your-server.example:22120` |
| 公网映射地址 | `[::]:13389` |
| 内网目标地址 | `127.0.0.1:3389` |
| 协议 | TCP |

公网机器运行 `intraportmap -s [::]:22120`，然后在应用中启动。通信密钥和协议必须与服务端一致。`127.0.0.1` 表示 Android 手机本身，映射局域网中的电脑时请填写电脑的局域网 IP。

支持客户端、服务端、TCP、TCP + UDP、仅 UDP、重连间隔和 UDP 超时。运行日志中的连接信息才表示隧道状态，“运行中”只表示服务已启动。修改配置后可直接再次点击启动。

### 最近任务与日志（0.1.3）

“后台保活”中的“在最近任务中隐藏这个应用”可随时开启或关闭，设置会保存。开启后应用不出现在系统最近任务列表，可从桌面图标或常驻通知重新打开；开关不会停止映射。它能减少误清理，不会提高进程优先级，也不能保证不被系统或厂商杀死。

运行日志自动追加到应用私有目录 `files/logs/intraportmap.log`，通常完整路径为 `/data/user/0/org.intraportmap.android/files/logs/intraportmap.log`。文件管理器通常不能直接读取该目录，请使用“导出日志文件”选择保存位置。导出会按时间顺序合并当前与历史日志，不需要额外的存储权限。应用界面不再保留或刷新内存日志。

Android 原生核心在编译时关闭 DEBUG 日志，保留 INFO/WARN/ERROR，并去掉每条日志中的构建机器源码路径。每份日志最多 2 MB，保留当前文件和 3 份历史文件，自动轮转。每条写入后关闭文件，减少进程被杀时丢失缓冲日志的风险；这不是掉电后的磁盘持久性保证。卸载应用或清除应用数据会删除私有日志，需提前导出。

日志组件测试：先用 JDK 编译 `android/src/org/intraportmap/android/LogStore.java` 和 `android/tests/org/intraportmap/android/LogStoreTest.java` 到测试输出目录，再运行 `org.intraportmap.android.LogStoreTest <可写测试目录>`。覆盖 UTF-8、重建后读取、DEBUG 过滤、轮转大小、导出顺序、多实例并发及失败清理。

设备测试：先安装当前 APK，再运行 `./android/test-device.ps1 -Serial <测试模拟器序列号>`。独立测试 APK 会切换最近任务隐藏标志，启动本地 `127.0.0.1:32120` 映射服务验证文件日志，并主动停止；请只在没有正在使用的映射配置的测试模拟器上运行。Android 14 模拟器已通过这些检查。

应用通过前台服务和常驻通知保持运行，映射期间持有 CPU 部分唤醒锁和 Wi-Fi 高性能锁，停止时释放；耗电会增加。Wi-Fi 锁的实际效果取决于 Android 版本、硬件和系统策略，不能开启已关闭的 Wi-Fi，也不能替代电池优化豁免。

在界面“后台保活”中点击“允许忽略电池优化”，在系统对话框中授权。界面显示当前豁免、省电和深度休眠状态。再通过“打开应用设置”按手机提供的选项允许电池无限制/后台运行。小米、华为、OPPO、vivo 等厂商可能还需要允许自启动、锁定最近任务。入口和实际行为随系统版本不同。

仅有前台服务和唤醒锁不足以保证 Doze 下持续联网；Android 的电池优化豁免允许应用在 Doze 中使用网络和部分唤醒锁，但仍不是永不被杀的保证。参见 [Android Doze 文档](https://developer.android.com/training/monitoring-device-state/doze-standby)。

服务使用 `START_REDELIVER_INTENT` 请求系统在回收进程后重新投递原启动参数；恢复时机由系统决定。原生核心异常退出后按 5、10、20、40、60 秒退避重启，正常运行一分钟后重置退避。可从应用或通知中停止，不会在用户停止后自行恢复。移除最近任务默认保留服务；厂商清理仍可能终止它。

应用不会开机自启；强行停止或重启手机后需手动启动。密钥不写入应用偏好设置或日志；为支持服务恢复，系统会持有并重新投递包含密钥的启动 Intent。原生子进程设置父进程死亡信号，服务进程被杀时同步结束，避免恢复时残留重复隧道。

## 保活验收

目前已通过三架构编译、Java/DEX 打包、签名与对齐检查。尚未完成手机休眠或厂商省电策略的设备验收。建议在目标手机上检查：

1. 建立真实 TCP/UDP 映射后授权电池优化豁免，熄屏静置并开启系统省电模式，从远端持续探测转发。
2. 通过 ADB 执行 `dumpsys battery unplug`、`dumpsys deviceidle force-idle`，检查远端 TCP/UDP 及唤醒后重连；结束后执行 `dumpsys deviceidle unforce`、`dumpsys battery reset` 恢复测试状态。
3. 模拟非强行停止的服务进程回收，检查系统重新投递配置和核心恢复；不要用“强行停止”来验证系统回收恢复，它应当停止应用。
4. 结束原生子进程，检查日志中的退避重启；随后从界面或通知主动停止，确认锁释放、通知消失、子进程退出且不再重启。

## 构建

运行 `./android/build.ps1`。无需 Gradle 或 Maven，使用 SDK 自带的 aapt、javac、d8、zipalign 和 apksigner 打包。

默认依赖目录：

- Android SDK：`$env:LOCALAPPDATA/Android/Sdk`，platforms/android-36、build-tools/36.1.0
- JDK：Android Studio 的 JBR，可通过 `-Java` 指定
- NDK r27d：`.build-tools/android-ndk-r27d`
- libevent 2.1.12：`.build-tools/libevent-2.1.12-stable`
- CMake 3.31.6：`.build-tools/python/cmake/data/bin/cmake.exe`
- Ninja：`.build-tools/python/bin/ninja.exe`

路径均可通过脚本参数覆盖。CMake 与 Ninja 可以用 `python -m pip install --target .build-tools/python cmake==3.31.6 ninja` 安装。NDK 与 libevent 需下载官方发行版并解压到以上目录。

默认包含 `arm64-v8a`、`armeabi-v7a` 和 `x86_64`。例如仅构建 ARM64：`./android/build.ps1 -Abis arm64-v8a`。

原生核心作为 PIE 可执行文件打包为 `lib/<ABI>/libintraportmap.so`，由系统解压至只读的 nativeLibraryDir，再由服务启动独立子进程。停止服务时终止子进程，由系统关闭套接字并回收进程资源。适配代码位于 Android 工程；`src/slog.h` 的新增分支仅改变 Android 的日志，不改变桌面平台行为。

Android 域名解析适配调用 Bionic 系统解析器，支持当前网络和 Private DNS；每次重连重新解析。该解析在核心事件线程同步完成，解析超时时可能短暂延迟其他网络事件。未启用可选的 `-G` UDP 批处理。

当前 target SDK 为 32，用于本地安装测试；上架前需升级目标 SDK、适配对应前台服务和通知规则，并配置正式签名。应用和原生依赖沿用各自开源许可证；再分发时应同时提供相应源码和许可证。
