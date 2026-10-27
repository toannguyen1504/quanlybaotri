package com.example.quanlybaotri.ai.api;

import com.example.quanlybaotri.ai.api.AiDtos.*;
import com.example.quanlybaotri.ai.application.AssistantService;
import com.example.quanlybaotri.ai.security.CurrentActor;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/conversations")
public class AiController {
    private final AssistantService service; private final CurrentActor current;
    public AiController(AssistantService service,CurrentActor current){this.service=service;this.current=current;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ConversationResponse create(){return service.create(current.require());}
    @PostMapping("/{id}/messages") public AssistantResponse message(@PathVariable UUID id,@Valid @RequestBody MessageRequest request){return service.message(id,request.message(),current.require());}
    @PostMapping("/{id}/drafts/{draftId}/confirm") public ConfirmResponse confirm(@PathVariable UUID id,@PathVariable UUID draftId){return service.confirm(id,draftId,current.require());}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id){service.delete(id,current.require());}
}
