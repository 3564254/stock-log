package com.stocklog.modules.member.entity;

import com.stocklog.modules.member.entity.type.MemberRole;
import com.stocklog.modules.purchase.portfolio.entity.Holding;
import com.stocklog.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    private MemberRole role;

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL)
    private List<Holding> holdings = new ArrayList<>();


    @Builder
    public Member(String loginId, String password, String username) {
        this.loginId = loginId;
        this.password = password;
        this.username = username;
        this.role = MemberRole.USER;
    }

    public void changeUsername(String newUsername) {
        this.username = newUsername;
    }

    public void changePassword(String newPassword) {
        this.password = newPassword;
    }

    public static Member createGuest() {
        Member guest = new Member();
        guest.username = "guestMember";
        guest.role = MemberRole.GUEST;
        return guest;
    }
}
