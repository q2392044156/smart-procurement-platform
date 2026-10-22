package com.procuremind.platform.agentintegration;

import com.procuremind.platform.common.ApiResponse;
import com.procuremind.platform.common.BusinessException;
import com.procuremind.platform.system.CurrentUser;
import com.procuremind.platform.system.SecurityUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Duration;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController @RequestMapping("/api/v1/ai")
public class AgentController {
    private final WebClient client;private final boolean enabled;private final String token;
    public AgentController(WebClient.Builder builder,@Value("${procuremind.agent.base-url}")String url,@Value("${procuremind.agent.enabled}")boolean enabled,@Value("${procuremind.agent.internal-token}")String token){this.client=builder.baseUrl(url).build();this.enabled=enabled;this.token=token;}
    @PostMapping(value="/chat/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody stream(@RequestBody Map<String,Object> input){check();Map<String,Object> body=identity(input);String requestId=MDC.get("requestId");return output->relay("/api/chat/stream",body,requestId,output);}
    @PostMapping(value="/chat/{threadId}/resume",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody resume(@PathVariable String threadId,@RequestBody Map<String,Object> input){check();Map<String,Object> body=identity(input);String requestId=MDC.get("requestId");return output->relay("/api/chat/"+threadId+"/resume",body,requestId,output);}
    @GetMapping("/history") public ApiResponse<Object> history(){check();CurrentUser u=SecurityUtils.currentUser();Object data=client.get().uri(uri->uri.path("/api/history").queryParam("user_id",u.id()).build()).header("X-Internal-Token",token).retrieve().bodyToMono(Object.class).block(Duration.ofSeconds(10));return ApiResponse.ok(data);}
    @GetMapping("/history/{threadId}") public ApiResponse<Object> historyMessages(@PathVariable String threadId){check();CurrentUser u=SecurityUtils.currentUser();Map<?,?> index=client.get().uri(uri->uri.path("/api/history").queryParam("user_id",u.id()).build()).header("X-Internal-Token",token).retrieve().bodyToMono(Map.class).block(Duration.ofSeconds(10));boolean owned=index!=null&&index.get("conversations") instanceof List<?> list&&list.stream().filter(Map.class::isInstance).map(Map.class::cast).anyMatch(x->threadId.equals(String.valueOf(x.get("thread_id"))));if(!owned)throw new BusinessException(HttpStatus.NOT_FOUND,"会话不存在");Object data=client.get().uri("/api/history/{id}/messages",threadId).header("X-Internal-Token",token).retrieve().bodyToMono(Object.class).block(Duration.ofSeconds(10));return ApiResponse.ok(data);}
    @GetMapping("/status") public ApiResponse<Map<String,Object>> status(){return ApiResponse.ok(Map.of("enabled",enabled));}
    private Map<String,Object> identity(Map<String,Object> input){CurrentUser u=SecurityUtils.currentUser();Map<String,Object>b=new HashMap<>(input);b.put("user_id",u.id().toString());b.put("username",u.username());b.put("request_id",MDC.get("requestId"));return b;}
    private void relay(String uri,Map<String,Object> body,String requestId,java.io.OutputStream output){
        try{
            client.post().uri(uri).header("X-Internal-Token",token).header("X-Request-Id",requestId).contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM).bodyValue(body)
                    .exchangeToFlux(response->response.statusCode().is2xxSuccessful()?response.bodyToFlux(DataBuffer.class):response.createException().flatMapMany(reactor.core.publisher.Flux::error))
                    .timeout(Duration.ofMinutes(10)).doOnNext(buffer->{try{byte[] bytes=new byte[buffer.readableByteCount()];buffer.read(bytes);output.write(bytes);output.flush();}catch(java.io.IOException e){throw new RuntimeException(e);}finally{DataBufferUtils.release(buffer);}}).blockLast();
        }catch(Exception e){throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"AI助手暂时不可用");}
    }
    private void check(){if(!enabled)throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"AI助手未启用，业务系统仍可正常使用");}
}
