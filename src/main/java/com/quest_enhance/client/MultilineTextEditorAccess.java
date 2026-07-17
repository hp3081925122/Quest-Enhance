package com.quest_enhance.client;

import net.minecraft.network.chat.Component;

public interface MultilineTextEditorAccess {
    // 在当前物理行末尾插入 FTB 描述标记
    void quest_enhance$insert_at_end_of_line(String text);

    // 判断当前选择是否能安全转换为单行组件
    boolean quest_enhance$has_single_line_selection();

    // 读取可安全转换的当前选择文字
    String quest_enhance$get_selected_text();

    // 使用当前版本注册表上下文插入原版文字组件
    void quest_enhance$insert_component(Component component);
}
