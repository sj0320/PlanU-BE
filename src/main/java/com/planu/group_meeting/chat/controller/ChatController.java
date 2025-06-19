package com.planu.group_meeting.chat.controller;


import com.planu.group_meeting.chat.controller.swagger.ChatDocs;
import com.planu.group_meeting.chat.dao.ChatDAO;
import com.planu.group_meeting.chat.dto.ChatMessage;
import com.planu.group_meeting.chat.dto.request.ChatFileRequest;
import com.planu.group_meeting.chat.dto.request.ChatMessageRequest;
import com.planu.group_meeting.chat.dto.response.ChatMessageResponse;
import com.planu.group_meeting.chat.dto.response.ChatRoomResponse;
import com.planu.group_meeting.chat.dto.response.GroupedChatMessages;
import com.planu.group_meeting.chat.handler.ReadMessageBatchProcessor;
import com.planu.group_meeting.chat.service.ChatService;
import com.planu.group_meeting.config.auth.CustomUserDetails;
import com.planu.group_meeting.dto.BaseResponse;
import com.planu.group_meeting.dto.DataResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ChatController implements ChatDocs {

    private final ChatService chatService;
    private final ReadMessageBatchProcessor batchProcessor;
    private final ChatDAO chatDAO;

    @MessageMapping("/chat/group/{groupId}")
    public void chat(ChatMessageRequest messageRequest, @DestinationVariable("groupId") Long groupId, StompHeaderAccessor accessor){
        String username = (String) accessor.getSessionAttributes().get("username");

        Integer type = messageRequest.getType();
        String message = messageRequest.getMessage();

        ChatMessage chatMessage = chatService.save(groupId, username, type, message);

        chatService.sendMessage(groupId, chatMessage, username, type, message);
    }



    @MessageMapping("/chat/read/{messageId}/{groupId}")
    public void readChat(@DestinationVariable("messageId") Long messageId, @DestinationVariable("groupId") Long groupId, StompHeaderAccessor accessor) {
        String username = (String) accessor.getSessionAttributes().get("username");
        chatService.updateReadStatus(messageId, username);
        batchProcessor.addReadRequest(messageId, groupId);
    }

    @ResponseBody
    @GetMapping("/chats")
    public ResponseEntity<DataResponse<List<ChatRoomResponse>>> chatRooms(@AuthenticationPrincipal CustomUserDetails userDetails) {
        List<ChatRoomResponse> chatRooms = chatService.getChatRooms(userDetails.getId());
        DataResponse<List<ChatRoomResponse>> response = new DataResponse<>(chatRooms);
        return ResponseEntity.ok(response);
    }


    @ResponseBody
    @GetMapping("/chats/messages")
    public ResponseEntity<DataResponse<List<GroupedChatMessages>>> getChats(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("groupId") Long groupId,
            @RequestParam(value = "messageId", required = false) Long messageId) {
        chatService.updateMessageStatusAsRead(userDetails.getId(), groupId);
        List<GroupedChatMessages> groupedChatMessages = chatService.getMessages(userDetails.getId(), groupId, messageId);
        DataResponse<List<GroupedChatMessages>> response = new DataResponse<>(groupedChatMessages);
        return ResponseEntity.ok(response);
    }

    @ResponseBody
    @GetMapping("/chats/messages/update")
    public ResponseEntity<DataResponse<List<ChatMessageResponse>>> getUpdateChats(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("groupId") Long groupId,
            @RequestParam("startId") Long startId,
            @RequestParam("endId") Long endId
    ) {
        List<ChatMessageResponse> chatMessageResponseList = chatService.getUpdateMessages(userDetails.getId(), groupId, startId, endId);
        DataResponse<List<ChatMessageResponse>> response = new DataResponse<>(chatMessageResponseList);
        return ResponseEntity.ok(response);
    }

    @ResponseBody
    @GetMapping("/chats/new")
    public ResponseEntity<Integer> countNewChat(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(chatService.getUnreadCountforUser(userDetails.getId()));
    }


    @ResponseBody
    @PostMapping("/chats/file")
    public ResponseEntity<BaseResponse> chatFileUpload(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                       @ModelAttribute ChatFileRequest chatFileRequest) {
        Long userId = userDetails.getId();
        String username = userDetails.getUsername();
        Long groupId = chatFileRequest.getGroupId();
        MultipartFile file = chatFileRequest.getFile();

        ChatMessage chatMessage = chatService.UploadFileAndSaveChat(groupId, userId, username, file);

        chatService.sendMessage(groupId, chatMessage, username, chatMessage.getType(), chatMessage.getContent());

        return BaseResponse.toResponseEntity(HttpStatus.OK, "사진 전송 및 업로드 성공");
    }
}
