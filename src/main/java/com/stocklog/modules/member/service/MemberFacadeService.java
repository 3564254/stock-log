package com.stocklog.modules.member.service;

import com.stocklog.modules.member.dto.request.LoginRequest;
import com.stocklog.modules.member.dto.request.PasswordRequest;
import com.stocklog.modules.member.dto.request.SignupRequest;
import com.stocklog.modules.member.dto.response.FriendResponse;
import com.stocklog.modules.member.dto.response.MemberDetailResponse;
import com.stocklog.modules.member.dto.response.MemberResponse;
import com.stocklog.modules.member.dto.response.PendingResponse;
import com.stocklog.modules.member.entity.Friendship;
import com.stocklog.modules.member.entity.Member;
import com.stocklog.global.error.BusinessException;
import com.stocklog.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class MemberFacadeService {
    private final MemberService memberService;
    private final FriendshipService friendshipService;
    private final AuthService authService;


    // ============== Member Service ============== //
    public void save(Member member) {
        memberService.save(member);
    }

    public MemberResponse getInfo(Long memberId) {
        return memberService.getInfo(memberId);
    }

    public MemberDetailResponse getDetailInfo(Long memberId) {
        return memberService.getDetailInfo(memberId);
    }

    public MemberResponse getMemberResponseByLoginId(String loginId) {
        return memberService.getMemberResponseByLoginId(loginId);
    }

    public Member findById(Long memberId) {
        return memberService.findById(memberId);
    }

    public Member getReference(Long id) {
        return memberService.getReferenceById(id);
    }

    public void updateUsername(Long memberId, String newUsername) {
        memberService.updateUsername(memberId, newUsername);
    }

    public void deleteById(Long id) {
        memberService.deleteById(id);
    }

    boolean isLoginIdDuplicate(String loginId) {
        return memberService.isLoginIdDuplicate(loginId);
    }

    private Optional<Member> findByLoginId(String loginId) {
        return memberService.findByLoginId(loginId);
    }

    // ============== Friendship Service ============== //
    public void sendRequest(Long fromId, Long toId) {
        Member from = memberService.findById(fromId);
        Member to = memberService.findById(toId);
        friendshipService.sendRequest(from, to);
    }

    public void acceptFriend(Long friendshipId, Long memberId) {
        friendshipService.acceptFriend(friendshipId, memberId);
    }

    public List<Friendship> findMyFriendship(Long memberId) {
        Member member = memberService.findById(memberId);
        return friendshipService.findMyFriendship(member);
    }

    public List<FriendResponse> findMyFriends(Long myId) {
        Member me = memberService.findById(myId);
        return friendshipService.findMyFriends(me);
    }

    public List<PendingResponse> findPendingRequest(Long myId) {
        Member me = memberService.findById(myId);
        return friendshipService.findPendingRequest(me);
    }

    public Friendship getFriendship(Long friendshipId) {
        return friendshipService.getFriendship(friendshipId);
    }

    public void removeFriend(Long friendshipId) {
        friendshipService.removeFriend(friendshipId);
    }

    public boolean isFriend(Long myId, Long friendId) {
        Member me = memberService.findById(myId);
        Member friend = memberService.findById(friendId);
        return friendshipService.isFriend(me, friend);
    }


    // ============== Auth Service ============== //
    public void signUp(SignupRequest request) {
        if (isLoginIdDuplicate(request.getLoginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        Member member = authService.signUp(request);
        save(member);
    }

    public MemberResponse login(LoginRequest request) {
        Optional<Member> byLoginId = findByLoginId(request.getLoginId());
        return authService.login(request, byLoginId);
    }

    public void updatePassword(Long memberId,PasswordRequest request) {
        Member member = memberService.findById(memberId);
        authService.updatePassword(member, request);
    }








}
