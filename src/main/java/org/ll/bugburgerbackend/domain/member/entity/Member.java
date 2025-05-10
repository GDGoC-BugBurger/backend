package org.ll.bugburgerbackend.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.ll.bugburgerbackend.domain.chat.entity.Chat;
import org.ll.bugburgerbackend.global.baseEntity.BaseEntity;
import org.ll.bugburgerbackend.global.type.DementiaStage;
import org.ll.bugburgerbackend.global.type.GenderType;

import java.util.List;

@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@SuperBuilder
public class Member extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String username;

    @Column(nullable = false)
    private String birth;

    @Column(nullable = false, unique = true, length = 20)
    private String nickname;

    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DementiaStage dementiaStage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GenderType gender;

    @Column(length = 500)
    private String interests;

    @Column(length = 500)
    private String background;

    @Column(length = 500)
    private String family;

    @Column(length = 500)
    private String recentAnalysis;

    @Column(unique = true, length = 128)
    private String token;

    @OneToMany(mappedBy = "member")
    private List<Chat> chats;
}
