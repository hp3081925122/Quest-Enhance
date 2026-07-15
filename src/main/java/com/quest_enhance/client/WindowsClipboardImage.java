package com.quest_enhance.client;

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

    private WindowsClipboardImage() {
    }

    // 从 Windows 原生剪贴板读取 DIB 图片并转换为 Java 图像
    public static BufferedImage read() throws IOException {
        if (!Platform.isWindows()) {
            throw new IOException("Native clipboard image reading is only available on Windows");
        }

        // 优先使用兼容性更好的 CF_DIB，必要时回退到 CF_DIBV5
        int clipboard_format;
        if (User32Clipboard.INSTANCE.IsClipboardFormatAvailable(WinUser.CF_DIB)) {
            clipboard_format = WinUser.CF_DIB;
        } else if (User32Clipboard.INSTANCE.IsClipboardFormatAvailable(WinUser.CF_DIBV5)) {
            clipboard_format = WinUser.CF_DIBV5;
        } else {
            return null;
        }

        // 短暂重试被其他程序占用的系统剪贴板
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

        // 复制系统管理的 DIB 内存后立即释放剪贴板锁
        byte[] dib_data;
        try {
            Pointer clipboard_handle = User32Clipboard.INSTANCE.GetClipboardData(clipboard_format);
            if (clipboard_handle == null) {
                throw new IOException("GetClipboardData failed with error " + Native.getLastError());
            }

            Pointer dib_pointer = Kernel32Clipboard.INSTANCE.GlobalLock(clipboard_handle);
            if (dib_pointer == null) {
                throw new IOException("GlobalLock failed with error " + Native.getLastError());
            }

            try {
                long dib_size = Kernel32Clipboard.INSTANCE.GlobalSize(clipboard_handle).longValue();
                if (dib_size <= 0L || dib_size > MAX_CLIPBOARD_IMAGE_BYTES) {
                    throw new IOException("Clipboard image size is invalid: " + dib_size);
                }
                dib_data = dib_pointer.getByteArray(0L, (int) dib_size);
            } finally {
                Kernel32Clipboard.INSTANCE.GlobalUnlock(clipboard_handle);
            }
        } finally {
            User32Clipboard.INSTANCE.CloseClipboard();
        }

        // 为剪贴板 DIB 补充 BMP 文件头后交给 ImageIO 解码
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
            if (header_size == 40 && compression == 3) {
                external_mask_size = 12;
            } else if (header_size == 40 && compression == 6) {
                external_mask_size = 16;
            }
        } else {
            throw new IOException("Unsupported clipboard DIB header size: " + header_size);
        }

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

    // 声明读取 Windows 剪贴板所需的 User32 接口
    private interface User32Clipboard extends StdCallLibrary {
        User32Clipboard INSTANCE = Native.load("user32", User32Clipboard.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean OpenClipboard(Pointer window_handle);

        boolean CloseClipboard();

        boolean IsClipboardFormatAvailable(int format);

        Pointer GetClipboardData(int format);
    }

    // 声明锁定 Windows 全局内存所需的 Kernel32 接口
    private interface Kernel32Clipboard extends StdCallLibrary {
        Kernel32Clipboard INSTANCE = Native.load("kernel32", Kernel32Clipboard.class, W32APIOptions.DEFAULT_OPTIONS);

        Pointer GlobalLock(Pointer memory_handle);

        boolean GlobalUnlock(Pointer memory_handle);

        BaseTSD.SIZE_T GlobalSize(Pointer memory_handle);
    }
}
