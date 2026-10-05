package com.example.quanlybaotri.ticket.api;

import com.example.quanlybaotri.shared.security.CurrentUser;
import com.example.quanlybaotri.ticket.application.AttachmentService;
import com.example.quanlybaotri.ticket.domain.AttachmentType;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class AttachmentController {
    private final AttachmentService service; private final CurrentUser current;
    public AttachmentController(AttachmentService service,CurrentUser current){this.service=service;this.current=current;}
    @PostMapping(value="/tickets/{ticketId}/attachments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public AttachmentView upload(@PathVariable UUID ticketId,@RequestParam(required=false) UUID workLogId,@RequestParam(required=false) UUID costItemId,@RequestParam(required=false,defaultValue="OTHER") AttachmentType type,@RequestPart("file") MultipartFile file){return service.upload(ticketId,workLogId,costItemId,type,file,current.require());}
    @GetMapping("/tickets/{ticketId}/attachments") public List<AttachmentView> list(@PathVariable UUID ticketId){return service.list(ticketId,current.require());}
    @GetMapping("/attachments/{id}") public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable UUID id){var d=service.download(id,current.require());return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.contentType())).contentLength(d.contentLength()).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(d.filename(),StandardCharsets.UTF_8).build().toString()).body(d.resource());}
    @GetMapping("/attachments/{id}/preview-url") public AttachmentService.Preview preview(@PathVariable UUID id){return service.preview(id,current.require());}
    @DeleteMapping("/attachments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id){service.delete(id,current.require());}
    public record AttachmentView(UUID id,String originalName,String contentType,long fileSize,String sha256,UUID uploadedById,String uploadedByName,AttachmentType type,String previewKind,boolean canDelete,UUID workLogId,UUID costItemId,Instant createdAt){}
}
