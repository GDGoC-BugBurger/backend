package org.ll.bugburgerbackend.domain.chat.service;

import lombok.RequiredArgsConstructor;
import org.ll.bugburgerbackend.domain.chat.entity.Chat;
import org.ll.bugburgerbackend.domain.chat.repository.ChatRepository;
import org.ll.bugburgerbackend.domain.member.entity.Member;
import org.ll.bugburgerbackend.global.type.ChatType;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatRepository chatRepository;

    public Chat saveChat(Member member, String message, ChatType chatType) {
        Chat chat = Chat.builder()
                .member(member)
                .message(message)
                .chatType(chatType)
                .build();
        return chatRepository.save(chat);
    }
}
