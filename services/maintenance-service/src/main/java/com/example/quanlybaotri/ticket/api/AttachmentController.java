package com.example.quanlybaotri.ticket.api;

import com.example.quanlybaotri.shared.security.CurrentUser;
import com.example.quanlybaotri.ticket.application.AttachmentService;
import com.example.quanlybaotri.ticket.domain.Attachment;
import com.example.quanlybaotri.identity.client.IdentityClient;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class AttachmentController {
    private final AttachmentService service;
    private final CurrentUser current;

    public AttachmentController(AttachmentService s, CurrentUser c) {
        service = s;
        current = c;
    }

    @PostMapping(value = "/tickets/{ticketId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentView upload(@PathVariable UUID ticketId, @RequestParam(required = false) UUID workLogId,
            @RequestPart("file") MultipartFile file) {
        return service.upload(ticketId, workLogId, file, current.require());
    }

    @GetMapping("/tickets/{ticketId}/attachments")
    public List<AttachmentView> list(@PathVariable UUID ticketId) {
        return service.list(ticketId, current.require());
    }

    @GetMapping("/attachments/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable UUID id) {
        var d = service.download(id, current.require());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(d.filename(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(d.resource());
    }

    public record AttachmentView(UUID id, String originalName, String contentType, long fileSize, String sha256,
            UUID uploadedById, String uploadedByName, Instant createdAt) {
        public static AttachmentView from(Attachment a, IdentityClient.UserRef uploader) {
            return new AttachmentView(a.getId(), a.getOriginalName(), a.getContentType(), a.getFileSize(),
                    a.getSha256(), a.getUploadedById(), uploader == null ? null : uploader.fullName(), a.getCreatedAt());
        }
    }
}
