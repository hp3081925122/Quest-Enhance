package com.quest_enhance.mixin;

import com.quest_enhance.common.canvas.ChapterImageSelectionAccess;
import de.marhali.json5.Json5Element;
import de.marhali.json5.Json5Object;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 为章节图片提供通用的范围选取属性和存取逻辑
@Mixin(value = ChapterImage.class, remap = false)
public abstract class ChapterImageSelectionMixin implements ChapterImageSelectionAccess {
    @Unique
    private static final String quest_enhance$EXCLUDE_FROM_BOX_SELECTION_KEY =
            "quest_enhance_exclude_from_box_selection";

    @Unique
    private boolean quest_enhance$exclude_from_box_selection;

    // 读取章节图片是否排除在拖动范围选取之外
    @Override
    @Unique
    public boolean quest_enhance$is_excluded_from_box_selection() {
        return this.quest_enhance$exclude_from_box_selection;
    }

    // 更新章节图片是否排除在拖动范围选取之外
    @Override
    @Unique
    public void quest_enhance$set_excluded_from_box_selection(boolean excluded) {
        this.quest_enhance$exclude_from_box_selection = excluded;
    }

    // 将范围选取属性写入章节图片 JSON5 存档
    @Inject(method = "writeData", at = @At("TAIL"))
    private void quest_enhance$write_box_selection_flag(
            Json5Object data,
            HolderLookup.Provider provider,
            CallbackInfo callback_info
    ) {
        data.addProperty(
                quest_enhance$EXCLUDE_FROM_BOX_SELECTION_KEY,
                this.quest_enhance$exclude_from_box_selection
        );
    }

    // 从章节图片 JSON5 存档恢复范围选取属性
    @Inject(method = "readData", at = @At("TAIL"))
    private void quest_enhance$read_box_selection_flag(
            Json5Object data,
            HolderLookup.Provider provider,
            CallbackInfo callback_info
    ) {
        Json5Element raw_value = data.get(quest_enhance$EXCLUDE_FROM_BOX_SELECTION_KEY);
        this.quest_enhance$exclude_from_box_selection = raw_value != null
                && raw_value.isJson5Primitive()
                && raw_value.getAsJson5Primitive().isBoolean()
                && raw_value.getAsBoolean();
    }

    // 将范围选取属性同步到客户端章节图片对象
    @Inject(method = "writeNetData", at = @At("TAIL"))
    private void quest_enhance$write_box_selection_net_data(
            RegistryFriendlyByteBuf buffer,
            CallbackInfo callback_info
    ) {
        buffer.writeBoolean(this.quest_enhance$exclude_from_box_selection);
    }

    // 从服务端网络数据恢复范围选取属性
    @Inject(method = "readNetData", at = @At("TAIL"))
    private void quest_enhance$read_box_selection_net_data(
            RegistryFriendlyByteBuf buffer,
            CallbackInfo callback_info
    ) {
        this.quest_enhance$exclude_from_box_selection = buffer.readBoolean();
    }
}
