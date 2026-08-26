# 26.1.2 NeoForge 功能兼容说明

## 版本边界

- Minecraft 26.1.2
- NeoForge 26.1.2.93
- FTB Quests 26.1.2.3
- FTB Library 26.1.2.7
- Java 25

## 与 1.21.1 的公共契约

`QuestUtils` 保留 1.21.1 的任务、章节、目标、奖励、进度、前置、画布元素和描述组件方法名，并为长整型 ID 保留字符串重载。

2612 的 FTB Quests 存档格式是 JSON5，因此内部使用 `Json5Object`；`getData`、`mergeData` 和 `getChapterImageData` 仍在 KubeJS 边界使用 `CompoundTag`，由模组负责两种数据格式转换。

2612 使用 `Identifier`，脚本资源参数仍使用字符串，不把 Minecraft 版本类型泄漏给脚本作者。

## 功能范围

已对齐章节背景、章节侧边栏背景、任务背景、任务详情背景、批量任务编辑、批量添加任务、玩家持久化数据描述、按键绑定选择、本地图片恢复、进度任务跳转和任务完成提示继承。

Ponder 不属于 2612 当前功能范围：不注册 Ponder 依赖、不加入 Ponder 描述组件、不加载 Ponder 类，也不保留对应语言键。

QuestLink 始终使用独立的 `l:<id>` 节点键；普通任务使用 `q:<id>`，辅助点使用自己的 UUID 键。批量任务编辑和批量背景设置遇到 QuestLink 时拒绝操作。

## KubeJS 示例

```js
// 直接使用字符串任务 ID，避免 JavaScript 长整数精度丢失
const quest = QuestUtils.getQuest("0x1234")
if (quest != null) {
    QuestUtils.setTitle("0x1234", "示例任务")
    QuestUtils.setQuestDescription("0x1234", QuestUtils.descriptionKeybind("key.jump"))
}
```

KubeJS 是可选依赖；移除 KubeJS 后，纯 FTB Quests 功能和客户端界面仍应能够启动。
