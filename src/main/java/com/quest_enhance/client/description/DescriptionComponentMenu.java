package com.quest_enhance.client.description;

import com.quest_enhance.client.canvas.ChapterCanvasText;
import com.quest_enhance.common.description.PlayerPersistentDataDescription;
import com.quest_enhance.common.description.QuestDescriptionComponents;
import com.quest_enhance.client.media.GifSelectionScreen;
import com.quest_enhance.client.media.VideoSelectionScreen;
import com.quest_enhance.client.media.VideoSupport;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.ftb.mods.ftblibrary.client.config.EditableConfigGroup;
import dev.ftb.mods.ftblibrary.client.config.editable.EditableItemStack;
import dev.ftb.mods.ftblibrary.util.NameMap;
import dev.ftb.mods.ftblibrary.client.config.gui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.client.config.gui.resource.SelectItemStackScreen;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.client.gui.widget.ContextMenuItem;
import dev.ftb.mods.ftblibrary.client.gui.widget.Panel;
import dev.ftb.mods.ftblibrary.client.util.ImageComponent;
import dev.ftb.mods.ftblibrary.client.util.ImageComponent.ImageAlign;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.FTBQuestsClient;
import dev.ftb.mods.ftbquests.client.gui.MultilineTextEditorScreen;
import dev.ftb.mods.ftbquests.client.gui.SelectQuestObjectScreen;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.QuestObjectType;
import dev.ftb.mods.ftbquests.client.config.EditableQuestObject;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class DescriptionComponentMenu {
    private static final Pattern NON_EMPTY = Pattern.compile(".+");
    private static final Pattern NO_BRACES = Pattern.compile("[^{}]*");
    private static final Pattern WEB_URL = Pattern.compile("https?://[^\\s{}]+");
    private static final Pattern COMMAND = Pattern.compile("/.+");
    private static final Pattern TECHNICAL_KEY = Pattern.compile("[^\\s{}]+");

    private DescriptionComponentMenu() {
    }

    // 打开统一描述组件菜单，只补充 FTB 原生工具栏没有的功能
    public static void open(Panel parent, MultilineTextEditorAccess editor) {
        // 导航分组包含网页、指定页跳转、复制和开发者命令
        List<ContextMenuItem> navigation = List.of(
                new ContextMenuItem(
                        Component.translatable("quest_enhance.description_component.web_link"),
                        Icons.GLOBE,
                        button -> openTextComponentConfig(parent, editor, TextAction.WEB_LINK)
                ),
                new ContextMenuItem(
                        Component.translatable("quest_enhance.description_component.quest_page"),
                        Icons.BOOK,
                        button -> selectQuestPage(parent, editor)
                ),
                new ContextMenuItem(
                        Component.translatable("quest_enhance.description_component.copy"),
                        Icons.NOTES,
                        button -> openTextComponentConfig(parent, editor, TextAction.COPY)
                ),
                new ContextMenuItem(
                        Component.translatable("quest_enhance.description_component.command"),
                        Icons.CONTROLLER,
                        button -> openTextComponentConfig(parent, editor, TextAction.COMMAND)
                ).setYesNoText(Component.translatable("quest_enhance.description_component.command.warning"))
        );

        // 媒体分组提供网络图片、物品展示和已有视频能力
        List<ContextMenuItem> media = new ArrayList<>(List.of(
                new ContextMenuItem(
                        Component.translatable("quest_enhance.description_component.remote_image"),
                        Icons.GLOBE,
                        button -> openRemoteImageConfig(parent, editor)
                ),
                new ContextMenuItem(
                        Component.translatable("quest_enhance.description_component.item_icon"),
                        Icons.DIAMOND,
                        button -> selectItem(parent, editor, false)
                ),
                new ContextMenuItem(
                        Component.translatable("quest_enhance.description_component.item_hover"),
                        Icons.INFO,
                        button -> selectItem(parent, editor, true)
                )
        ));
        if (VideoSupport.isAvailable()) {
            media.add(new ContextMenuItem(
                        Component.translatable("quest_enhance.video.description.add"),
                        Icons.CAMERA,
                        button -> openVideoSelector(parent, editor)
            ));
        }
        media.add(new ContextMenuItem(
                Component.translatable("quest_enhance.gif.description.add"),
                Icons.CAMERA,
                button -> openGifSelector(parent, editor)
        ));

        // 文字分组生成原版 JSON 组件，保持任务文件兼容
        List<ContextMenuItem> text = List.of(
                new ContextMenuItem(
                        Component.translatable("quest_enhance.description_component.hover_text"),
                        Icons.CHAT,
                        button -> openTextComponentConfig(parent, editor, TextAction.HOVER_TEXT)
                ),
                new ContextMenuItem(
                        Component.translatable("quest_enhance.description_component.font"),
                        Icons.FEATHER,
                        button -> openTextComponentConfig(parent, editor, TextAction.FONT)
                ),
                new ContextMenuItem(
                        Component.translatable("quest_enhance.description_component.translation"),
                        Icons.MAP,
                        button -> openTextComponentConfig(parent, editor, TextAction.TRANSLATION)
                ),
                new ContextMenuItem(
                        Component.translatable("quest_enhance.description_component.keybind"),
                        Icons.CONTROLLER,
                        button -> KeybindSelectionScreen.open(parent, null, keybind -> {
                            if (keybind != null) {
                                editor.quest_enhance$insert_component(
                                        Component.translatableWithFallback(keybind, keybind)
                                                .append(Component.literal(": "))
                                                .append(Component.keybind(keybind))
                                );
                            }
                        })
                ),
                new ContextMenuItem(
                        Component.translatable("quest_enhance.description_component.persistent_data"),
                        Icons.INFO,
                        button -> openPersistentDataConfig(
                                parent,
                                "example.key",
                                false,
                                markup -> editor.quest_enhance$insert_at_end_of_line("\n" + markup)
                        )
                ),
                new ContextMenuItem(
                        Component.translatable("quest_enhance.description_component.table"),
                        Icons.INV_IO,
                        button -> QuestDescriptionTable.openConfig(
                                parent,
                                QuestDescriptionTable.defaultTable(),
                                markup -> editor.quest_enhance$insert_at_end_of_line("\n" + markup)
                        )
                ),
                new ContextMenuItem(
                        Component.translatable("quest_enhance.description_component.obfuscated"),
                        Icons.NOTES,
                        button -> openTextComponentConfig(parent, editor, TextAction.OBFUSCATED)
                )
        );

        // 使用 FTB 原生三级上下文菜单展示全部组件类别
        parent.getGui().openContextMenu(List.of(
                ContextMenuItem.subMenu(
                        Component.translatable("quest_enhance.description_component.navigation"),
                        Icons.GLOBE,
                        navigation
                ),
                ContextMenuItem.subMenu(
                        Component.translatable("quest_enhance.description_component.media"),
                        Icons.ART,
                        media
                ),
                ContextMenuItem.subMenu(
                        Component.translatable("quest_enhance.description_component.text"),
                        Icons.NOTES,
                        text
                )
        ));
    }

    // 识别独立的快捷 JSON 组件，并用与插入时相同的配置页编辑原值
    public static boolean edit(Panel parent, String raw_text, Consumer<String> save) {
        Optional<QuestDescriptionTable.TableData> table = QuestDescriptionTable.decodeMarkup(raw_text);
        if (table.isPresent()) {
            QuestDescriptionTable.openConfig(parent, table.get(), save);
            return true;
        }

        JsonObject json;
        Component component;
        try {
            JsonElement element = JsonParser.parseString(raw_text);
            if (!element.isJsonObject()) {
                return false;
            }
            json = element.getAsJsonObject();
            component = ComponentSerialization.CODEC.parse(
                    FTBQuestsClient.holderLookup().createSerializationContext(JsonOps.INSTANCE),
                    element
            ).result().orElse(null);
        } catch (RuntimeException exception) {
            return false;
        }

        // 多组件 JSON 可能同时包含普通文字和多个样式，继续交给原版编辑器
        if (component == null || !component.getSiblings().isEmpty()) {
            return false;
        }

        // 识别玩家持久化数据占位组件并恢复专用配置页
        Optional<PlayerPersistentDataDescription.Placeholder> persistent_data =
                PlayerPersistentDataDescription.get(component);
        if (persistent_data.isPresent()) {
            PlayerPersistentDataDescription.Placeholder placeholder = persistent_data.get();
            openPersistentDataConfig(parent, placeholder.key(), placeholder.i18n(), save);
            return true;
        }

        // 保存时重新使用当前注册表上下文生成合法的 1.21.1 组件 JSON
        Consumer<Component> component_save = edited -> ComponentSerialization.CODEC.encodeStart(
                FTBQuestsClient.holderLookup().createSerializationContext(JsonOps.INSTANCE),
                edited
        ).result().ifPresent(serialized -> save.accept(serialized.toString()));
        Style style = component.getStyle();
        ClickEvent click_event = style.getClickEvent();

        // 点击事件组件可直接还原动作值和显示文字
        if (click_event != null) {
            TextAction action = switch (click_event) {
                case ClickEvent.OpenUrl ignored -> TextAction.WEB_LINK;
                case ClickEvent.CopyToClipboard ignored -> TextAction.COPY;
                case ClickEvent.RunCommand ignored -> TextAction.COMMAND;
                default -> null;
            };
            if (action != null) {
                String action_value = switch (click_event) {
                    case ClickEvent.OpenUrl open_url -> open_url.uri().toString();
                    case ClickEvent.CopyToClipboard copy -> copy.value();
                    case ClickEvent.RunCommand command -> command.command();
                    default -> "";
                };
                openTextComponentConfig(
                        parent,
                        action,
                        component.getString(),
                        action_value,
                        ChapterCanvasText.DEFAULT_FONT,
                        component_save
                );
                return true;
            }

            // 指定页跳转只接管带合法页码的任务链接，普通 FTB 任务链接保持原行为
            if (click_event instanceof ClickEvent.Custom custom
                    && custom.id().equals(MultilineTextEditorScreen.QUEST_LINK_ACTION)) {
                CompoundTag payload = custom.payload().flatMap(tag -> tag.asCompound()).orElse(null);
                if (payload != null) {
                    Quest quest = QuestObjectBase.parseHexId(payload.getStringOr("quest_id", "0"))
                            .map(ClientQuestFile.getInstance()::getQuest)
                            .orElse(null);
                    int page = payload.getIntOr("page", 1);
                    if (quest != null && page >= 1) {
                        openQuestPageConfig(parent, quest, component.getString(), page, component_save);
                        return true;
                    }
                }
            }
            return false;
        }

        // 悬停事件分别恢复文字内容或完整物品堆栈
        HoverEvent hover_event = style.getHoverEvent();
        if (hover_event != null) {
            if (hover_event instanceof HoverEvent.ShowText show_text) {
                openTextComponentConfig(
                        parent,
                        TextAction.HOVER_TEXT,
                        component.getString(),
                        show_text.value().getString(),
                        ChapterCanvasText.DEFAULT_FONT,
                        component_save
                );
                return true;
            }

            if (hover_event instanceof HoverEvent.ShowItem show_item) {
                openItemHoverConfig(parent, show_item.item().create(), component.getString(), component_save);
                return true;
            }
            return false;
        }

        // 内容类型组件从真实 contents 中恢复技术键和回退文字
        if (component.getContents() instanceof TranslatableContents contents) {
            openTextComponentConfig(
                    parent,
                    TextAction.TRANSLATION,
                    contents.getFallback() == null ? component.getString() : contents.getFallback(),
                    contents.getKey(),
                    ChapterCanvasText.DEFAULT_FONT,
                    component_save
            );
            return true;
        }
        if (component.getContents() instanceof KeybindContents contents) {
            openKeybindSelector(
                    parent,
                    contents.getName(),
                    component_save
            );
            return true;
        }

        // 字体和混淆样式只接管序列化结果中明确存在的对应字段
        if (json.has("font")) {
            openTextComponentConfig(
                    parent,
                    TextAction.FONT,
                    component.getString(),
                    "",
                    style.getFont() instanceof FontDescription.Resource resource
                            ? resource.id()
                            : ChapterCanvasText.DEFAULT_FONT,
                    component_save
            );
            return true;
        }
        if (style.isObfuscated()) {
            openTextComponentConfig(
                    parent,
                    TextAction.OBFUSCATED,
                    component.getString(),
                    "",
                    ChapterCanvasText.DEFAULT_FONT,
                    component_save
            );
            return true;
        }
        return false;
    }

    // 使用专用列表选择器编辑按键绑定，避免手写技术键造成错误
    private static void openKeybindSelector(
            Panel parent,
            String current_keybind,
            Consumer<Component> save
    ) {
        KeybindSelectionScreen.openSingle(parent, current_keybind, keybind -> save.accept(Component.keybind(keybind)));
    }

    // 配置玩家持久化数据占位组件的键名和翻译模式
    private static void openPersistentDataConfig(
            Panel parent,
            String initial_key,
            boolean initial_i18n,
            Consumer<String> save
    ) {
        String[] key = {initial_key};
        boolean[] i18n = {initial_i18n};
        EditableConfigGroup group = new EditableConfigGroup("quest_enhance", accepted -> {
            if (accepted && PlayerPersistentDataDescription.isValidKey(key[0])) {
                save.accept(QuestDescriptionComponents.playerPersistentData(key[0], i18n[0]));
            }
            parent.run();
        }) {
            @Override
            public Component getName() {
                return Component.translatable("quest_enhance.description_component.persistent_data");
            }
        };
        group.addString(
                "key",
                key[0],
                value -> key[0] = value,
                key[0],
                PlayerPersistentDataDescription.DATA_KEY
        ).setNameKey("quest_enhance.description_component.persistent_data.key");
        group.addBool(
                "i18n",
                i18n[0],
                value -> i18n[0] = value,
                false
        ).setNameKey("quest_enhance.description_component.persistent_data.i18n");
        new EditConfigScreen(group).setAutoclose(true).openGui();
    }

    // 根据所选类型配置显示文字与动作值，再生成原版文字组件
    private static void openTextComponentConfig(
            Panel parent,
            MultilineTextEditorAccess editor,
            TextAction action
    ) {
        String selected_text = editor.quest_enhance$get_selected_text();
        String display_text = selected_text.isBlank()
                ? Component.translatable(action.default_text_key).getString()
                : selected_text;
        String action_value = switch (action) {
            case HOVER_TEXT -> Component.translatable(
                    "quest_enhance.description_component.default.hover_content"
            ).getString();
            case COPY -> Component.translatable(
                    "quest_enhance.description_component.default.copy_value"
            ).getString();
            default -> action.default_value;
        };
        openTextComponentConfig(
                parent,
                action,
                display_text,
                action_value,
                ChapterCanvasText.DEFAULT_FONT,
                editor::quest_enhance$insert_component
        );
    }

    // 统一承载新建和编辑组件，保证两条入口使用相同字段与生成逻辑
    private static void openTextComponentConfig(
            Panel parent,
            TextAction action,
            String initial_display_text,
            String initial_action_value,
            Identifier initial_font,
            Consumer<Component> save
    ) {
        String[] display_text = {initial_display_text};
        String[] action_value = {initial_action_value};
        Identifier[] font = {initial_font};

        // 确认配置后按动作类型构造点击、悬停或样式组件
        EditableConfigGroup group = new EditableConfigGroup("quest_enhance", accepted -> {
            if (accepted) {
                Component component = switch (action) {
                    case WEB_LINK -> Component.literal(display_text[0]).withStyle(Style.EMPTY
                            .withColor(ChatFormatting.AQUA)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.OpenUrl(URI.create(action_value[0]))));
                    case HOVER_TEXT -> Component.literal(display_text[0]).withStyle(Style.EMPTY
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal(action_value[0]))));
                    case COPY -> Component.literal(display_text[0]).withStyle(Style.EMPTY
                            .withColor(ChatFormatting.AQUA)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.CopyToClipboard(action_value[0])));
                    case FONT -> Component.literal(display_text[0]).withStyle(style -> style.withFont(new FontDescription.Resource(font[0])));
                    case TRANSLATION -> Component.translatableWithFallback(action_value[0], display_text[0]);
                    case COMMAND -> Component.literal(display_text[0]).withStyle(Style.EMPTY
                            .withColor(ChatFormatting.GOLD)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.RunCommand(action_value[0])));
                    case KEYBIND -> Component.keybind(action_value[0]);
                    case OBFUSCATED -> Component.literal(display_text[0]).withStyle(ChatFormatting.OBFUSCATED);
                };
                save.accept(component);
            }
            parent.run();
        }) {
            @Override
            public Component getName() {
                return Component.translatable(action.title_key);
            }
        };

        // 按键绑定只需要技术键，其余组件均允许设置显示文字
        if (action != TextAction.KEYBIND) {
            group.addString("text", display_text[0], value -> display_text[0] = value, display_text[0], NON_EMPTY)
                    .setNameKey("quest_enhance.description_component.display_text");
        }

        // 每种动作只展示真正需要的额外配置字段
        switch (action) {
            case WEB_LINK -> group.addString(
                    "url",
                    action_value[0],
                    value -> action_value[0] = value,
                    action_value[0],
                    WEB_URL
            ).setNameKey("quest_enhance.description_component.url");
            case HOVER_TEXT -> group.addString(
                    "hover",
                    action_value[0],
                    value -> action_value[0] = value,
                    action_value[0],
                    NON_EMPTY
            ).setNameKey("quest_enhance.description_component.hover");
            case COPY -> group.addString(
                    "copy",
                    action_value[0],
                    value -> action_value[0] = value,
                    action_value[0],
                    NON_EMPTY
            ).setNameKey("quest_enhance.description_component.copy_value");
            case FONT -> {
                // 字体列表直接复用章节画布已经验证的资源包扫描结果
                List<Identifier> fonts = new ArrayList<>(ChapterCanvasText.getAvailableFonts());
                if (!fonts.contains(font[0])) {
                    fonts.add(font[0]);
                }
                NameMap<Identifier> font_map = NameMap.of(font[0], fonts)
                        .name(value -> Component.literal(value.toString()))
                        .create();
                group.addEnum(
                        "font",
                        font[0],
                        value -> font[0] = value,
                        font_map,
                        font[0]
                ).setNameKey("quest_enhance.description_component.font");
            }
            case TRANSLATION -> group.addString(
                    "translation",
                    action_value[0],
                    value -> action_value[0] = value,
                    action_value[0],
                    TECHNICAL_KEY
            ).setNameKey("quest_enhance.description_component.translation_key");
            case COMMAND -> group.addString(
                    "command",
                    action_value[0],
                    value -> action_value[0] = value,
                    action_value[0],
                    COMMAND
            ).setNameKey("quest_enhance.description_component.command_value");
            case KEYBIND -> group.addString(
                    "keybind",
                    action_value[0],
                    value -> action_value[0] = value,
                    action_value[0],
                    TECHNICAL_KEY
            ).setNameKey("quest_enhance.description_component.keybind_value");
            case OBFUSCATED -> {
            }
        }

        // 使用 FTB 原生配置屏幕承载输入、校验和确认行为
        new EditConfigScreen(group) {
            @Override
            public Component getTitle() {
                return group.getName();
            }
        }.openGui();
    }

    // 先使用 FTB 原生任务选择器选择目标任务
    private static void selectQuestPage(Panel parent, MultilineTextEditorAccess editor) {
        EditableQuestObject<Quest> quest_config = new EditableQuestObject<>(QuestObjectType.QUEST);
        new SelectQuestObjectScreen<>(quest_config, accepted -> {
            Quest quest = quest_config.getValue();
            if (accepted && quest != null) {
                String selected_text = editor.quest_enhance$get_selected_text();
                openQuestPageConfig(
                        parent,
                        quest,
                        selected_text.isBlank() ? quest.getTitle().getString() : selected_text,
                        1,
                        editor::quest_enhance$insert_component
                );
            } else {
                parent.run();
            }
        }).openGui();
    }

    // 配置目标任务页码和可点击显示文字
    private static void openQuestPageConfig(
            Panel parent,
            Quest quest,
            String initial_display_text,
            int initial_page,
            Consumer<Component> save
    ) {
        int page_count = Math.max(1, quest.buildDescriptionIndex().size());
        String[] display_text = {initial_display_text};
        int[] page = {Math.max(1, Math.min(initial_page, page_count))};

        // 确认后生成 FTB 已支持的任务 ID 与页码点击值
        EditableConfigGroup group = new EditableConfigGroup("quest_enhance", accepted -> {
            if (accepted) {
                CompoundTag payload = new CompoundTag();
                payload.putString("quest_id", quest.getCodeString());
                payload.putInt("page", page[0]);
                Component component = Component.literal(display_text[0]).withStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.Custom(
                                MultilineTextEditorScreen.QUEST_LINK_ACTION,
                                Optional.of(payload)
                        )));
                save.accept(component);
            }
            parent.run();
        }) {
            @Override
            public Component getName() {
                return Component.translatable("quest_enhance.description_component.quest_page");
            }
        };

        // 页码上限取目标任务当前实际描述页数
        group.addString(
                "text",
                display_text[0],
                value -> display_text[0] = value,
                display_text[0],
                NON_EMPTY
        ).setNameKey("quest_enhance.description_component.display_text");
        group.addInt("page", page[0], value -> page[0] = value, page[0], 1, page_count)
                .setNameKey("quest_enhance.description_component.page");
        new EditConfigScreen(group).openGui();
    }

    // 使用 FTB Library 原生物品列表选择物品图标或物品悬停
    private static void selectItem(
            Panel parent,
            MultilineTextEditorAccess editor,
            boolean hover
    ) {
        EditableItemStack item_config = new EditableItemStack(1L);
        new SelectItemStackScreen(item_config, accepted -> {
            if (accepted && !item_config.getValue().isEmpty()) {
                if (hover) {
                    openItemHoverConfig(parent, editor, item_config.getValue());
                } else {
                    openItemIconConfig(parent, editor, item_config.getValue());
                }
            } else {
                parent.run();
            }
        }).openGui();
    }

    // 配置物品图标尺寸、对齐方式和可选悬停文字
    private static void openItemIconConfig(
            Panel parent,
            MultilineTextEditorAccess editor,
            ItemStack selected_stack
    ) {
        // 图标保留完整 Data Components、耐久和数量
        String item_icon = ItemIcon.ofItemStack(selected_stack.copy()).toString();
        openItemIconConfig(
                parent,
                item_icon,
                18,
                18,
                ImageAlign.CENTER,
                false,
                selected_stack.getHoverName().getString(),
                markup -> editor.quest_enhance$insert_at_end_of_line("\n" + markup)
        );
    }

    // 编辑已有物品图片时保留物品图标类型和当前布局参数
    public static void editItemIcon(
            Panel parent,
            ImageComponent component,
            String hover_text,
            Consumer<String> save
    ) {
        openItemIconConfig(
                parent,
                component.imageStr(),
                component.getWidth(),
                component.getHeight(),
                component.getAlign(),
                component.isFit(),
                hover_text,
                save
        );
    }

    // 统一承载新建和编辑物品图标，避免原生图片选择器丢失 item 图标
    private static void openItemIconConfig(
            Panel parent,
            String item_icon,
            int initial_width,
            int initial_height,
            ImageAlign initial_align,
            boolean initial_fit,
            String initial_hover_text,
            Consumer<String> save
    ) {
        int[] width = {initial_width};
        int[] height = {initial_height};
        ImageAlign[] align = {initial_align};
        boolean[] fit = {initial_fit};
        String[] hover_text = {initial_hover_text};

        // 物品图标沿用 FTB 原生序列化格式保存完整 Data Components
        EditableConfigGroup group = new EditableConfigGroup("quest_enhance", accepted -> {
            if (accepted) {
                save.accept(imageMarkup(
                        item_icon,
                        width[0],
                        height[0],
                        align[0],
                        fit[0],
                        hover_text[0]
                ));
            }
            parent.run();
        }) {
            @Override
            public Component getName() {
                return Component.translatable("quest_enhance.description_component.item_icon");
            }
        };
        addImageFields(group, width, height, align, fit, hover_text);
        new EditConfigScreen(group).openGui();
    }

    // 配置物品 tooltip 前方显示的文字
    private static void openItemHoverConfig(
            Panel parent,
            MultilineTextEditorAccess editor,
            ItemStack selected_stack
    ) {
        String selected_text = editor.quest_enhance$get_selected_text();
        openItemHoverConfig(
                parent,
                selected_stack,
                selected_text.isBlank() ? selected_stack.getHoverName().getString() : selected_text,
                editor::quest_enhance$insert_component
        );
    }

    // 统一承载新建和编辑物品悬停组件，并保留完整物品数据组件
    private static void openItemHoverConfig(
            Panel parent,
            ItemStack selected_stack,
            String initial_display_text,
            Consumer<Component> save
    ) {
        String[] display_text = {initial_display_text};

        // 物品悬停保留所选物品的 Data Components 供 tooltip 展示
        EditableConfigGroup group = new EditableConfigGroup("quest_enhance", accepted -> {
            if (accepted) {
                Component component = Component.literal(display_text[0]).withStyle(Style.EMPTY.withHoverEvent(
                        new HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(selected_stack.copy()))
                ));
                save.accept(component);
            }
            parent.run();
        }) {
            @Override
            public Component getName() {
                return Component.translatable("quest_enhance.description_component.item_hover");
            }
        };
        group.addString(
                "text",
                display_text[0],
                value -> display_text[0] = value,
                display_text[0],
                NON_EMPTY
        ).setNameKey("quest_enhance.description_component.display_text");
        new EditConfigScreen(group).openGui();
    }

    // 配置由 FTB URLImageIcon 加载的网络图片
    private static void openRemoteImageConfig(Panel parent, MultilineTextEditorAccess editor) {
        openRemoteImageConfig(
                parent,
                "https://example.com/image.png",
                100,
                100,
                ImageAlign.CENTER,
                true,
                "",
                markup -> editor.quest_enhance$insert_at_end_of_line("\n" + markup)
        );
    }

    // 编辑已有网络图片时保留网址和当前布局参数
    public static void editRemoteImage(
            Panel parent,
            ImageComponent component,
            String hover_text,
            Consumer<String> save
    ) {
        openRemoteImageConfig(
                parent,
                component.imageStr(),
                component.getWidth(),
                component.getHeight(),
                component.getAlign(),
                component.isFit(),
                hover_text,
                save
        );
    }

    // 统一承载新建和编辑网络图片，避免原生纹理选择器丢失网址
    private static void openRemoteImageConfig(
            Panel parent,
            String initial_url,
            int initial_width,
            int initial_height,
            ImageAlign initial_align,
            boolean initial_fit,
            String initial_hover_text,
            Consumer<String> save
    ) {
        String[] url = {initial_url};
        int[] width = {initial_width};
        int[] height = {initial_height};
        ImageAlign[] align = {initial_align};
        boolean[] fit = {initial_fit};
        String[] hover_text = {initial_hover_text};

        // 网络图片继续使用 FTB 原生图片标记，不增加新的保存格式
        EditableConfigGroup group = new EditableConfigGroup("quest_enhance", accepted -> {
            if (accepted) {
                save.accept(imageMarkup(
                        url[0],
                        width[0],
                        height[0],
                        align[0],
                        fit[0],
                        hover_text[0]
                ));
            }
            parent.run();
        }) {
            @Override
            public Component getName() {
                return Component.translatable("quest_enhance.description_component.remote_image");
            }
        };
        group.addString("url", url[0], value -> url[0] = value, url[0], WEB_URL)
                .setNameKey("quest_enhance.description_component.url");
        addImageFields(group, width, height, align, fit, hover_text);
        new EditConfigScreen(group).openGui();
    }

    // 向物品与网络图片配置页加入相同的尺寸和显示字段
    private static void addImageFields(
            EditableConfigGroup group,
            int[] width,
            int[] height,
            ImageAlign[] align,
            boolean[] fit,
            String[] hover_text
    ) {
        group.addInt("width", width[0], value -> width[0] = value, width[0], 1, 1000)
                .setNameKey("quest_enhance.description_component.width");
        group.addInt("height", height[0], value -> height[0] = value, height[0], 1, 1000)
                .setNameKey("quest_enhance.description_component.height");
        group.addEnum("align", align[0], value -> align[0] = value, ImageAlign.NAME_MAP, ImageAlign.CENTER)
                .setNameKey("quest_enhance.description_component.align");
        group.addBool("fit", fit[0], value -> fit[0] = value, fit[0])
                .setNameKey("quest_enhance.description_component.fit");
        group.addString("hover", hover_text[0], value -> hover_text[0] = value, "", NO_BRACES)
                .setNameKey("quest_enhance.description_component.hover_optional");
    }

    // 生成 FTB 原生图片标记并按其规则转义属性中的空格
    private static String imageMarkup(
            String icon,
            int width,
            int height,
            ImageAlign align,
            boolean fit,
            String hover_text
    ) {
        StringBuilder markup = new StringBuilder("{image:")
                .append(icon.replace(" ", "%20"))
                .append(" width:").append(width)
                .append(" height:").append(height)
                .append(" align:").append(align.getName());
        if (fit) {
            markup.append(" fit:true");
        }
        if (!hover_text.isBlank()) {
            markup.append(" text:").append(hover_text.replace(" ", "%20"));
        }
        return markup.append('}').toString();
    }

    // 打开 Quest Enhance 已有的视频选择与显示文字配置流程
    private static void openVideoSelector(Panel parent, MultilineTextEditorAccess editor) {
        VideoSelectionScreen.open(parent, "", false, video_path -> {
            if (!video_path.isBlank()) {
                DescriptionVideoConfigScreen.open(parent, editor, video_path);
            }
        });
    }

    // 打开资源包 GIF 选择器并插入动态图片标记
    private static void openGifSelector(Panel parent, MultilineTextEditorAccess editor) {
        GifSelectionScreen.open(parent, null, resource_location ->
                editor.quest_enhance$insert_at_end_of_line(
                        "\n" + QuestDescriptionGif.createMarkup(resource_location)
                )
        );
    }

    private enum TextAction {
        WEB_LINK(
                "quest_enhance.description_component.web_link",
                "quest_enhance.description_component.default.web_link",
                "https://example.com"
        ),
        HOVER_TEXT(
                "quest_enhance.description_component.hover_text",
                "quest_enhance.description_component.default.hover_text",
                ""
        ),
        COPY(
                "quest_enhance.description_component.copy",
                "quest_enhance.description_component.default.copy",
                ""
        ),
        FONT(
                "quest_enhance.description_component.font",
                "quest_enhance.description_component.default.font",
                ""
        ),
        TRANSLATION(
                "quest_enhance.description_component.translation",
                "quest_enhance.description_component.default.translation",
                "quest_enhance.example"
        ),
        COMMAND(
                "quest_enhance.description_component.command",
                "quest_enhance.description_component.default.command",
                "/help"
        ),
        KEYBIND(
                "quest_enhance.description_component.keybind",
                "quest_enhance.description_component.default.keybind",
                "key.jump"
        ),
        OBFUSCATED(
                "quest_enhance.description_component.obfuscated",
                "quest_enhance.description_component.default.obfuscated",
                ""
        );

        private final String title_key;
        private final String default_text_key;
        private final String default_value;

        // 保存配置页标题、默认显示文字和默认技术值
        TextAction(String title_key, String default_text_key, String default_value) {
            this.title_key = title_key;
            this.default_text_key = default_text_key;
            this.default_value = default_value;
        }
    }
}
