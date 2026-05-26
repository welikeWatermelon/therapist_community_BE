package com.therapyCommunity_Vol1.backend.post.service;

import com.therapyCommunity_Vol1.backend.follow.service.FollowService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PostVisibilityAccessPolicyTest {

    private FollowService followService;
    private PostVisibilityAccessPolicy policy;

    private User author;
    private TherapyPost publicPost;
    private TherapyPost privatePost;
    private TherapyPost followersOnlyPost;
    private TherapyPost verifiedFollowersOnlyPost;

    @BeforeEach
    void setUp() {
        followService = mock(FollowService.class);
        policy = new PostVisibilityAccessPolicy(followService);

        author = User.builder().id(10L).email("author@test.com").nickname("작성자").role(UserRole.THERAPIST).build();
        publicPost = TherapyPost.create("공개 글", null, Visibility.PUBLIC, author);
        privatePost = TherapyPost.create("비공개 글", null, Visibility.PRIVATE, author);
        followersOnlyPost = TherapyPost.create("팔로워 전용", null, Visibility.FOLLOWERS_ONLY, author);
        verifiedFollowersOnlyPost = TherapyPost.create("인증 팔로워 전용", null, Visibility.VERIFIED_FOLLOWERS_ONLY, author);
    }

    @Nested
    class PUBLIC_게시글 {
        @Test
        void 모든_역할_접근_가능() {
            assertThatCode(() -> policy.checkAccess(publicPost, UserRole.USER, 1L)).doesNotThrowAnyException();
            assertThatCode(() -> policy.checkAccess(publicPost, UserRole.THERAPIST, 2L)).doesNotThrowAnyException();
            assertThatCode(() -> policy.checkAccess(publicPost, UserRole.ADMIN, 3L)).doesNotThrowAnyException();
        }
    }

    @Nested
    class PRIVATE_게시글 {
        @Test
        void USER는_접근_불가() {
            assertThatThrownBy(() -> policy.checkAccess(privatePost, UserRole.USER, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.THERAPIST_VERIFICATION_REQUIRED);
        }

        @Test
        void THERAPIST는_접근_가능() {
            assertThatCode(() -> policy.checkAccess(privatePost, UserRole.THERAPIST, 2L)).doesNotThrowAnyException();
        }

        @Test
        void 작성자_본인은_항상_접근_가능() {
            assertThatCode(() -> policy.checkAccess(privatePost, UserRole.USER, 10L)).doesNotThrowAnyException();
        }
    }

    @Nested
    class FOLLOWERS_ONLY_게시글 {
        @Test
        void 팔로워는_접근_가능() {
            when(followService.isFollowing(1L, 10L)).thenReturn(true);
            assertThatCode(() -> policy.checkAccess(followersOnlyPost, UserRole.USER, 1L)).doesNotThrowAnyException();
        }

        @Test
        void 비팔로워는_접근_불가() {
            when(followService.isFollowing(1L, 10L)).thenReturn(false);
            assertThatThrownBy(() -> policy.checkAccess(followersOnlyPost, UserRole.USER, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POST_ACCESS_DENIED);
        }

        @Test
        void 작성자_본인은_항상_접근_가능() {
            assertThatCode(() -> policy.checkAccess(followersOnlyPost, UserRole.THERAPIST, 10L)).doesNotThrowAnyException();
        }
    }

    @Nested
    class VERIFIED_FOLLOWERS_ONLY_게시글 {
        @Test
        void 팔로워이면서_THERAPIST는_접근_가능() {
            when(followService.isFollowing(2L, 10L)).thenReturn(true);
            assertThatCode(() -> policy.checkAccess(verifiedFollowersOnlyPost, UserRole.THERAPIST, 2L)).doesNotThrowAnyException();
        }

        @Test
        void 팔로워이지만_USER는_접근_불가() {
            when(followService.isFollowing(1L, 10L)).thenReturn(true);
            assertThatThrownBy(() -> policy.checkAccess(verifiedFollowersOnlyPost, UserRole.USER, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.THERAPIST_VERIFICATION_REQUIRED);
        }

        @Test
        void THERAPIST이지만_비팔로워는_접근_불가() {
            when(followService.isFollowing(2L, 10L)).thenReturn(false);
            assertThatThrownBy(() -> policy.checkAccess(verifiedFollowersOnlyPost, UserRole.THERAPIST, 2L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POST_ACCESS_DENIED);
        }

        @Test
        void 작성자_본인은_항상_접근_가능() {
            assertThatCode(() -> policy.checkAccess(verifiedFollowersOnlyPost, UserRole.USER, 10L)).doesNotThrowAnyException();
        }
    }

    @Nested
    class 작성권한_검증 {
        @Test
        void USER는_FOLLOWERS_ONLY_작성_불가() {
            assertThatThrownBy(() -> policy.checkCanWriteVisibility(Visibility.FOLLOWERS_ONLY, UserRole.USER))
                    .isInstanceOf(CustomException.class);
        }

        @Test
        void USER는_VERIFIED_FOLLOWERS_ONLY_작성_불가() {
            assertThatThrownBy(() -> policy.checkCanWriteVisibility(Visibility.VERIFIED_FOLLOWERS_ONLY, UserRole.USER))
                    .isInstanceOf(CustomException.class);
        }

        @Test
        void THERAPIST는_모든_Visibility_작성_가능() {
            assertThatCode(() -> policy.checkCanWriteVisibility(Visibility.PUBLIC, UserRole.THERAPIST)).doesNotThrowAnyException();
            assertThatCode(() -> policy.checkCanWriteVisibility(Visibility.PRIVATE, UserRole.THERAPIST)).doesNotThrowAnyException();
            assertThatCode(() -> policy.checkCanWriteVisibility(Visibility.FOLLOWERS_ONLY, UserRole.THERAPIST)).doesNotThrowAnyException();
            assertThatCode(() -> policy.checkCanWriteVisibility(Visibility.VERIFIED_FOLLOWERS_ONLY, UserRole.THERAPIST)).doesNotThrowAnyException();
        }
    }
}
