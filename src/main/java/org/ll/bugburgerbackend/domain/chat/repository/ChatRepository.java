package org.ll.bugburgerbackend.domain.chat.repository;

import org.ll.bugburgerbackend.domain.chat.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {

}
