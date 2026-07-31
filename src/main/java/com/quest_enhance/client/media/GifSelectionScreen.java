package com.quest_enhance.client.media;

import com.quest_enhance.QuestEnhance;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.client.gui.widget.Panel;
import dev.ftb.mods.ftblibrary.client.gui.widget.SimpleTextButton;
import dev.ftb.mods.ftblibrary.client.gui.screens.AbstractButtonListScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public final class GifSelectionScreen extends AbstractButtonListScreen {
    private final Panel parent_panel;
    private final Identifier current_gif;
    private final Consumer<Identifier> selection_callback;
    private final List<Identifier> gifs;

    private GifSelectionScreen(Panel parent_panel, Identifier current_gif, Consumer<Identifier> selection_callback) {
        this.parent_panel = parent_panel;
        this.current_gif = current_gif;
        this.selection_callback = selection_callback;
        this.gifs = new ArrayList<>(Minecraft.getInstance().getResourceManager()
                .listResources("textures", location -> location.getNamespace().equals(QuestEnhance.MOD_ID)
                        && location.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".gif"))
                .keySet());
        this.gifs.sort(Comparator.comparing(Identifier::toString));
        this.setTitle(Component.translatable("quest_enhance.gif.select"));
        this.setHasSearchBox(true);
    }

    // 使用 FTB 原生列表窗口选择当前资源包中的 GIF 文件
    public static void open(Panel parent_panel, Identifier current_gif, Consumer<Identifier> selection_callback) {
        new GifSelectionScreen(parent_panel, current_gif, selection_callback).openGui();
    }

    @Override
    public void addButtons(Panel panel) {
        if (this.gifs.isEmpty()) {
            panel.add(SimpleTextButton.create(
                    panel,
                    Component.translatable("quest_enhance.gif.empty"),
                    Icons.INFO,
                    button -> {
                    }
            ));
            return;
        }

        // 显示完整资源标识，避免同名 GIF 在不同路径下难以区分
        for (Identifier gif : this.gifs) {
            Component name = Component.literal(gif.toString());
            Component label = gif.equals(this.current_gif)
                    ? Component.translatable("quest_enhance.gif.selected", name)
                    : name;
            panel.add(SimpleTextButton.create(
                    panel,
                    label,
                    Icons.CAMERA,
                    button -> {
                        this.parent_panel.run();
                        this.selection_callback.accept(gif);
                    }
            ));
        }
    }

    @Override
    public boolean onInit() {
        int target_width = Mth.clamp(420, 176, this.getWindow().getGuiScaledWidth() * 3 / 4);
        int target_height = Mth.clamp(
                this.gifs.size() * 20 + 50,
                166,
                this.getWindow().getGuiScaledHeight() * 4 / 5
        );
        this.setSize(target_width, target_height);
        return super.onInit();
    }

    @Override
    public void onBack() {
        this.parent_panel.run();
    }

    @Override
    protected void doCancel() {
        this.parent_panel.run();
    }

    @Override
    protected void doAccept() {
        this.parent_panel.run();
    }
}
