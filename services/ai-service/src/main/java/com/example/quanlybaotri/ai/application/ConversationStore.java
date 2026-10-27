package com.example.quanlybaotri.ai.application;

import com.example.quanlybaotri.ai.api.AiDtos.TicketDraft;
import com.example.quanlybaotri.ai.config.AiProperties;
import com.example.quanlybaotri.ai.shared.ApiException;
import java.time.Instant;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ConversationStore {
    private static final String PREFIX="ai:conversation:";
    private final StringRedisTemplate redis; private final ObjectMapper json; private final AiProperties properties;
    public ConversationStore(StringRedisTemplate redis,ObjectMapper json,AiProperties properties){this.redis=redis;this.json=json;this.properties=properties;}
    public State create(UUID userId){State state=new State(UUID.randomUUID(),userId,new ArrayList<>(),null,Instant.now());save(state);return state;}
    public State require(UUID userId,UUID id){
        try{
            String value=redis.opsForValue().get(key(userId,id));
            if(value==null) throw expired();
            State state=json.readValue(value,State.class); touch(state); return state;
        }catch(ApiException e){throw e;}catch(Exception e){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"AI_SESSION_UNAVAILABLE","Chưa thể truy cập phiên trợ lý");}
    }
    public void save(State state){
        trim(state.turns()); state.updatedAt=Instant.now();
        try{redis.opsForValue().set(key(state.userId(),state.id()),json.writeValueAsString(state),properties.getSessionTtl());}
        catch(Exception e){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"AI_SESSION_UNAVAILABLE","Chưa thể lưu phiên trợ lý");}
    }
    public void delete(UUID userId,UUID id){try{redis.delete(key(userId,id));}catch(RuntimeException e){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"AI_SESSION_UNAVAILABLE","Chưa thể xóa phiên trợ lý");}}
    public Instant expiresAt(State state){return state.updatedAt().plus(properties.getSessionTtl());}
    private void touch(State state){state.updatedAt=Instant.now();redis.expire(key(state.userId(),state.id()),properties.getSessionTtl());}
    private void trim(List<Turn> turns){int max=Math.max(2,properties.getMaxTurns()*2);if(turns.size()>max)turns.subList(0,turns.size()-max).clear();}
    private String key(UUID user,UUID conversation){return PREFIX+user+":"+conversation;}
    private ApiException expired(){return new ApiException(HttpStatus.GONE,"AI_CONVERSATION_EXPIRED","Phiên trợ lý đã hết hạn, vui lòng bắt đầu lại");}
    public record Turn(String role,String content) {}
    public static final class State {
        private UUID id; private UUID userId; private List<Turn> turns; private TicketDraft draft; private Instant updatedAt;
        public State(){}
        public State(UUID id,UUID userId,List<Turn> turns,TicketDraft draft,Instant updatedAt){this.id=id;this.userId=userId;this.turns=turns;this.draft=draft;this.updatedAt=updatedAt;}
        public UUID id(){return id;} public UUID userId(){return userId;} public List<Turn> turns(){return turns;}
        public TicketDraft draft(){return draft;} public void draft(TicketDraft value){draft=value;} public Instant updatedAt(){return updatedAt;}
        public UUID getId(){return id;} public void setId(UUID value){id=value;}
        public UUID getUserId(){return userId;} public void setUserId(UUID value){userId=value;}
        public List<Turn> getTurns(){return turns;} public void setTurns(List<Turn> value){turns=value;}
        public TicketDraft getDraft(){return draft;} public void setDraft(TicketDraft value){draft=value;}
        public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant value){updatedAt=value;}
    }
}
