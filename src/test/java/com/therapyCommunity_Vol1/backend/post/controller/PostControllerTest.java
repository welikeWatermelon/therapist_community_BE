package com.therapyCommunity_Vol1.backend.post.controller;

import com.therapyCommunity_Vol1.backend.post.domain.PostSortType;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.post.dto.PostSearchCondition;
import com.therapyCommunity_Vol1.backend.post.dto.TherapyPostSummaryResponse;
import com.therapyCommunity_Vol1.backend.post.service.PostService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PostControllerTest {

    @Mock
    private PostService postService;

    @InjectMocks
    private PostController postController;

    @Test
    void 게시글_목록_조회_성공() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(postController).build();

        // given
        PagedResponse<TherapyPostSummaryResponse> serviceResponse = new PagedResponse<>(
                List.of(),
                0,
                10,
                0L,
                0,
                false
        );
        given(postService.getPosts(eq(0), eq(10), eq(PostSortType.LATEST), any(PostSearchCondition.class)))
                .willReturn(serviceResponse);

        // when
        mockMvc.perform(get("/api/v1/posts")
                        .queryParam("page", "0")
                        .queryParam("size", "10")
                        .queryParam("sortType", "LATEST")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0))
                .andExpect(jsonPath("$.data.items").isArray());

        verify(postService).getPosts(eq(0), eq(10), eq(PostSortType.LATEST), any(PostSearchCondition.class));
    }
}
