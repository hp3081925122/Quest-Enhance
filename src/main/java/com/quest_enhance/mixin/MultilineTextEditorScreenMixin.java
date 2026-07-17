package com.quest_enhance.mixin;

import com.google.gson.JsonArray;
import com.quest_enhance.QuestEnhance;
import com.quest_enhance.client.MultilineTextEditorAccess;
import com.quest_enhance.client.QuestDescriptionWidthContext;
import com.quest_enhance.client.WindowsClipboardImage;
import com.mojang.blaze3d.platform.NativeImage;
import dev.ftb.mods.ftblibrary.config.ConfigCallback;
import dev.ftb.mods.ftblibrary.config.ListConfig;
import dev.ftb.mods.ftblibrary.config.StringConfig;
import dev.ftb.mods.ftblibrary.ui.MultilineTextBox;
import dev.ftb.mods.ftblibrary.ui.MultilineTextBox.StringExtents;
import dev.ftb.mods.ftblibrary.ui.input.Key;
import dev.ftb.mods.ftblibrary.util.client.ImageComponent;
import dev.ftb.mods.ftbquests.client.gui.MultilineTextEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Component.Serializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Mixin(value = MultilineTextEditorScreen.class, remap = false)
public abstract class MultilineTextEditorScreenMixin implements MultilineTextEditorAccess {
    @Unique
    private int quest_enhance$description_content_width;

    @Shadow
    @Final
    private MultilineTextBox textBox;

    // 复用 FTB Quests 原有的行末图片组件插入行为
    @Invoker("insertAtEndOfLine")
    @Override
    public abstract void quest_enhance$insert_at_end_of_line(String text);

    // 只允许原生任务链接处理单行选择内容
    @Override
    public boolean quest_enhance$has_single_line_selection() {
        return this.textBox.hasSelection() && !this.textBox.getSelectedText().contains("\n");
    }

    // 为快速组件配置页提供当前选择文字
    @Override
    public String quest_enhance$get_selected_text() {
        return this.quest_enhance$has_single_line_selection()
                ? this.textBox.getSelectedText()
                : "";
    }

    // 把原版文字组件安全地写成 JSON，并保留选择内容所在行的前后文字
    @Override
    public void quest_enhance$insert_component(Component component) {
        if (!this.quest_enhance$has_single_line_selection()) {
            this.quest_enhance$insert_at_end_of_line("\n" + Serializer.toJson(component));
            return;
        }

        String editor_text = this.textBox.getText();
        StringExtents selection = this.textBox.getSelected();
        int line_start = editor_text.lastIndexOf('\n', selection.start() - 1) + 1;
        int next_line = editor_text.indexOf('\n', selection.end());
        int line_end = next_line < 0 ? editor_text.length() : next_line;
        String line = editor_text.substring(line_start, line_end);

        // 已经是 JSON 的行无法仅凭原始字符选择可靠定位组件，改为插入新行
        if ((line.startsWith("{") || line.startsWith("["))
                && (line.endsWith("}") || line.endsWith("]"))) {
            this.quest_enhance$insert_at_end_of_line("\n" + Serializer.toJson(component));
            return;
        }

        // 使用 Gson 组件树转义引号和特殊字符，避免生成损坏的任务描述
        JsonArray parts = new JsonArray();
        String before = editor_text.substring(line_start, selection.start());
        String after = editor_text.substring(selection.end(), line_end);
        if (!before.isEmpty()) {
            parts.add(Serializer.toJsonTree(Component.literal(before)));
        }
        parts.add(Serializer.toJsonTree(component));
        if (!after.isEmpty()) {
            parts.add(Serializer.toJsonTree(Component.literal(after)));
        }

        // 按 FTB 原编辑器的选择替换方式更新整行并恢复文本框焦点
        this.textBox.setSelecting(false);
        this.textBox.seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE, line_start);
        this.textBox.setSelecting(true);
        this.textBox.seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE, line_end);
        this.textBox.insertText(parts.toString());
        this.textBox.setSelecting(false);
        this.textBox.setFocused(true);
    }

    // 接收任务节点介绍面板的内容宽度，其他多行编辑器保持原有默认尺寸
    @Inject(method = "<init>", at = @At("RETURN"))
    private void quest_enhance$receive_description_width(
            Component title,
            ListConfig<String, StringConfig> config,
            ConfigCallback callback,
            CallbackInfo callback_info
    ) {
        this.quest_enhance$description_content_width = QuestDescriptionWidthContext.consume();
    }

    // 打开 FTB 原生图片选择器时绑定当前任务介绍内容宽度
    @Inject(method = "openImageSelector", at = @At("HEAD"))
    private void quest_enhance$activate_description_width(CallbackInfo callback_info) {
        QuestDescriptionWidthContext.activate(
                this.quest_enhance$description_content_width > 0
                        ? this.quest_enhance$description_content_width
                        : 100
        );
    }

    // 把描述编辑器中新建图片组件的自适应选项默认设为开启
    @ModifyVariable(method = "openImageSelector", at = @At("STORE"), ordinal = 0)
    private ImageComponent quest_enhance$enable_fit_by_default(ImageComponent component) {
        component.fit = true;
        return component;
    }

    // 在任务描述文本框聚焦时优先处理剪贴板图片
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$paste_clipboard_image(Key key, CallbackInfoReturnable<Boolean> callback_info) {
        if (!this.textBox.isFocused() || !key.paste()) {
            return;
        }

        // 通过 Windows 原生接口读取图片并保留普通文本粘贴行为
        BufferedImage buffered_image;
        try {
            buffered_image = WindowsClipboardImage.read();
            if (buffered_image == null) {
                return;
            }
        } catch (IOException | RuntimeException exception) {
            QuestEnhance.LOGGER.error("Failed to read a clipboard image", exception);
            return;
        }

        callback_info.setReturnValue(true);

        // 将剪贴板图片保存为可持久化的 PNG 资源
        try {
            // 把图片写入模组自己的配置资源目录
            Minecraft minecraft = Minecraft.getInstance();
            Path output_directory = FMLPaths.CONFIGDIR.get()
                    .resolve(QuestEnhance.MOD_ID)
                    .resolve("assets")
                    .resolve(QuestEnhance.MOD_ID)
                    .resolve("textures")
                    .resolve("ftb");
            Files.createDirectories(output_directory);

            String file_name = "clipboard_" + System.currentTimeMillis() + ".png";
            Path output_path = output_directory.resolve(file_name);
            if (!ImageIO.write(buffered_image, "png", output_path.toFile())) {
                throw new IOException("No PNG writer is available");
            }

            // 动态注册刚保存的纹理，避免粘贴一次就重载整个资源包
            ResourceLocation resource_location = ResourceLocation.fromNamespaceAndPath(
                    QuestEnhance.MOD_ID,
                    "textures/ftb/" + file_name
            );
            try (InputStream input_stream = Files.newInputStream(output_path)) {
                NativeImage native_image = NativeImage.read(input_stream);
                minecraft.getTextureManager().register(resource_location, new DynamicTexture(native_image));
            }

            // 按任务介绍区宽度和原图比例计算图片组件尺寸
            int image_width = this.quest_enhance$description_content_width > 0
                    ? this.quest_enhance$description_content_width
                    : 100;
            int image_height = QuestDescriptionWidthContext.calculateHeight(
                    image_width,
                    buffered_image.getWidth(),
                    buffered_image.getHeight()
            );
            this.quest_enhance$insert_at_end_of_line("\n{image:" + resource_location
                    + " width:" + image_width + " height:" + image_height + " align:center fit:true}");
            QuestEnhance.LOGGER.info("Pasted clipboard image to {}", output_path);
        } catch (IOException | RuntimeException exception) {
            QuestEnhance.LOGGER.error("Failed to paste clipboard image", exception);
        }
    }
}
