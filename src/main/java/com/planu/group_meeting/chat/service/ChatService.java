package com.planu.group_meeting.chat.service;

import com.planu.group_meeting.chat.dao.ChatDAO;
import com.planu.group_meeting.chat.dto.ChatInfo;
import com.planu.group_meeting.chat.dto.ChatMessage;
import com.planu.group_meeting.chat.dto.MessageStatus;
import com.planu.group_meeting.chat.dto.response.ChatMessageResponse;
import com.planu.group_meeting.chat.dto.response.ChatRoomResponse;
import com.planu.group_meeting.chat.dto.response.GroupedChatMessages;
import com.planu.group_meeting.dao.GroupDAO;
import com.planu.group_meeting.dao.UserDAO;
import com.planu.group_meeting.dto.GroupResponseDTO;
import com.planu.group_meeting.dto.GroupUserDTO;
import com.planu.group_meeting.entity.GroupUser;
import com.planu.group_meeting.service.file.S3Uploader;
import com.planu.group_meeting.valid.InputValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final SimpMessageSendingOperations simpMessageSendingOperations;
    private final UserDAO userDAO;
    private final ChatDAO chatDAO;
    private final GroupDAO groupDAO;
    private final InputValidator inputValidator;
    private final S3Uploader s3Uploader;

    @Transactional
    public ChatMessage save(Long groupId, String username, Integer type, String content){
        Long userId = userDAO.findIdByUsername(username);

        ChatMessage chatMessage = ChatMessage.builder()
                .userId(userId)
                .groupId(groupId)
                .type(type)
                .content(content)
                .build();

        chatDAO.saveChatMessage(chatMessage);

        saveMessageStatus(groupId, chatMessage.getId());

        return chatDAO.findById(chatMessage.getId());
    }

    @Transactional
    private void saveMessageStatus(Long groupId, Long chatMessageId) {
        List<GroupUserDTO> users = groupDAO.findGroupsByGroupId(groupId);

        for (GroupUserDTO user : users) {
            MessageStatus messageStatus = MessageStatus.builder()
                    .messageId(chatMessageId)
                    .userId(user.getUserId())
                    .build();

            chatDAO.saveMessageStatus(messageStatus);
        }
    }

    @Transactional
    public List<ChatRoomResponse> getChatRooms(Long userId) {
        List<GroupResponseDTO> groupList = groupDAO.findGroupsByUserId(userId);

        return groupList.stream()
                .map(group -> {
                    ChatInfo chatInfo = chatDAO.getChatInfo(group.getGroupId());
                    return ChatRoomResponse.builder()
                            .groupId(group.getGroupId())
                            .groupName(group.getGroupName())
                            .groupImageUrl(group.getGroupImageUrl())
                            .participant(Long.parseLong(group.getParticipant()))
                            .isPin(group.getIsPin())
                            .lastChat(Optional.ofNullable(chatInfo).map(ChatInfo::getLastChat).orElse(""))
                            .lastChatDate(Optional.ofNullable(chatInfo).map(ChatInfo::getLastChatDate).orElse(""))
                            .lastChatTime(Optional.ofNullable(chatInfo).map(ChatInfo::getLastChatTime).orElse(""))
                            .unreadChats(chatDAO.countUnreadChatByUserAndGroup(userId, group.getGroupId()))
                            .build();
                })
                .sorted(Comparator.comparing(ChatRoomResponse::getIsPin)
                        .reversed()
                        .thenComparing(Comparator.comparing(ChatRoomResponse::getLastChatDate).reversed())
                        .thenComparing(Comparator.comparing(ChatRoomResponse::getLastChatTime).reversed()))
                .collect(Collectors.toList());

    }

    @Transactional
    public void sendMessage(Long groupId, ChatMessage chatMessage, String username, Integer type, String message) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String date = chatMessage.getCreatedDate().format(dateFormatter);

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        String time = chatMessage.getCreatedDate().format(timeFormatter);

        updateReadStatus(chatMessage.getId(), username);

        ChatMessageResponse chatMessageResponse = ChatMessageResponse.builder()
                .messageId(chatMessage.getId())
                .type(type)
                .sender(username)
                .profileImageUrl(userDAO.findProfileImageById(userDAO.findIdByUsername(username)))
                .name(userDAO.findNameById(userDAO.findIdByUsername(username)))
                .message(message)
                .unReadCount(getUnreadCountforMessage(chatMessage.getId()))
                .chatTime(time)
                .chatDate(date)
                .build();

        simpMessageSendingOperations.convertAndSend("/sub/chat/group/" + groupId, chatMessageResponse);
    }

    @Transactional
    public int getUnreadCountforMessage(Long messageId) {
        return chatDAO.countUnreadByMessageId(messageId);
    }

    @Transactional
    public Integer getUnreadCountforUser(Long userId) {
        return chatDAO.countUnreadByUserId(userId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void updateReadStatus(Long messageId, String username) {
        Long userId = userDAO.findIdByUsername(username);

        chatDAO.markAsRead(userId, messageId);
    }

    @Transactional
    public List<GroupedChatMessages> getMessages(Long userId, Long groupId, Long offset) {
        GroupUser groupUser = groupDAO.findGroupUserByUserIdAndGroupId(userId, groupId);

        if(groupUser == null){
            throw new IllegalArgumentException("해당 그룹의 멤버가 아닙니다.");
        }
        if(groupUser.getGroupState() == 0) {
            throw new IllegalArgumentException("해당 그룹의 멤버가 아닙니다.");
        }

        int limit = 50;

        List<ChatMessage> chatMessageList = chatDAO.findChatMessages(groupId, offset, limit);

        simpMessageSendingOperations.convertAndSend("/sub/chat/group/" + groupId, ChatMessageResponse.builder().type(3).build());

        // 메시지 변환 후 그룹화
        Map<String, List<ChatMessageResponse>> groupedMessages = chatMessageList.stream()
                .map(this::convertToResponse) // ChatMessage -> ChatMessageResponse 변환
                .collect(Collectors.groupingBy(ChatMessageResponse::getChatDate)); // ChatDate 기준 그룹화

        // 그룹화된 데이터를 List<GroupedChatMessages> 형태로 변환
        return groupedMessages.entrySet().stream()
                .map(entry -> new GroupedChatMessages(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(GroupedChatMessages::getChatDate)) // 날짜 순 정렬
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateMessageStatusAsRead(Long userId, Long groupId) {
        chatDAO.updateMessageStatusAsRead(userId, groupId);
    }

    private ChatMessageResponse convertToResponse(ChatMessage chatMessage) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        return ChatMessageResponse.builder()
                .messageId(chatMessage.getId())
                .type(chatMessage.getType())
                .message(chatMessage.getContent())
                .sender(userDAO.findUsernameById(chatMessage.getUserId()))// sender 변환
                .profileImageUrl(userDAO.findProfileImageById(chatMessage.getUserId()))// 프로필 이미지
                .name(userDAO.findNameById(chatMessage.getUserId()))
                .unReadCount(chatDAO.countUnreadByMessageId(chatMessage.getId())) // 안 읽은 사람 수 조회
                .chatDate(chatMessage.getCreatedDate().format(dateFormatter)) // 날짜 변환
                .chatTime(chatMessage.getCreatedDate().format(timeFormatter)) // 시간 변환
                .build();
    }

    @Transactional
    public List<ChatMessageResponse> getUpdateMessages(Long userId, Long groupId, Long startId, Long endId) {
        GroupUser groupUser = groupDAO.findGroupUserByUserIdAndGroupId(userId, groupId);

        if(groupUser == null){
            throw new IllegalArgumentException("해당 그룹의 멤버가 아닙니다.");
        }
        if(groupUser.getGroupState() == 0) {
            throw new IllegalArgumentException("해당 그룹의 멤버가 아닙니다.");
        }

        List<Long> messageIds = chatDAO.getMessageIdsByGroupAndRange(groupId, startId, endId);

        return messageIds.stream()
                .map(messageId -> ChatMessageResponse.builder()
                        .messageId(messageId)
                        .type(4)
                        .unReadCount(chatDAO.countUnreadByMessageId(messageId))
                        .build()
                )
                .collect(Collectors.toList());
    }

    @Transactional
    public void joinChat(String username, Long groupId) {
        ChatMessage chatMessage = save(groupId, username, 5, null);

        chatDAO.updateIsReadByMessageId(chatMessage.getId());

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String date = chatMessage.getCreatedDate().format(dateFormatter);

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        String time = chatMessage.getCreatedDate().format(timeFormatter);

        ChatMessageResponse chatMessageResponse = ChatMessageResponse.builder()
                .type(chatMessage.getType())
                .sender(username)
                .name(userDAO.findNameById(userDAO.findIdByUsername(username)))
                .chatDate(date)
                .chatTime(time)
                .build();

        simpMessageSendingOperations.convertAndSend("/sub/chat/group/" + groupId,chatMessageResponse);
    }
    
    @Transactional
    public void expelChat(String username, Long groupId) {
        ChatMessage chatMessage = save(groupId, username, 6, null);

        chatDAO.updateIsReadByMessageId(chatMessage.getId());

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String date = chatMessage.getCreatedDate().format(dateFormatter);

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        String time = chatMessage.getCreatedDate().format(timeFormatter);

        ChatMessageResponse chatMessageResponse = ChatMessageResponse.builder()
                                            .type(chatMessage.getType())
                                            .sender(username)
                                            .name(userDAO.findNameById(userDAO.findIdByUsername(username)))
                                            .chatTime(date)
                                            .chatDate(time)
                                            .build();

        simpMessageSendingOperations.convertAndSend("/sub/chat/group/" + groupId,chatMessageResponse);

        simpMessageSendingOperations.convertAndSend("/sub/disconnect/" + username, "웹소켓 연결 종료요청 보내주세요.");
    }

    @Transactional
    public ChatMessage UploadFileAndSaveChat(Long groupId, Long userId, String username, MultipartFile file) {
        inputValidator.chatImageValid(file);
        GroupUser groupUser = groupDAO.findGroupUserByUserIdAndGroupId(userId, groupId);
        if (groupUser == null || groupUser.getGroupState() == 0) {
            throw new IllegalArgumentException("잘못된 groupId");
        }

        String fileUrl = s3Uploader.uploadFile(file);

        return save(groupId, username, 2, fileUrl);
    }
}
