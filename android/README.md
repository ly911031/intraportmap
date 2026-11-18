# IntraPortMap Android

Android 8.0（API 26）及以上的独立应用，复用仓库中的 C++ / libevent TCP、UDP 核心。无需 root。

## 安装与使用

安装 `../dist/intraportmap-android.apk`。这是本地调试密钥签名的安装包，适合直接安装测试，未配置商店发布签名。

客户端配置示例：

| 配置 | 示例 |
| --- | --- |
| 模式 | 客户端 |
| 服务端 | `your-server.example:22120` |
| 公网映射地址 | `[::]:13389` |
| 内网目标地址 | `127.0.0.1:3389` |
| 协议 | TCP |

公网机器运行 `intraportmap -s [::]:22120`，然后在应用中启动。通信密钥和协议必须与服务端一致。`127.0.0.1` 表示 Android 手机本身，映射局域网中的电脑时请填写电脑的局域网 IP。

支持客户端、服务端、TCP、TCP + UDP、仅 UDP、重连间隔和 UDP 超时。运行日志中的连接信息才表示隧道状态，“运行中”只表示进程已启动。停止后可以修改配置重新启动。

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
