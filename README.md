# 家有好物 · SmartStorage

Kotlin / Jetpack Compose Android 本地收纳应用。产品范围见 [设计文档](docs/Android-App设计文档.md)。v0.4.0 接入 OpenRouter 免费视觉模型，支持照片识别和自动填表；云备份仍预留接口。AI 识别会上传所选照片及分类选项，remove.bg 抠图会上传所选照片，其他库存资料保存在本机。

本轮安装包：`artifacts/SmartStorage-0.5.0-debug.apk`。62 项测试通过，Lint 无问题；尚未进行本轮真机视觉验收。

v0.5.0 将统计页全部改为图表，并移除计量单位的输入、展示和导出列，详见 [变更与验证记录](docs/统计图表与单位移除-v0.5.0.md)。

OpenRouter 配置、免费模型限制和验收步骤见 [AI 识别说明](docs/OpenRouter-AI识别.md)。

v0.3.0 功能与密钥配置见 [功能增强说明](docs/功能增强-v0.3.0.md)。

## 运行工程

- JDK 21，Android SDK 35，Build Tools 35.0.0。
- 用 Android Studio 打开本目录，设置 SDK 路径并等待 Gradle 同步。
- 命令行使用 `local.properties` 指定 `sdk.dir`，或配置 `ANDROID_HOME`。
- 调试包支持 Android 8.0 及以上。

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug
```

官方 Maven 仓库连接困难时，可以显式使用公开镜像：

```powershell
.\gradlew.bat -PuseMirror=true :core:test :app:testDebugUnitTest :app:assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。调试包用于开发验证，正式发布还需配置自己的签名和分发资料。

上一版交付安装包：`artifacts/SmartStorage-0.3.0-debug.apk`。50 项自动化测试通过、Android Lint 无问题，详见[验证记录](docs/验证记录-v0.3.0.md)。尚未进行真机 UI 验收。上一版安装包保留。

v0.2.0 新增房子/房间/容器/物品卡片网格、容器分层管理、统一十格参考线吸附，以及 Material 3 日期与单选组件，详见[空间与组件优化说明](docs/空间与组件优化-v0.2.0.md)。

本机还可运行 `scripts/Build.ps1 -Mirror`，它会使用项目内的工具和缓存，避免修改全局 Gradle 配置。Windows 若提示 `Could not move temporary workspace`，等待 Gradle 退出后运行 `scripts/Repair-GradleCache.ps1`，只恢复项目内已完整生成但未完成重命名的缓存，再重试构建。

## 已接入的本地流程

- 首页临期/过期清单、最近物品、房子范围选择。
- 系统相机拍照、相册选图、手动资料、同名提醒、单独补货批次。
- 多房子、房间、三级收纳容器，空间改名/迁移/空空间归档。
- 物品拖到同屏容器或从详情选择目标；支持指定移动数量。
- 中文搜索、分类/位置/标签/保质期/价格筛选及排序。
- 标签创建、改名、颜色、删除和合并。
- 入库、消耗、丢弃、盘点、移动、受版本约束的撤销及历史流水。
- 全局与批次临期天数、暂停批次通知、WorkManager 每日聚合提醒。
- 本地备份与恢复、可选密码加密、校验与恢复前安全快照。
- 全量或筛选结果导出 `.xlsx` 四张表。
- 五类收纳模板、分区创建预览、行李箱检查清单。
- 房间二维容器布局，拖拽、网格吸附、尺寸/方向调整、撤销/重做。
- 分类/房间/标签价值、贵重物品、拥有时间和过期损耗统计。

## 代码组织

- `core/`：纯 Kotlin 领域模型、库存事务规则、筛选、备份编解码、Excel 写出与单元测试。
- `app/`：Compose 页面、ViewModel、Room Repository、照片和通知系统接口。
- `app/schemas/`：Room 自动导出的数据库结构。
- `docs/`：产品设计与实现说明。

数据通过 Repository 串行写入，并在 Room 事务中同时保存余额和操作记录。数据库按实体拆表，批次和库存位置使用外键及唯一索引；部分实体字段使用版本化 JSON payload，便于首版迭代。当前查询从内存快照计算，尚未针对设计文档的十万条流水目标做性能验收。

## 使用建议

1. 在“空间”中新建房子、房间和柜子；柜子可设置层数，点击层卡片查看对应物品。
2. 点击“录入物品”，设置数量及位置；价格与日期可后补。
3. 同款再次购买时从物品详情“补货”，保留不同批次的到期日。
4. 使用“消耗一件”时检查默认推荐批次；已过期批次需额外确认。
5. 在“我的”导出 `.ssb` 备份，保存到应用之外；Excel 清单不能用于完整恢复。

## 当前边界

详细差异与待验证项见 [实现说明](docs/实现说明.md)。系统相机、通知省电策略、实际触控体验需要真机验收。没有配置远程服务的入口会明确显示“待接入”，不会返回模拟识别结果或虚假备份成功。

技术依赖配置参考 Android 官方 [AGP 8.9 兼容要求](https://developer.android.com/build/releases/agp-8-9-0-release-notes) 与 [Compose 编译器插件说明](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)。
