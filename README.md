# SecureLedger｜本地加密记账

SecureLedger 是使用 Kotlin / Jetpack Compose 开发的 Android 记账项目，支持手动记账、支付信息辅助识别、本地 OCR、离线共享账本和多人 AA 会签。识别结果先由用户核对，不直接操作付款或自动保存账目；共享数据由用户主动通过文件或二维码交换，不依赖项目自建服务器。

**版本：1.0.0 · Android 8.0+（API 26）· Kotlin / Jetpack Compose · Apache-2.0**

[项目仓库](https://github.com/FleetingPhantomw/SecureLedger) · [构建与运行](#获取构建与运行) · [使用说明](#使用说明) · [许可证](LICENSE)

## 目录

- [功能特性](#功能特性)
- [技术栈与环境](#技术栈与环境)
- [获取、构建与运行](#获取构建与运行)
- [使用说明](#使用说明)
- [交易信息识别与 OCR](#交易信息识别与-ocr)
- [数据库、加密与密钥管理](#数据库加密与密钥管理)
- [Android 权限与隐私](#android-权限与隐私)
- [架构与目录](#架构与目录)
- [构建与测试](#构建与测试)
- [使用边界](#使用边界)
- [仓库与数据管理](#仓库与数据管理)
- [问题反馈](#问题反馈)
- [许可证](#许可证)

## 功能特性

| 功能 | 说明 |
| --- | --- |
| 日常记账 | 收入、支出、转账；金额、商户、时间、分类、支付方式、标签、备注与凭证引用；金额使用整数分 |
| 分类与银行卡 | 固定八类消费分类，支付宝、微信和银行卡支付；银行卡保存银行、别名及后四位，支持编辑、停用和删除 |
| 账目与图表 | 按月份、账本筛选，按月 / 周 / 日分组；月收支、结余、每日支出柱状图和分类占比环图，转账不重复计收支 |
| 可审计更正 | 追加金额更正及原因，保留原始签名记录和更正历史 |
| 消费辅助识别 | 所选应用的无障碍页面规则，可选微信 / 支付宝付款通知识别；候选融合、悬浮窗 / 通知提醒，核对后保存 |
| 本地 OCR | 相册选图、拍摄票据、外部图片分享及主动读取剪贴板文本，生成可编辑账目草稿 |
| 购物列表补录 | 一张截图识别多笔独立订单，勾选批量保存；同名商品不合并，缺准确时间时标注估计时间 |
| 银行流水导入 | 一张支持布局的截图解析多笔，外部流水加密保存；疑似重复单列，用户选择保留、忽略或补记 |
| 备份与换机 | 独立密码保护的 `.sldb` 备份，向空保险库恢复身份与账本 |
| 共享账本 | 接收方绑定邀请、签名同步、验证后事务合并及重放拒绝，通过文件或二维码离线交换 |
| 多人 AA | 选择本次账单及成员，展示已付、应承担和补差，支持独立会签，不执行资金转账 |
| 二维码 | 邀请、同步、AA 统一竖屏扫描，支持相册选图与同步分片 |
| 主题外观 | 跟随系统、浅色、纯黑模式，以及预设主题色和六位 HEX 自定义 |

## 技术栈与环境

版本依据根目录、`app/` 和 `ppocr-sdk/` 下的 Gradle 配置。

| 项目 | 配置 |
| --- | --- |
| 语言、UI | Kotlin 2.0.21，Compose 编译插件 2.0.21，Compose BOM 2024.09.03，Material 3 |
| 构建工具 | Gradle Wrapper 8.7，Android Gradle Plugin 8.6.1，KSP 2.0.21-1.0.28 |
| Java 环境 | Java / Kotlin JVM 目标 17；已有构建使用 Temurin JDK 17.0.20.1+1 |
| Android SDK | `minSdk = 26`，`compileSdk = 35`，`targetSdk = 35` |
| 数据、异步 | Room 2.6.1，Coroutines 1.9.0，kotlinx.serialization 1.7.3 |
| 安全组件 | Android JCA / Keystore，Bouncy Castle 1.78.1 |
| 图片、扫码 | CameraX 1.5.3，ZXing Android Embedded 4.3.0 |
| OCR | 本地 `:ppocr-sdk`，PP-OCRv6_small ONNX 模型，ONNX Runtime Android 1.21.1，OpenCV Android 4.9.0 |
| 测试 | JUnit 4.13.2，AndroidX Test、Espresso、Compose UI Test；具体版本见模块配置 |

使用 Android Studio 打开工程，Gradle JDK 选择 JDK 17。使用 IntelliJ IDEA 时，需要安装与当前 IDE 兼容的 Android 支持并配置 SDK。构建使用仓库提供的 Gradle Wrapper。

Gradle 未显式固定 `buildToolsVersion`，由 AGP 选择所需工具；需安装 Android SDK Platform 35、相应 Build Tools 和 Platform Tools。可选 Windows Release 脚本另依赖项目本地 Build Tools 35.0.0。随包 OCR 模型和原生运行时使安装包较大，目前未配置按 ABI 拆分 APK。

## 获取、构建与运行

### 打开与配置

```powershell
git clone https://github.com/FleetingPhantomw/SecureLedger.git
cd SecureLedger
```

1. 获取完整源码，保留 `app/`、`ppocr-sdk/` 与模型资产。在 Android Studio 中选择 **Open**，打开含 `settings.gradle.kts` 的根目录，而不是单独打开 `app/`。
2. 为 Gradle 选择 JDK 17；命令行构建时将 `JAVA_HOME` 指向自己的 JDK 17。
3. 安装上述 SDK 工具，让 IDE 生成本机 `local.properties`，或自行配置 `sdk.dir`。不要复制他人的绝对路径或提交该文件。
4. 执行 Gradle Sync。首次构建需访问 Wrapper 分发地址、Google Maven、Maven Central、Gradle Plugin Portal；仅依赖已缓存时使用 `--offline`。
5. 启动 Android 8.0 或更高版本设备 / AVD。测试环境包括 API 35 模拟器和 Android 16 / API 36 手机。

### Debug 构建与安装

在项目根目录的 PowerShell 执行：

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat :app:assembleDebug
adb devices -l
```

将 `DEVICE_SERIAL` 替换为实际设备序列号；`adb` 需位于 PATH，或改用本机 Platform Tools 的可执行文件。

```powershell
adb -s DEVICE_SERIAL install -r app\build\outputs\apk\debug\app-debug.apk
adb -s DEVICE_SERIAL shell am start -n com.secureledger/.MainActivity
```

也可在 Android Studio 选择 `app` 和目标设备执行 **Run**。Debug 输出是 `app/build/outputs/apk/debug/app-debug.apk`，应用 ID 是 `com.secureledger`。

`install -r` 只能覆盖应用 ID 与签名证书兼容的安装。Debug 与本地 Release 使用不同签名时不能互相覆盖；出现“安装包无效 / 不兼容”应查看具体安装错误，**不要直接卸载真实账本**。卸载、清除数据或删除 Keystore 可能使数据无法恢复。

### Release 签名

Gradle 从以下四个环境变量读取配置，配置时须全部提供：`SECURELEDGER_RELEASE_KEYSTORE`、`SECURELEDGER_RELEASE_STORE_PASSWORD`、`SECURELEDGER_RELEASE_KEY_ALIAS`、`SECURELEDGER_RELEASE_KEY_PASSWORD`。全部未提供时，不会自动获得可分发的签名 Release 包。

Windows 自动签名脚本使用项目本地 `.jdk17/`、`.android-sdk/` 工具布局，需先配置相应目录：

```powershell
.\scripts\build-release.ps1
```

它在被忽略的 `.signing/` 中生成或复用 RSA-4096 APK 签名密钥，以随机密码保护，用 Windows DPAPI 保存密码封装，并调用 `apksigner` 验证。RSA 用于 **APK 签名**，不是账目加密。输出为 `app/build/outputs/apk/release/app-release.apk`；密钥与密码须分别备份，DPAPI 文件不能单独跨 Windows 账户 / 系统恢复。密钥保管和已有安装的签名兼容说明见 [签名说明](docs/RELEASE_SIGNING.md)。

## 使用说明

### 首次启动、记账与查看

1. 首次启动选择“创建本地账本”，输入并确认至少 8 位密码，点击“创建并进入”。后续用该密码解锁，无需网络账户；应用不提供预置通用密码。
2. 首页点击“记一笔”，选择支出 / 收入 / 转账及账本，填写金额、商户、分类、实际交易时间、支付方式、标签、备注后保存。
3. 使用银行卡前，在“设置 → 银行卡”添加银行名称、可选别名和后四位；不接收完整卡号。历史账目保留保存时的显示快照。
4. 最多添加 5 个图片 / PDF 凭证。目前保存本机授权 URI 引用，不把原附件复制为可跨设备恢复的加密文件。
5. 在“账本”切换月份和账本，选择月 / 周 / 日分组及图表。点击记录查看详情；“更正此记录”仅追加金额更正及原因，不删除或覆盖原记录。
6. “设置 → 数据与安全 → 验证账本完整性”检查签名、哈希链与解密认证；“立即锁定”返回解锁界面。

### 开启与关闭消费识别

1. 在“设置 → 自动识别消费”开启开关并勾选应用。默认未开启，也未选择应用。
2. 点击“打开系统无障碍设置”并启用 SecureLedger 消费识别；按需授予悬浮窗和通知权限。
3. 如需微信 / 支付宝通知补充识别，再开启“支付通知辅助识别”并单独授予“通知读取”。显示通知与读取通知不是同一权限。
4. 在所选应用正常支付，满足规则后出现应用内面板、悬浮窗或通知。用户选择“忽略”或“记一笔”；后者仍需解锁、核对并保存。
5. 没有提醒时可截图或手动补录。停止识别可关闭应用内开关、取消应用选择，或在系统中关闭无障碍 / 通知读取；悬浮窗和通知授权也可独立撤销。

提醒不是支付凭证，软件不自动付款，也不将候选静默写入账本。

### 图片、购物列表与银行流水

- **单笔：** 首页“识别截图”进入“图片识别”，使用“选择截图”“拍摄票据”；也可主动分享图片给应用或点击“从剪贴板文本识别”。核对草稿、补齐单笔所需时间后“确认并加密记账”。
- **购物：** 选择“选择购物订单列表（逐笔确认）”，勾选识别出的订单并点击“确认批量记账”，单次最多 100 笔，事务写入。相同商品 / 商户名仍是独立订单。时间留空时使用导入时刻并加入 `time-estimated` 标记，以区分真实付款时间。
- **银行：** 选择“选择银行流水截图（多笔识别）”，逐笔修改金额、方向、日期、摘要后勾选导入，单批最多 500 笔。先保存为加密外部流水，**不会直接增加正式账目**。
- **核对：** 点击“查看导入流水及重复项”，在“历史导入记录”查看全部、漏记、冲突和重复。疑似重复由用户保留或忽略；保留不自动补记，需另行确认补记。不会自动删除已签名账目。

“多笔”是一张截图中的多条记录，不是一次选择多张图片；模糊、缺日期或布局变化会导致误识别或漏识别。

### 共享项目、同步与 AA

1. 创建者在“项目”新建项目；接收者在“加入多人项目”生成加入请求，以二维码或文本交给创建者。
2. 创建者在“成员与邀请”核对请求并生成绑定接收者的邀请，接收者扫描 / 粘贴后验证加入。
3. 记账时选择共同项目；在“同步”选账本并“分享同步文件”，对方打开后确认导入。双方各有新增时各分享一次，二维码 / 粘贴为替代方式，不是自动联网同步。
4. 扫码页固定竖屏，可“从相册选择”。分片同步需逐张扫描 / 选图直至拼装完成，识别一张不一定完成整个同步。
5. 在项目“AA 结算”选择本次支出和成员，核对已付、应承担、补差后保存、分享会签文件。默认以账目作者作为付款人，整数分等额分摊，余数按稳定成员编号排序分配；代付 / 非等额可核对高级手动方案。
6. 对方先同步账本，再独立确认并返回会签包，创建者合并签名。打开文件仅进入核对流程，不自动同意，也不执行转账。

### 加密备份与恢复

“设置 → 加密备份与换机”输入并确认独立的、至少 10 位备份密码，点击“创建加密备份”，通过系统选择器保存 `.sldb`。备份包含身份密钥材料、账目、项目、结算、导入流水、银行卡及重放状态；外观 / 识别偏好和凭证原文件不在备份载荷中。

恢复仅允许在未创建保险库、数据库为空的首次启动界面：点击“从加密备份恢复”，填写新保险库密码及确认密码、原备份密码，再选 `.sldb`。没有覆盖或合并现有保险库的恢复入口。忘记本机密码不能绕过解锁，需备份文件和备份密码；没有可用副本或密码时无法恢复。请在长期使用前演练恢复，不要为试验而清除唯一数据副本。

## 交易信息识别与 OCR

### 页面与通知的实际范围

`PaymentAccessibilityService` 响应窗口状态 / 内容变化，提取所选包的当前窗口文字。为检测退出受保护页面，服务也接收其他窗口的切换事件，但不提取未选应用文字。`AccessibilityTextExtractor` 读取节点文本与内容描述，跳过密码节点及 `EditText`，限制遍历节点数和字符量；不是持续截屏 OCR。

页面预置规则包括支付宝、微信、淘宝、京东、拼多多的包名、付款成功等正向关键词、负向噪声与金额正则。`TransactionRuleEngine` 结合相邻文字提取金额、商户、渠道和可用时间；多金额订单列表不会直接当成一笔付款提醒。服务还检查规则启用状态、置信度、内容变化及冷却时间。可编辑规则和添加包名，但添加包名不等于完成页面适配。

`PaymentNotificationParser` 仅处理所选微信 / 支付宝的付款通知，排除汇总、退款、到账、验证码及失败 / 待支付等情况，多个不一致金额不会强行取值。`RecognitionFusion` 在短窗口内融合页面与通知来源，不能保证同额消费永不误判。双来源思路参考 Biller-Android；页面规则、解析与融合由本项目独立实现，未包含该项目源码或运行依赖，见 [第三方说明](THIRD_PARTY_LICENSES.md)。

提醒使用应用内面板、悬浮窗或通知。支付保护可能使悬浮窗不可见；当前仅在窗口变化后做有时限的单次延后重试，不持续轮询或绕过保护。状态通知是服务观察到的连接状态，不保证进程永久运行。

### OCR 调用链与布局限制

`PaddleOcrEngine` 用 `PaddleOCR.create()` 创建并复用会话，调用 `recognize(bytes)`；SDK 的 `ORTSessionManager` 从 assets 加载检测 / 识别 ONNX 模型并执行 `OrtSession.run()`，配合 OpenCV 处理图像，输出文字、置信度、位置框，再交给业务解析器。初始化与推理在后台执行，以互斥锁串行复用会话；读取图片限制为 20 MiB。提供预热接口和耗时日志，识别速度取决于设备、图片大小及内容。

模型位于 `ppocr-sdk/src/main/assets/models/`，含检测、识别模型与字符配置。来源声明、上游固定提交和模型 SHA-256 见 [OCR 模块说明](ppocr-sdk/README.md)，第三方许可证见 [ppocr-sdk/LICENSE](ppocr-sdk/LICENSE)。运行时不需要云 OCR 或下载模型，构建依赖仍可能需要网络。

- `OrderParser` 解析单笔金额、商户、时间、订单号和支付拆分，明显列表需改用专用入口。用户确认后，原文摘要可随加密备注保存。
- `ShoppingOrderParser` 按页面关键词、状态行及坐标分段，覆盖现有淘宝 / 京东 / 拼多多示例布局。淘宝识别出的卡片明确关闭 / 取消时排除，退款状态保留并提示核对；拼多多明确退款成功卡片也被排除。只有应付款或商品价时会警告，不能当作实际已支付证明；缺状态、金额、商户仍可能漏识别。
- `StatementParser` 对现有工行、建行（含一类信用卡布局）、中行、中信示例有分支，并非所有银行通用。已接纳的金额中负号为支出，其余按收入预设；无符号金额目前仅在部分建行 / 中行布局接纳，部分其他布局仍过滤。缺时间时可能推断年月或取中午并提示核对，无法获得可用日期时可能跳过。
- `BusinessFingerprint` / `ReconciliationMatcher` 提供银行流水相似与对账线索，不证明一定重复；购物批量不因商品同名去重。

二维码相册使用独立 `QrImageDecoder` / ZXing，不经过 PaddleOCR；后台解码，图片最长边限制为 2048 像素，再交原同步 / 邀请 / 会签流程校验。

## 数据库、加密与密钥管理

### 数据库与加密范围

`AppDatabase` 使用 **Room 2.6.1 / SQLite**，数据库名 `secureledger.db`，schema **7**，包含记录、项目、成员、项目密钥、结算、导入流水、导入批次、重放标记及银行卡表。代码显式注册 1→2→3→4→5→6→7 迁移，不以破坏性重建作为升级兜底。

**不是 SQLCipher 或整库加密。** 金额、商户、备注、账户、标签、支付拆分和凭证 URI 等账目载荷在持久化前加密；项目密钥、结算证明及外部流水内容也有加密封装。数据库仍可见项目 / 成员名称与公钥、银行卡名称 / 别名 / 后四位、记录类型与时间、项目 / 作者标识、序号、哈希、签名、导入状态等元数据。

### 算法与密钥生命周期

| 用途 | 实际实现 |
| --- | --- |
| 载荷认证加密 | `AesGcmManager`：`AES/GCM/NoPadding`，256 位密钥，96 位随机 nonce，128 位标签，绑定 AAD；`doFinal()` 验证标签 |
| 密码派生 | `PasswordKdf`：Argon2id，64 MiB、3 轮、并行度 1、32 字节输出；创建保险库使用 16 字节随机盐 |
| 本机保护 | 随机 32 字节主密钥由密码派生密钥加密，密码封套再用 Android Keystore AES 密钥包装，封装结果存应用私有 SharedPreferences |
| 身份签名 | `Ed25519Manager`：32 字节私钥种子，由主密钥加密保存，用于账目及相关协议签名 |
| 项目密钥 | 多人项目各有随机 32 字节 AES 密钥，本机由主密钥加密保存；个人账本使用主密钥 |
| 邀请分发 | `X25519Manager`：X25519 协商、HKDF-SHA256 派生包装密钥，针对接收者封装项目密钥；协商私钥由主密钥加密保存 |
| 完整性与合并 | 规范化、SHA-256、项目内作者哈希链、Ed25519、确定性 Merkle 根；验证签名、链、冲突、密钥可用性及重放状态后事务合并 |
| 可迁移备份 | `PortableBackupCodec`：独立备份密码经 Argon2id 派生后 AES-GCM 加密，封套签名；加密载荷内包含恢复所需身份和主密钥材料 |

本地解锁使用应用密码流程，Keystore 不要求每次生物识别授权；其硬件保护能力取决于设备。AES-GCM 每次由 `SecureRandom` 生成新 nonce。

签名与哈希链用于发现修改和验证协议身份，不证明现实消费，也不对已完全受控设备作绝对保证。原账目不覆写，更正是新签名记录；成员撤销后的历史密钥轮换不完整。

`SL1:` 同步包中的账目载荷为密文，包元数据可见；`SLS1.` AA 会签编码含可读分摊金额，**是签名数据而非加密传输格式**。Base64 不等于加密，仅向相关成员分享。直接复制数据库或 Keystore 包装数据不能代替 `.sldb` 跨设备恢复。

## Android 权限与隐私

| 权限 / 系统授权 | 用途 | 必要性 |
| --- | --- | --- |
| `CAMERA` | CameraX 拍票据、ZXing 相机扫码；相机硬件非必需 | 手动记账和选图不需要 |
| `POST_NOTIFICATIONS` | Android 13+ 服务状态与候选提醒 | 按需；不同于通知读取 |
| `SYSTEM_ALERT_WINDOW` | 在其他应用上显示“忽略 / 记一笔” | 悬浮窗模式需要 |
| 无障碍授权 | 系统通过 `BIND_ACCESSIBILITY_SERVICE` 绑定服务，读取所选应用可访问文字 | 页面识别需要，非普通运行时权限 |
| 通知读取授权 | 系统通过 `BIND_NOTIFICATION_LISTENER_SERVICE` 绑定可选服务，读取范围内标题和内容 | 通知识别需要，默认关闭 |
| 文件 / 照片选择器 URI 授权 | 用户选图、选凭证、保存 / 读取备份和同步文件 | 按用户选择的 URI 范围访问 |

应用主清单不声明 `INTERNET`、`READ_MEDIA_IMAGES` 或全盘读写权限，业务不接入云 OCR、遥测或自动上传服务。OCR、规则与数据库处理在本机执行；用户选择外部分享目标或云文件提供方后，传输与存储遵循相应服务的规则。

无障碍完整页面、完整通知及候选指纹没有对应持久化路径，识别配置与候选内存状态分开处理。仍需注意：

- 拍照生成的 `ocr_*.jpg` 在应用私有缓存中，当前没有识别后立即加密或删除机制，不保证即时擦除。
- 凭证原文件仍在所选提供方，备份 / 同步只带引用，换机可能不可访问。
- 通知 / 悬浮窗可能显示金额与商户，应核对系统锁屏设置。
- 主动复制邀请 / 同步文本会进入系统剪贴板；日志含解锁、加载和推理耗时等诊断信息，反馈前仍需脱敏。
- `allowBackup=false`、`fullBackupContent=false` 禁用声明的系统备份，自主 `.sldb` 文件仍由用户保管。

## 架构与目录

界面经 ViewModel / Repository 调用业务，纯规则与协议和 Android 集成分开。项目实现业务解析、候选融合、有效账目视图、统计、分摊及协议组合；UI、数据库、OCR 引擎与密码原语使用成熟框架和第三方库。见 [架构说明](docs/ARCHITECTURE.md)。

```text
.
├── app/                         Android 应用
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/secureledger/
│       │   │   ├── app/          主导航、账目页面、LedgerViewModel
│       │   │   ├── feature/      解锁、账本、OCR、核对、项目、设置
│       │   │   ├── design/       Compose 主题与外观偏好
│       │   │   ├── core/         crypto、ledger、ocr、accessibility、
│       │   │   │                 importer、protocol、merkle、settlement、backup
│       │   │   ├── data/         Room database、entity、dao、repository
│       │   │   └── integration/  无障碍、通知、OCR、二维码、文件分享
│       │   └── res/              扫码布局、主题、服务资源
│       ├── test/                 JVM 规则、密码 / 协议、解析和统计测试
│       └── androidTest/          Keystore、Room、OCR、扫码与 UI 集成测试
├── ppocr-sdk/
│   ├── src/main/assets/models/   ONNX 检测 / 识别模型、字符配置
│   ├── src/main/java/            SDK 引擎、预处理与后处理
│   ├── README.md                 来源、上游提交、模型校验值
│   └── LICENSE                   第三方 SDK 的 Apache-2.0 文本
├── docs/                         架构、测试、性能、Release 签名说明
├── scripts/                      构建、模拟器及双设备验证脚本
├── gradle/wrapper/               Wrapper 配置与工具
├── build.gradle.kts              插件版本
├── settings.gradle.kts           模块和依赖源
├── LICENSE                       项目 Apache-2.0 许可证
├── NOTICE                        项目归属与第三方声明
├── THIRD_PARTY_LICENSES.md        第三方清单与设计参考边界
└── README.md
```

本地 SDK / JDK、签名材料、交易示例、课程报告与模板不是源码分发必需模块；课程材料继续在 Git 外管理，不为复现目录树而提交个人或生成数据。

## 构建与测试

### 可复现命令

构建与 JVM 测试无需设备：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

在自控测试设备上替换 `DEVICE_SERIAL` 后执行；避免对唯一真实账本设备运行不熟悉的场景脚本。

```powershell
adb -s DEVICE_SERIAL install -r app\build\outputs\apk\debug\app-debug.apk
adb -s DEVICE_SERIAL install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s DEVICE_SERIAL shell pm grant com.secureledger android.permission.CAMERA
adb -s DEVICE_SERIAL shell am instrument -w -r com.secureledger.test/androidx.test.runner.AndroidJUnitRunner
```

仅执行竖屏扫码 / 相册回传时，将最后一条替换为：

```powershell
adb -s DEVICE_SERIAL shell am instrument -w -r -e class com.secureledger.QrScannerDeviceTest com.secureledger.test/androidx.test.runner.AndroidJUnitRunner
```

`scripts/test-emulator.ps1` 可构建及执行套件，但依赖其本机 SDK / JDK 目录和 AVD 约定。核对 `-AvdName`、`-DeviceSerial`，不要把默认序列号当成当前设备。常规用例主要使用隔离密钥命名空间和测试数据库；扫码用例临时创建一张合成 MediaStore 图片并清理对应 URI。私人截图基准与双设备 AA 默认跳过，**跳过不计通过**。

`scripts/test-two-device-aa.ps1` 需显式提供两设备序列号和运行时密码，会在真实保险库添加标记测试项目、账目和交换文件，不是零副作用测试；勿在日志、命令历史或 Git 中留下密码。私人交易示例不是运行公开测试的前提。

### 验证记录

最近记录的验证日期为 **2026-10-07**：

| 范围 | 环境与结果 |
| --- | --- |
| JVM 单元测试 | 45 项，无失败 / 错误，覆盖密码与协议、解析、统计等核心逻辑 |
| Android 集成测试 | API 35 / x86_64 模拟器及 Android 16 / API 36 / arm64 手机，各执行 17 项；2 项可选场景默认跳过 |
| 双设备 AA | 请求、邀请、加入与同步、结算、独立确认、合并六阶段场景通过 |
| 竖屏扫码与相册 | API 35 模拟器定向用例通过，覆盖竖屏、相机预览、方形扫描框、选图入口、真实 URI 解码及结果返回 |

扫码测试通过监视器提供选图结果，验证应用的选图回传与解码链路；双设备场景通过 ADB 搬运交换文件。测试结果限定于各自的构建和环境，不代表所有厂商页面、分享面板或相机条件。完整执行范围、异常记录与性能采样见 [测试说明](docs/TESTING.md) 和 [性能说明](docs/PERFORMANCE.md)。

## 使用边界

- 识别取决于第三方页面、通知文案、图片质量、权限及系统后台策略；支付保护可能限制悬浮窗，未出现提醒时可截图或手动补录。
- OCR 结果需核对。应付款不证明付款完成，无符号金额不是所有银行页面的通用规则；估计时间会明确标注。
- 正式记录采用追加审计模型，仅支持金额更正，不提供直接删除、全文搜索或任意字段覆盖编辑；不提供 CSV、自定义分类管理或云同步入口。
- 凭证原文件及外观 / 识别偏好不随备份完整迁移；已分发给共享成员的历史密钥不会被远程撤销。
- 部分厂商系统可能把纯黑背景渲染为灰色；模型及原生运行时随包分发，安装包体积较大。

## 仓库与数据管理

仓库仅管理程序源码、必要模型、构建配置与开发文档。`.gitignore` 排除本地 SDK / JDK、IDE 与构建缓存、签名材料、环境配置、数据库及其辅助文件、备份 / 同步包、日志与 APK / AAB。个人交易示例、课程报告、模板及报告工具保持在 Git 外，运行密码与个人账单不纳入版本管理。

OCR 模型属于运行资产，其来源和校验值随模块保存。`.gitignore` 不会追溯删除已经跟踪的文件；若密钥或 Token 已泄露，应撤销 / 轮换，并处理相关历史与副本。提交前可检查：

```powershell
git status --short --ignored
git diff --cached --name-only
git diff --cached
```

## 问题反馈

请通过 [SecureLedger 项目仓库](https://github.com/FleetingPhantomw/SecureLedger) 反馈问题，并附源码提交号、Debug / Release 类型、Android 版本与厂商、复现步骤、预期与实际结果及脱敏日志。不要提交真实账单、完整卡号、备份文件、私钥或密码；安装签名冲突应先保全账本备份。

## 许可证

SecureLedger 自主编写的代码、文档及构建配置采用 **Apache License 2.0**，完整条款见根目录 [LICENSE](LICENSE)，项目归属声明见 [NOTICE](NOTICE)。使用、修改和再分发时应遵守许可证条款，保留适用的版权、许可与归属声明。

第三方组件和随附资产仍遵循各自许可证，不因根目录许可证而被重新授权：PaddleOCR Android SDK 与随附模型的授权及来源见 [模块说明](ppocr-sdk/README.md) 和 [原许可证](ppocr-sdk/LICENSE)；其余依赖及设计参考见 [第三方清单](THIRD_PARTY_LICENSES.md)。Biller-Android 仅为设计参考，未包含其源码，不属于本项目的授权范围。
