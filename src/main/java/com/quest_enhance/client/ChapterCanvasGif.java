package com.quest_enhance.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.quest_enhance.QuestEnhance;
import com.quest_enhance.mixin.ChapterImageAccessor;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ChapterCanvasGif {
    private static final String PREFIX = "quest_enhance:gif_v1:";
    private static final int MAXIMUM_FRAME_COUNT = 64;
    private static final int MAXIMUM_FRAME_SIZE = 1024;
    private static final long MAXIMUM_TOTAL_PIXELS = 16L * 1024L * 1024L;
    private static final Map<ResourceLocation, Optional<Animation>> ANIMATIONS = new HashMap<>();

    private ChapterCanvasGif() {
    }

    // 判断章节图片是否是本模组保存的 GIF 并读取资源标识
    public static Optional<GifData> getGifData(ChapterImage image) {
        String click = ChapterImageClickData.get(image);
        if (!click.startsWith(PREFIX)) {
            return Optional.empty();
        }

        return ResourceLocation.read(click.substring(PREFIX.length())).result().map(GifData::new);
    }

    // 修改 GIF 时继续复用 FTB 原生点击字段保存资源标识
    public static void setGif(ChapterImage image, ResourceLocation resource_location) {
        ChapterImageClickData.set(image, PREFIX + resource_location);
    }

    // 创建按 GIF 原始比例缩放的章节画布对象
    public static ChapterImage create(Chapter chapter, ResourceLocation resource_location, double x, double y) {
        double aspect_ratio = getAspectRatio(resource_location).orElse(1.0D);
        double width = 4.0D;
        ChapterImage image = new ChapterImage(0L, chapter)
                .setPosition(x, y)
                .setImage(Color4I.DARK_GRAY);
        ChapterImageAccessor accessor = (ChapterImageAccessor) (Object) image;
        accessor.quest_enhance$set_width(width);
        accessor.quest_enhance$set_height(Math.max(0.25D, width / aspect_ratio));
        setGif(image, resource_location);
        return image;
    }

    // 获取当前动画帧的动态纹理图标，解码失败时返回空值交由调用方绘制占位背景
    public static Optional<Icon> getCurrentFrame(ResourceLocation resource_location) {
        try {
            return ANIMATIONS
                    .computeIfAbsent(resource_location, location -> Optional.ofNullable(loadAnimation(location)))
                    .map(Animation::currentIcon);
        } catch (RuntimeException exception) {
            QuestEnhance.LOGGER.error("Failed to update GIF animation {}", resource_location, exception);
            return Optional.empty();
        }
    }

    // 读取 GIF 第一帧尺寸，用于创建时保持原图比例
    public static Optional<Double> getAspectRatio(ResourceLocation resource_location) {
        return ANIMATIONS
                .computeIfAbsent(resource_location, location -> Optional.ofNullable(loadAnimation(location)))
                .map(animation -> (double) animation.width / animation.height);
    }

    // 从资源包解码 GIF 帧，并限制尺寸、帧数和总像素量避免占用过量客户端内存
    private static Animation loadAnimation(ResourceLocation resource_location) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(resource_location);
        if (resource.isEmpty()) {
            QuestEnhance.LOGGER.warn("GIF resource was not found: {}", resource_location);
            return null;
        }

        try (InputStream input_stream = resource.get().open(); ImageInputStream image_input = ImageIO.createImageInputStream(input_stream)) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                throw new IOException("No GIF reader is available");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(image_input, false, false);
                int frame_count = reader.getNumImages(true);
                if (frame_count <= 0 || frame_count > MAXIMUM_FRAME_COUNT) {
                    throw new IOException("GIF frame count is outside the supported range: " + frame_count);
                }

                GifSize size = readGifSize(reader.getStreamMetadata(), reader.read(0));
                if (size.width() > MAXIMUM_FRAME_SIZE || size.height() > MAXIMUM_FRAME_SIZE
                        || (long) size.width() * size.height() * frame_count > MAXIMUM_TOTAL_PIXELS) {
                    throw new IOException("GIF is too large to animate safely: " + size.width() + "x" + size.height() + ", frames=" + frame_count);
                }

                List<NativeImage> frames = new ArrayList<>(frame_count);
                long[] frame_delays = new long[frame_count];
                BufferedImage canvas = new BufferedImage(size.width(), size.height(), BufferedImage.TYPE_INT_ARGB);
                for (int frame_index = 0; frame_index < frame_count; frame_index++) {
                    BufferedImage source = reader.read(frame_index);
                    GifFrameInfo info = readFrameInfo(reader.getImageMetadata(frame_index));
                    BufferedImage previous = "restoreToPrevious".equals(info.disposal()) ? copyImage(canvas) : null;
                    Graphics2D graphics = canvas.createGraphics();
                    graphics.setComposite(AlphaComposite.SrcOver);
                    graphics.drawImage(source, info.left(), info.top(), null);
                    graphics.dispose();
                    frames.add(toNativeImage(canvas));
                    frame_delays[frame_index] = Math.max(20L, info.delay_millis());

                    if ("restoreToBackgroundColor".equals(info.disposal())) {
                        Graphics2D clear_graphics = canvas.createGraphics();
                        clear_graphics.setComposite(AlphaComposite.Clear);
                        clear_graphics.fillRect(info.left(), info.top(), source.getWidth(), source.getHeight());
                        clear_graphics.dispose();
                    } else if (previous != null) {
                        canvas = previous;
                    }
                }
                return new Animation(resource_location, size.width(), size.height(), frames, frame_delays);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            QuestEnhance.LOGGER.error("Failed to load GIF animation {}", resource_location, exception);
            return null;
        }
    }

    // 从 GIF 流元数据读取逻辑画布尺寸，缺失时回退到首帧尺寸
    private static GifSize readGifSize(IIOMetadata metadata, BufferedImage first_frame) {
        Node descriptor = findNode(metadata, "LogicalScreenDescriptor");
        int width = getAttribute(descriptor, "logicalScreenWidth", first_frame.getWidth());
        int height = getAttribute(descriptor, "logicalScreenHeight", first_frame.getHeight());
        return new GifSize(Math.max(1, width), Math.max(1, height));
    }

    // 从 GIF 帧元数据读取偏移、帧延迟和销毁方式
    private static GifFrameInfo readFrameInfo(IIOMetadata metadata) {
        Node descriptor = findNode(metadata, "ImageDescriptor");
        Node control = findNode(metadata, "GraphicControlExtension");
        return new GifFrameInfo(
                getAttribute(descriptor, "imageLeftPosition", 0),
                getAttribute(descriptor, "imageTopPosition", 0),
                getAttribute(control, "delayTime", 10) * 10L,
                getStringAttribute(control, "disposalMethod", "none")
        );
    }

    // 在 GIF 元数据树中按名称寻找节点
    private static Node findNode(IIOMetadata metadata, String node_name) {
        if (metadata == null) {
            return null;
        }

        try {
            return findNode(metadata.getAsTree("javax_imageio_gif_image_1.0"), node_name);
        } catch (IllegalArgumentException exception) {
            try {
                return findNode(metadata.getAsTree("javax_imageio_gif_stream_1.0"), node_name);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    // 递归遍历元数据节点，避免依赖 JDK 内部 GIF 实现类
    private static Node findNode(Node node, String node_name) {
        if (node == null) {
            return null;
        }
        if (node_name.equals(node.getNodeName())) {
            return node;
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            Node found = findNode(child, node_name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // 从元数据属性中读取整数并在缺失或异常时保留默认值
    private static int getAttribute(Node node, String name, int default_value) {
        String value = getStringAttribute(node, name, "");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return default_value;
        }
    }

    // 从元数据属性中读取字符串并在节点不存在时保留默认值
    private static String getStringAttribute(Node node, String name, String default_value) {
        if (node == null || node.getAttributes() == null || node.getAttributes().getNamedItem(name) == null) {
            return default_value;
        }
        return node.getAttributes().getNamedItem(name).getNodeValue();
    }

    // 复制当前合成画布，用于 GIF 的 restoreToPrevious 销毁方式
    private static BufferedImage copyImage(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return copy;
    }

    // 转换 Java ARGB 图像为 Minecraft 动态纹理使用的 ABGR 像素格式
    private static NativeImage toNativeImage(BufferedImage source) {
        NativeImage image = new NativeImage(source.getWidth(), source.getHeight(), true);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int abgr = (argb & 0xFF00FF00)
                        | ((argb & 0x00FF0000) >>> 16)
                        | ((argb & 0x000000FF) << 16);
                image.setPixelRGBA(x, y, abgr);
            }
        }
        return image;
    }

    public record GifData(ResourceLocation resource_location) {
    }

    private record GifSize(int width, int height) {
    }

    private record GifFrameInfo(int left, int top, long delay_millis, String disposal) {
    }

    // 共享同一 GIF 的所有画布对象共用一组解码帧和动态纹理
    private static final class Animation {
        private final int width;
        private final int height;
        private final List<NativeImage> frames;
        private final long[] frame_delays;
        private final DynamicTexture texture;
        private final Icon icon;
        private int frame_index;
        private long next_frame_time;

        private Animation(
                ResourceLocation source_location,
                int width,
                int height,
                List<NativeImage> frames,
                long[] frame_delays
        ) {
            this.width = width;
            this.height = height;
            this.frames = frames;
            this.frame_delays = frame_delays;
            NativeImage texture_pixels = new NativeImage(width, height, true);
            texture_pixels.copyFrom(frames.getFirst());
            this.texture = new DynamicTexture(texture_pixels);
            ResourceLocation texture_location = ResourceLocation.fromNamespaceAndPath(
                    QuestEnhance.MOD_ID,
                    "dynamic_gif/" + UUID.nameUUIDFromBytes(source_location.toString().getBytes(StandardCharsets.UTF_8)) + ".png"
            );
            Minecraft.getInstance().getTextureManager().register(texture_location, this.texture);
            this.icon = Icon.getIcon(texture_location);
            this.next_frame_time = System.currentTimeMillis() + frame_delays[0];
        }

        // 根据 GIF 帧延迟推进纹理，并在窗口卡顿后跳过过期帧避免积压
        private Icon currentIcon() {
            long current_time = System.currentTimeMillis();
            if (frames.size() > 1 && current_time >= this.next_frame_time) {
                do {
                    this.frame_index = (this.frame_index + 1) % this.frames.size();
                    this.next_frame_time += this.frame_delays[this.frame_index];
                } while (current_time >= this.next_frame_time);
                NativeImage pixels = this.texture.getPixels();
                if (pixels != null) {
                    pixels.copyFrom(this.frames.get(this.frame_index));
                    this.texture.upload();
                }
            }
            return this.icon;
        }
    }
}
