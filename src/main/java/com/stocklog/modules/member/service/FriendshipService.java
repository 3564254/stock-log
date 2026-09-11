package com.stocklog.modules.member.service;


import com.stocklog.modules.member.dto.response.FriendResponse;
import com.stocklog.modules.member.dto.response.PendingResponse;
import com.stocklog.modules.member.entity.Friendship;
import com.stocklog.modules.member.entity.Member;
import com.stocklog.modules.member.entity.type.FriendStatus;
import com.stocklog.modules.member.repository.FriendshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendshipService {
    private final FriendshipRepository friendshipRepository;

    @Transactional
    public void sendRequest(Member from, Member to) {
        validateSelfRequest(from.getId(),to.getId());

        Optional<Friendship> existingRelation = friendshipRepository.findRelation(from, to);

        if (existingRelation.isEmpty()) {
            createNewRegistration(from, to);
        } else {
            handleExistingRelation(existingRelation.get(), from);
        }
    }

    @Transactional
    public void acceptFriend(Long friendshipId, Long memberId) {
        Friendship friendship = friendshipRepository.findById(friendshipId).orElseThrow();

        if (!friendship.getToMember().getId().equals(memberId)) throw new IllegalStateException("권한이 없습니다");
        friendship.accept();
    }

    public List<Friendship> findMyFriendship(Member member) {
        return friendshipRepository.findAllByMemberAndStatus(member, FriendStatus.ACCEPTED);
    }

    public List<FriendResponse> findMyFriends(Member me) {
        List<FriendResponse> friendResponses = new ArrayList<>();

        List<Friendship> myFriends = friendshipRepository.findAllByMemberAndStatus(me, FriendStatus.ACCEPTED);

        for (Friendship friendship : myFriends) {
            Member friend = friendship.opponent(me);
            friendResponses.add(FriendResponse.builder()
                    .id(friend.getId())
                    .username(friend.getUsername())
                    .friendshipId(friendship.getId())
                    .build());
        }
        return friendResponses;
    }

    public List<PendingResponse> findPendingRequest(Member me) {
        List<PendingResponse> responses = new ArrayList<>();
        List<Friendship> pendingRequests = friendshipRepository.findAllByToMemberAndStatus(me, FriendStatus.PENDING);

        for (Friendship friendship : pendingRequests) {
            responses.add(PendingResponse.from(friendship));
        }

        return responses;
    }

    public Friendship getFriendship(Long friendshipId) {
        return friendshipRepository.findById(friendshipId).orElseThrow();
    }

    @Transactional
    public void removeFriend(Long friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId).orElseThrow();
        friendshipRepository.delete(friendship);
    }

    public boolean isFriend(Member me, Member friend) {
        return friendshipRepository.findRelation(me, friend, FriendStatus.ACCEPTED).isPresent();
    }


    private void validateSelfRequest(Long fromId, Long toId) {
        if (fromId.equals(toId)) {
            throw new IllegalStateException("자신에게 친구 요청을 보낼 수 없습니다.");
        }
    }

    private void createNewRegistration(Member from, Member to) {
        Friendship friendship = Friendship.builder()
                .fromMember(from)
                .toMember(to)
                .status(FriendStatus.PENDING)
                .build();
        friendshipRepository.save(friendship);
    }

    private void handleExistingRelation(Friendship friendship, Member requester) {
        FriendStatus status = friendship.getStatus();

        if (status == FriendStatus.ACCEPTED) {
            throw new IllegalStateException("이미 친구 관계입니다.");
        }

        if (status == FriendStatus.BLOCKED) {
            throw new IllegalStateException("차단된 상태입니다.");
        }

        if (status == FriendStatus.PENDING) {
            if (friendship.getToMember().getId().equals(requester.getId())) {
                friendship.accept();
            } else {
                throw new IllegalStateException("이미 친구 요청을 보낸 상태입니다.");
            }
        }


    }
}
