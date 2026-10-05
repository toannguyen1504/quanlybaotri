package com.example.quanlybaotri.shared.storage;

import com.example.quanlybaotri.shared.api.ApiException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

public final class FilePolicy {
    private static final long MIB = 1024L * 1024L;
    private static final Set<String> IMAGE = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> VIDEO = Set.of("video/mp4", "video/webm");
    private static final Set<String> DOCUMENT = Set.of(
        "application/pdf", "application/msword", "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private FilePolicy() {}

    public static ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("INVALID_FILE_SIZE", "Tệp phải có dung lượng lớn hơn 0");
        String name = safeName(file.getOriginalFilename());
        String type;
        try { type = detect(file, extension(name)); }
        catch (IOException ex) { throw invalid("INVALID_FILE_TYPE", "Không thể nhận diện định dạng tệp"); }
        long max = IMAGE.contains(type) ? 10 * MIB : VIDEO.contains(type) ? 200 * MIB : DOCUMENT.contains(type) ? 25 * MIB : 0;
        if (max == 0) throw invalid("INVALID_FILE_TYPE", "Chỉ hỗ trợ JPG, PNG, WebP, MP4, WebM, PDF, DOC, DOCX, XLS và XLSX");
        if (file.getSize() > max) throw invalid("INVALID_FILE_SIZE", "Tệp vượt quá giới hạn dung lượng cho định dạng này");
        return new ValidatedFile(name, type, IMAGE.contains(type) ? "IMAGE" : VIDEO.contains(type) ? "VIDEO" : type.equals("application/pdf") ? "PDF" : "DOWNLOAD");
    }

    private static String detect(MultipartFile file, String ext) throws IOException {
        byte[] header = new byte[16];
        int count;
        try (InputStream in = new BufferedInputStream(file.getInputStream())) { count = in.read(header); }
        if (count >= 3 && u(header[0]) == 0xff && u(header[1]) == 0xd8 && u(header[2]) == 0xff) return requireExt(ext, "jpg", "jpeg", "image/jpeg");
        if (count >= 8 && matches(header, new int[]{0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a})) return requireExt(ext, "png", null, "image/png");
        if (count >= 12 && ascii(header, 0, "RIFF") && ascii(header, 8, "WEBP")) return requireExt(ext, "webp", null, "image/webp");
        if (count >= 5 && ascii(header, 0, "%PDF-")) return requireExt(ext, "pdf", null, "application/pdf");
        if (count >= 8 && ascii(header, 4, "ftyp")) return requireExt(ext, "mp4", null, "video/mp4");
        if (count >= 4 && matches(header, new int[]{0x1a,0x45,0xdf,0xa3})) return requireExt(ext, "webm", null, "video/webm");
        if (count >= 8 && matches(header, new int[]{0xd0,0xcf,0x11,0xe0,0xa1,0xb1,0x1a,0xe1})) {
            if ("doc".equals(ext)) return "application/msword";
            if ("xls".equals(ext)) return "application/vnd.ms-excel";
        }
        if (count >= 4 && u(header[0]) == 0x50 && u(header[1]) == 0x4b) return detectOoxml(file, ext);
        return "application/octet-stream";
    }

    private static String detectOoxml(MultipartFile file, String ext) throws IOException {
        boolean word = false, excel = false;
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            int inspected = 0;
            while ((entry = zip.getNextEntry()) != null && inspected++ < 100) {
                word |= entry.getName().startsWith("word/");
                excel |= entry.getName().startsWith("xl/");
            }
        }
        if (word && "docx".equals(ext)) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (excel && "xlsx".equals(ext)) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return "application/octet-stream";
    }

    private static String requireExt(String actual, String first, String second, String type) {
        return actual.equals(first) || (second != null && actual.equals(second)) ? type : "application/octet-stream";
    }
    private static String extension(String name) { int dot = name.lastIndexOf('.'); return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT); }
    public static String safeName(String name) {
        if (name == null || name.isBlank()) return "attachment";
        String normalized = name.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[\\r\\n]", "_");
        return normalized.substring(0, Math.min(255, normalized.length()));
    }
    private static boolean ascii(byte[] value, int offset, String expected) { for (int i=0;i<expected.length();i++) if (offset+i >= value.length || value[offset+i] != (byte)expected.charAt(i)) return false; return true; }
    private static boolean matches(byte[] value, int[] expected) { for (int i=0;i<expected.length;i++) if (u(value[i]) != expected[i]) return false; return true; }
    private static int u(byte value) { return value & 0xff; }
    private static ApiException invalid(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
    public record ValidatedFile(String name, String contentType, String previewKind) {}
}
