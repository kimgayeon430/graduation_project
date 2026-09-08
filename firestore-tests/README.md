# Firestore 보안 규칙 테스트

`../firestore.rules` 를 Firestore 에뮬레이터에 올려 `@firebase/rules-unit-testing` 으로 검증한다.
`assertSucceeds` / `assertFails` 20건 (`rules.test.js`).

## 실행

```bash
cd firestore-tests
npm install
npm test          # firebase emulators:exec 로 에뮬레이터를 띄우고 node --test 실행
```

- 필요: Node ≥ 18, Java ≥ 11 (에뮬레이터 JVM). `firebase-tools` 는 전역 또는 npx.
- 첫 실행 때 Firestore 에뮬레이터(jar)를 내려받는다.

## 커버 (`rules.test.js`)

| 컬렉션 | 검증 |
| --- | --- |
| `admins` | 로그인 사용자 본인 관리자 여부 확인 가능 · 비관리자는 목록/쓰기 불가 · 자기를 관리자로 못 만듦 |
| `missions` | 게스트 읽기 O · 비관리자 생성/삭제 X · 비관리자는 `completionCount` 만 갱신 (다른 필드 섞으면 거부) |
| `users` | 로그인 읽기 O(게스트 X) · 본인/관리자만 수정 · 삭제 불가 |
| `user_missions` | 본인 문서만 생성 · 본인은 진행 상태 수정 O · **본인은 `photoNeedsReview` true→false 불가** · 관리자만 승인/삭제 |

## 배포 (검증 후)

```bash
firebase login
firebase deploy --only firestore:rules --project grad-proj-5e09c
```
