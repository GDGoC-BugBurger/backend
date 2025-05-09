package org.ll.bugburgerbackend.domain.member.repository;

import org.ll.bugburgerbackend.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

}
