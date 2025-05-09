package org.ll.bugburgerbackend.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.ll.bugburgerbackend.global.baseEntity.BaseEntity;

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

    @Column(unique = true, length = 128)
    private String token;
}
