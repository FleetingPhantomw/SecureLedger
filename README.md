# SecureLedger

本地优先的 Android 安全记账应用，使用 Kotlin/JVM 与 Jetpack Compose，支持个人账本、购物和银行账单补录、月度可视化、离线共同账本与多人 AA 独立会签，不需要在线服务器账户。

## 产品功能

- 手工收入、支出与转账；银行卡安全尾号引用、支付渠道、组合支付与凭证；修改账目通过追加更正保留签名历史。
- 项目与月份筛选，按月、周、日分组；月度收支、每日支出柱状图、分类环图，内部转账不重复计收支。
- 跟随系统、浅色、纯黑模式；主题色预设和六位 HEX 自定义颜色，收入/支出保留语义配色。
- 本地 PP-OCRv6_small 中文识别，支持相机、相册、图片分享和文本解析，核对后保存。
- 购物批量补录：同名商品保持独立订单；淘宝仅明确交易关闭/已取消时排除，缺时间允许标注为估计导入时间。
- 银行多笔导入：无符号默认入账，负号出账；疑似重复单列，由用户决定保留或忽略，不删除正式签名记录。历史加密导入继续可对账，新 CSV 入口已撤除。
- 所选应用的支付页面识别，可选微信/支付宝支出通知识别；短窗口跨来源融合，悬浮窗提供忽略或继续记账，不自动保存或操作付款。
- 项目创建、接收方绑定邀请和签名同步；AA 选择本次支出与成员，展示每人已付、应承担和补差，独立签名确认。
- 同步、成员邀请及 AA 会签统一使用竖屏扫码；扫码页可从相册选择二维码图片，未开启相机权限时仍可选图。识别后沿用原有签名校验与用户确认流程。

AA 默认以账目作者作为付款人，整数分等额均摊，余数按稳定成员编号排序分配。代付或自定义承担可用高级手动分摊，由成员核对。应用给出方案，不执行资金转账。

纯黑主题在模拟器已验证；本次手机复查仍有系统渲染后的灰色差异，原因尚未确认，按用户要求暂停专项排查。

## 安全与保护范围

Argon2id（64MiB、3轮、并行度1）从用户密码派生密钥；Android Keystore 提供本机包装。随机主密钥保护个人数据与私钥材料，共享项目使用独立密钥。业务载荷采用 AES-256-GCM（96位随机 nonce、128位标签）；规范化、SHA-256、项目/作者哈希链、Ed25519 和确定 Merkle 根验证历史与来源。邀请采用接收方绑定的 X25519 与 HKDF-SHA256。

原始记录不覆写，更正另建签名记录。同步先验证后事务合并，并登记重放标记。AA 所选账单与分配进入哈希，接收者检查本地项目根并重算后才签名。空扩展保留旧凭证语义，Room 显式迁移至版本7。

SQLite 仍有项目、作者、时间等可见元数据；签名不证明现实消费。`SL1:` 记录载荷是密文，包元数据可见；`SLS1.` AA 文件是签名编码，**不是加密通道，含分摊金额，仅分享给项目成员**。Base64不等于加密。成员可读取其授权项目数据，当前没有完整的成员撤销后历史访问轮换。

忘记密码不能绕过解锁，恢复需要独立密码保护的 `.sldb` 备份，仅能恢复到空密码库。本机 Keystore 包装不能直接复制到另一手机。长期使用前请备份并演练恢复；Git 管理源码，不备份账目和密钥。

## 自动记账权限

设置中选择应用并开启识别，再按需授权：

| 权限 | 用途 |
| --- | --- |
| 无障碍 | 所选支付页面识别，过滤密码输入 |
| 显示通知 | 服务状态与备用提示，不代表读取其它应用通知 |
| 悬浮窗 | 在其它应用上显示确认入口 |
| 通知读取（可选） | 微信/支付宝支出候选；默认关闭，单独授权 |

状态通知不保证进程永久存活。支付保护页限制弹窗时，在窗口切换后单次重试，不持续轮询；真实 OEM 保护页和长期后台仍需专项验证。识别只产生候选，不保存完整页面或通知全文，不上传服务器。

参考 [Biller-Android](https://github.com/kangkaipeng/Biller-Android) 的双来源、按应用提取、短窗口融合设计，在本项目独立实现，未复制源码或伪装系统服务。该项目是设计参考而非依赖，见 [第三方清单](THIRD_PARTY_LICENSES.md)。

## 共同账本简明流程

1. 接收者在“加入项目”生成请求，创建者针对请求生成邀请，接收者接受。
2. 记账时选择共同项目。在“同步”选择项目并分享文件，对方用 SecureLedger 打开并确认导入；双方新增各分享一次即可。二维码/粘贴为替代方式。
3. 在“AA结算”选择支出与参与成员，核对总额及分配，保存后分享确认文件。
4. 对方打开文件进入对应项目，先同步完整账本，再独立确认并分享返回，创建者合并成员签名。

文件有尺寸限制，打开和验证不会自动批准结算。

## 构建与IDE

Android Studio 或 IntelliJ IDEA 打开仓库根目录，使用 Gradle wrapper、JDK17、SDK35。`local.properties` 配置本机SDK，IDE与缓存不提交。

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。已在 API35/x86_64 模拟器与 Android16/API36 arm64 手机上原位更新，未卸载主应用。

```powershell
.\scripts\build-release.ps1
```

Release输出 `app/build/outputs/apk/release/app-release.apk`。脚本首次在忽略的 `.signing/` 生成密钥，随机密码用Windows DPAPI保护；请分别备份密钥和密码，DPAPI文件不能单独跨电脑恢复。Debug与Release签名不同，不能互相覆盖，不要为解决签名冲突直接卸载真实账本。见 [签名说明](docs/RELEASE_SIGNING.md)。本次验收使用Debug，新版Release需重新构建和复测。

## 测试

2026-10-07：45项JVM无失败；两设备各执行17项常规instrumentation无失败，2项可选测试默认跳过。双设备AA六阶段另行通过：三笔240.01元，两方承担120.00/120.01元，补差45.00元，独立确认2/2。范围见 [验证矩阵](docs/TESTING.md)，耗时限制见 [性能说明](docs/PERFORMANCE.md)。

```powershell
adb -s <序列号> install -r app\build\outputs\apk\debug\app-debug.apk
adb -s <序列号> install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s <序列号> shell pm grant com.secureledger.test android.permission.CAMERA
adb -s <序列号> shell am instrument -w -r com.secureledger.test/androidx.test.runner.AndroidJUnitRunner
```

普通测试使用命名空间密码库与内存数据库，不清空主数据。`scripts/test-two-device-aa.ps1` 必须显式提供序列号和运行时密码，仅新增联机测试项目及模拟开销。不要把用户密码写进脚本或Git。`交易示例/` 是忽略的本地私人样例，可选OCR基准仅在自控模拟器暂存，测试后清理，不随APK或课程材料提交。

## 自主实现与课程材料

自主设计包括分层、有效账目与更正视图、订单/流水解析、候选融合、日历统计、整数分AA、选择绑定、协议封套与测试流程。Compose、Room、OCR引擎和密码原语来自框架/库，不冒称自行实现。目录见 [架构说明](docs/ARCHITECTURE.md)。

`reports/` 中两份DOCX正文各约35页，仅摘核心代码：Java工程实践报告侧重自主搭建与验证；密码学报告侧重方案、密钥生命周期、认证与安全边界。姓名、学号、班级/院系待填，密码学评分表在第一页。源码随仓库提交，模板要求的3–5分钟讲解录屏需另行制作。

`scripts/build-course-reports.py` 可重建报告（需要原模板与本地脱敏截图）；`scripts/render-reports.ps1 -RefreshFields` 通过Word更新目录并渲染。Word会改写部分模板内部部件，之后运行 `build-course-reports.py --restore-template-parts` 恢复原字节，再不带 `-RefreshFields` 只读渲染最终文件。`scripts/report-qa.py` 生成逐页QA图，`scripts/verify-course-reports.py <预览目录>` 核验模板保留、关系、正文页码和输入模板哈希。文档工具使用工作区Python依赖，不属于Android运行依赖。
