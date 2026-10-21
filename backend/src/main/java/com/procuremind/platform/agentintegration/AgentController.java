package com.procuremind.platform.agentintegration;

import com.procuremind.platform.common.ApiResponse;
import com.procuremind.platform.common.BusinessException;
import com.procuremind.platform.system.CurrentUser;
import com.procuremind.platform.system.SecurityUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController @RequestMapping("/api/v1/ai")
public class AgentController {
    private final WebClient client;private final boolean enabled;private final String token;
    public AgentController(WebClient.Builder builder,@Value("${procuremind.agent.base-url}")String url,@Value("${procuremind.agent.enabled}")boolean enabled,@Value("${procuremind.agent.internal-token}")String token){this.client=builder.baseUrl(url).build();this.enabled=enabled;this.token=token;}
    @PostMapping(value="/chat/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody Map<String,Object> input){check();Map<String,Object> body=identity(input);return client.post().uri("/api/chat/stream").header("X-Internal-Token",token).header("X-Request-Id",MDC.get("requestId")).contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM).bodyValue(body).retrieve().bodyToFlux(String.class).timeout(Duration.ofMinutes(10)).onErrorMap(e->new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"AI助手暂时不可用"));}
    @PostMapping(value="/chat/{threadId}/resume",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> resume(@PathVariable String threadId,@RequestBody Map<String,Object> input){check();return client.post().uri("/api/chat/{id}/resume",threadId).header("X-Internal-Token",token).header("X-Request-Id",MDC.get("requestId")).contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM).bodyValue(identity(input)).retrieve().bodyToFlux(String.class);}
    @GetMapping("/history") public ApiResponse<Object> history(){check();CurrentUser u=SecurityUtils.currentUser();Object data=client.get().uri(uri->uri.path("/api/history").queryParam("user_id",u.id()).build()).header("X-Internal-Token",token).retrieve().bodyToMono(Object.class).block(Duration.ofSeconds(10));return ApiResponse.ok(data);}
    @GetMapping("/status") public ApiResponse<Map<String,Object>> status(){return ApiResponse.ok(Map.of("enabled",enabled));}
    private Map<String,Object> identity(Map<String,Object> input){CurrentUser u=SecurityUtils.currentUser();Map<String,Object>b=new HashMap<>(input);b.put("user_id",u.id().toString());b.put("username",u.username());b.put("request_id",MDC.get("requestId"));return b;}
    private void check(){if(!enabled)throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"AI助手未启用，业务系统仍可正常使用");}
}
