package org.ll.bugburgerbackend.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.ll.bugburgerbackend.domain.member.entity.Member;
import org.ll.bugburgerbackend.global.baseEntity.BaseEntity;
import org.ll.bugburgerbackend.global.type.ChatType;

@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@SuperBuilder
public class Chat extends BaseEntity {

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatType chatType;

    @ManyToOne
    private Member member;
}
