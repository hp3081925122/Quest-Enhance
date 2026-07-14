package com.ftb_paste_image.client;

public final class QuestDescriptionWidthContext {
    private static int pending_content_width;
    private static int active_content_width;

    private QuestDescriptionWidthContext() {
    }

    // 记录即将打开的任务描述编辑器所对应的介绍内容宽度
    public static void capture(int content_width) {
        pending_content_width = Math.max(1, content_width);
        active_content_width = pending_content_width;
    }

    // 仅让紧接着创建的描述编辑器取得宽度，避免影响其他多行编辑器
    public static int consume() {
        int content_width = pending_content_width;
        pending_content_width = 0;
        return content_width;
    }

    // 标记当前图片选择器应使用的任务介绍内容宽度
    public static void activate(int content_width) {
        active_content_width = Math.max(1, content_width);
    }

    // 提供给 FTB 原生图片选择器读取当前任务介绍内容宽度
    public static int getActiveContentWidth() {
        return active_content_width;
    }

    // 按指定显示宽度和原图比例计算至少为一像素的显示高度
    public static int calculateHeight(int content_width, int image_width, int image_height) {
        return Math.max(1, (int) Math.round(content_width * (double) image_height / image_width));
    }
}
