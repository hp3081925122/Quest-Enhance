package com.quest_enhance.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;

public final class QuestEnhanceTextClickEvent implements KubeEvent {
    private final String text;
    private final String chapterId;
    private final double x;
    private final double y;
    private final double width;
    private final double height;

    // 保存被点击文字的内容、章节标识和画布位置
    public QuestEnhanceTextClickEvent(
            String text,
            String chapterId,
            double x,
            double y,
            double width,
            double height
    ) {
        this.text = text;
        this.chapterId = chapterId;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public String getText() {
        return this.text;
    }

    public String getChapterId() {
        return this.chapterId;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getWidth() {
        return this.width;
    }

    public double getHeight() {
        return this.height;
    }
}
