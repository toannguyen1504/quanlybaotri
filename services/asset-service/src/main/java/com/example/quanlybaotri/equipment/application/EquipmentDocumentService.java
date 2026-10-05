package com.example.quanlybaotri.equipment.application;

import com.example.quanlybaotri.equipment.api.EquipmentDocumentController.DocumentView;
import com.example.quanlybaotri.equipment.domain.*;
import com.example.quanlybaotri.equipment.persistence.*;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.CurrentUser.Actor;
import com.example.quanlybaotri.shared.storage.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EquipmentDocumentService {
    private static final Logger log=LoggerFactory.getLogger(EquipmentDocumentService.class);
    private final EquipmentRepository equipment;private final EquipmentDocumentRepository documents;private final S3ObjectStorage storage;private final ObjectStorageProperties properties;
    public EquipmentDocumentService(EquipmentRepository equipment,EquipmentDocumentRepository documents,S3ObjectStorage storage,ObjectStorageProperties properties){this.equipment=equipment;this.documents=documents;this.storage=storage;this.properties=properties;}
    @Transactional
    public DocumentView upload(UUID equipmentId,EquipmentDocumentType requestedType,MultipartFile file,Actor actor){requireManager(actor);Equipment item=findEquipment(equipmentId);EquipmentDocumentType type=requestedType==null?EquipmentDocumentType.OTHER:requestedType;var valid=FilePolicy.validate(file);if(Set.of(EquipmentDocumentType.REPORT,EquipmentDocumentType.INVOICE,EquipmentDocumentType.DEVICE_DOCUMENT).contains(type)&&"VIDEO".equals(valid.previewKind()))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DOCUMENT_CONTENT","Loại tài liệu này không nhận video");String key="equipment/"+equipmentId+"/"+LocalDate.now(ZoneOffset.UTC).toString().substring(0,7).replace('-','/')+"/"+UUID.randomUUID();boolean stored=false;try{String hash;try{hash=storage.put(key,valid.contentType(),file.getSize(),file.getInputStream());}catch(IOException ex){throw unavailable();}stored=true;EquipmentDocument doc=documents.saveAndFlush(new EquipmentDocument(item,type,key,valid.name(),valid.contentType(),file.getSize(),hash,actor.id(),actor.fullName()));return view(doc,true);}catch(ObjectStorageException ex){throw unavailable();}catch(RuntimeException ex){if(stored)try{storage.delete(key);}catch(RuntimeException cleanupError){log.error("Compensating equipment document cleanup failed key={}",key,cleanupError);}throw ex;}}
    @Transactional(readOnly=true) public List<DocumentView> list(UUID equipmentId,Actor actor){requireInternal(actor);findEquipment(equipmentId);return documents.findByEquipmentIdAndDeletedAtIsNullOrderByCreatedAtDesc(equipmentId).stream().map(d->view(d,actor.has("ADMIN")||actor.has("MANAGER"))).toList();}
    @Transactional(readOnly=true) public Download download(UUID id,Actor actor){requireInternal(actor);EquipmentDocument doc=active(id);try{var object=storage.get(doc.getStorageKey());return new Download(new InputStreamResource(object.stream()),doc.getOriginalName(),doc.getContentType(),object.contentLength());}catch(ObjectStorageException ex){throw unavailable();}}
    @Transactional(readOnly=true) public Preview preview(UUID id,Actor actor){requireInternal(actor);EquipmentDocument doc=active(id);if("DOWNLOAD".equals(previewKind(doc.getContentType())))throw new ApiException(HttpStatus.BAD_REQUEST,"PREVIEW_NOT_SUPPORTED","Định dạng này chỉ hỗ trợ tải xuống");try{return new Preview(storage.presign(doc.getStorageKey(),doc.getContentType(),doc.getOriginalName()).toString(),Instant.now().plus(Duration.ofMinutes(properties.getPreviewMinutes())));}catch(ObjectStorageException ex){throw unavailable();}}
    @Transactional public void delete(UUID id,Actor actor){requireManager(actor);active(id).markDeleted(actor.id());}
    @Scheduled(fixedDelayString="${app.storage.cleanup-delay:60000}") @Transactional public void cleanupDeleted(){for(EquipmentDocument doc:documents.findTop100ByDeletedAtIsNotNullOrderByDeletedAtAsc()){try{storage.delete(doc.getStorageKey());documents.delete(doc);log.info("Equipment document tombstone cleanup completed documentId={} key={}",doc.getId(),doc.getStorageKey());}catch(ObjectStorageException ex){log.warn("Equipment document tombstone cleanup will retry documentId={} key={}",doc.getId(),doc.getStorageKey(),ex);}}}
    private Equipment findEquipment(UUID id){return equipment.findById(id).orElseThrow(()->ApiException.notFound("Không tìm thấy thiết bị"));} private EquipmentDocument active(UUID id){return documents.findByIdAndDeletedAtIsNull(id).orElseThrow(()->ApiException.notFound("Không tìm thấy tài liệu"));}
    private static void requireInternal(Actor actor){if(!actor.has("ADMIN")&&!actor.has("MANAGER")&&!actor.has("TECHNICIAN"))throw ApiException.forbidden("Không có quyền xem tài liệu thiết bị");}private static void requireManager(Actor actor){if(!actor.has("ADMIN")&&!actor.has("MANAGER"))throw ApiException.forbidden("Chỉ quản lý được thay đổi tài liệu thiết bị");}
    private static DocumentView view(EquipmentDocument d,boolean canDelete){return new DocumentView(d.getId(),d.getType(),d.getOriginalName(),d.getContentType(),d.getFileSize(),d.getSha256(),d.getUploadedById(),d.getUploadedByName(),previewKind(d.getContentType()),canDelete,d.getCreatedAt());} private static String previewKind(String type){if(type.startsWith("image/"))return "IMAGE";if(type.startsWith("video/"))return "VIDEO";return "application/pdf".equals(type)?"PDF":"DOWNLOAD";}private static ApiException unavailable(){return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"OBJECT_STORAGE_UNAVAILABLE","Kho lưu trữ tệp tạm thời không khả dụng");}
    public record Download(InputStreamResource resource,String filename,String contentType,long contentLength){} public record Preview(String url,Instant expiresAt){}
}
