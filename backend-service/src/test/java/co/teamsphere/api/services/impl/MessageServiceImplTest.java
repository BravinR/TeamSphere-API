package co.teamsphere.api.services.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.*;

import co.teamsphere.api.exception.ChatException;
import co.teamsphere.api.exception.MessageException;
import co.teamsphere.api.exception.UserException;
import co.teamsphere.api.models.Chat;
import co.teamsphere.api.models.Messages;
import co.teamsphere.api.models.User;
import co.teamsphere.api.repository.MessageRepository;
import co.teamsphere.api.request.SendMessageRequest;
import co.teamsphere.api.services.ChatService;
import co.teamsphere.api.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class MessageServiceImplTest {
    @Mock
    private MessageRepository messageRepo;

    @Mock
    private UserService userService;

    @Mock
    private ChatService chatService;

    @InjectMocks
    private MessageServiceImpl messageService;

    private User user;
    private Chat chat;
    private Messages message;
    private UUID messageId;
    private UUID chatId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        user = User.builder()
            .id(UUID.randomUUID())
            .email("email@email.com")
            .build();
        chat = new Chat();
        chat.setId(UUID.randomUUID());
        chat.setIsGroup(false);
        messageId = UUID.randomUUID();
        chatId = chat.getId();
        message = Messages.builder()
                .id(messageId)
                .chat(chat)
                .username(user)
                .content("Hello World")
                .timeStamp(LocalDateTime.now())
                .isRead(false)
                .build();
    }

    @Test
    void sendMessageSavesMessageWhenValidRequest() throws UserException, ChatException {
        // Arrange
        SendMessageRequest request = new SendMessageRequest();
        request.setUserId(user.getId());
        request.setChatId(chat.getId());
        request.setContent("Hello World");

        Set<User> chatUsers = new HashSet<>();
        chatUsers.add(user);
        chat.setUsers(chatUsers);

        when(userService.findUserById(user.getId())).thenReturn(user);
        when(chatService.findChatById(chat.getId())).thenReturn(chat);
        when(messageRepo.save(any(Messages.class))).thenReturn(message);

        // Act
        Messages savedMessage = messageService.sendMessage(request);

        // Assert
        assertNotNull(savedMessage);
        assertEquals("Hello World", savedMessage.getContent());
        assertEquals(chat, savedMessage.getChat());
        assertEquals(user, savedMessage.getUsername());

        verify(userService).findUserById(user.getId());
        verify(chatService).findChatById(chat.getId());
        verify(messageRepo).save(any(Messages.class));
    }


    @Test
    void deleteMessageRemovesMessageWhenMessageExists() throws MessageException {
        when(messageRepo.findById(messageId)).thenReturn(Optional.of(message));
        messageService.deleteMessage(messageId, user.getId());
        verify(messageRepo, times(1)).deleteById(messageId);
    }


    @Test
    void deleteMessageThrowsExceptionWhenMessageNotFound() {
        when(messageRepo.findById(messageId)).thenReturn(Optional.empty());
        assertThrows(MessageException.class, () -> messageService.deleteMessage(messageId, user.getId()));
        verify(messageRepo, never()).deleteById(any());
    }


    @Test
    void getChatsMessagesReturnsMessagesListWhenChatExists() throws ChatException {
        // Arrange
        UUID chatId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);

        Chat chat = new Chat();
        chat.setId(chatId);
        chat.setUsers(Set.of(user));

        Messages message = new Messages();
        message.setContent("Hello World");

        when(chatService.findChatById(chatId)).thenReturn(chat);
        when(messageRepo.findMessageByChatId(chatId)).thenReturn(List.of(message));

        // Act
        List<Messages> messagesList = messageService.getChatsMessages(chatId, userId);

        // Assert
        assertNotNull(messagesList);
        assertFalse(messagesList.isEmpty());
        assertEquals(1, messagesList.size());
        assertEquals("Hello World", messagesList.get(0).getContent());
    }


    @Test
    void getChatsMessagesThrowsExceptionWhenChatNotFound() throws ChatException {
        when(chatService.findChatById(chatId)).thenThrow(new ChatException("Chat not found"));
        assertThrows(ChatException.class, () -> messageService.getChatsMessages(chatId, user.getId()));
        verify(messageRepo, never()).findMessageByChatId(any());
    }
    @Test
    void findMessageByIdReturnsMessageWhenMessageExists() throws MessageException {
        when(messageRepo.findById(messageId)).thenReturn(Optional.of(message));
        Messages foundMessage = messageService.findMessageById(messageId);
        assertNotNull(foundMessage);
        assertEquals("Hello World", foundMessage.getContent());
    }
    @Test
    void findMessageByIdThrowsExceptionWhenMessageNotFound() {
        when(messageRepo.findById(messageId)).thenReturn(Optional.empty());
        assertThrows(MessageException.class, () -> messageService.findMessageById(messageId));
    }
}

