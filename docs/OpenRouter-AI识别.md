# OpenRouter AI 识别（v0.4.0）

## 使用
1. 注册 OpenRouter，在 https://openrouter.ai/keys 创建 API Key。
2. 在“我的 → AI 识别 · OpenRouter”，或录入页的同名配置面板，填写 Key。
3. 默认模型为 openrouter/free。也可填写支持图片输入、以 :free 结尾的模型 ID。
4. 点击“同意并开启”。后续拍照或选图会上传照片及分类选项，自动填写名称、分类、单位、备注，匹配已有标签。
5. 多种物品会弹出选择列表，仅填写所选物品。核对后点击“保存入库”。
6. 关闭自动填写后，可通过“AI 识别”手动触发；“撤销 AI 填写”保留手动编辑的字段。更换照片会撤销上一张照片仍未手动修改的建议。

修改配置需重新输入 Key。清除配置会移除本机密钥、模型和自动识别开关。
补货页沿用既有物品信息，没有新增识别入口。

## 数据与费用
- 调用固定 HTTPS 地址 https://openrouter.ai/api/v1/chat/completions；禁用 HTTP 重定向。
- 仅允许 openrouter/free 或 :free 模型。不提供付费回退，不自动重试。
- 免费模型可用性、速率和额度由 OpenRouter 及上游决定；不保证永久可用或国内网络可达。
- 只发送压缩 JPEG 及分类选项，不发送完整库存、位置、购买信息或已有标签列表。
- 使用导入后的原图识别，独立于 remove.bg 抠图。临时 JPEG 在内存中生成，不修改原图。
- Key 以 Android Keystore AES-GCM 加密保存，不记入日志，不包含在物品备份中，系统备份/设备迁移也已禁用。
- OpenRouter 与其模型服务商会处理上传内容，数据使用受对应服务条款约束。不要在应用里配置共享发布密钥。

## 字段保护
- 仅填空白名称/备注、默认分类/单位、空标签；用户改动过的字段（即使改回空白）不自动覆盖。
- 编辑已有物品时保留现有分类和单位。
- 不自动创建标签，不填写数量、价格、购买/到期日期、位置和贵重标记。
- 请求期间允许修改文本；返回时按最新字段值和手动修改记录合并。
- 页面销毁时取消识别协程；返回结果同时校验图片标识。
- 重试失败保持当前表单。请求失败、超时、限流、结果格式错误、无法识别时都可手动填写。
- AI 结果只更新表单草稿，保存入库仍走现有数据校验和事务。

## 实现
- OpenRouterClient：图片编码、HTTP 请求、大小上限、错误映射、JSON 校验。
- RecognitionSettings：隔离的加密配置，复用已有 Keystore 存储实现。
- RecognitionDraft：字段合并与撤销规则。
- ItemForm：自动/手动识别、状态、多物品选择、撤销、请求生命周期。
- RecognitionSettingsPanel：Key、免费模型、自动识别开关。
- 数据库结构不变，无新外部依赖。

## 验收
自动化测试覆盖请求格式、免费模型限制、401/402/429/超时等错误、超大/截断/异常 JSON、多物品、标签映射、用户编辑保护与撤销。
构建命令：scripts/Build.ps1 -Mirror（core 测试、app 单元测试、Lint、debug APK）。

真实 API 尚需用户在应用内配置 Key 后验证：
- 清晰单物品、多物品、模糊/空白照片。
- 识别等待时手动输入，结果不覆盖输入。
- 连续重试、切换照片、旋转屏幕、取消选择、撤销后保存。
- 网络不通、免费额度用尽、抠图与识别同时启用。
- 真机验证 Keystore 保存/读取/清除，重启应用后配置仍可用。

接口文档：https://openrouter.ai/docs/guides/overview/multimodal/image-understanding
免费路由：https://openrouter.ai/openrouter/free
额度：https://openrouter.ai/docs/api_reference/limits

## 本次验证结果
2026-09-23：
- 完整构建成功（61 个 Gradle 任务），版本 0.4.0 / versionCode 4。
- 58 项测试通过：core 34 项，app 24 项；其中新增 OpenRouter 相关 8 项。
- Android Lint：No issues found.
- 安装包：artifacts/SmartStorage-0.4.0-debug.apk。
- 未使用真实 API Key 调用服务，未进行真机 UI / Keystore 验收；网络测试使用可注入 HTTP 连接模拟响应。
