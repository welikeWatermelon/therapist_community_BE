package com.therapyCommunity_Vol1.backend.post.service.search;

import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EmbeddingServiceTest {

    @Nested
    class ToVectorString {

        @Test
        void 올바른_포맷으로_변환한다() {
            float[] embedding = {0.1f, -0.2f, 0.3f};

            String result = EmbeddingService.toVectorString(embedding);

            assertThat(result).isEqualTo("[0.1,-0.2,0.3]");
        }

        @Test
        void 빈_배열을_처리한다() {
            float[] embedding = {};

            String result = EmbeddingService.toVectorString(embedding);

            assertThat(result).isEqualTo("[]");
        }

        @Test
        void 단일_요소를_처리한다() {
            float[] embedding = {0.5f};

            String result = EmbeddingService.toVectorString(embedding);

            assertThat(result).isEqualTo("[0.5]");
        }
    }

    @Nested
    class EmbedCache {

        private EmbeddingModel embeddingModel;
        private EmbeddingService embeddingService;

        @BeforeEach
        void setUp() {
            embeddingModel = mock(EmbeddingModel.class);
            TherapyPostRepository repository = mock(TherapyPostRepository.class);
            embeddingService = new EmbeddingService(embeddingModel, repository, new SimpleMeterRegistry());

            when(embeddingModel.embed(anyString()))
                    .thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        }

        @Test
        void 동일_텍스트는_캐시에서_반환하여_API를_한번만_호출한다() {
            embeddingService.embed("테스트 쿼리");
            embeddingService.embed("테스트 쿼리");
            embeddingService.embed("테스트 쿼리");

            verify(embeddingModel, times(1)).embed("테스트 쿼리");
        }

        @Test
        void 다른_텍스트는_각각_API를_호출한다() {
            embeddingService.embed("쿼리1");
            embeddingService.embed("쿼리2");

            verify(embeddingModel, times(1)).embed("쿼리1");
            verify(embeddingModel, times(1)).embed("쿼리2");
        }
    }

    @Nested
    class GenerateAndSave {

        @Test
        void API_실패_시_예외가_전파된다() {
            EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
            TherapyPostRepository repository = mock(TherapyPostRepository.class);
            EmbeddingService embeddingService = new EmbeddingService(embeddingModel, repository, new SimpleMeterRegistry());

            when(embeddingModel.embed(anyString()))
                    .thenThrow(new RuntimeException("OpenAI API 오류"));

            assertThatThrownBy(() -> embeddingService.generateAndSave(1L, "텍스트"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("OpenAI API 오류");
        }
    }
}
