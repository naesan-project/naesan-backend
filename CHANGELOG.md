# 변경 이력

이 프로젝트의 주요 변경 사항을 릴리스 단위로 기록합니다.

## [0.1.0] - 2026-08-07

### 추가

- JWT access token, 회전 가능한 refresh token과 CSRF 기반 계정 인증
- 구매 증빙 초안·파일·확정 snapshot과 패스 발급 수명주기
- capability token 기반 제한 공유와 공개 파일 일치 확인
- 동시성 제어를 포함한 패스 소유권 이전과 소유 이력
- Transactional Outbox 기반 EVM anchor 제출·재시도·복구
- Sepolia 배포, 트랜잭션 근거와 Mainnet 비용 시나리오 측정
- Swagger UI, OpenAPI JSON·YAML과 핵심 데이터 모델 ERD
- PostgreSQL·MinIO·프론트엔드를 포함한 production Compose 브라우저 검증

### 운영과 검증

- liveness, database·proof provider readiness, Prometheus와 구조화 로그
- Spring 391개 테스트와 Anvil 기반 EVM 25개 통합 테스트
- 실제 React UI의 회원가입·증빙 업로드·발급·공유·폐기 E2E
- GitHub Actions의 계약·테스트·Compose·production 이미지 검증

### 알려진 한계

- Render 무료 인스턴스 cold start와 Sepolia confirmation 지연이 존재합니다.
- proof writer와 anchor contract는 중앙 운영 주체에 의존합니다.
- Mainnet 배포와 실제 금전 지출은 수행하지 않았습니다.
- iOS PWA 실기기 검증은 수행하지 않았습니다.

[0.1.0]: https://github.com/naesan-project/naesan-backend/releases/tag/v0.1.0
