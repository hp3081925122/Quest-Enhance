package com.quest_enhance.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ItemStackConfig;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.config.ui.resource.SelectItemStackScreen;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.util.client.ImageComponent.ImageAlign;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.FTBQuestsClient;
import dev.ftb.mods.ftbquests.client.gui.SelectQuestObjectScreen;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.QuestObjectType;
import dev.ftb.mods.ftbquests.util.ConfigQuestObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
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
        List<ContextMenuItem> media = List.of(
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
                ),
                new ContextMenuItem(
                        Component.translatable("quest_enhance.video.description.add"),
                        Icons.CAMERA,
                        button -> openVideoSelector(parent, editor)
                )
        );

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
                        button -> openTextComponentConfig(parent, editor, TextAction.KEYBIND)
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
        JsonObject json;
        Component component;
        try {
            JsonElement element = JsonParser.parseString(raw_text);
            if (!element.isJsonObject()) {
                return false;
            }
            json = element.getAsJsonObject();
            component = Component.Serializer.fromJson(raw_text, FTBQuestsClient.holderLookup());
        } catch (RuntimeException exception) {
            return false;
        }

        // 多组件 JSON 可能同时包含普通文字和多个样式，继续交给原版编辑器
        if (component == null || !component.getSiblings().isEmpty()) {
            return false;
        }

        // 保存时重新使用当前注册表上下文生成合法的 1.21.1 组件 JSON
        Consumer<Component> component_save = edited -> save.accept(Component.Serializer.toJson(
                edited,
                FTBQuestsClient.holderLookup()
        ));
        Style style = component.getStyle();
        ClickEvent click_event = style.getClickEvent();

        // 点击事件组件可直接还原动作值和显示文字
        if (click_event != null) {
            TextAction action = switch (click_event.getAction()) {
                case OPEN_URL -> TextAction.WEB_LINK;
                case COPY_TO_CLIPBOARD -> TextAction.COPY;
                case RUN_COMMAND -> TextAction.COMMAND;
                default -> null;
            };
            if (action != null) {
                openTextComponentConfig(
                        parent,
                        action,
                        component.getString(),
                        click_event.getValue(),
                        ChapterCanvasText.DEFAULT_FONT,
                        component_save
                );
                return true;
            }

            // 指定页跳转只接管带合法页码的任务链接，普通 FTB 任务链接保持原行为
            if (click_event.getAction() == ClickEvent.Action.CHANGE_PAGE) {
                String[] fields = click_event.getValue().split("/", 2);
                if (fields.length == 2) {
                    try {
                        int page = Integer.parseInt(fields[1]);
                        Quest quest = QuestObjectBase.parseHexId(fields[0])
                                .map(ClientQuestFile.INSTANCE::getQuest)
                                .orElse(null);
                        if (quest != null && page >= 1) {
                            openQuestPageConfig(parent, quest, component.getString(), page, component_save);
                            return true;
                        }
                    } catch (NumberFormatException exception) {
                        return false;
                    }
                }
            }
            return false;
        }

        // 悬停事件分别恢复文字内容或完整物品堆栈
        HoverEvent hover_event = style.getHoverEvent();
        if (hover_event != null) {
            Component hover_text = hover_event.getValue(HoverEvent.Action.SHOW_TEXT);
            if (hover_text != null) {
                openTextComponentConfig(
                        parent,
                        TextAction.HOVER_TEXT,
                        component.getString(),
                        hover_text.getString(),
                        ChapterCanvasText.DEFAULT_FONT,
                        component_save
                );
                return true;
            }

            HoverEvent.ItemStackInfo item_info = hover_event.getValue(HoverEvent.Action.SHOW_ITEM);
            if (item_info != null) {
                openItemHoverConfig(parent, item_info.getItemStack(), component.getString(), component_save);
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
            openTextComponentConfig(
                    parent,
                    TextAction.KEYBIND,
                    component.getString(),
                    contents.getName(),
                    ChapterCanvasText.DEFAULT_FONT,
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
                    style.getFont(),
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
            ResourceLocation initial_font,
            Consumer<Component> save
    ) {
        String[] display_text = {initial_display_text};
        String[] action_value = {initial_action_value};
        ResourceLocation[] font = {initial_font};

        // 确认配置后按动作类型构造点击、悬停或样式组件
        ConfigGroup group = new ConfigGroup("quest_enhance", accepted -> {
            if (accepted) {
                Component component = switch (action) {
                    case WEB_LINK -> Component.literal(display_text[0]).withStyle(Style.EMPTY
                            .withColor(ChatFormatting.AQUA)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, action_value[0])));
                    case HOVER_TEXT -> Component.literal(display_text[0]).withStyle(Style.EMPTY
                            .withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    Component.literal(action_value[0])
                            )));
                    case COPY -> Component.literal(display_text[0]).withStyle(Style.EMPTY
                            .withColor(ChatFormatting.AQUA)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent(
                                    ClickEvent.Action.COPY_TO_CLIPBOARD,
                                    action_value[0]
                            )));
                    case FONT -> Component.literal(display_text[0]).withStyle(style -> style.withFont(font[0]));
                    case TRANSLATION -> Component.translatableWithFallback(action_value[0], display_text[0]);
                    case COMMAND -> Component.literal(display_text[0]).withStyle(Style.EMPTY
                            .withColor(ChatFormatting.GOLD)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, action_value[0])));
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
                List<ResourceLocation> fonts = new ArrayList<>(ChapterCanvasText.getAvailableFonts());
                if (!fonts.contains(font[0])) {
                    fonts.add(font[0]);
                }
                NameMap<ResourceLocation> font_map = NameMap.of(font[0], fonts)
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
        ConfigQuestObject<Quest> quest_config = new ConfigQuestObject<>(QuestObjectType.QUEST);
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
        ConfigGroup group = new ConfigGroup("quest_enhance", accepted -> {
            if (accepted) {
                Component component = Component.literal(display_text[0]).withStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.CHANGE_PAGE,
                                quest.getCodeString() + "/" + page[0]
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
        ItemStackConfig item_config = new ItemStackConfig(1L);
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
        int[] width = {18};
        int[] height = {18};
        ImageAlign[] align = {ImageAlign.CENTER};
        boolean[] fit = {false};
        String[] hover_text = {selected_stack.getHoverName().getString()};
        String item_icon = "item:" + BuiltInRegistries.ITEM.getKey(selected_stack.getItem());

        // 物品图标只保存注册名，避免完整 Data Components 写入任务描述
        ConfigGroup group = new ConfigGroup("quest_enhance", accepted -> {
            if (accepted) {
                editor.quest_enhance$insert_at_end_of_line("\n" + imageMarkup(
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
        ConfigGroup group = new ConfigGroup("quest_enhance", accepted -> {
            if (accepted) {
                Component component = Component.literal(display_text[0]).withStyle(Style.EMPTY.withHoverEvent(
                        new HoverEvent(
                                HoverEvent.Action.SHOW_ITEM,
                                new HoverEvent.ItemStackInfo(selected_stack.copy())
                        )
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
        String[] url = {"https://example.com/image.png"};
        int[] width = {100};
        int[] height = {100};
        ImageAlign[] align = {ImageAlign.CENTER};
        boolean[] fit = {true};
        String[] hover_text = {""};

        // 网络图片继续使用 FTB 原生图片标记，不增加新的保存格式
        ConfigGroup group = new ConfigGroup("quest_enhance", accepted -> {
            if (accepted) {
                editor.quest_enhance$insert_at_end_of_line("\n" + imageMarkup(
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
            ConfigGroup group,
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
