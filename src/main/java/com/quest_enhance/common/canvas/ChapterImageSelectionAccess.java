package com.quest_enhance.common.canvas;

// 暴露章节图片的范围选取属性
public interface ChapterImageSelectionAccess {
    // 读取画板图片是否排除在拖动范围选取之外
    boolean quest_enhance$is_excluded_from_box_selection();

    // 更新画板图片是否排除在拖动范围选取之外
    void quest_enhance$set_excluded_from_box_selection(boolean excluded);
}
