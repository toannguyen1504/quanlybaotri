package com.example.quanlybaotri.ticket.application;

import com.example.quanlybaotri.cost.domain.CostStatus;
import com.example.quanlybaotri.cost.domain.TicketCostItem;
import com.example.quanlybaotri.cost.persistence.TicketCostItemRepository;
import com.example.quanlybaotri.cost.persistence.TicketCostSummaryRepository;
import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.integration.CommandSource;
import com.example.quanlybaotri.shared.security.CurrentUser.Actor;
import com.example.quanlybaotri.shared.storage.*;
import com.example.quanlybaotri.ticket.api.AttachmentController.AttachmentView;
import com.example.quanlybaotri.ticket.domain.*;
import com.example.quanlybaotri.ticket.persistence.*;
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
public class AttachmentService {
    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
    private static final Set<TicketStatus> TERMINAL = Set.of(TicketStatus.CLOSED, TicketStatus.REJECTED, TicketStatus.CANCELLED);
    private final TicketRepository tickets;
    private final WorkLogRepository workLogs;
    private final AttachmentRepository attachments;
    private final TicketEventService events;
    private final IdentityClient identities;
    private final TicketCostItemRepository costItems;
    private final TicketCostSummaryRepository costSummaries;
    private final S3ObjectStorage storage;
    private final ObjectStorageProperties storageProperties;

    public AttachmentService(TicketRepository tickets, WorkLogRepository workLogs, AttachmentRepository attachments,
        TicketEventService events, IdentityClient identities, TicketCostItemRepository costItems,
        TicketCostSummaryRepository costSummaries, S3ObjectStorage storage, ObjectStorageProperties storageProperties) {
        this.tickets=tickets; this.workLogs=workLogs; this.attachments=attachments; this.events=events;
        this.identities=identities; this.costItems=costItems; this.costSummaries=costSummaries;
        this.storage=storage; this.storageProperties=storageProperties;
    }

    @Transactional
    public AttachmentView upload(UUID ticketId, UUID workLogId, UUID costItemId, AttachmentType requestedType, MultipartFile file, Actor actor) {
        MaintenanceTicket ticket=find(ticketId); visible(ticket,actor);
        AttachmentType type=requestedType==null?AttachmentType.OTHER:requestedType;
        authorizeUpload(ticket,type,actor);
        var validated=FilePolicy.validate(file); validateContentForType(type,validated.previewKind());
        WorkLog workLog=null;
        if(workLogId!=null){workLog=workLogs.findById(workLogId).orElseThrow(()->ApiException.notFound("Không tìm thấy nhật ký xử lý"));if(!workLog.getTicket().getId().equals(ticketId))throw ApiException.conflict("Nhật ký không thuộc phiếu");}
        TicketCostItem costItem=null;
        if(costItemId!=null){
            if(type!=AttachmentType.INVOICE)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ATTACHMENT_TYPE","Chứng từ chi phí phải có loại INVOICE");
            if(!actor.has(RoleName.TECHNICIAN)||!actor.id().equals(ticket.getAssigneeId()))throw ApiException.forbidden("Chỉ kỹ thuật viên phụ trách được thêm chứng từ chi phí");
            if(costSummaries.findById(ticketId).map(s->s.getStatus()!=CostStatus.DRAFT).orElse(false))throw ApiException.conflict("Bảng chi phí không còn ở trạng thái bản nháp");
            costItem=costItems.findById(costItemId).orElseThrow(()->ApiException.notFound("Không tìm thấy khoản chi phí"));
            if(!costItem.getTicketId().equals(ticketId))throw ApiException.conflict("Khoản chi phí không thuộc phiếu");
        }
        String key="tickets/"+ticketId+"/"+LocalDate.now(ZoneOffset.UTC).toString().substring(0,7).replace('-','/')+"/"+UUID.randomUUID();
        boolean stored=false;
        try{
            String hash;try{hash=storage.put(key,validated.contentType(),file.getSize(),file.getInputStream());}catch(IOException ex){throw storageUnavailable();}
            stored=true;
            Attachment attachment=attachments.saveAndFlush(new Attachment(ticket,workLog,costItem,actor.id(),type,key,validated.name(),validated.contentType(),file.getSize(),hash));
            events.record(ticket,actor.id(),"ATTACHMENT_ADDED",ticket.getStatus(),ticket.getStatus(),"Đã thêm tệp "+attachment.getOriginalName(),CommandSource.metadata(Map.of("attachmentId",attachment.getId(),"attachmentType",type.name())));
            return view(attachment,identities.get(actor.id()),actor);
        }catch(ObjectStorageException ex){throw storageUnavailable();}
        catch(RuntimeException ex){
            if(stored)try{storage.delete(key);}catch(RuntimeException cleanupError){log.error("Compensating attachment cleanup failed key={}",key,cleanupError);}
            throw ex;
        }
    }

    @Transactional(readOnly=true)
    public List<AttachmentView> list(UUID ticketId,Actor actor){MaintenanceTicket ticket=find(ticketId);visible(ticket,actor);List<Attachment> result=attachments.findByTicketIdAndDeletedAtIsNullOrderByCreatedAtDesc(ticketId);if(!canSeeCostDetails(ticket,actor))result=result.stream().filter(a->!isCostAttachment(a)).toList();var users=identities.resolve(result.stream().map(Attachment::getUploadedById).toList());return result.stream().map(a->view(a,users.get(a.getUploadedById()),actor)).toList();}

    @Transactional(readOnly=true)
    public Download download(UUID id,Actor actor){Attachment attachment=active(id);authorizeRead(attachment,actor);try{var object=storage.get(attachment.getStorageKey());return new Download(new InputStreamResource(object.stream()),attachment.getOriginalName(),attachment.getContentType(),object.contentLength());}catch(ObjectStorageException ex){throw storageUnavailable();}}

    @Transactional(readOnly=true)
    public Preview preview(UUID id,Actor actor){Attachment attachment=active(id);authorizeRead(attachment,actor);if("DOWNLOAD".equals(previewKind(attachment.getContentType())))throw new ApiException(HttpStatus.BAD_REQUEST,"PREVIEW_NOT_SUPPORTED","Định dạng này chỉ hỗ trợ tải xuống");try{return new Preview(storage.presign(attachment.getStorageKey(),attachment.getContentType(),attachment.getOriginalName()).toString(),Instant.now().plus(Duration.ofMinutes(storageProperties.getPreviewMinutes())));}catch(ObjectStorageException ex){throw storageUnavailable();}}

    @Transactional
    public void delete(UUID id,Actor actor){Attachment attachment=active(id);authorizeRead(attachment,actor);MaintenanceTicket ticket=attachment.getTicket();boolean elevated=actor.has(RoleName.ADMIN)||actor.has(RoleName.MANAGER);if(!elevated&&(!actor.id().equals(attachment.getUploadedById())||TERMINAL.contains(ticket.getStatus())))throw ApiException.forbidden("Không có quyền xóa tệp này");if(!elevated&&attachment.getCostItemId()!=null&&costSummaries.findById(ticket.getId()).map(s->s.getStatus()!=CostStatus.DRAFT).orElse(false))throw ApiException.conflict("Không thể xóa chứng từ sau khi báo giá đã được gửi cho khách hàng");attachment.markDeleted(actor.id());events.record(ticket,actor.id(),"ATTACHMENT_DELETED",ticket.getStatus(),ticket.getStatus(),"Đã xóa tệp "+attachment.getOriginalName(),CommandSource.metadata(Map.of("attachmentId",attachment.getId(),"attachmentType",attachment.getType().name())));}

    @Scheduled(fixedDelayString="${app.storage.cleanup-delay:60000}")
    @Transactional
    public void cleanupDeletedObjects(){
        for(Attachment attachment:attachments.findTop100ByDeletedAtIsNotNullOrderByDeletedAtAsc()){
            try{
                storage.delete(attachment.getStorageKey());
                attachments.delete(attachment);
                log.info("Attachment tombstone cleanup completed attachmentId={} key={}",attachment.getId(),attachment.getStorageKey());
            }catch(ObjectStorageException ex){
                log.warn("Attachment tombstone cleanup will retry attachmentId={} key={}",attachment.getId(),attachment.getStorageKey(),ex);
            }
        }
    }

    private AttachmentView view(Attachment a,IdentityClient.UserRef uploader,Actor actor){return new AttachmentView(a.getId(),a.getOriginalName(),a.getContentType(),a.getFileSize(),a.getSha256(),a.getUploadedById(),uploader==null?null:uploader.fullName(),a.getType(),previewKind(a.getContentType()),canDelete(a,actor),a.getWorkLogId(),a.getCostItemId(),a.getCreatedAt());}
    private boolean canDelete(Attachment a,Actor actor){
        if(actor.has(RoleName.ADMIN)||actor.has(RoleName.MANAGER))return true;
        if(!actor.id().equals(a.getUploadedById())||TERMINAL.contains(a.getTicket().getStatus()))return false;
        return a.getCostItemId()==null||costSummaries.findById(a.getTicket().getId()).map(s->s.getStatus()==CostStatus.DRAFT).orElse(true);
    }
    private Attachment active(UUID id){return attachments.findByIdAndDeletedAtIsNull(id).orElseThrow(()->ApiException.notFound("Không tìm thấy tệp"));}
    private MaintenanceTicket find(UUID id){return tickets.findById(id).orElseThrow(()->ApiException.notFound("Không tìm thấy phiếu"));}
    private void authorizeRead(Attachment a,Actor actor){visible(a.getTicket(),actor);if(isCostAttachment(a)&&!canSeeCostDetails(a.getTicket(),actor))throw ApiException.forbidden("Không có quyền xem chứng từ chi phí");}
    private void visible(MaintenanceTicket t,Actor actor){boolean elevated=actor.has(RoleName.ADMIN)||actor.has(RoleName.MANAGER);if(!elevated&&!(actor.customer()&&actor.canAccess(t.getCustomerId(),t.getSiteId()))&&!actor.id().equals(t.getAssigneeId()))throw ApiException.forbidden("Không có quyền truy cập tệp");}
    private boolean canSeeCostDetails(MaintenanceTicket t,Actor actor){return actor.has(RoleName.ADMIN)||actor.has(RoleName.MANAGER)||actor.id().equals(t.getAssigneeId());}
    private boolean isCostAttachment(Attachment attachment){return attachment.getCostItemId()!=null||attachment.getType()==AttachmentType.INVOICE;}
    private void authorizeUpload(MaintenanceTicket t,AttachmentType type,Actor actor){if(actor.has(RoleName.ADMIN)||actor.has(RoleName.MANAGER))return;if(TERMINAL.contains(t.getStatus()))throw ApiException.conflict("Phiếu đã kết thúc, không thể thêm tệp");if(actor.id().equals(t.getRequesterId())&&type==AttachmentType.INCIDENT_EVIDENCE)return;if(actor.has(RoleName.TECHNICIAN)&&actor.id().equals(t.getAssigneeId()))return;throw ApiException.forbidden("Không có quyền thêm loại tệp này");}
    private static void validateContentForType(AttachmentType type,String kind){if(Set.of(AttachmentType.INCIDENT_EVIDENCE,AttachmentType.BEFORE_REPAIR,AttachmentType.AFTER_REPAIR).contains(type)&&!Set.of("IMAGE","VIDEO").contains(kind))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ATTACHMENT_CONTENT","Minh chứng sự cố và ảnh trước/sau chỉ nhận ảnh hoặc video");if(Set.of(AttachmentType.REPORT,AttachmentType.INVOICE).contains(type)&&"VIDEO".equals(kind))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ATTACHMENT_CONTENT","Biên bản và hóa đơn không nhận video");}
    private static String previewKind(String type){if(type.startsWith("image/"))return "IMAGE";if(type.startsWith("video/"))return "VIDEO";return "application/pdf".equals(type)?"PDF":"DOWNLOAD";}
    private static ApiException storageUnavailable(){return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"OBJECT_STORAGE_UNAVAILABLE","Kho lưu trữ tệp tạm thời không khả dụng");}
    public record Download(InputStreamResource resource,String filename,String contentType,long contentLength){}
    public record Preview(String url,Instant expiresAt){}
}
