package com.example.quanlybaotri.ticket.application;

import com.example.quanlybaotri.identity.domain.*;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.ticket.api.AttachmentController.AttachmentView;
import com.example.quanlybaotri.ticket.domain.*;
import com.example.quanlybaotri.ticket.persistence.*;
import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {
    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "application/pdf");
    private final Path root;
    private final TicketRepository tickets;
    private final WorkLogRepository workLogs;
    private final AttachmentRepository attachments;
    private final TicketEventService events;

    public AttachmentService(@Value("${app.storage.root}") String root, TicketRepository t, WorkLogRepository w,
            AttachmentRepository a, TicketEventService e) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
        tickets = t;
        workLogs = w;
        attachments = a;
        events = e;
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create FILE_STORAGE_ROOT", e);
        }
    }

    @Transactional
    public AttachmentView upload(UUID ticketId, UUID workLogId, MultipartFile file, UserAccount actor) {
        MaintenanceTicket t = find(ticketId);
        visible(t, actor);
        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024L)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_SIZE",
                    "Tệp phải có dung lượng từ 1 byte đến 10 MB");
        String type = file.getContentType();
        if (!ALLOWED.contains(type))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "Chỉ hỗ trợ JPG, PNG và PDF");
        WorkLog log = null;
        if (workLogId != null) {
            log = workLogs.findById(workLogId).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhật ký xử lý"));
            if (!log.getTicket().getId().equals(ticketId))
                throw ApiException.conflict("Nhật ký không thuộc phiếu");
        }
        String key = LocalDate.now(ZoneOffset.UTC).toString().replace("-", "/") + "/" + UUID.randomUUID();
        Path target = safe(key);
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String hash = HexFormat.of().formatHex(digest.digest());
            Attachment a = attachments.save(new Attachment(t, log, actor, key, safeName(file.getOriginalFilename()),
                    type, file.getSize(), hash));
            events.record(t, actor, "ATTACHMENT_ADDED", t.getStatus(), t.getStatus(),
                    "Đã thêm tệp " + a.getOriginalName(), Map.of("attachmentId", a.getId()));
            return AttachmentView.from(a);
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_ERROR", "Không thể lưu tệp");
        }
    }

    @Transactional(readOnly = true)
    public List<AttachmentView> list(UUID ticketId, UserAccount actor) {
        MaintenanceTicket t = find(ticketId);
        visible(t, actor);
        return attachments.findByTicketIdOrderByCreatedAtDesc(ticketId).stream().map(AttachmentView::from).toList();
    }

    @Transactional(readOnly = true)
    public Download download(UUID id, UserAccount actor) {
        Attachment a = attachments.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tệp"));
        visible(a.getTicket(), actor);
        Path path = safe(a.getStorageKey());
        if (!Files.exists(path))
            throw ApiException.notFound("Tệp không còn trên bộ nhớ");
        return new Download(new FileSystemResource(path), a.getOriginalName(), a.getContentType());
    }

    private Path safe(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STORAGE_PATH", "Đường dẫn tệp không hợp lệ");
        return p;
    }

    private String safeName(String n) {
        if (n == null || n.isBlank())
            return "attachment";
        String v = Paths.get(n).getFileName().toString().replaceAll("[\\r\\n]", "_");
        return v.substring(0, Math.min(255, v.length()));
    }

    private MaintenanceTicket find(UUID id) {
        return tickets.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu"));
    }

    private void visible(MaintenanceTicket t, UserAccount u) {
        boolean elevated = u.getRoles().stream()
                .anyMatch(r -> r.getName() == RoleName.ADMIN || r.getName() == RoleName.MANAGER);
        if (!elevated && !t.getRequester().getId().equals(u.getId())
                && (t.getAssignee() == null || !t.getAssignee().getId().equals(u.getId())))
            throw ApiException.forbidden("Không có quyền truy cập tệp");
    }

    public record Download(Resource resource, String filename, String contentType) {
    }
}
