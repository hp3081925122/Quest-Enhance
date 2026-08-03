package com.quest_enhance.kubejs;

import com.quest_enhance.DecorativeAnchor;
import com.quest_enhance.QuestEnhance;
import com.quest_enhance.common.canvas.ChapterCanvasData;
import com.quest_enhance.common.description.QuestDescriptionComponents;
import com.quest_enhance.mixin.ChapterAccessor;
import dev.ftb.mods.ftbquests.net.CreateObjectResponseMessage;
import dev.ftb.mods.ftbquests.net.EditObjectResponseMessage;
import dev.ftb.mods.ftbquests.net.MoveMovableResponseMessage;
import dev.ftb.mods.ftbquests.net.OpenQuestBookMessage;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.QuestObjectType;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.util.ProgressChange;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class QuestUtils {
    private static final String CLIENT_FILE_CLASS = "dev.ftb.mods.ftbquests.client.ClientQuestFile";
    private static final ResourceLocation DEFAULT_FONT = ResourceLocation.fromNamespaceAndPath("minecraft", "default");
    private static final String MEDIA_PLACEHOLDER_IMAGE = "#212121";
    private static boolean canvasImageValidationLogged;

    private QuestUtils() {
    }

    // 查询当前任务书中的对象，客户端脚本和服务端脚本都可以使用。
    public static QuestObjectBase getObject(long id) {
        BaseQuestFile file = getActiveFile();
        return file == null ? null : file.getBase(id);
    }

    public static QuestObjectBase getObject(String id) {
        Long parsedId = parseId(id);
        return parsedId == null ? null : getObject(parsedId);
    }

    public static Quest getQuest(long id) {
        BaseQuestFile file = getActiveFile();
        return file == null ? null : file.getQuest(id);
    }

    public static Quest getQuest(String id) {
        Long parsedId = parseId(id);
        return parsedId == null ? null : getQuest(parsedId);
    }

    public static Chapter getChapter(long id) {
        BaseQuestFile file = getActiveFile();
        return file == null ? null : file.getChapter(id);
    }

    public static Chapter getChapter(String id) {
        Long parsedId = parseId(id);
        return parsedId == null ? null : getChapter(parsedId);
    }

    public static Task getTask(long id) {
        BaseQuestFile file = getActiveFile();
        return file == null ? null : file.getTask(id);
    }

    public static Task getTask(String id) {
        Long parsedId = parseId(id);
        return parsedId == null ? null : getTask(parsedId);
    }

    // 查询章节画布中的图片、GIF、视频、文字和辅助点。
    public static List<ChapterImage> getChapterImages(long chapterId) {
        BaseQuestFile file = getActiveFile();
        Chapter chapter = file == null ? null : file.getChapter(chapterId);
        return chapter == null ? List.of() : List.copyOf(chapter.getImages());
    }

    public static List<ChapterImage> getChapterImages(String chapterId) {
        Long parsedId = parseId(chapterId);
        return parsedId == null ? List.of() : getChapterImages(parsedId);
    }

    public static int getChapterImageCount(long chapterId) {
        return getChapterImages(chapterId).size();
    }

    public static int getChapterImageCount(String chapterId) {
        Long parsedId = parseId(chapterId);
        return parsedId == null ? 0 : getChapterImageCount(parsedId);
    }

    public static ChapterImage getChapterImage(long chapterId, int index) {
        List<ChapterImage> images = getChapterImages(chapterId);
        return index >= 0 && index < images.size() ? images.get(index) : null;
    }

    public static ChapterImage getChapterImage(String chapterId, int index) {
        Long parsedId = parseId(chapterId);
        return parsedId == null ? null : getChapterImage(parsedId, index);
    }

    public static CompoundTag getChapterImageData(ChapterImage image) {
        return image == null ? null : image.writeData(new CompoundTag());
    }

    public static String getChapterImageType(ChapterImage image) {
        if (image == null) {
            return "";
        }
        if (ChapterCanvasData.getGif(image).isPresent()) {
            return "gif";
        }
        if (ChapterCanvasData.getVideo(image).isPresent()) {
            return "video";
        }
        if (ChapterCanvasData.getText(image, DEFAULT_FONT).isPresent()) {
            return "text";
        }
        return DecorativeAnchor.isAnchor(image) ? "anchor" : "image";
    }

    public static String getChapterGifResource(ChapterImage image) {
        return ChapterCanvasData.getGif(image).map(ResourceLocation::toString).orElse(null);
    }

    public static String getChapterVideoPath(ChapterImage image) {
        return ChapterCanvasData.getVideo(image).orElse(null);
    }

    public static String getChapterText(ChapterImage image) {
        return ChapterCanvasData.getText(image, DEFAULT_FONT)
                .map(ChapterCanvasData.TextData::text)
                .orElse(null);
    }

    public static String getChapterTextFont(ChapterImage image) {
        return ChapterCanvasData.getText(image, DEFAULT_FONT)
                .map(data -> data.font().toString())
                .orElse(null);
    }

    public static String getDecorativeAnchorKey(ChapterImage image) {
        return image == null ? null : DecorativeAnchor.nodeKey(image).orElse(null);
    }

    // 查询玩家或队伍在任务节点上的状态和进度。
    public static boolean isStarted(Entity player, long id) {
        QuestObject object = getServerObject(id);
        TeamData teamData = getTeamData(player);
        return object != null && teamData != null && teamData.isStarted(object);
    }

    public static boolean isStarted(Entity player, String id) {
        Long parsedId = parseId(id);
        return parsedId != null && isStarted(player, parsedId);
    }

    public static boolean isCompleted(Entity player, long id) {
        QuestObject object = getServerObject(id);
        TeamData teamData = getTeamData(player);
        return object != null && teamData != null && teamData.isCompleted(object);
    }

    public static boolean isCompleted(Entity player, String id) {
        Long parsedId = parseId(id);
        return parsedId != null && isCompleted(player, parsedId);
    }

    public static boolean isVisible(Entity player, long id) {
        QuestObject object = getServerObject(id);
        TeamData teamData = getTeamData(player);
        return object != null && teamData != null && object.isVisible(teamData);
    }

    public static boolean isVisible(Entity player, String id) {
        Long parsedId = parseId(id);
        return parsedId != null && isVisible(player, parsedId);
    }

    public static long getProgress(Entity player, long id) {
        ServerQuestFile file = getServerFile();
        TeamData teamData = getTeamData(player);
        if (file == null || teamData == null) {
            return 0L;
        }

        QuestObjectBase object = file.getBase(id);
        if (object instanceof Task task) {
            return teamData.getProgress(task);
        }
        if (object instanceof QuestObject questObject) {
            return teamData.getRelativeProgress(questObject);
        }
        return 0L;
    }

    public static long getProgress(Entity player, String id) {
        Long parsedId = parseId(id);
        return parsedId == null ? 0L : getProgress(player, parsedId);
    }

    public static int getQuestProgress(Entity player, long id) {
        QuestObject object = getServerObject(id);
        TeamData teamData = getTeamData(player);
        return object == null || teamData == null ? 0 : teamData.getRelativeProgress(object);
    }

    public static int getQuestProgress(Entity player, String id) {
        Long parsedId = parseId(id);
        return parsedId == null ? 0 : getQuestProgress(player, parsedId);
    }

    public static long getMaxProgress(long taskId) {
        Task task = getServerTask(taskId);
        return task == null ? 0L : task.getMaxProgress();
    }

    public static long getMaxProgress(String taskId) {
        Long parsedId = parseId(taskId);
        return parsedId == null ? 0L : getMaxProgress(parsedId);
    }

    public static int getCompletionCount(Entity player, long questId) {
        Quest quest = getServerQuest(questId);
        TeamData teamData = getTeamData(player);
        return quest == null || teamData == null ? 0 : teamData.getCompletionCount(quest);
    }

    public static int getCompletionCount(Entity player, String questId) {
        Long parsedId = parseId(questId);
        return parsedId == null ? 0 : getCompletionCount(player, parsedId);
    }

    // 修改任务和任务目标的进度，完成与重置都会触发 FTB Quests 原生同步和事件。
    public static boolean complete(Entity player, long id) {
        return changeQuestProgress(player, id, false);
    }

    public static boolean complete(Entity player, String id) {
        Long parsedId = parseId(id);
        return parsedId != null && complete(player, parsedId);
    }

    public static boolean reset(Entity player, long id) {
        return changeQuestProgress(player, id, true);
    }

    public static boolean reset(Entity player, String id) {
        Long parsedId = parseId(id);
        return parsedId != null && reset(player, parsedId);
    }

    public static boolean completeTask(Entity player, long taskId) {
        Task task = getServerTask(taskId);
        return task != null && setProgress(player, taskId, task.getMaxProgress());
    }

    public static boolean completeTask(Entity player, String taskId) {
        Long parsedId = parseId(taskId);
        return parsedId != null && completeTask(player, parsedId);
    }

    public static boolean setProgress(Entity player, long taskId, long progress) {
        Task task = getServerTask(taskId);
        TeamData teamData = getTeamData(player);
        if (task == null || teamData == null || teamData.isLocked()) {
            return false;
        }

        teamData.setProgress(task, progress);
        return true;
    }

    public static boolean setProgress(Entity player, String taskId, long progress) {
        Long parsedId = parseId(taskId);
        return parsedId != null && setProgress(player, parsedId, progress);
    }

    public static boolean addProgress(Entity player, long taskId, long amount) {
        Task task = getServerTask(taskId);
        TeamData teamData = getTeamData(player);
        if (task == null || teamData == null || teamData.isLocked()) {
            return false;
        }

        teamData.addProgress(task, amount);
        return true;
    }

    public static boolean addProgress(Entity player, String taskId, long amount) {
        Long parsedId = parseId(taskId);
        return parsedId != null && addProgress(player, parsedId, amount);
    }

    public static boolean resetTask(Entity player, long taskId) {
        return setProgress(player, taskId, 0L);
    }

    public static boolean resetTask(Entity player, String taskId) {
        Long parsedId = parseId(taskId);
        return parsedId != null && resetTask(player, parsedId);
    }

    // 查询、添加、删除任务前置关系，并阻止产生循环依赖。
    public static boolean arePrerequisitesCompleted(Entity player, long questId) {
        Quest quest = getServerQuest(questId);
        TeamData teamData = getTeamData(player);
        return quest != null && teamData != null && teamData.areDependenciesComplete(quest);
    }

    public static boolean arePrerequisitesCompleted(Entity player, String questId) {
        Long parsedId = parseId(questId);
        return parsedId != null && arePrerequisitesCompleted(player, parsedId);
    }

    public static int completedPrerequisiteCount(Entity player, long questId) {
        Quest quest = getServerQuest(questId);
        TeamData teamData = getTeamData(player);
        if (quest == null || teamData == null) {
            return 0;
        }

        return (int) quest.streamDependencies().filter(teamData::isCompleted).count();
    }

    public static int completedPrerequisiteCount(Entity player, String questId) {
        Long parsedId = parseId(questId);
        return parsedId == null ? 0 : completedPrerequisiteCount(player, parsedId);
    }

    public static int prerequisiteCount(long questId) {
        Quest quest = getServerQuest(questId);
        return quest == null ? 0 : (int) quest.streamDependencies().count();
    }

    public static int prerequisiteCount(String questId) {
        Long parsedId = parseId(questId);
        return parsedId == null ? 0 : prerequisiteCount(parsedId);
    }

    public static List<String> prerequisites(long questId) {
        Quest quest = getServerQuest(questId);
        if (quest == null) {
            return List.of();
        }

        return quest.streamDependencies().map(dependency -> dependency.getCodeString()).toList();
    }

    public static List<String> prerequisites(String questId) {
        Long parsedId = parseId(questId);
        return parsedId == null ? List.of() : prerequisites(parsedId);
    }

    public static boolean hasPrerequisite(long prerequisiteQuestId, long questId) {
        Quest prerequisite = getServerQuest(prerequisiteQuestId);
        Quest quest = getServerQuest(questId);
        return prerequisite != null && quest != null && quest.hasDependency(prerequisite);
    }

    public static boolean hasPrerequisite(String prerequisiteQuestId, String questId) {
        Long parsedPrerequisiteId = parseId(prerequisiteQuestId);
        Long parsedQuestId = parseId(questId);
        return parsedPrerequisiteId != null
                && parsedQuestId != null
                && hasPrerequisite(parsedPrerequisiteId, parsedQuestId);
    }

    public static boolean setPrerequisite(long prerequisiteQuestId, long questId) {
        ServerQuestFile file = getServerFile();
        Quest prerequisite = file == null ? null : file.getQuest(prerequisiteQuestId);
        Quest quest = file == null ? null : file.getQuest(questId);
        if (prerequisite == null || quest == null || prerequisite == quest) {
            return false;
        }
        if (quest.hasDependency(prerequisite)) {
            return true;
        }

        quest.addDependency(prerequisite);
        if (!quest.verifyDependencies(false)) {
            quest.removeDependency(prerequisite);
            return false;
        }

        syncEditedObject(file, quest);
        return true;
    }

    public static boolean setPrerequisite(String prerequisiteQuestId, String questId) {
        Long parsedPrerequisiteId = parseId(prerequisiteQuestId);
        Long parsedQuestId = parseId(questId);
        return parsedPrerequisiteId != null
                && parsedQuestId != null
                && setPrerequisite(parsedPrerequisiteId, parsedQuestId);
    }

    public static boolean removePrerequisite(long prerequisiteQuestId, long questId) {
        ServerQuestFile file = getServerFile();
        Quest prerequisite = file == null ? null : file.getQuest(prerequisiteQuestId);
        Quest quest = file == null ? null : file.getQuest(questId);
        if (prerequisite == null || quest == null || !quest.hasDependency(prerequisite)) {
            return false;
        }

        quest.removeDependency(prerequisite);
        syncEditedObject(file, quest);
        return true;
    }

    public static boolean removePrerequisite(String prerequisiteQuestId, String questId) {
        Long parsedPrerequisiteId = parseId(prerequisiteQuestId);
        Long parsedQuestId = parseId(questId);
        return parsedPrerequisiteId != null
                && parsedQuestId != null
                && removePrerequisite(parsedPrerequisiteId, parsedQuestId);
    }

    public static boolean clearPrerequisites(long questId) {
        ServerQuestFile file = getServerFile();
        Quest quest = file == null ? null : file.getQuest(questId);
        if (quest == null || !quest.hasDependencies()) {
            return false;
        }

        quest.clearDependencies();
        syncEditedObject(file, quest);
        return true;
    }

    public static boolean clearPrerequisites(String questId) {
        Long parsedId = parseId(questId);
        return parsedId != null && clearPrerequisites(parsedId);
    }

    // 修改节点可见性和常用编辑字段，并把结果广播给所有任务书客户端。
    public static boolean hideQuest(long id) {
        return setObjectBoolean(getServerQuest(id), "invisible", true);
    }

    public static boolean hideQuest(String id) {
        Long parsedId = parseId(id);
        return parsedId != null && hideQuest(parsedId);
    }

    public static boolean isQuestHidden(long id) {
        Quest quest = getServerQuest(id);
        if (quest == null) {
            return false;
        }

        CompoundTag data = new CompoundTag();
        quest.writeData(data);
        return data.getBoolean("invisible");
    }

    public static boolean isQuestHidden(String id) {
        Long parsedId = parseId(id);
        return parsedId != null && isQuestHidden(parsedId);
    }

    public static boolean unhideQuest(long id) {
        return setObjectBoolean(getServerQuest(id), "invisible", false);
    }

    public static boolean unhideQuest(String id) {
        Long parsedId = parseId(id);
        return parsedId != null && unhideQuest(parsedId);
    }

    public static boolean hideChapter(long id) {
        ServerQuestFile file = getServerFile();
        Chapter chapter = file == null ? null : file.getChapter(id);
        if (chapter == null) {
            return false;
        }

        ((ChapterAccessor) (Object) chapter).quest_enhance$set_always_invisible(true);
        syncEditedObject(file, chapter);
        return true;
    }

    public static boolean hideChapter(String id) {
        Long parsedId = parseId(id);
        return parsedId != null && hideChapter(parsedId);
    }

    public static boolean isChapterHidden(long id) {
        ServerQuestFile file = getServerFile();
        Chapter chapter = file == null ? null : file.getChapter(id);
        return chapter != null && chapter.isAlwaysInvisible();
    }

    public static boolean isChapterHidden(String id) {
        Long parsedId = parseId(id);
        return parsedId != null && isChapterHidden(parsedId);
    }

    public static boolean unhideChapter(long id) {
        ServerQuestFile file = getServerFile();
        Chapter chapter = file == null ? null : file.getChapter(id);
        if (chapter == null) {
            return false;
        }

        ((ChapterAccessor) (Object) chapter).quest_enhance$set_always_invisible(false);
        syncEditedObject(file, chapter);
        return true;
    }

    public static boolean unhideChapter(String id) {
        Long parsedId = parseId(id);
        return parsedId != null && unhideChapter(parsedId);
    }

    public static boolean setTitle(long id, String title) {
        if (title == null) {
            return false;
        }
        return updateObject(id, data -> {
            if (title.isEmpty()) {
                data.remove("title");
            } else {
                data.putString("title", title);
            }
        });
    }

    public static boolean setTitle(String id, String title) {
        Long parsedId = parseId(id);
        return parsedId != null && setTitle(parsedId, title);
    }

    public static boolean setQuestSubtitle(long id, String subtitle) {
        if (subtitle == null || getServerQuest(id) == null) {
            return false;
        }
        return updateObject(id, data -> {
            if (subtitle.isEmpty()) {
                data.remove("subtitle");
            } else {
                data.putString("subtitle", subtitle);
            }
        });
    }

    public static boolean setQuestSubtitle(String id, String subtitle) {
        Long parsedId = parseId(id);
        return parsedId != null && setQuestSubtitle(parsedId, subtitle);
    }

    public static boolean setQuestDescription(long id, String... lines) {
        if (lines == null || getServerQuest(id) == null) {
            return false;
        }
        return updateObject(id, data -> {
            data.remove("description");
            if (lines.length > 0) {
                ListTag description = new ListTag();
                for (String line : lines) {
                    if (line != null) {
                        description.add(StringTag.valueOf(line));
                    }
                }
                if (!description.isEmpty()) {
                    data.put("description", description);
                }
            }
        });
    }

    public static boolean setQuestDescription(String id, String... lines) {
        Long parsedId = parseId(id);
        return parsedId != null && setQuestDescription(parsedId, lines);
    }

    // 构造描述中的网页链接 JSON 组件。
    public static String descriptionWebLink(String displayText, String url) {
        return QuestDescriptionComponents.webLink(displayText, url);
    }

    // 构造描述中的点击复制 JSON 组件。
    public static String descriptionCopy(String displayText, String value) {
        return QuestDescriptionComponents.copy(displayText, value);
    }

    // 构造描述中的命令点击 JSON 组件。
    public static String descriptionCommand(String displayText, String command) {
        return QuestDescriptionComponents.command(displayText, command);
    }

    // 构造描述中的网络图片标记。
    public static String descriptionImage(
            String url,
            int width,
            int height,
            String alignment,
            boolean fit,
            String hoverText
    ) {
        return QuestDescriptionComponents.remoteImage(url, width, height, alignment, fit, hoverText);
    }

    // 构造描述中的物品图标标记，并保留物品数量、损伤值和 NBT。
    public static String descriptionItemIcon(
            ItemStack stack,
            int width,
            int height,
            String alignment,
            boolean fit,
            String hoverText
    ) {
        return QuestDescriptionComponents.itemIcon(stack, width, height, alignment, fit, hoverText);
    }

    // 构造描述中的物品悬停 JSON 组件，并保留完整物品 NBT。
    public static String descriptionItemHover(String displayText, ItemStack stack) {
        return QuestDescriptionComponents.itemHover(displayText, stack);
    }

    // 构造描述中的 GIF 标记。
    public static String descriptionGif(
            String resourceId,
            int width,
            int height,
            String alignment,
            boolean fit,
            String hoverText
    ) {
        return QuestDescriptionComponents.gif(resourceId, width, height, alignment, fit, hoverText);
    }

    // 构造描述中的视频标记并自动编码相对路径。
    public static String descriptionVideo(String videoPath, String displayText) {
        return QuestDescriptionComponents.video(videoPath, displayText);
    }

    // 构造描述中的文字悬停 JSON 组件。
    public static String descriptionHoverText(String displayText, String hoverText) {
        return QuestDescriptionComponents.hoverText(displayText, hoverText);
    }

    // 构造描述中的自定义字体 JSON 组件。
    public static String descriptionFont(String displayText, String fontId) {
        return QuestDescriptionComponents.font(displayText, fontId);
    }

    // 构造描述中的本地化 JSON 组件。
    public static String descriptionTranslation(String displayText, String translationKey) {
        return QuestDescriptionComponents.translation(displayText, translationKey);
    }

    // 构造描述中的按键绑定 JSON 组件。
    public static String descriptionKeybind(String keybind) {
        return QuestDescriptionComponents.keybind(keybind);
    }

    // 使用默认布局和颜色构造描述表格。
    public static String descriptionTable(List<?> rows, boolean header, String alignment) {
        return descriptionTable(
                rows,
                header,
                alignment,
                0,
                18,
                1,
                0x808080,
                0x374151,
                0x1F2937,
                0xFFFFFF
        );
    }

    // 使用完整布局和颜色参数构造描述表格，颜色使用 0xRRGGBB。
    public static String descriptionTable(
            List<?> rows,
            boolean header,
            String alignment,
            int tableWidth,
            int rowHeight,
            int lineWidth,
            int borderColor,
            int headerColor,
            int cellColor,
            int textColor
    ) {
        return QuestDescriptionComponents.table(
                rows,
                header,
                alignment,
                tableWidth,
                rowHeight,
                lineWidth,
                0xFF000000 | borderColor & 0xFFFFFF,
                0xFF000000 | headerColor & 0xFFFFFF,
                0xFF000000 | cellColor & 0xFFFFFF,
                0xFF000000 | textColor & 0xFFFFFF
        );
    }

    public static boolean setQuestSize(long questId, double size) {
        if (!Double.isFinite(size) || size <= 0.0D || getServerQuest(questId) == null) {
            return false;
        }
        return updateObject(questId, data -> {
            if (size == 1.0D) {
                data.remove("size");
            } else {
                data.putDouble("size", size);
            }
        });
    }

    public static boolean setQuestSize(String questId, double size) {
        Long parsedId = parseId(questId);
        return parsedId != null && setQuestSize(parsedId, size);
    }

    public static boolean setQuestShape(long questId, String shape) {
        if (shape == null || getServerQuest(questId) == null) {
            return false;
        }
        return updateObject(questId, data -> {
            if (shape.isEmpty() || shape.equals("default")) {
                data.remove("shape");
            } else {
                data.putString("shape", shape);
            }
        });
    }

    public static boolean setQuestShape(String questId, String shape) {
        Long parsedId = parseId(questId);
        return parsedId != null && setQuestShape(parsedId, shape);
    }

    public static boolean setQuestOptional(long questId, boolean optional) {
        return setObjectBoolean(getServerQuest(questId), "optional", optional);
    }

    public static boolean setQuestOptional(String questId, boolean optional) {
        Long parsedId = parseId(questId);
        return parsedId != null && setQuestOptional(parsedId, optional);
    }

    public static boolean setTaskOptional(long taskId, boolean optional) {
        return setObjectBoolean(getServerTask(taskId), "optional_task", optional);
    }

    public static boolean setTaskOptional(String taskId, boolean optional) {
        Long parsedId = parseId(taskId);
        return parsedId != null && setTaskOptional(parsedId, optional);
    }

    public static CompoundTag getData(long id) {
        QuestObjectBase object = getObject(id);
        if (object == null) {
            return null;
        }

        CompoundTag data = new CompoundTag();
        object.writeData(data);
        return data;
    }

    public static CompoundTag getData(String id) {
        Long parsedId = parseId(id);
        return parsedId == null ? null : getData(parsedId);
    }

    public static boolean mergeData(long id, CompoundTag changes) {
        if (changes == null) {
            return false;
        }
        return updateObject(id, data -> data.merge(changes.copy()));
    }

    public static boolean mergeData(String id, CompoundTag changes) {
        Long parsedId = parseId(id);
        return parsedId != null && mergeData(parsedId, changes);
    }

    // 动态创建、删除和移动任务书节点；创建流程与 FTB Quests 编辑器保持一致。
    public static Chapter createChapter(long groupId, String title) {
        ServerQuestFile file = getServerFile();
        if (file == null) {
            return null;
        }

        CompoundTag extra = new CompoundTag();
        extra.putLong("group", groupId);
        Chapter chapter = (Chapter) file.create(file.newID(), QuestObjectType.CHAPTER, 1L, extra);
        if (title != null && !title.isEmpty()) {
            chapter.setRawTitle(title);
        }
        return finishCreatedObject(file, chapter, extra);
    }

    public static Chapter createChapter(String groupId, String title) {
        Long parsedId = parseId(groupId);
        return parsedId == null ? null : createChapter(parsedId, title);
    }

    public static Quest createQuest(long chapterId, double x, double y, String title) {
        ServerQuestFile file = getServerFile();
        Chapter chapter = file == null ? null : file.getChapter(chapterId);
        if (file == null || chapter == null) {
            return null;
        }

        Quest quest = (Quest) file.create(file.newID(), QuestObjectType.QUEST, chapterId, new CompoundTag());
        quest.setX(x);
        quest.setY(y);
        if (title != null && !title.isEmpty()) {
            quest.setRawTitle(title);
        }
        return finishCreatedObject(file, quest, null);
    }

    public static Quest createQuest(long chapterId, double x, double y) {
        return createQuest(chapterId, x, y, null);
    }

    public static Quest createQuest(String chapterId, double x, double y, String title) {
        Long parsedId = parseId(chapterId);
        return parsedId == null ? null : createQuest(parsedId, x, y, title);
    }

    public static Task createTask(long questId, String type, String title) {
        ServerQuestFile file = getServerFile();
        Quest quest = file == null ? null : file.getQuest(questId);
        if (file == null || quest == null || type == null || type.isEmpty()) {
            return null;
        }

        Task task = TaskType.createTask(file.newID(), quest, type);
        if (task == null) {
            return null;
        }
        if (title != null && !title.isEmpty()) {
            task.setRawTitle(title);
        }
        CompoundTag extra = new CompoundTag();
        extra.putString("type", task.getType().getTypeForNBT());
        return finishCreatedObject(file, task, extra);
    }

    public static Task createTask(long questId, String type) {
        return createTask(questId, type, null);
    }

    public static Task createTask(String questId, String type, String title) {
        Long parsedId = parseId(questId);
        return parsedId == null ? null : createTask(parsedId, type, title);
    }

    public static Task createTask(String questId, String type) {
        return createTask(questId, type, null);
    }

    public static Reward createReward(long questId, String type, String title) {
        ServerQuestFile file = getServerFile();
        Quest quest = file == null ? null : file.getQuest(questId);
        if (file == null || quest == null || type == null || type.isEmpty()) {
            return null;
        }

        Reward reward = RewardType.createReward(file.newID(), quest, type);
        if (reward == null) {
            return null;
        }
        if (title != null && !title.isEmpty()) {
            reward.setRawTitle(title);
        }
        CompoundTag extra = new CompoundTag();
        extra.putString("type", reward.getType().getTypeForNBT());
        return finishCreatedObject(file, reward, extra);
    }

    public static Reward createReward(long questId, String type) {
        return createReward(questId, type, null);
    }

    public static Reward createReward(String questId, String type, String title) {
        Long parsedId = parseId(questId);
        return parsedId == null ? null : createReward(parsedId, type, title);
    }

    public static Reward createReward(String questId, String type) {
        return createReward(questId, type, null);
    }

    // 创建原生图片及本模组支持的画布媒体元素，并通过章节编辑消息同步客户端。
    public static ChapterImage createChapterImage(
            long chapterId,
            String resource,
            double x,
            double y,
            double width,
            double height
    ) {
        ResourceLocation resourceLocation = resource == null ? null : ResourceLocation.tryParse(resource);
        return resourceLocation == null
                ? null
                : createCanvasImage(chapterId, resourceLocation.toString(), "", x, y, width, height);
    }

    public static ChapterImage createChapterImage(
            String chapterId,
            String resource,
            double x,
            double y,
            double width,
            double height
    ) {
        Long parsedId = parseId(chapterId);
        return parsedId == null ? null : createChapterImage(parsedId, resource, x, y, width, height);
    }

    public static ChapterImage createChapterGif(
            long chapterId,
            String resource,
            double x,
            double y,
            double width,
            double height
    ) {
        ResourceLocation resourceLocation = resource == null ? null : ResourceLocation.tryParse(resource);
        return resourceLocation == null
                ? null
                : createCanvasImage(
                        chapterId,
                        MEDIA_PLACEHOLDER_IMAGE,
                        ChapterCanvasData.gifClick(resourceLocation),
                        x,
                        y,
                        width,
                        height
                );
    }

    public static ChapterImage createChapterGif(
            String chapterId,
            String resource,
            double x,
            double y,
            double width,
            double height
    ) {
        Long parsedId = parseId(chapterId);
        return parsedId == null ? null : createChapterGif(parsedId, resource, x, y, width, height);
    }

    public static ChapterImage createChapterVideo(
            long chapterId,
            String videoPath,
            double x,
            double y,
            double width,
            double height
    ) {
        String click = ChapterCanvasData.videoClick(videoPath).orElse(null);
        return click == null
                ? null
                : createCanvasImage(chapterId, MEDIA_PLACEHOLDER_IMAGE, click, x, y, width, height);
    }

    public static ChapterImage createChapterVideo(
            String chapterId,
            String videoPath,
            double x,
            double y,
            double width,
            double height
    ) {
        Long parsedId = parseId(chapterId);
        return parsedId == null ? null : createChapterVideo(parsedId, videoPath, x, y, width, height);
    }

    public static ChapterImage createChapterText(
            long chapterId,
            String text,
            String font,
            double x,
            double y,
            double width,
            double height
    ) {
        ResourceLocation fontLocation = font == null ? null : ResourceLocation.tryParse(font);
        if (text == null || fontLocation == null) {
            return null;
        }
        return createCanvasImage(
                chapterId,
                "",
                ChapterCanvasData.textClick(text, fontLocation),
                x,
                y,
                width,
                height
        );
    }

    public static ChapterImage createChapterText(
            String chapterId,
            String text,
            String font,
            double x,
            double y,
            double width,
            double height
    ) {
        Long parsedId = parseId(chapterId);
        return parsedId == null ? null : createChapterText(parsedId, text, font, x, y, width, height);
    }

    public static ChapterImage createDecorativeAnchor(long chapterId, double x, double y) {
        return createCanvasImage(
                chapterId,
                "",
                ChapterCanvasData.anchorClick(UUID.randomUUID()),
                x,
                y,
                0.4D,
                0.4D
        );
    }

    public static ChapterImage createDecorativeAnchor(String chapterId, double x, double y) {
        Long parsedId = parseId(chapterId);
        return parsedId == null ? null : createDecorativeAnchor(parsedId, x, y);
    }

    // 修改画布元素的原生字段，所有入口都会校验对象仍属于服务端当前章节。
    public static boolean setChapterImagePosition(ChapterImage image, double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            return false;
        }
        return updateChapterImage(image, data -> {
            data.putDouble("x", x);
            data.putDouble("y", y);
        });
    }

    public static boolean setChapterImageSize(ChapterImage image, double width, double height) {
        if (!isPositiveFinite(width) || !isPositiveFinite(height)) {
            return false;
        }
        return updateChapterImage(image, data -> {
            data.putDouble("width", width);
            data.putDouble("height", height);
        });
    }

    public static boolean setChapterImageRotation(ChapterImage image, double rotation) {
        if (!Double.isFinite(rotation) || rotation < -180.0D || rotation > 180.0D) {
            return false;
        }
        return updateChapterImage(image, data -> data.putDouble("rotation", rotation));
    }

    public static boolean setChapterImageResource(ChapterImage image, String resource) {
        ResourceLocation resourceLocation = resource == null ? null : ResourceLocation.tryParse(resource);
        return resourceLocation != null
                && updateChapterImage(image, data -> data.putString("image", resourceLocation.toString()));
    }

    public static boolean setChapterImageColor(ChapterImage image, int rgb) {
        return updateChapterImage(image, data -> data.putInt("color", rgb & 0xFFFFFF));
    }

    public static boolean setChapterImageAlpha(ChapterImage image, int alpha) {
        return alpha >= 0 && alpha <= 255
                && updateChapterImage(image, data -> data.putInt("alpha", alpha));
    }

    public static boolean setChapterImageOrder(ChapterImage image, int order) {
        return updateChapterImage(image, data -> data.putInt("order", order));
    }

    public static boolean setChapterImageHover(ChapterImage image, String... lines) {
        if (lines == null) {
            return false;
        }
        return updateChapterImage(image, data -> {
            data.remove("hover");
            ListTag hover = new ListTag();
            for (String line : lines) {
                if (line != null) {
                    hover.add(StringTag.valueOf(line));
                }
            }
            if (!hover.isEmpty()) {
                data.put("hover", hover);
            }
        });
    }

    public static boolean setChapterImageClick(ChapterImage image, String click) {
        if (click == null) {
            return false;
        }
        return updateChapterImage(image, data -> {
            if (click.isEmpty()) {
                data.remove("click");
            } else {
                data.putString("click", click);
            }
        });
    }

    public static boolean setChapterImageEditorsOnly(ChapterImage image, boolean editorsOnly) {
        return updateChapterImage(image, data -> data.putBoolean("dev", editorsOnly));
    }

    public static boolean setChapterImageAlignToCorner(ChapterImage image, boolean alignToCorner) {
        return updateChapterImage(image, data -> data.putBoolean("corner", alignToCorner));
    }

    public static boolean setChapterImageDependency(ChapterImage image, long questId) {
        Quest quest = getServerQuest(questId);
        return quest != null
                && updateChapterImage(image, data -> data.putString("dependency", quest.getCodeString()));
    }

    public static boolean setChapterImageDependency(ChapterImage image, String questId) {
        Long parsedId = parseId(questId);
        return parsedId != null && setChapterImageDependency(image, parsedId);
    }

    public static boolean clearChapterImageDependency(ChapterImage image) {
        return updateChapterImage(image, data -> data.remove("dependency"));
    }

    // 修改 GIF、视频和文字的专用数据时保留元素类型及其他原生属性。
    public static boolean setChapterGifResource(ChapterImage image, String resource) {
        ResourceLocation resourceLocation = resource == null ? null : ResourceLocation.tryParse(resource);
        return resourceLocation != null
                && "gif".equals(getChapterImageType(image))
                && updateChapterImage(
                        image,
                        data -> data.putString("click", ChapterCanvasData.gifClick(resourceLocation))
                );
    }

    public static boolean setChapterVideoPath(ChapterImage image, String videoPath) {
        String click = ChapterCanvasData.videoClick(videoPath).orElse(null);
        return click != null
                && "video".equals(getChapterImageType(image))
                && updateChapterImage(image, data -> data.putString("click", click));
    }

    public static boolean setChapterText(ChapterImage image, String text) {
        ChapterCanvasData.TextData current = ChapterCanvasData.getText(image, DEFAULT_FONT).orElse(null);
        return text != null
                && current != null
                && updateChapterImage(
                        image,
                        data -> data.putString("click", ChapterCanvasData.textClick(text, current.font()))
                );
    }

    public static boolean setChapterTextFont(ChapterImage image, String font) {
        ChapterCanvasData.TextData current = ChapterCanvasData.getText(image, DEFAULT_FONT).orElse(null);
        ResourceLocation fontLocation = font == null ? null : ResourceLocation.tryParse(font);
        return current != null
                && fontLocation != null
                && updateChapterImage(
                        image,
                        data -> data.putString("click", ChapterCanvasData.textClick(current.text(), fontLocation))
                );
    }

    // 复制、跨章节移动和删除时保留 ChapterImage 的章节级同步语义。
    public static ChapterImage copyChapterImage(ChapterImage image, long chapterId, double x, double y) {
        ServerQuestFile file = getServerFile();
        Chapter targetChapter = file == null ? null : file.getChapter(chapterId);
        if (!isServerChapterImage(image)
                || targetChapter == null
                || !Double.isFinite(x)
                || !Double.isFinite(y)) {
            return null;
        }

        CompoundTag data = image.writeData(new CompoundTag());
        data.putDouble("x", x);
        data.putDouble("y", y);
        ChapterImage copy = new ChapterImage(targetChapter);
        copy.readData(data);
        if (DecorativeAnchor.isAnchor(copy)) {
            DecorativeAnchor.assignNewId(copy);
        }
        targetChapter.addImage(copy);
        syncEditedObject(file, targetChapter);
        return copy;
    }

    public static ChapterImage copyChapterImage(ChapterImage image, String chapterId, double x, double y) {
        Long parsedId = parseId(chapterId);
        return parsedId == null ? null : copyChapterImage(image, parsedId, x, y);
    }

    public static ChapterImage moveChapterImage(ChapterImage image, long chapterId, double x, double y) {
        ServerQuestFile file = getServerFile();
        Chapter targetChapter = file == null ? null : file.getChapter(chapterId);
        if (!isServerChapterImage(image)
                || targetChapter == null
                || !Double.isFinite(x)
                || !Double.isFinite(y)) {
            return null;
        }

        Chapter sourceChapter = image.getChapter();
        if (sourceChapter == targetChapter) {
            image.setPosition(x, y);
            syncEditedObject(file, sourceChapter);
            return image;
        }

        CompoundTag data = image.writeData(new CompoundTag());
        data.putDouble("x", x);
        data.putDouble("y", y);
        ChapterImage moved = new ChapterImage(targetChapter);
        moved.readData(data);
        sourceChapter.removeImage(image);
        targetChapter.addImage(moved);
        syncEditedObject(file, sourceChapter);
        syncEditedObject(file, targetChapter);
        return moved;
    }

    public static ChapterImage moveChapterImage(ChapterImage image, String chapterId, double x, double y) {
        Long parsedId = parseId(chapterId);
        return parsedId == null ? null : moveChapterImage(image, parsedId, x, y);
    }

    public static boolean deleteChapterImage(ChapterImage image) {
        ServerQuestFile file = getServerFile();
        if (file == null || !isServerChapterImage(image)) {
            return false;
        }

        Chapter chapter = image.getChapter();
        chapter.removeImage(image);
        syncEditedObject(file, chapter);
        return true;
    }

    public static boolean deleteChapterImage(long chapterId, int index) {
        return deleteChapterImage(getServerChapterImage(chapterId, index));
    }

    public static boolean deleteChapterImage(String chapterId, int index) {
        Long parsedId = parseId(chapterId);
        return parsedId != null && deleteChapterImage(parsedId, index);
    }

    public static boolean deleteObject(long id) {
        ServerQuestFile file = getServerFile();
        if (file == null || file.getBase(id) == null) {
            return false;
        }

        file.deleteObject(id);
        return true;
    }

    public static boolean deleteObject(String id) {
        Long parsedId = parseId(id);
        return parsedId != null && deleteObject(parsedId);
    }

    public static boolean moveQuest(long questId, long chapterId, double x, double y) {
        ServerQuestFile file = getServerFile();
        Quest quest = file == null ? null : file.getQuest(questId);
        Chapter chapter = file == null ? null : file.getChapter(chapterId);
        if (file == null || quest == null || chapter == null || !Double.isFinite(x) || !Double.isFinite(y)) {
            return false;
        }

        quest.onMoved(x, y, chapterId);
        file.markDirty();
        new MoveMovableResponseMessage(quest, chapterId, x, y).sendToAll(file.server);
        return true;
    }

    public static boolean moveQuest(String questId, String chapterId, double x, double y) {
        Long parsedQuestId = parseId(questId);
        Long parsedChapterId = parseId(chapterId);
        return parsedQuestId != null
                && parsedChapterId != null
                && moveQuest(parsedQuestId, parsedChapterId, x, y);
    }

    public static boolean setQuestPosition(long questId, double x, double y) {
        Quest quest = getServerQuest(questId);
        return quest != null && moveQuest(questId, quest.getChapter().getId(), x, y);
    }

    public static boolean setQuestPosition(String questId, double x, double y) {
        Long parsedId = parseId(questId);
        return parsedId != null && setQuestPosition(parsedId, x, y);
    }

    public static boolean moveTaskLeft(long taskId) {
        ServerQuestFile file = getServerFile();
        Task task = getServerTask(taskId);
        if (file == null || task == null) {
            return false;
        }

        Quest quest = task.getQuest();
        quest.moveTaskLeft(task);
        syncEditedObject(file, quest);
        return true;
    }

    public static boolean moveTaskRight(long taskId) {
        ServerQuestFile file = getServerFile();
        Task task = getServerTask(taskId);
        if (file == null || task == null) {
            return false;
        }

        Quest quest = task.getQuest();
        quest.moveTaskRight(task);
        syncEditedObject(file, quest);
        return true;
    }

    public static boolean moveTaskLeft(String taskId) {
        Long parsedId = parseId(taskId);
        return parsedId != null && moveTaskLeft(parsedId);
    }

    public static boolean moveTaskRight(String taskId) {
        Long parsedId = parseId(taskId);
        return parsedId != null && moveTaskRight(parsedId);
    }

    // 客户端通过 ID 打开任务书，服务端通过原生网络包让指定玩家打开节点。
    public static boolean open(long id) {
        try {
            Class<?> clientFileClass = Class.forName(CLIENT_FILE_CLASS);
            Method openMethod = clientFileClass.getMethod("openBookToQuestObject", long.class);
            openMethod.invoke(null, id);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return false;
        }
    }

    public static boolean open(String id) {
        Long parsedId = parseId(id);
        return parsedId != null && open(parsedId);
    }

    public static boolean open(Entity player, long id) {
        if (player instanceof ServerPlayer serverPlayer) {
            ServerQuestFile file = getServerFile();
            if (file == null || getServerObject(id) == null) {
                return false;
            }
            new OpenQuestBookMessage(id).sendTo(serverPlayer);
            return true;
        }
        return open(id);
    }

    public static boolean open(Entity player, String id) {
        Long parsedId = parseId(id);
        return parsedId != null && open(player, parsedId);
    }

    // 读取服务端任务书并保持可选 KubeJS 依赖的类加载边界。
    private static BaseQuestFile getActiveFile() {
        ServerQuestFile serverFile = getServerFile();
        if (serverFile != null) {
            return serverFile;
        }

        try {
            Class<?> clientFileClass = Class.forName(CLIENT_FILE_CLASS);
            return (BaseQuestFile) clientFileClass.getField("INSTANCE").get(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            return null;
        }
    }

    private static ServerQuestFile getServerFile() {
        return ServerQuestFile.INSTANCE;
    }

    private static TeamData getTeamData(Entity player) {
        ServerQuestFile file = getServerFile();
        return file == null || player == null ? null : file.getOrCreateTeamData(player);
    }

    private static QuestObject getServerObject(long id) {
        ServerQuestFile file = getServerFile();
        QuestObjectBase object = file == null ? null : file.getBase(id);
        return object instanceof QuestObject questObject ? questObject : null;
    }

    private static Quest getServerQuest(long id) {
        ServerQuestFile file = getServerFile();
        return file == null ? null : file.getQuest(id);
    }

    private static Task getServerTask(long id) {
        ServerQuestFile file = getServerFile();
        return file == null ? null : file.getTask(id);
    }

    // 创建章节级画布元素，并复用章节编辑同步覆盖新增图片列表。
    private static ChapterImage createCanvasImage(
            long chapterId,
            String imageResource,
            String click,
            double x,
            double y,
            double width,
            double height
    ) {
        ServerQuestFile file = getServerFile();
        Chapter chapter = file == null ? null : file.getChapter(chapterId);
        if (file == null
                || chapter == null
                || imageResource == null
                || click == null
                || !Double.isFinite(x)
                || !Double.isFinite(y)
                || !isPositiveFinite(width)
                || !isPositiveFinite(height)) {
            return null;
        }

        CompoundTag data = new CompoundTag();
        data.putDouble("x", x);
        data.putDouble("y", y);
        data.putDouble("width", width);
        data.putDouble("height", height);
        data.putDouble("rotation", 0.0D);
        data.putString("image", imageResource);
        if (!click.isEmpty()) {
            data.putString("click", click);
        }

        ChapterImage image = new ChapterImage(chapter);
        image.readData(data);
        chapter.addImage(image);
        syncEditedObject(file, chapter);
        return image;
    }

    private static ChapterImage getServerChapterImage(long chapterId, int index) {
        ServerQuestFile file = getServerFile();
        Chapter chapter = file == null ? null : file.getChapter(chapterId);
        return chapter != null && index >= 0 && index < chapter.getImages().size()
                ? chapter.getImages().get(index)
                : null;
    }

    private static boolean isServerChapterImage(ChapterImage image) {
        ServerQuestFile file = getServerFile();
        if (file == null || image == null || image.getChapter() == null) {
            if (!canvasImageValidationLogged) {
                canvasImageValidationLogged = true;
                QuestEnhance.LOGGER.warn(
                        "Canvas image validation failed: filePresent={}, imagePresent={}, chapterPresent={}",
                        file != null,
                        image != null,
                        image != null && image.getChapter() != null
                );
            }
            return false;
        }

        Chapter chapter = image.getChapter();
        Chapter registeredChapter = file.getChapter(chapter.id);
        boolean sameChapter = registeredChapter == chapter;
        boolean chapterContainsImage = chapter.getImages().contains(image);
        boolean registeredChapterContainsImage = registeredChapter != null
                && registeredChapter.getImages().contains(image);
        if ((!sameChapter || !chapterContainsImage) && !canvasImageValidationLogged) {
            canvasImageValidationLogged = true;
            QuestEnhance.LOGGER.warn(
                    "Canvas image validation failed: chapterId={}, imageIdentity={}, chapterIdentity={}, registeredChapterIdentity={}, sameChapter={}, chapterContainsImage={}, registeredChapterContainsImage={}, chapterImageCount={}, registeredChapterImageCount={}",
                    chapter.getCodeString(),
                    System.identityHashCode(image),
                    System.identityHashCode(chapter),
                    registeredChapter == null ? 0 : System.identityHashCode(registeredChapter),
                    sameChapter,
                    chapterContainsImage,
                    registeredChapterContainsImage,
                    chapter.getImages().size(),
                    registeredChapter == null ? 0 : registeredChapter.getImages().size()
            );
        }
        return sameChapter && chapterContainsImage;
    }

    private static boolean updateChapterImage(ChapterImage image, Consumer<CompoundTag> updater) {
        ServerQuestFile file = getServerFile();
        if (file == null || !isServerChapterImage(image)) {
            return false;
        }

        CompoundTag data = image.writeData(new CompoundTag());
        updater.accept(data);
        image.readData(data);
        syncEditedObject(file, image.getChapter());
        return true;
    }

    private static boolean isPositiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0D;
    }

    private static boolean changeQuestProgress(Entity player, long id, boolean reset) {
        ServerQuestFile file = getServerFile();
        Quest quest = file == null ? null : file.getQuest(id);
        TeamData teamData = getTeamData(player);
        if (quest == null || teamData == null || teamData.isLocked() || player == null) {
            return false;
        }

        ProgressChange change = new ProgressChange(file, quest, player.getUUID()).withNotifications();
        change.setReset(reset);
        quest.forceProgressRaw(teamData, change);
        return true;
    }

    private static boolean setObjectBoolean(QuestObjectBase object, String key, boolean value) {
        if (object == null) {
            return false;
        }
        return updateObject(object.id, data -> data.putBoolean(key, value));
    }

    private static boolean updateObject(long id, Consumer<CompoundTag> updater) {
        ServerQuestFile file = getServerFile();
        QuestObjectBase object = file == null ? null : file.getBase(id);
        if (file == null || object == null) {
            return false;
        }

        CompoundTag data = new CompoundTag();
        object.writeData(data);
        updater.accept(data);
        object.readData(data);
        syncEditedObject(file, object);
        return true;
    }

    private static void syncEditedObject(ServerQuestFile file, QuestObjectBase object) {
        object.clearCachedData();
        file.clearCachedData();
        file.markDirty();
        new EditObjectResponseMessage(object).sendToAll(file.server);
    }

    private static <T extends QuestObjectBase> T finishCreatedObject(ServerQuestFile file, T object, CompoundTag extra) {
        object.onCreated();
        file.refreshIDMap();
        file.clearCachedData();
        file.markDirty();
        new CreateObjectResponseMessage(object, extra).sendToAll(file.server);
        return object;
    }

    private static Long parseId(String id) {
        if (id == null) {
            return null;
        }

        try {
            return QuestObjectBase.parseCodeString(id);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
