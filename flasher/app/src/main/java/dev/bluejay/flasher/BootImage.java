package dev.bluejay.flasher;

import android.content.ContentResolver;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads just enough of the selected file to tell an Android boot image from a
 * vendor_boot image, and to report its header version and size.
 */
final class BootImage {

    enum Kind { BOOT, VENDOR_BOOT, UNKNOWN }

    final Kind kind;
    final int headerVersion;
    final long size;
    String displayName = "recovery.img";

    private BootImage(Kind kind, int headerVersion, long size) {
        this.kind = kind;
        this.headerVersion = headerVersion;
        this.size = size;
    }

    static BootImage inspect(ContentResolver resolver, Uri uri) throws IOException {
        byte[] head = new byte[44];
        long size = 0;

        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                throw new IOException("The selected file could not be opened.");
            }

            int read = 0;
            while (read < head.length) {
                int n = in.read(head, read, head.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }

            if (read < 8) {
                throw new IOException("The selected file is too small to be an Android image.");
            }

            byte[] buffer = new byte[1 << 16];
            int n;
            while ((n = in.read(buffer)) > 0) {
                size += n;
            }
            size += read;
        }

        String magic = new String(head, 0, 8, StandardCharsets.US_ASCII);
        if ("VNDRBOOT".equals(magic)) {
            // vendor_boot_img_hdr_v3/v4: header_version is the second field.
            return new BootImage(Kind.VENDOR_BOOT, littleEndian(head, 8), size);
        }
        if ("ANDROID!".equals(magic)) {
            // boot_img_hdr v0..v4 all keep header_version at offset 40.
            return new BootImage(Kind.BOOT, littleEndian(head, 40), size);
        }
        return new BootImage(Kind.UNKNOWN, -1, size);
    }

    private static int littleEndian(byte[] data, int offset) {
        if (offset + 4 > data.length) {
            return -1;
        }
        return (data[offset] & 0xff)
                | (data[offset + 1] & 0xff) << 8
                | (data[offset + 2] & 0xff) << 16
                | (data[offset + 3] & 0xff) << 24;
    }

    String kindLabel() {
        switch (kind) {
            case BOOT:
                return headerVersion >= 0
                        ? "Android boot image (header v" + headerVersion + ")"
                        : "Android boot image";
            case VENDOR_BOOT:
                return headerVersion >= 0
                        ? "vendor_boot image (header v" + headerVersion + ")"
                        : "vendor_boot image";
            default:
                return "Unrecognised header — not a boot or vendor_boot image";
        }
    }
}
