package com.quest_enhance.client.clipboard;

import com.quest_enhance.QuestEnhance;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class WindowsClipboardImage {
    private static final int MAX_CLIPBOARD_IMAGE_BYTES = 256 * 1024 * 1024;
    private static final int BI_RGB = 0;
    private static final int BI_BITFIELDS = 3;
    private static final int BI_ALPHABITFIELDS = 6;
    private static final int DEFAULT_RED_MASK = 0x00FF0000;
    private static final int DEFAULT_GREEN_MASK = 0x0000FF00;
    private static final int DEFAULT_BLUE_MASK = 0x000000FF;
    private static final int DEFAULT_ALPHA_MASK = 0xFF000000;

    private static volatile int png_format_id = -1;
    private static volatile int image_png_format_id = -1;

    private WindowsClipboardImage() {
    }

    // 从 Windows 原生剪贴板读取图片并优先保留透明通道
    public static BufferedImage read() throws IOException {
        if (!Platform.isWindows()) {
            throw new IOException("Native clipboard image reading is only available on Windows");
        }

        // 按透明通道保真度从高到低依次尝试：PNG -> image/png -> CF_DIBV5 -> CF_DIB
        int[] candidate_formats = new int[]{
                getPngFormatId(),
                getImagePngFormatId(),
                WinUser.CF_DIBV5,
                WinUser.CF_DIB
        };
        String[] candidate_names = new String[]{
                "PNG",
                "image/png",
                "CF_DIBV5",
                "CF_DIB"
        };

        boolean found_supported_format = false;
        IOException last_error = null;
        for (int index = 0; index < candidate_formats.length; index++) {
            int format = candidate_formats[index];
            if (format <= 0 || !User32Clipboard.INSTANCE.IsClipboardFormatAvailable(format)) {
                continue;
            }
            found_supported_format = true;
            String format_name = candidate_names[index];
            try {
                byte[] clipboard_bytes = readClipboardBytes(format);
                if (clipboard_bytes == null || clipboard_bytes.length == 0) {
                    continue;
                }
                BufferedImage decoded = isPngFormat(format)
                        ? decodePng(clipboard_bytes)
                        : decodeDib(clipboard_bytes);
                if (decoded != null) {
                    QuestEnhance.LOGGER.debug(
                            "Decoded Windows clipboard image: format={}, size={}x{}, hasAlpha={}, bytes={}",
                            format_name,
                            decoded.getWidth(),
                            decoded.getHeight(),
                            decoded.getColorModel().hasAlpha(),
                            clipboard_bytes.length
                    );
                    return decoded;
                }
                QuestEnhance.LOGGER.debug(
                        "Clipboard format {} returned null image, trying fallback format",
                        format_name
                );
            } catch (IOException | RuntimeException exception) {
                QuestEnhance.LOGGER.debug(
                        "Failed to decode Windows clipboard format {}, trying fallback format",
                        format_name,
                        exception
                );
                last_error = exception instanceof IOException
                        ? (IOException) exception
                        : new IOException("Failed to decode clipboard format " + format_name, exception);
            }
        }

        if (!found_supported_format) {
            return null;
        }
        if (last_error != null) {
            throw last_error;
        }
        throw new IOException("Unable to decode any supported Windows clipboard image format");
    }

    // 读取指定剪贴板格式的原始全局内存字节并立即释放剪贴板锁
    private static byte[] readClipboardBytes(int clipboard_format) throws IOException {
        boolean clipboard_opened = false;
        for (int attempt = 0; attempt < 5; attempt++) {
            if (User32Clipboard.INSTANCE.OpenClipboard(null)) {
                clipboard_opened = true;
                break;
            }

            try {
                Thread.sleep(5L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while opening the Windows clipboard", exception);
            }
        }

        if (!clipboard_opened) {
            throw new IOException("OpenClipboard failed with error " + Native.getLastError());
        }

        try {
            Pointer clipboard_handle = User32Clipboard.INSTANCE.GetClipboardData(clipboard_format);
            if (clipboard_handle == null) {
                throw new IOException("GetClipboardData failed with error " + Native.getLastError());
            }

            Pointer memory_pointer = Kernel32Clipboard.INSTANCE.GlobalLock(clipboard_handle);
            if (memory_pointer == null) {
                throw new IOException("GlobalLock failed with error " + Native.getLastError());
            }

            try {
                long byte_size = Kernel32Clipboard.INSTANCE.GlobalSize(clipboard_handle).longValue();
                if (byte_size <= 0L || byte_size > MAX_CLIPBOARD_IMAGE_BYTES) {
                    throw new IOException("Clipboard image size is invalid: " + byte_size);
                }
                return memory_pointer.getByteArray(0L, (int) byte_size);
            } finally {
                Kernel32Clipboard.INSTANCE.GlobalUnlock(clipboard_handle);
            }
        } finally {
            User32Clipboard.INSTANCE.CloseClipboard();
        }
    }

    // 直接解码剪贴板中的原始 PNG 字节流以完整保留透明通道
    static BufferedImage decodePng(byte[] png_data) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(png_data));
    }

    // 解码 CF_DIB / CF_DIBV5 字节流，对 32 位位图手动提取 ARGB 以避免 ImageIO 丢弃透明通道
    static BufferedImage decodeDib(byte[] dib_data) throws IOException {
        if (dib_data.length < 12) {
            throw new IOException("Clipboard DIB header is incomplete");
        }

        ByteBuffer dib_header = ByteBuffer.wrap(dib_data).order(ByteOrder.LITTLE_ENDIAN);
        int header_size = dib_header.getInt(0);
        int palette_entries = 0;
        int palette_entry_size;
        int external_mask_size = 0;

        if (header_size == 12) {
            int bit_count = Short.toUnsignedInt(dib_header.getShort(10));
            palette_entries = bit_count <= 8 ? 1 << bit_count : 0;
            palette_entry_size = 3;
        } else if (header_size >= 40 && header_size <= dib_data.length) {
            int bit_count = Short.toUnsignedInt(dib_header.getShort(14));
            int compression = dib_header.getInt(16);
            long colors_used = Integer.toUnsignedLong(dib_header.getInt(32));
            palette_entries = bit_count <= 8
                    ? (colors_used == 0L ? 1 << bit_count : Math.toIntExact(colors_used))
                    : 0;
            palette_entry_size = 4;
            if (header_size == 40 && compression == BI_BITFIELDS) {
                external_mask_size = 12;
            } else if (header_size == 40 && compression == BI_ALPHABITFIELDS) {
                external_mask_size = 16;
            }

            // 32 位非压缩/位域 DIB 优先手动按通道掩码解码，防止 JDK BMPImageReader 忽略 Alpha
            if (bit_count == 32 && (compression == BI_RGB || compression == BI_BITFIELDS || compression == BI_ALPHABITFIELDS)) {
                int pixel_offset_in_dib = header_size + external_mask_size + palette_entries * palette_entry_size;
                BufferedImage decoded_32bit = decode32BitDib(dib_data, dib_header, header_size, compression, pixel_offset_in_dib);
                if (decoded_32bit != null) {
                    return decoded_32bit;
                }
            }
        } else {
            throw new IOException("Unsupported clipboard DIB header size: " + header_size);
        }

        // 其他位深或压缩格式回退为补齐 BMP 文件头后交由 ImageIO 解码
        int pixel_offset = 14 + header_size + external_mask_size + palette_entries * palette_entry_size;
        byte[] bmp_data = new byte[14 + dib_data.length];
        ByteBuffer bmp_header = ByteBuffer.wrap(bmp_data).order(ByteOrder.LITTLE_ENDIAN);
        bmp_header.put((byte) 'B');
        bmp_header.put((byte) 'M');
        bmp_header.putInt(bmp_data.length);
        bmp_header.putInt(0);
        bmp_header.putInt(pixel_offset);
        System.arraycopy(dib_data, 0, bmp_data, 14, dib_data.length);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bmp_data));
        if (image == null) {
            throw new IOException("ImageIO could not decode the clipboard DIB image");
        }
        return image;
    }

    // 手动解析 32 位 DIB / DIBV5 像素数据，保留有效 Alpha 并兼容全 0 Alpha 的不透明位图
    private static BufferedImage decode32BitDib(
            byte[] dib_data,
            ByteBuffer dib_header,
            int header_size,
            int compression,
            int pixel_offset_in_dib
    ) {
        int width = dib_header.getInt(4);
        int raw_height = dib_header.getInt(8);
        long abs_height = Math.abs((long) raw_height);
        if (width <= 0 || abs_height <= 0L || abs_height > Integer.MAX_VALUE) {
            return null;
        }
        int height = (int) abs_height;
        long pixel_count_long = (long) width * (long) height;
        if (pixel_count_long <= 0L || pixel_count_long > Integer.MAX_VALUE) {
            return null;
        }
        long required_bytes = (long) pixel_offset_in_dib + pixel_count_long * 4L;
        if (pixel_offset_in_dib < 0 || dib_data.length < required_bytes) {
            return null;
        }

        int red_mask = DEFAULT_RED_MASK;
        int green_mask = DEFAULT_GREEN_MASK;
        int blue_mask = DEFAULT_BLUE_MASK;
        int alpha_mask = DEFAULT_ALPHA_MASK;

        if (compression == BI_BITFIELDS || compression == BI_ALPHABITFIELDS) {
            if (dib_data.length < 52) {
                return null;
            }
            red_mask = dib_header.getInt(40);
            green_mask = dib_header.getInt(44);
            blue_mask = dib_header.getInt(48);
            if (red_mask == 0 && green_mask == 0 && blue_mask == 0) {
                red_mask = DEFAULT_RED_MASK;
                green_mask = DEFAULT_GREEN_MASK;
                blue_mask = DEFAULT_BLUE_MASK;
            }

            if ((header_size >= 56 || compression == BI_ALPHABITFIELDS) && dib_data.length >= 56) {
                alpha_mask = dib_header.getInt(52);
            } else {
                alpha_mask = 0;
            }
            if (alpha_mask == 0) {
                int remaining_bits = ~(red_mask | green_mask | blue_mask);
                alpha_mask = remaining_bits != 0 ? remaining_bits : 0;
            }
        } else if (header_size >= 56 && dib_data.length >= 56) {
            int header_alpha_mask = dib_header.getInt(52);
            if (header_alpha_mask != 0) {
                alpha_mask = header_alpha_mask;
            }
        }

        int[] argb_pixels = new int[(int) pixel_count_long];
        boolean top_down = raw_height < 0;
        boolean has_non_zero_alpha = false;
        boolean standard_bgra = red_mask == DEFAULT_RED_MASK
                && green_mask == DEFAULT_GREEN_MASK
                && blue_mask == DEFAULT_BLUE_MASK
                && alpha_mask == DEFAULT_ALPHA_MASK;

        int byte_index = pixel_offset_in_dib;
        for (int row = 0; row < height; row++) {
            int target_y = top_down ? row : (height - 1 - row);
            int row_offset = target_y * width;
            for (int x = 0; x < width; x++) {
                int pixel = dib_header.getInt(byte_index);
                byte_index += 4;

                int argb;
                int alpha;
                if (standard_bgra) {
                    alpha = (pixel >>> 24) & 0xFF;
                    argb = pixel;
                } else {
                    int red = extractChannel(pixel, red_mask);
                    int green = extractChannel(pixel, green_mask);
                    int blue = extractChannel(pixel, blue_mask);
                    alpha = alpha_mask != 0 ? extractChannel(pixel, alpha_mask) : 0xFF;
                    argb = (alpha << 24) | (red << 16) | (green << 8) | blue;
                }

                if (alpha != 0) {
                    has_non_zero_alpha = true;
                }
                argb_pixels[row_offset + x] = argb;
            }
        }

        // 部分传统程序写入 32 位 DIB 时会把未使用的 Alpha 字节全填为 0，此时恢复为不透明避免整图透明
        if (!has_non_zero_alpha) {
            for (int index = 0; index < argb_pixels.length; index++) {
                argb_pixels[index] |= 0xFF000000;
            }
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, argb_pixels, 0, width);
        return image;
    }

    // 按通道位掩码提取像素分量并缩放到 0..255
    private static int extractChannel(int pixel, int mask) {
        if (mask == 0) {
            return 0;
        }
        int shift = Integer.numberOfTrailingZeros(mask);
        int max_value = mask >>> shift;
        int value = (pixel & mask) >>> shift;
        if (max_value == 255) {
            return value;
        }
        if (max_value <= 0) {
            return 0;
        }
        return (int) ((Integer.toUnsignedLong(value) * 255L + Integer.toUnsignedLong(max_value) / 2L)
                / Integer.toUnsignedLong(max_value));
    }

    // 判断格式 ID 是否属于 PNG 原始字节流格式
    private static boolean isPngFormat(int format) {
        return format > 0 && (format == png_format_id || format == image_png_format_id);
    }

    // 惰性查询 Windows 剪贴板注册格式 PNG
    private static int getPngFormatId() {
        int cached = png_format_id;
        if (cached < 0) {
            cached = User32Clipboard.INSTANCE.RegisterClipboardFormat("PNG");
            png_format_id = cached;
        }
        return cached;
    }

    // 惰性查询 Windows 剪贴板注册格式 image/png
    private static int getImagePngFormatId() {
        int cached = image_png_format_id;
        if (cached < 0) {
            cached = User32Clipboard.INSTANCE.RegisterClipboardFormat("image/png");
            image_png_format_id = cached;
        }
        return cached;
    }

    // 声明读取 Windows 剪贴板所需的 User32 接口
    private interface User32Clipboard extends StdCallLibrary {
        User32Clipboard INSTANCE = Native.load("user32", User32Clipboard.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean OpenClipboard(Pointer window_handle);

        boolean CloseClipboard();

        boolean IsClipboardFormatAvailable(int format);

        Pointer GetClipboardData(int format);

        int RegisterClipboardFormat(String format_name);
    }

    // 声明锁定 Windows 全局内存所需的 Kernel32 接口
    private interface Kernel32Clipboard extends StdCallLibrary {
        Kernel32Clipboard INSTANCE = Native.load("kernel32", Kernel32Clipboard.class, W32APIOptions.DEFAULT_OPTIONS);

        Pointer GlobalLock(Pointer memory_handle);

        boolean GlobalUnlock(Pointer memory_handle);

        BaseTSD.SIZE_T GlobalSize(Pointer memory_handle);
    }
}
