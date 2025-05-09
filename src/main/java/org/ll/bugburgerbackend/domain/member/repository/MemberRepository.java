package org.ll.bugburgerbackend.domain.member.repository;

import org.ll.bugburgerbackend.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByToken(String token);

    Optional<Member> findByUsername(String username);

    Optional<Object> findByNickname(String username);
}
