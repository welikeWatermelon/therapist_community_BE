-- 런타임 기능 토글용 범용 플래그 테이블.
-- 자동답글(ai_comment)을 재배포 없이 관리자 API로 on/off 하기 위해 도입.
-- row가 없으면 애플리케이션 프로퍼티 기본값으로 fallback 하므로 시드는 두지 않는다.
CREATE TABLE feature_flags (
    flag_key   VARCHAR(100) PRIMARY KEY,
    enabled    BOOLEAN      NOT NULL,
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);
