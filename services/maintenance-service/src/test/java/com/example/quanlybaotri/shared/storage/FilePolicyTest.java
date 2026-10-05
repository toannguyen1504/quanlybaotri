package com.example.quanlybaotri.shared.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.example.quanlybaotri.shared.api.ApiException;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FilePolicyTest {
    @Test
    void detectsImageFromSignatureInsteadOfBrowserMime() {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0};
        var file = new MockMultipartFile("file", "damage.png", "text/html", png);
        var valid = FilePolicy.validate(file);
        assertEquals("image/png", valid.contentType());
        assertEquals("IMAGE", valid.previewKind());
    }

    @Test
    void rejectsSpoofedExtension() {
        byte[] pdf = "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var file = new MockMultipartFile("file", "invoice.jpg", "image/jpeg", pdf);
        ApiException error = assertThrows(ApiException.class, () -> FilePolicy.validate(file));
        assertEquals("INVALID_FILE_TYPE", error.getCode());
    }

    @Test
    void identifiesOfficeContainerByZipStructure() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<document/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var file = new MockMultipartFile("file", "report.docx", "application/zip", bytes.toByteArray());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", FilePolicy.validate(file).contentType());
    }
}
