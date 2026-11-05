package com.example.quanlybaotri.ai.application;

import static org.assertj.core.api.Assertions.*;

import com.example.quanlybaotri.ai.application.ConversationStore.Turn;
import com.example.quanlybaotri.ai.config.AiProperties;
import com.example.quanlybaotri.ai.shared.ApiException;
import com.sun.net.httpserver.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class NvidiaAiClientTest {
    @Test void classifyUsesStructuredLowTokenRequestAndRecordsUsage() throws Exception {
        String intent="""
            {"intent":"SEARCH_TICKETS","query":"máy bơm","entityId":"","status":"","view":"OPEN","equipmentId":"","equipmentQuery":"","requesterId":"","requesterQuery":"","title":"","description":"","priority":""}
            """.trim();
        String response="""
            {"choices":[{"message":{"role":"assistant","content":%s}}],"usage":{"prompt_tokens":120,"completion_tokens":44,"total_tokens":164,"prompt_tokens_details":{"cached_tokens":0}}}
            """.formatted(new ObjectMapper().writeValueAsString(intent));
        try(TestEndpoint endpoint=new TestEndpoint(200,response,0)){
            SimpleMeterRegistry meters=new SimpleMeterRegistry();NvidiaAiClient client=client(endpoint,meters,Duration.ofSeconds(1));
            List<Turn> history=List.of(new Turn("user","oldest-marker"),new Turn("assistant","a1"),new Turn("user","u2"),new Turn("assistant","a2"),new Turn("user","u3"));

            NvidiaAiClient.IntentResult result=client.classify("phiếu đang mở",history,"equipment=TB-01");

            assertThat(result.intent()).isEqualTo(NvidiaAiClient.Intent.SEARCH_TICKETS);assertThat(result.view()).isEqualTo("OPEN");
            assertThat(endpoint.authorization.get()).isEqualTo("Bearer test-key");
            assertThat(endpoint.requestBody.get()).contains("nvidia/nemotron-3.5-super-vl","\"reasoning\":{\"enabled\":false}","\"max_tokens\":256","\"response_format\"","a1","u3")
                .doesNotContain("oldest-marker");
            assertThat(meters.find("ai.openrouter.tokens").tag("type","prompt").counter().count()).isEqualTo(120);
            assertThat(meters.find("ai.openrouter.tokens").tag("type","completion").counter().count()).isEqualTo(44);
            assertThat(meters.find("ai.openrouter.tokens").tag("type","total").counter().count()).isEqualTo(164);
        }
    }

    @Test void answerUsesSmallBudgetAndReturnsContent() throws Exception {
        String response="""
            {"choices":[{"message":{"role":"assistant","content":"Thực hiện theo [ticket-workflow-0]."}}],"usage":{"prompt_tokens":30,"completion_tokens":9,"total_tokens":39}}
            """;
        try(TestEndpoint endpoint=new TestEndpoint(200,response,0)){
            NvidiaAiClient client=client(endpoint,new SimpleMeterRegistry(),Duration.ofSeconds(1));
            var snippet=new KnowledgeIndex.Snippet("ticket-workflow-0","Quy trình","Tạo phiếu","Nội dung kiểm duyệt",Set.of());

            assertThat(client.answer("Tạo phiếu thế nào?",List.of(snippet))).isEqualTo("Thực hiện theo [ticket-workflow-0].");
            assertThat(endpoint.requestBody.get()).contains("\"max_tokens\":400","Nội dung kiểm duyệt").doesNotContain("response_format");
        }
    }

    @Test void generalChatUsesRecentHistoryAndLargerBudget() throws Exception {
        String response="""
            {"choices":[{"message":{"role":"assistant","content":"Đây là câu trả lời tổng quát."}}],"usage":{"prompt_tokens":50,"completion_tokens":12,"total_tokens":62}}
            """;
        try(TestEndpoint endpoint=new TestEndpoint(200,response,0)){
            NvidiaAiClient client=client(endpoint,new SimpleMeterRegistry(),Duration.ofSeconds(1));
            List<Turn> history=new ArrayList<>();history.add(new Turn("user","oldest-marker"));
            for(int i=1;i<=8;i++)history.add(new Turn(i%2==0?"assistant":"user","recent-"+i));

            assertThat(client.generalChat("Giải thích điện toán đám mây",history)).isEqualTo("Đây là câu trả lời tổng quát.");
            assertThat(endpoint.requestBody.get()).contains("\"max_tokens\":800","Giải thích điện toán đám mây","recent-1","recent-8")
                .doesNotContain("oldest-marker","response_format");
        }
    }

    @Test void providerHttpFailuresAreUnavailable() throws Exception {
        for(int status:List.of(401,429,500)){
            try(TestEndpoint endpoint=new TestEndpoint(status,"{\"error\":\"not exposed\"}",0)){
                NvidiaAiClient client=client(endpoint,new SimpleMeterRegistry(),Duration.ofSeconds(1));
                assertUnavailable(()->client.answer("question",List.of(new KnowledgeIndex.Snippet("s","t","x","source",Set.of()))));
            }
        }
    }

    @Test void timeoutAndEmptyResponsesAreUnavailable() throws Exception {
        try(TestEndpoint endpoint=new TestEndpoint(200,"{\"choices\":[]}",0)){
            assertUnavailable(()->client(endpoint,new SimpleMeterRegistry(),Duration.ofSeconds(1)).answer("question",List.of(new KnowledgeIndex.Snippet("s","t","x","source",Set.of()))));
        }
        try(TestEndpoint endpoint=new TestEndpoint(200,"{\"choices\":[]}",200)){
            assertUnavailable(()->client(endpoint,new SimpleMeterRegistry(),Duration.ofMillis(20)).answer("question",List.of(new KnowledgeIndex.Snippet("s","t","x","source",Set.of()))));
        }
    }

    private NvidiaAiClient client(TestEndpoint endpoint,SimpleMeterRegistry meters,Duration readTimeout){
        AiProperties properties=new AiProperties();properties.setApiKey("test-key");properties.setApiBaseUrl(endpoint.baseUrl());properties.setReadTimeout(readTimeout);
        return new NvidiaAiClient(new ObjectMapper(),properties,meters,RestClient.builder());
    }
    private void assertUnavailable(ThrowingCallable call){
        assertThatThrownBy(()->call.call()).isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.code()).isEqualTo("OPENROUTER_AI_UNAVAILABLE");assertThat(e.status().value()).isEqualTo(503);});
    }
    @FunctionalInterface private interface ThrowingCallable {void call() throws Exception;}

    private static final class TestEndpoint implements AutoCloseable {
        private final HttpServer server;private final int status;private final String response;private final long delayMillis;
        private final AtomicReference<String> requestBody=new AtomicReference<>();private final AtomicReference<String> authorization=new AtomicReference<>();
        TestEndpoint(int status,String response,long delayMillis) throws IOException {
            this.status=status;this.response=response;this.delayMillis=delayMillis;
            server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);server.createContext("/v1/chat/completions",this::handle);server.start();
        }
        String baseUrl(){return "http://127.0.0.1:"+server.getAddress().getPort()+"/v1";}
        private void handle(HttpExchange exchange) throws IOException {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));requestBody.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            if(delayMillis>0)try{Thread.sleep(delayMillis);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");
            try{exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);}finally{exchange.close();}
        }
        @Override public void close(){server.stop(0);}
    }
}
