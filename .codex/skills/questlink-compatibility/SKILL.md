---
name: questlink-compatibility
description: 维护 Quest Enhance 中 FTB Quests 链接任务（QuestLink）兼容性。涉及任务书画布的节点选择、右键菜单、绘制、移动、复制、删除、序列化或装饰线功能时使用，尤其是在代码按 Quest 或 Movable 分支处理节点时。
---

# QuestLink 兼容性

将 `QuestLink` 视为独立画布节点，而不是它的 `linked_quest`。链接任务有自己的 `id`、坐标、尺寸和形状，配置文件位于章节的 `quest_links` 列表。

## 实现规则

- 修改画布功能前，检查当前两版 FTB Quests 源码或反编译结果，确认 `QuestLink`、`QuestLinkButton`、`Chapter#getQuestLinks`、`Chapter#removeQuestLink` 与相关 `Movable` 方法存在且签名匹配。
- 不要把链接任务替换为 `getRelatedQuest()` 或 `linked_quest` 后再继续处理。真实任务和链接任务可同时出现在画布，必须保持各自的身份和坐标。
- 遍历 `Movable` 时显式处理 `Quest`、`QuestLink` 和本模组辅助点；未知类型保持拒绝，不要将其误认为任务节点。
- 对持久化节点键使用独立前缀。普通任务使用 `q:<id>`，链接任务使用 `l:<id>`，辅助点继续使用自身 UUID 键；不要让三类节点共用同一键空间。
- 从 `QuestButton` 的继承逻辑进入右键菜单时，检测实际对象是否为 `QuestLinkButton`。该按钮继承的 `quest` 字段是关联真实任务；通过 Accessor 读取其 `link` 字段，再传入画布功能。
- 涉及绘制、命中、选择或位置映射时，按 `QuestLink#getMovableID()` 建立链接节点映射，使用链接节点自己的 `getX()`、`getY()`、宽高和形状。
- 涉及删除时，给 `Chapter#removeQuestLink` 增加与 `removeQuest`、`removeImage` 对应的清理逻辑，删除所有引用该 `l:` 节点键的数据。
- 涉及复制或粘贴时，先确认 FTB 的网络消息是否接受 `QuestLink`。若原生消息只接受真实任务 ID，明确提示或转换为已验证的真实任务复制语义，不要伪造链接任务数据。

## 修改检查

1. 检查菜单入口是否把选中的 `QuestLink` 原样传入功能逻辑。
2. 检查渲染映射是否能从 `QuestLink` 找到对应画布按钮。
3. 检查序列化和网络同步是否保存并恢复 `l:` 节点键。
4. 检查删除链接任务后，引用它的线、缓存或选择状态是否同步清理。
5. 为新增、删除或无法匹配链接节点的异常路径添加最小英文调试日志，不要在每帧渲染中输出日志。

## 验证

分别在 1.21.1 NeoForge 与 1.20.1 Forge 执行受影响模块的 `compileJava` 和 `processResources`。涉及 Mixin、右键菜单、选择或绘制时，启动对应 `runClient`：

1. 创建或打开已有链接任务。
2. 与普通任务、辅助点分别进行 `Ctrl` 多选。
3. 执行目标功能并移动链接任务，确认使用链接节点位置。
4. 删除链接任务，确认没有残留显示或持久化引用。
