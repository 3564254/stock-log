package com.stocklog.domain.member.repository;

import com.stocklog.domain.member.entity.Friendship;
import com.stocklog.domain.member.entity.Member;
import com.stocklog.domain.member.entity.type.FriendStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    @Query("SELECT f FROM Friendship f WHERE (f.fromMember = :member OR f.toMember = :member) AND f.status = :status")
    List<Friendship> findAllByMemberAndStatus(Member member, FriendStatus status);

    @Query("SELECT f FROM Friendship f WHERE (f.fromMember = :memberA AND f.toMember = :memberB) OR (f.fromMember = :memberB AND f.toMember = :memberA)")
    Optional<Friendship> findRelation(Member memberA, Member memberB);

    List<Friendship> findAllByToMemberAndStatus(Member member, FriendStatus status);


    @Query("SELECT f FROM Friendship f WHERE ((f.fromMember = :memberA AND f.toMember = :memberB) OR (f.fromMember = :memberB AND f.toMember = :memberA)) AND (f.status = :status)")
    Optional<Friendship> findRelation(Member memberA, Member memberB, FriendStatus status);
}
