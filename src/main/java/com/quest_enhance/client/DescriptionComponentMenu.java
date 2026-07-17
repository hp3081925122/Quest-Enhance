package com.quest_enhance.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ItemStackConfig;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.config.ui.SelectItemStackScreen;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.util.client.ImageComponent;
import dev.ftb.mods.ftblibrary.util.client.ImageComponent.ImageAlign;
import net.minecraft.ChatFormatting;
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
import java.util.Locale;
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

    // 打开统一的描述组件菜单，只补充 FTB 原生工具栏没有的功能
    public static void open(Panel parent, MultilineTextEditorAccess editor) {
        // 把交互、媒体和文字组件分组，避免工具栏继续横向增长
        List<ContextMenuItem> navigation = List.of(
                new ContextMenuItem(
                        Component.translatable("quest_enhance.description_component.web_link"),
                        Icons.GLOBE,
                        button -> openTextComponentConfig(parent, editor, TextAction.WEB_LINK)
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

        // 媒体分组提供物品、网络图片和 Quest Enhance 视频能力
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

        // 文字分组生成原版 JSON 组件，保持跨任务文件和语言文件兼容
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
                        Component.translatable("quest_enhance.description_component.table"),
                        Icons.INV_IO,
                        button -> QuestDescriptionTable.openConfig(
                                parent,
                                QuestDescriptionTable.defaultTable(),
                                markup -> editor.quest_enhance$insert_at_end_of_line("\n" + markup)
                        )
                )
        );

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
            component = Component.Serializer.fromJson(raw_text);
        } catch (RuntimeException exception) {
            return false;
        }

        // 多组件 JSON 可能包含多个独立样式，继续交给 FTB 原编辑器
        if (component == null || !component.getSiblings().isEmpty()) {
            return false;
        }

        // 保存时使用 1.20.1 原版序列化器生成合法组件 JSON
        Consumer<Component> component_save = edited -> save.accept(Component.Serializer.toJson(edited));
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
            if (action == null) {
                return false;
            }
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

        // 自定义字体只接管序列化结果中明确存在 font 字段的组件
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
        return false;
    }

    // 根据所选类型配置显示文字和动作值，再序列化为原版文字组件
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

        // 确认后根据动作类型构造带样式、悬停或点击事件的组件
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

        // 按组件类型只显示真正需要的配置字段
        if (action != TextAction.KEYBIND) {
            group.addString("text", display_text[0], value -> display_text[0] = value, display_text[0], NON_EMPTY)
                    .setNameKey("quest_enhance.description_component.display_text");
        }
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
        }

        // 使用 FTB 原生配置屏幕承载输入和确认行为
        new EditConfigScreen(group) {
            @Override
            public Component getTitle() {
                return group.getName();
            }
        }.openGui();
    }

    // 使用 FTB Library 原生物品列表选择展示图标或悬停物品
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
        // 图标只保存物品类型，避免把背包物品的临时 NBT 写入任务描述
        ItemStack icon_stack = new ItemStack(selected_stack.getItem());
        openItemIconConfig(
                parent,
                ItemIcon.getItemIcon(icon_stack).toString(),
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
                component.image.toString(),
                component.width,
                component.height,
                component.align,
                component.fit,
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

        // 物品图标继续使用 FTB 原生图片标记，不改变任务描述格式
        ConfigGroup group = new ConfigGroup("quest_enhance", accepted -> {
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

    // 配置物品 tooltip 前方显示的可点击文字
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

    // 统一承载新建和编辑物品悬停组件，并保留完整物品数据
    private static void openItemHoverConfig(
            Panel parent,
            ItemStack selected_stack,
            String initial_display_text,
            Consumer<Component> save
    ) {
        String[] display_text = {initial_display_text};
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
                component.image.toString(),
                component.width,
                component.height,
                component.align,
                component.fit,
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

        // 网络图片继续使用 FTB 原生图片标记，不改变任务描述格式
        ConfigGroup group = new ConfigGroup("quest_enhance", accepted -> {
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

    // 向物品和网络图片配置页加入相同的尺寸与显示字段
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

    // 生成 FTB Library 原生图片标记并按其规则转义属性中的空格
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
                .append(" align:").append(align.name().toLowerCase(Locale.ROOT));
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
