package com.stocklog.domain.member.service;


import com.stocklog.domain.member.dto.response.FriendResponse;
import com.stocklog.domain.member.dto.response.PendingResponse;
import com.stocklog.domain.member.entity.Friendship;
import com.stocklog.domain.member.entity.Member;
import com.stocklog.domain.member.entity.type.FriendStatus;
import com.stocklog.domain.member.repository.FriendshipRepository;
import com.stocklog.domain.portfolio.dto.response.PortfolioResponse;
import com.stocklog.domain.portfolio.service.PortfolioService;
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
    private final MemberService memberService;
    private final PortfolioService portfolioService;

    @Transactional
    public void sendRequest(Long fromId, Long toId) {
        validateSelfRequest(fromId,toId);

        Member from = memberService.findById(fromId);
        Member to = memberService.findById(toId);

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

    public List<Friendship> findMyFriendship(Long memberId) {
        Member member = memberService.findById(memberId);
        return friendshipRepository.findAllByMemberAndStatus(member, FriendStatus.ACCEPTED);
    }

    public List<FriendResponse> findMyFriends(Long myId) {
        List<FriendResponse> friendResponses = new ArrayList<>();

        Member me = memberService.findById(myId);
        List<Friendship> myFriends = friendshipRepository.findAllByMemberAndStatus(me, FriendStatus.ACCEPTED);

        for (Friendship friendship : myFriends) {
            Member friend = friendship.opponent(me);
            PortfolioResponse friendPortfolio = portfolioService.getMyPortfolio(friend.getId());
            friendResponses.add(FriendResponse.builder()
                    .id(friend.getId())
                    .username(friend.getUsername())
                    .friendshipId(friendship.getId())
                    .profitRate(friendPortfolio.totalReturnRate())
                    .build());
        }
        return friendResponses;
    }

    public List<PendingResponse> findPendingRequest(Long memberId) {
        List<PendingResponse> responses = new ArrayList<>();
        Member me = memberService.findById(memberId);
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

    public boolean isFriend(Long myId, Long friendId) {
        Member me = memberService.findById(myId);
        Member friend = memberService.findById(friendId);
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
