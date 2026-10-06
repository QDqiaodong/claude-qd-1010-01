package com.museum.collection.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.museum.collection.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByMemberNo(String memberNo);

    List<Member> findByLevel(String level);
}
