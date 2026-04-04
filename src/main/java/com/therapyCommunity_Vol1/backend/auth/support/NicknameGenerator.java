package com.therapyCommunity_Vol1.backend.auth.support;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class NicknameGenerator {

    private static final int MAX_ATTEMPTS = 5;

    private static final List<String> ANIMALS = List.of(
            "돌고래", "수달", "판다", "코알라", "라쿤",
            "햄스터", "토끼", "여우", "고양이", "강아지",
            "다람쥐", "고슴도치", "미어캣", "펭귄", "바다표범",
            "알파카", "카피바라", "비버", "오소리", "플라밍고"
    );

    private final UserRepository userRepository;

    public String generate() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String nickname = randomAnimal() + "#" + randomNumber();
            if (!userRepository.existsByNickname(nickname)) {
                return nickname;
            }
        }
        throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private String randomAnimal() {
        return ANIMALS.get(ThreadLocalRandom.current().nextInt(ANIMALS.size()));
    }

    private int randomNumber() {
        return ThreadLocalRandom.current().nextInt(1000, 10000);
    }
}
