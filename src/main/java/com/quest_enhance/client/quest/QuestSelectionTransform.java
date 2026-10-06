package com.quest_enhance.client.quest;

import com.quest_enhance.mixin.QuestScreenAccessor;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.client.gui.widget.ContextMenuItem;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import dev.ftb.mods.ftbquests.quest.Movable;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestLink;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class QuestSelectionTransform {
    public enum Operation {
        ROTATE_CLOCKWISE,
        ROTATE_COUNTERCLOCKWISE,
        MIRROR_HORIZONTAL,
        MIRROR_VERTICAL
    }

    private QuestSelectionTransform() {
    }

    public static ContextMenuItem createMenu(QuestScreen screen) {
        return ContextMenuItem.subMenu(
                Component.translatable("quest_enhance.selection_transform"),
                Icons.INFO,
                List.of(
                        createItem(screen, Operation.ROTATE_CLOCKWISE, "quest_enhance.selection_transform.rotate_clockwise"),
                        createItem(screen, Operation.ROTATE_COUNTERCLOCKWISE, "quest_enhance.selection_transform.rotate_counterclockwise"),
                        createItem(screen, Operation.MIRROR_HORIZONTAL, "quest_enhance.selection_transform.mirror_horizontal"),
                        createItem(screen, Operation.MIRROR_VERTICAL, "quest_enhance.selection_transform.mirror_vertical")
                )
        );
    }

    public static void apply(QuestScreen screen, Operation operation) {
        QuestScreenAccessor accessor = (QuestScreenAccessor) (Object) screen;
        Chapter chapter = accessor.quest_enhance$get_selected_chapter();
        List<Movable> selected = accessor.quest_enhance$get_selected_objects().stream()
                .distinct()
                .filter(object -> object != null && object.getChapter() == chapter)
                .toList();
        if (chapter == null || !accessor.quest_enhance$get_file().canEdit()) {
            QuestScreen.displayError(Component.translatable("quest_enhance.selection_transform.no_permission"));
            return;
        }
        if (selected.size() < 2) {
            QuestScreen.displayError(Component.translatable("quest_enhance.selection_transform.need_multiple"));
            return;
        }
        if (selected.stream().anyMatch(object -> !(object instanceof Quest)
                && !(object instanceof QuestLink)
                && !(object instanceof ChapterImage))) {
            QuestScreen.displayError(Component.translatable("quest_enhance.selection_transform.unsupported"));
            return;
        }

        double left = selected.stream()
                .mapToDouble(object -> object.getX() - object.getWidth() / 2.0D)
                .min()
                .orElse(0.0D);
        double right = selected.stream()
                .mapToDouble(object -> object.getX() + object.getWidth() / 2.0D)
                .max()
                .orElse(0.0D);
        double top = selected.stream()
                .mapToDouble(object -> object.getY() - object.getHeight() / 2.0D)
                .min()
                .orElse(0.0D);
        double bottom = selected.stream()
                .mapToDouble(object -> object.getY() + object.getHeight() / 2.0D)
                .max()
                .orElse(0.0D);
        double center_x = (left + right) / 2.0D;
        double center_y = (top + bottom) / 2.0D;

        for (Movable object : selected) {
            double x = object.getX();
            double y = object.getY();
            double transformed_x = x;
            double transformed_y = y;
            switch (operation) {
                case ROTATE_CLOCKWISE -> {
                    transformed_x = center_x - (y - center_y);
                    transformed_y = center_y + (x - center_x);
                }
                case ROTATE_COUNTERCLOCKWISE -> {
                    transformed_x = center_x + (y - center_y);
                    transformed_y = center_y - (x - center_x);
                }
                case MIRROR_HORIZONTAL -> transformed_x = center_x * 2.0D - x;
                case MIRROR_VERTICAL -> transformed_y = center_y * 2.0D - y;
            }
            object.requestMove(chapter, transformed_x, transformed_y);
        }
        screen.refreshQuestPanel();
    }

    private static ContextMenuItem createItem(
            QuestScreen screen,
            Operation operation,
            String translation_key
    ) {
        return new ContextMenuItem(
                Component.translatable(translation_key),
                Icons.INFO,
                button -> apply(screen, operation)
        );
    }
}
