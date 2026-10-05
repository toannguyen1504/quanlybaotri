package com.example.quanlybaotri.equipment.api;

import com.example.quanlybaotri.equipment.application.EquipmentDocumentService;
import com.example.quanlybaotri.equipment.domain.EquipmentDocumentType;
import com.example.quanlybaotri.shared.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class EquipmentDocumentController {
    private final EquipmentDocumentService service;private final CurrentUser current;public EquipmentDocumentController(EquipmentDocumentService service,CurrentUser current){this.service=service;this.current=current;}
    @PostMapping(value="/equipment/{equipmentId}/documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED) public DocumentView upload(@PathVariable UUID equipmentId,@RequestParam(defaultValue="OTHER") EquipmentDocumentType type,@RequestPart("file") MultipartFile file){return service.upload(equipmentId,type,file,current.require());}
    @GetMapping("/equipment/{equipmentId}/documents") public List<DocumentView> list(@PathVariable UUID equipmentId){return service.list(equipmentId,current.require());}
    @GetMapping("/equipment-documents/{id}") public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable UUID id){var d=service.download(id,current.require());return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.contentType())).contentLength(d.contentLength()).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(d.filename(),StandardCharsets.UTF_8).build().toString()).body(d.resource());}
    @GetMapping("/equipment-documents/{id}/preview-url") public EquipmentDocumentService.Preview preview(@PathVariable UUID id){return service.preview(id,current.require());}
    @DeleteMapping("/equipment-documents/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id){service.delete(id,current.require());}
    public record DocumentView(UUID id,EquipmentDocumentType type,String originalName,String contentType,long fileSize,String sha256,UUID uploadedById,String uploadedByName,String previewKind,boolean canDelete,Instant createdAt){}
}
