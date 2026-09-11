package com.stocklog.modules.member.repository;

import com.stocklog.modules.member.entity.Friendship;
import com.stocklog.modules.member.entity.Member;
import com.stocklog.modules.member.entity.type.FriendStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    // TODO(JPA): 친구 목록을 DTO로 바꿀 때 fromMember/toMember의 LAZY 로딩이 반복되면 N+1이 생길 수 있다.
    // 호출 화면에서 실제 접근하는 연관관계를 확인한 뒤 @EntityGraph, fetch join, DTO projection 중 하나를 선택하고
    // 테스트 환경에서 Hibernate SQL/통계로 쿼리 수를 검증해 보자.

    @Query("SELECT f FROM Friendship f WHERE (f.fromMember = :member OR f.toMember = :member) AND f.status = :status")
    List<Friendship> findAllByMemberAndStatus(Member member, FriendStatus status);

    @Query("SELECT f FROM Friendship f WHERE (f.fromMember = :memberA AND f.toMember = :memberB) OR (f.fromMember = :memberB AND f.toMember = :memberA)")
    Optional<Friendship> findRelation(Member memberA, Member memberB);

    List<Friendship> findAllByToMemberAndStatus(Member member, FriendStatus status);


    @Query("SELECT f FROM Friendship f WHERE ((f.fromMember = :memberA AND f.toMember = :memberB) OR (f.fromMember = :memberB AND f.toMember = :memberA)) AND (f.status = :status)")
    Optional<Friendship> findRelation(Member memberA, Member memberB, FriendStatus status);
}
