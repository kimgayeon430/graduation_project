/**
 * firestore.rules 단위 테스트.
 *
 *   cd firestore-tests && npm install && npm test
 *
 * (Firestore 에뮬레이터를 8080 에 띄우고 `node --test` 로 이 파일을 돌린다.)
 */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, test } from "node:test";

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
} from "firebase/firestore";

const PROJECT_ID = "grad-proj-5e09c";
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8"),
      host: "127.0.0.1",
      port: 8080,
    },
  });
});
after(() => env.cleanup());
beforeEach(() => env.clearFirestore());

/** 규칙 무시하고 초기 데이터를 심는다. */
async function seed(fn) {
  await env.withSecurityRulesDisabled((ctx) => fn(ctx.firestore()));
}
const asUser = (uid) => env.authenticatedContext(uid).firestore();
const asGuest = () => env.unauthenticatedContext().firestore();

async function seedAdmin(uid) {
  await seed((db) => setDoc(doc(db, "admins", uid), { email: "a@b.c", name: "관리자" }));
}
async function seedMission(id, extra = {}) {
  await seed((db) => setDoc(doc(db, "missions", id),
    { title: "미션", category: "투어", points: 100, completionCount: 3, ...extra }));
}
async function seedUser(uid, extra = {}) {
  await seed((db) => setDoc(doc(db, "users", uid),
    { nickname: "u", mail: "u@x.c", points: 0, level: "Lv.1", preferences: [], ...extra }));
}
async function seedUserMission(id, extra = {}) {
  await seed((db) => setDoc(doc(db, "user_missions", id),
    { userId: "alice", missionId: "m1", status: "In Progress", progress: 0.5, ...extra }));
}

describe("admins/{uid}", () => {
  test("로그인 사용자는 자기 관리자 여부(get)를 확인할 수 있다", async () => {
    await seedAdmin("admin1");
    await assertSucceeds(getDoc(doc(asUser("someone"), "admins", "admin1")));
  });
  test("비관리자는 admins 목록(list)을 읽을 수 없다", async () => {
    await seedAdmin("admin1");
    await assertFails(getDocs(collection(asUser("bob"), "admins")));
  });
  test("관리자는 목록을 읽고 다른 사람을 관리자로 지정할 수 있다", async () => {
    await seedAdmin("admin1");
    await assertSucceeds(getDocs(collection(asUser("admin1"), "admins")));
    await assertSucceeds(setDoc(doc(asUser("admin1"), "admins", "bob"), { email: "b", name: "b" }));
  });
  test("비관리자는 자신을 관리자로 만들 수 없다", async () => {
    await assertFails(setDoc(doc(asUser("bob"), "admins", "bob"), { email: "b", name: "b" }));
  });
});

describe("missions/{id}", () => {
  test("게스트도 미션을 읽을 수 있다", async () => {
    await seedMission("m1");
    await assertSucceeds(getDoc(doc(asGuest(), "missions", "m1")));
  });
  test("비관리자는 미션을 생성/삭제할 수 없다", async () => {
    await assertFails(setDoc(doc(asUser("bob"), "missions", "new"),
      { title: "x", category: "투어", points: 10 }));
    await seedMission("m1");
    await assertFails(deleteDoc(doc(asUser("bob"), "missions", "m1")));
  });
  test("관리자는 미션을 생성할 수 있다", async () => {
    await seedAdmin("admin1");
    await assertSucceeds(setDoc(doc(asUser("admin1"), "missions", "new"),
      { title: "x", category: "투어", points: 10 }));
  });
  test("로그인 사용자는 completionCount 필드만 갱신할 수 있다", async () => {
    await seedMission("m1", { completionCount: 5 });
    await assertSucceeds(updateDoc(doc(asUser("bob"), "missions", "m1"), { completionCount: 6 }));
  });
  test("로그인 사용자가 completionCount 외 필드를 바꾸면 거부된다", async () => {
    await seedMission("m1");
    await assertFails(updateDoc(doc(asUser("bob"), "missions", "m1"), { points: 9999 }));
    await assertFails(updateDoc(doc(asUser("bob"), "missions", "m1"),
      { completionCount: 6, title: "해킹" }));
  });
});

describe("users/{uid}", () => {
  test("로그인 사용자는 사용자 문서를 읽을 수 있다(랭킹용)", async () => {
    await seedUser("alice");
    await assertSucceeds(getDoc(doc(asUser("bob"), "users", "alice")));
    await assertFails(getDoc(doc(asGuest(), "users", "alice")));
  });
  test("본인은 자기 문서를 만들고 수정할 수 있다", async () => {
    await assertSucceeds(setDoc(doc(asUser("alice"), "users", "alice"),
      { nickname: "a", mail: "a", points: 0, level: "Lv.1", preferences: ["투어"] }));
    await assertSucceeds(updateDoc(doc(asUser("alice"), "users", "alice"), { points: 50 }));
  });
  test("남의 문서는 만들거나 수정할 수 없다", async () => {
    await seedUser("alice");
    await assertFails(updateDoc(doc(asUser("mallory"), "users", "alice"), { points: 999999 }));
    await assertFails(setDoc(doc(asUser("mallory"), "users", "alice"), { nickname: "x" }));
  });
  test("관리자는 다른 사용자 포인트를 조정할 수 있다(검수 반려 시 회수)", async () => {
    await seedAdmin("admin1");
    await seedUser("alice", { points: 100 });
    await assertSucceeds(updateDoc(doc(asUser("admin1"), "users", "alice"), { points: 70 }));
  });
  test("사용자 문서는 삭제할 수 없다", async () => {
    await seedAdmin("admin1");
    await seedUser("alice");
    await assertFails(deleteDoc(doc(asUser("alice"), "users", "alice")));
    await assertFails(deleteDoc(doc(asUser("admin1"), "users", "alice")));
  });
});

describe("user_missions/{id}", () => {
  test("본인 문서만 생성할 수 있다", async () => {
    await assertSucceeds(setDoc(doc(asUser("alice"), "user_missions", "um1"),
      { userId: "alice", missionId: "m1", status: "In Progress" }));
    await assertFails(setDoc(doc(asUser("alice"), "user_missions", "um2"),
      { userId: "bob", missionId: "m1", status: "In Progress" }));
  });
  test("본인은 자기 진행 상태를 수정할 수 있다", async () => {
    await seedUserMission("um1", { userId: "alice" });
    await assertSucceeds(updateDoc(doc(asUser("alice"), "user_missions", "um1"),
      { status: "Completed", progress: 1 }));
  });
  test("본인은 자기 photoNeedsReview 를 true→false 로 되돌릴 수 없다", async () => {
    await seedUserMission("um1", { userId: "alice", photoNeedsReview: true });
    await assertFails(updateDoc(doc(asUser("alice"), "user_missions", "um1"),
      { photoNeedsReview: false }));
  });
  test("관리자는 검수 승인(photoNeedsReview=false)을 할 수 있다", async () => {
    await seedAdmin("admin1");
    await seedUserMission("um1", { userId: "alice", photoNeedsReview: true });
    await assertSucceeds(updateDoc(doc(asUser("admin1"), "user_missions", "um1"),
      { photoNeedsReview: false, photoVerified: true }));
  });
  test("남의 진행 문서는 수정할 수 없다", async () => {
    await seedUserMission("um1", { userId: "alice" });
    await assertFails(updateDoc(doc(asUser("mallory"), "user_missions", "um1"), { status: "Completed" }));
  });
  test("삭제는 관리자만 가능하다", async () => {
    await seedAdmin("admin1");
    await seedUserMission("um1", { userId: "alice" });
    await assertFails(deleteDoc(doc(asUser("alice"), "user_missions", "um1")));
    await assertSucceeds(deleteDoc(doc(asUser("admin1"), "user_missions", "um1")));
  });
});
