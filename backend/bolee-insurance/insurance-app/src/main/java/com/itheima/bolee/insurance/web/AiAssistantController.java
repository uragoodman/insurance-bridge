package com.itheima.bolee.insurance.web;

import com.itheima.bolee.framework.commons.basic.ResponseResult;
import com.itheima.bolee.framework.commons.utils.ResponseResultBuild;
import com.itheima.bolee.framework.commons.utils.SubjectContent;
import com.itheima.bolee.framework.commons.dto.security.UserVO;
import com.itheima.bolee.insurance.dto.ai.AiAssistantChatRequestVO;
import com.itheima.bolee.insurance.dto.ai.AiAssistantChatResponseVO;
import com.itheima.bolee.insurance.dto.ai.contract.AiConversationMessagesResponseDTO;
import com.itheima.bolee.insurance.dto.ai.contract.AiConversationListResponseDTO;
import com.itheima.bolee.insurance.service.ai.AgnoAssistantService;
import com.itheima.bolee.insurance.service.ai.AiOwnPolicyQueryService;
import com.itheima.bolee.insurance.dto.ai.AiOwnPolicyPageResponseVO;
import com.itheima.bolee.insurance.dto.ai.AiOwnPolicyDetailResponseVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Customer-facing endpoint that keeps the Agno service behind the Java authentication boundary. */
@Api(tags = "AI 客服")
@RestController
@RequestMapping("ai-assistant")
public class AiAssistantController {

    @Autowired
    private AgnoAssistantService agnoAssistantService;

    @Autowired
    private AiOwnPolicyQueryService ownPolicyQueryService;

    @GetMapping("policies")
    @ApiOperation(value = "分页查询本人保单", notes = "仅返回当前登录用户的保单摘要字段")
    public ResponseResult<AiOwnPolicyPageResponseVO> listOwnPolicies(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, private");
        response.setHeader("Pragma", "no-cache");
        return ResponseResultBuild.successBuild(ownPolicyQueryService.findOwnPoliciesPage(
                String.valueOf(SubjectContent.getUserVO().getId()), cursor, limit));
    }

    @GetMapping("policies/{policyId}")
    @ApiOperation(value = "查询本人单张保单", notes = "服务端按当前登录用户重新校验保单归属")
    public ResponseResult<AiOwnPolicyDetailResponseVO> getOwnPolicy(@PathVariable String policyId,
                                                                     HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, private");
        response.setHeader("Pragma", "no-cache");
        return ResponseResultBuild.successBuild(ownPolicyQueryService.findOwnCustomerPolicy(
                String.valueOf(SubjectContent.getUserVO().getId()), policyId));
    }

    @PostMapping("chat")
    @ApiOperation(value = "保险客服对话", notes = "由 Java 身份上下文绑定用户，再调用内部 Agno 服务")
    public ResponseResult<AiAssistantChatResponseVO> chat(@RequestBody AiAssistantChatRequestVO request) {
        return ResponseResultBuild.successBuild(agnoAssistantService.chat(request, SubjectContent.getUserVO()));
    }

    @PostMapping(value = "chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiOperation(value = "流式保险客服对话", notes = "按 SSE 返回回答增量，并在结束时返回结构化结果")
    public ResponseEntity<StreamingResponseBody> streamChat(@RequestBody AiAssistantChatRequestVO request) {
        UserVO currentUser = SubjectContent.getUserVO();
        StreamingResponseBody body = output -> agnoAssistantService.streamChat(request, currentUser, output);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "event-stream", java.nio.charset.StandardCharsets.UTF_8));
        headers.setCacheControl("no-cache, no-store, private");
        headers.set("X-Accel-Buffering", "no");
        return ResponseEntity.ok().headers(headers).body(body);
    }

    @GetMapping("conversations")
    @ApiOperation(value = "查询 AI 客服会话列表", notes = "按当前登录用户返回最近会话及标题预览")
    public ResponseResult<AiConversationListResponseDTO> listConversations(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, private");
        response.setHeader("Pragma", "no-cache");
        return ResponseResultBuild.successBuild(
                agnoAssistantService.listConversations(cursor, limit, SubjectContent.getUserVO()));
    }

    @GetMapping("conversations/{sessionId}/messages")
    @ApiOperation(value = "查询 AI 客服会话历史", notes = "按当前登录用户校验会话归属，仅返回用户与助手消息")
    public ResponseResult<AiConversationMessagesResponseDTO> getConversationMessages(
            @PathVariable String sessionId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "25") int limit,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, private");
        response.setHeader("Pragma", "no-cache");
        return ResponseResultBuild.successBuild(agnoAssistantService.getConversationMessages(
                sessionId, cursor, limit, SubjectContent.getUserVO()));
    }

    @DeleteMapping("conversations/{sessionId}")
    @ApiOperation(value = "删除 AI 客服会话", notes = "立即禁止访问并异步删除 Agno 会话数据")
    public ResponseResult<Boolean> deleteConversation(@PathVariable String sessionId) {
        return ResponseResultBuild.successBuild(
                agnoAssistantService.deleteConversation(sessionId, SubjectContent.getUserVO()));
    }
}
