# 1.21.1 任务描述快捷组件 - 平行任务表

> 来源：[设计文档](1211-description-components-design.md) + [总任务表](1211-description-components-task-plan.md)。
> 规则：同阶段任务不共享文件且彼此不依赖；阶段之间按门禁串行推进。

## 0. 使用方法

1. 仅领取前置阶段全部完成的任务，并记录负责人。
2. 只修改任务的 Owns 文件；Reads 只读。
3. 完成后同步更新本表、总任务表和设计文档中的已验证事实。
4. 阶段全部完成后执行门禁，门禁通过才进入下一阶段。

## 1. 进度回写协议

- 每个文件同一时间只有一个负责人。
- 领取：`todo` 改为 `in-progress` 并填写 owner。
- 完成：记录产物或验证命令，并同步总任务表对应 ID。
- 修改冻结约定或借用其他任务文件时，必须先在第 7 节登记变更请求。

## 2. 共享约定（已冻结）

- 包名：`com.quest_enhance.client` 与 `com.quest_enhance.mixin`。
- 新类名：`DescriptionComponentMenu`。
- 接口签名：设计文档第 5 节四个方法。
- 语言键前缀：`quest_enhance.description_component.`。
- 菜单包含网页链接、任务指定页、复制、开发者命令、网络图片、物品图标、物品悬停、视频、悬停文字、自定义字体、本地化文本、按键绑定和混淆文字。
- FTB 原生分页符、本地图片、任务跳转和 JSON 转换不进入新增菜单。
- 文字组件 JSON 必须使用 `Component.Serializer.toJson(component, FTBQuestsClient.holderLookup())`，确保物品数据组件具备注册表上下文。
- 不加入 `open_file`、`suggest_command`、实体悬停、选择器、计分板或 NBT 组件。

## 3. 阶段概览

```mermaid
flowchart LR
    A["阶段 A：研究与发布"] --> B["阶段 B：并行实现"] --> C["阶段 C：工具栏集成"] --> D["阶段 D：2.0 构建"] --> E["阶段 E：可选增强"]
```

| 阶段 | 平行任务 | 前置 | 里程碑 |
|---|---|---|---|
| A | PA-1、PA-2 | - | M0 |
| B | PB-1、PB-2、PB-3 | 阶段 A | M1 |
| C | PC-1 | 阶段 B | M1 |
| D | PD-1、随后 PD-2 | 阶段 C | M2 |
| E | PE-1、PE-2 | 阶段 D | M3 |

## 4. 任务详情

### 阶段 A：研究与发布

**PA-1：推送 1.20.1 Forge 2.0**  done owner:root output:远端分支已包含 `1889ea5`
- Owns：1.20.1 工作树 Git 远端状态。
- Reads：1.20.1 构建产物与提交历史。
- 验收：`origin/codex/1.20.1-forge` 指向或包含 `1889ea5`。
- 对应：T0.1。

**PA-2：核对 1.21.1 API 与功能范围**  done owner:root output:设计文档第 7 节
- Owns：`docs/1211-description-components-design.md`。
- Reads：1.21.1 项目、FTB Quests 2101.1.27、FTB Library 2101.1.31。
- 验收：关键类、方法、包路径和序列化方式均由当前依赖源码确认。
- 对应：T0.2。

> 阶段 A 门禁：PA-1 与 PA-2 均已完成，M0 通过，可以进入阶段 B。

### 阶段 B：并行实现

**PB-1：菜单主体**  done owner:root output:13 项菜单与配置流程
- Owns：`src/main/java/com/quest_enhance/client/DescriptionComponentMenu.java`。
- Reads：冻结接口、现有视频与字体类、1.20.1 同名实现。
- 验收：13 个菜单项完整，使用 1.21.1 物品选择器包和图片 API。
- 对应：T1.1。

**PB-2：编辑器接口与组件插入**  done owner:root output:`compileJava` 成功
- Owns：`src/main/java/com/quest_enhance/client/MultilineTextEditorAccess.java`、`src/main/java/com/quest_enhance/mixin/MultilineTextEditorScreenMixin.java`。
- Reads：FTB `MultilineTextEditorScreen`、冻结接口。
- 验收：选区逻辑与 JSON 序列化通过 `compileJava`。
- 对应：T1.2。

**PB-3：中英文语言资源**  done owner:root output:91 个键一致且 `processResources` 成功
- Owns：`src/main/resources/assets/quest_enhance/lang/zh_cn.json`、`en_us.json`。
- Reads：冻结语言键前缀与 1.20.1 文案。
- 验收：两份 JSON 包含相同键集合且 `processResources` 成功。
- 对应：T1.3。

> 阶段 B 门禁：已通过，`compileJava` 与 `processResources` 均成功。

### 阶段 C：工具栏集成

**PC-1：统一组件按钮**  done owner:root output:统一 `+` 替换独立视频按钮
- Owns：`src/main/java/com/quest_enhance/mixin/MultilineTextEditorToolbarMixin.java`。
- Reads：阶段 B 产物、FTB 工具栏实际坐标。
- 验收：独立视频按钮删除，新增组件按钮可打开菜单，原生按钮未复制。
- 对应：T2.1、T2.2。

> 阶段 C 门禁：已通过，Mixin 编译与资源处理无错误。

### 阶段 D：2.0 发布准备

**PD-1：版本资源**  done owner:root output:`1.21.1-neoforge-2.0`
- Owns：`gradle.properties`。
- Reads：当前版本命名约定。
- 验收：版本为 `1.21.1-neoforge-2.0`。
- 对应：T3.1。

**PD-2：构建与手测清单**  done owner:root output:`build` 成功并核对 JAR
- Owns：构建输出与本表执行记录。
- Reads：全部源码和资源。
- 验收：`gradlew build` 成功，JAR 元数据正确，列出用户手动测试项。
- 对应：T3.2、T3.3。

> 阶段 D 门禁：已通过，JAR 名称、模组元数据和手测清单全部核对完成。

### 阶段 E：候选增强

**PE-1：跳转到任务指定页**  done owner:root output:任务选择器、实际页数限制和 `任务ID/页码`
- Owns：`DescriptionComponentMenu.java`，由阶段 B 串行移交。
- Reads：FTB `change_page` 点击处理与任务选择器。
- 验收：可选择任务和页码，生成 FTB 支持的 `任务ID/页码`，普通任务跳转仍使用原生按钮。
- 对应：T4.1。

**PE-2：混淆文字**  done owner:root output:原版 `OBFUSCATED` 组件样式
- Owns：`DescriptionComponentMenu.java`，由阶段 B 串行移交。
- Reads：FTB `ClientTextComponentUtils` 的 `&k` 解析。
- 验收：只补充缺失的格式入口，不改变原生格式按钮。
- 对应：T4.2。

> 阶段 E 门禁：用户已确认全部加入，两个增强均编译并打包成功。

## 5. 研究结论

- 1.21.1 的 `SelectItemStackScreen` 位于 `dev.ftb.mods.ftblibrary.config.ui.resource`。
- Minecraft 1.21.1 的 `Component.Serializer.toJson` 需要 `HolderLookup.Provider`，可直接使用 FTB 的 `FTBQuestsClient.holderLookup()`。
- FTB 原生工具栏已经提供分页符、本地图片、任务跳转和 JSON 转换，因此不应在新增菜单重复。
- 现有视频标记和配置页没有版本迁移阻塞，可直接作为媒体菜单的一项复用。
- `change_page` 支持 `任务ID/页码`，适合补充“跳转到任务指定页”；FTB 文本解析支持 `&k`，可选补充“混淆文字”。

## 6. 文件所有权矩阵

| 文件或目录 | 负责人任务 | 阶段 |
|---|---|---|
| `docs/1211-description-components-design.md` | PA-2 | A |
| `DescriptionComponentMenu.java` ※ | PB-1 -> PE-1/PE-2 | B -> E |
| `MultilineTextEditorAccess.java` | PB-2 | B |
| `MultilineTextEditorScreenMixin.java` | PB-2 | B |
| `zh_cn.json`、`en_us.json` | PB-3 | B |
| `MultilineTextEditorToolbarMixin.java` | PC-1 | C |
| `gradle.properties` | PD-1 | D |
| `build/libs` 与手测记录 | PD-2 | D |

## 7. 变更记录

| 编号 | 日期 | 提交人 | 变更 | 处理 |
|---|---|---|---|---|
| CR-1 | 2026-07-17 | root | 用户确认把两个候选都加入 2.0，阶段 E 串行接手菜单文件 | 已完成并通过构建 |

## 8. 平行任务映射

| 平行任务 | 总任务 ID | 里程碑 |
|---|---|---|
| PA-1 | T0.1 | M0 |
| PA-2 | T0.2 | M0 |
| PB-1 | T1.1 | M1 |
| PB-2 | T1.2 | M1 |
| PB-3 | T1.3 | M1 |
| PC-1 | T2.1、T2.2 | M1 |
| PD-1 | T3.1 | M2 |
| PD-2 | T3.2、T3.3 | M2 |
| PE-1 | T4.1 | M3 |
| PE-2 | T4.2 | M3 |
