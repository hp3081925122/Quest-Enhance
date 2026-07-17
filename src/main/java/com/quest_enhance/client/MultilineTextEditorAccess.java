package com.quest_enhance.client;

import net.minecraft.network.chat.Component;

public interface MultilineTextEditorAccess {
    void quest_enhance$insert_at_end_of_line(String text);

    boolean quest_enhance$has_single_line_selection();

    String quest_enhance$get_selected_text();

    void quest_enhance$insert_component(Component component);
}
