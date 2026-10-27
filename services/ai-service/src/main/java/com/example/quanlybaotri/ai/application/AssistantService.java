package com.example.quanlybaotri.ai.application;

import static com.example.quanlybaotri.ai.api.AiDtos.*;

import com.example.quanlybaotri.ai.application.ConversationStore.*;
import com.example.quanlybaotri.ai.application.NvidiaAiClient.*;
import com.example.quanlybaotri.ai.config.AiProperties;
import com.example.quanlybaotri.ai.integration.BusinessClient;
import com.example.quanlybaotri.ai.integration.BusinessClient.*;
import com.example.quanlybaotri.ai.security.CurrentActor.Actor;
import com.example.quanlybaotri.ai.shared.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AssistantService {
    private final ConversationStore conversations; private final AiRateLimiter limiter; private final NvidiaAiClient ai;
    private final KnowledgeIndex knowledge; private final BusinessClient business; private final AiProperties properties; private final MeterRegistry meters;
    public AssistantService(ConversationStore conversations,AiRateLimiter limiter,NvidiaAiClient ai,KnowledgeIndex knowledge,
        BusinessClient business,AiProperties properties,MeterRegistry meters){this.conversations=conversations;this.limiter=limiter;this.ai=ai;this.knowledge=knowledge;this.business=business;this.properties=properties;this.meters=meters;}
    public ConversationResponse create(Actor actor){State state=conversations.create(actor.id());return new ConversationResponse(state.id(),conversations.expiresAt(state));}
    public AssistantResponse message(UUID conversationId,String raw,Actor actor){
        limiter.check(actor.id());String message=raw.trim();if(message.length()>properties.getMaxInputLength())throw new ApiException(HttpStatus.BAD_REQUEST,"MESSAGE_TOO_LONG","Tin nhắn vượt quá giới hạn cho phép");
        State state=conversations.require(actor.id(),conversationId);
        IntentResult intent=ai.classify(message,state.turns(),draftContext(state.draft()));
        meters.counter("ai.messages","intent",intent.intent().name()).increment();
        state.turns().add(new Turn("user",message));
        AssistantResponse response=switch(intent.intent()){
            case PROCESS_ADVICE -> advice(state,message,intent);
            case SEARCH_TICKETS,GET_TICKET -> tickets(state,intent,actor);
            case SEARCH_EQUIPMENT,GET_EQUIPMENT -> equipment(state,intent,actor);
            case CREATE_TICKET -> draft(state,intent,actor);
            case OUT_OF_SCOPE -> text(state,ai.generalChat(message,state.turns()),intent);
        };
        state.turns().add(new Turn("assistant",response.reply()));conversations.save(state);
        return withExpiry(response,state);
    }
    public ConfirmResponse confirm(UUID conversationId,UUID draftId,Actor actor){
        State state=conversations.require(actor.id(),conversationId);TicketDraft draft=state.draft();
        if(draft==null||!draft.id().equals(draftId))throw new ApiException(HttpStatus.GONE,"AI_DRAFT_EXPIRED","Bản nháp không còn tồn tại");
        if(draft.status()!=DraftStatus.READY&&draft.status()!=DraftStatus.CREATED)throw new ApiException(HttpStatus.CONFLICT,"AI_DRAFT_INCOMPLETE","Bản nháp chưa đủ thông tin để tạo phiếu");
        if(actor.has("TECHNICIAN")&&!actor.elevated())throw new ApiException(HttpStatus.FORBIDDEN,"TICKET_CREATE_FORBIDDEN","Kỹ thuật viên không có quyền tạo phiếu");
        TicketRef created=business.create(draft,actor);TicketCard card=business.card(created);
        state.draft(new TicketDraft(draft.id(),DraftStatus.CREATED,draft.equipmentId(),draft.equipmentCode(),draft.equipmentName(),draft.requesterId(),draft.requesterName(),draft.title(),draft.description(),draft.priority(),List.of(),List.of()));
        state.turns().add(new Turn("assistant","Đã tạo phiếu "+card.code()+"."));conversations.save(state);meters.counter("ai.ticket.confirmed").increment();
        return new ConfirmResponse(card,"Đã tạo phiếu "+card.code()+" thành công.");
    }
    public void delete(UUID conversationId,Actor actor){conversations.delete(actor.id(),conversationId);}
    private AssistantResponse advice(State state,String message,IntentResult intent){List<KnowledgeIndex.Snippet> snippets=knowledge.search(message);String reply=ai.answer(message,snippets);
        return response(state,ResponseType.TEXT,reply,intent,List.of(),List.of(),state.draft(),knowledge.sources(snippets));}
    private AssistantResponse tickets(State state,IntentResult intent,Actor actor){
        List<TicketRef> found=new ArrayList<>();UUID id=uuid(intent.entityId());
        if(intent.intent()==Intent.GET_TICKET&&id!=null)found.add(business.ticket(id,actor));else found.addAll(business.searchTickets(intent.query(),intent.status(),intent.view(),intent.priority(),properties.getMaxResults(),actor));
        List<TicketCard> cards=found.stream().map(business::card).toList();String reply=cards.isEmpty()?"Không tìm thấy phiếu phù hợp trong phạm vi bạn được phép xem.":
            "Tìm thấy "+cards.size()+" phiếu: "+String.join(", ",cards.stream().map(TicketCard::code).toList())+".";
        return response(state,ResponseType.TICKET_RESULTS,reply,intent,cards,List.of(),state.draft(),List.of());
    }
    private AssistantResponse equipment(State state,IntentResult intent,Actor actor){
        List<EquipmentRef> found=new ArrayList<>();UUID id=uuid(intent.entityId());
        if(intent.intent()==Intent.GET_EQUIPMENT&&id!=null)found.add(business.equipment(id,actor));else found.addAll(business.searchEquipment(intent.query(),properties.getMaxResults(),actor));
        List<EquipmentCard> cards=found.stream().map(business::card).toList();String reply=cards.isEmpty()?"Không tìm thấy thiết bị phù hợp trong phạm vi bạn được phép xem.":
            "Tìm thấy "+cards.size()+" thiết bị: "+String.join(", ",cards.stream().map(EquipmentCard::assetCode).toList())+".";
        return response(state,ResponseType.EQUIPMENT_RESULTS,reply,intent,List.of(),cards,state.draft(),List.of());
    }
    private AssistantResponse draft(State state,IntentResult intent,Actor actor){
        if(actor.has("TECHNICIAN")&&!actor.elevated())throw new ApiException(HttpStatus.FORBIDDEN,"TICKET_CREATE_FORBIDDEN","Kỹ thuật viên không có quyền tạo phiếu");
        TicketDraft old=state.draft();UUID draftId=old==null||old.status()==DraftStatus.CREATED?UUID.randomUUID():old.id();
        UUID equipmentId=firstUuid(intent.equipmentId(),old==null?null:old.equipmentId());EquipmentRef selected=null;List<EquipmentRef> candidates=List.of();
        if(equipmentId!=null)selected=business.equipment(equipmentId,actor);
        else if(text(intent.equipmentQuery())){
            candidates=business.searchEquipment(intent.equipmentQuery(),properties.getMaxResults(),actor);
            selected=exactEquipment(candidates,intent.equipmentQuery());if(selected==null&&candidates.size()==1)selected=candidates.get(0);
        }else if(old!=null&&old.equipmentId()!=null)selected=business.equipment(old.equipmentId(),actor);
        if(selected!=null&&!selected.active())selected=null;
        UUID requesterId=firstUuid(intent.requesterId(),old==null?null:old.requesterId());String requesterName=old==null?null:old.requesterName();List<RequesterOption> options=List.of();
        if(actor.customer()){requesterId=actor.id();requesterName=actor.name();}
        else if(selected!=null){
            UUID selectedSiteId=selected.siteId();UUID requestedRequesterId=requesterId;
            List<UserRef> allowed=business.requesters(selected.customerId(),100,actor).stream().filter(u->u.canAccess(selectedSiteId)).toList();
            UserRef requester=allowed.stream().filter(u->u.id().equals(requestedRequesterId)).findFirst().orElse(null);
            if(requester==null&&text(intent.requesterQuery()))requester=matchRequester(allowed,intent.requesterQuery());
            if(requester!=null){requesterId=requester.id();requesterName=requester.fullName();}else{requesterId=null;requesterName=null;}
            options=allowed.stream().limit(20).map(u->new RequesterOption(u.id(),u.fullName(),u.email())).toList();
        }
        String title=prefer(intent.title(),old==null?null:old.title());String description=prefer(intent.description(),old==null?null:old.description());String priority=prefer(intent.priority(),old==null?null:old.priority());
        List<String> missing=new ArrayList<>();if(selected==null)missing.add("Thiết bị");if(!text(title)||title.length()>200)missing.add("Tiêu đề hợp lệ (tối đa 200 ký tự)");
        if(!text(description)||description.length()>10000)missing.add("Mô tả sự cố");if(!Set.of("CRITICAL","HIGH","MEDIUM","LOW").contains(priority))missing.add("Mức ưu tiên");if(requesterId==null)missing.add("Người yêu cầu");
        TicketDraft value=new TicketDraft(draftId,missing.isEmpty()?DraftStatus.READY:DraftStatus.INCOMPLETE,selected==null?null:selected.id(),selected==null?null:selected.assetCode(),
            selected==null?null:selected.name(),requesterId,requesterName,title,description,priority,missing,options);state.draft(value);
        List<EquipmentCard> cards=selected==null?candidates.stream().map(business::card).toList():List.of();String reply=missing.isEmpty()?"Bản nháp đã đủ thông tin. Vui lòng kiểm tra kỹ trước khi xác nhận tạo phiếu.":
            "Bản nháp còn thiếu: "+String.join(", ",missing)+". Bạn có thể bổ sung ngay trong tin nhắn tiếp theo.";
        return response(state,ResponseType.TICKET_DRAFT,reply,intent,List.of(),cards,value,List.of());
    }
    private AssistantResponse text(State state,String reply,IntentResult intent){return response(state,ResponseType.TEXT,reply,intent,List.of(),List.of(),state.draft(),List.of());}
    private AssistantResponse response(State state,ResponseType type,String reply,IntentResult intent,List<TicketCard> tickets,List<EquipmentCard> equipment,TicketDraft draft,List<SourceRef> sources){
        return new AssistantResponse(state.id(),type,reply,intent.intent().name(),tickets,equipment,draft,sources,Instant.now());}
    private AssistantResponse withExpiry(AssistantResponse r,State state){return new AssistantResponse(r.conversationId(),r.type(),r.reply(),r.intent(),r.tickets(),r.equipment(),r.draft(),r.sources(),conversations.expiresAt(state));}
    private String draftContext(TicketDraft d){
        if(d==null)return "không có";List<String> fields=new ArrayList<>();
        if(text(d.equipmentCode()))fields.add("equipment="+d.equipmentCode());if(text(d.requesterName()))fields.add("requester="+d.requesterName());
        if(text(d.title()))fields.add("title="+d.title());if(text(d.description()))fields.add("description=đã có");if(text(d.priority()))fields.add("priority="+d.priority());
        return fields.isEmpty()?"trống":String.join("; ",fields);
    }
    private EquipmentRef exactEquipment(List<EquipmentRef> values,String query){return values.stream().filter(e->e.assetCode()!=null&&e.assetCode().equalsIgnoreCase(query.trim())).findFirst().orElse(null);}
    private UserRef matchRequester(List<UserRef> users,String query){String q=query.toLowerCase(Locale.ROOT);List<UserRef> found=users.stream().filter(u->(u.fullName()!=null&&u.fullName().toLowerCase(Locale.ROOT).contains(q))||(u.email()!=null&&u.email().toLowerCase(Locale.ROOT).contains(q))).toList();return found.size()==1?found.get(0):null;}
    private UUID firstUuid(String value,UUID fallback){UUID parsed=uuid(value);return parsed==null?fallback:parsed;}
    private UUID uuid(String value){try{return text(value)?UUID.fromString(value):null;}catch(IllegalArgumentException e){return null;}}
    private String prefer(String value,String fallback){return text(value)?value.trim():fallback;}
    private boolean text(String value){return value!=null&&!value.isBlank();}
}
