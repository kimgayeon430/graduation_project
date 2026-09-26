package smu.ai.graduation_project.model

data class Mission(
    val id: String = "",
    val title: String = "",
    val desc: String = "",
    val points: Int = 0,
    val category: String = "투어",
    val imageUrl: String = "",
    /**
     * 대표 이미지([imageUrl])의 CLIP 임베딩. `ml/embed_missions.py` 로 사전계산해
     * `missions/{id}.photoEmbedding` 에 저장한다. 사진 인증에서 촬영본과의 코사인 유사도를
     * 보조 신호로 쓴다(보고서 6.7). 없으면 카테고리 규칙만 적용.
     */
    val photoEmbedding: List<Float> = emptyList(),
    val status: String = "미 진행",
    val progress: Float = 0f,
    val progressText: String = "0/1",
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** 사진 인증은 제출됐지만 자동 판정이 애매해 관리자 승인 대기 중인지. 승인 전까지 완료·포인트 지급 보류. */
    val photoNeedsReview: Boolean = false,
    /** 예상 소요 시간(분). 관리자가 아직 입력하지 않은 기존 미션은 null — UI 는 null 이면 이 값을 숨긴다. */
    val estimatedMinutes: Int? = null,
    /** 이 사용자가 사진 인증에 실제로 제출한 사진(`user_missions.photoUrl`). [imageUrl](대표 이미지)과 다르다. */
    val verifiedPhotoUrl: String? = null,
    /** 이 미션을 제안한 사용자 uid. 관리자가 만든 미션(기존 문서 전부 포함)은 null. */
    val creatorId: String? = null,
    /** 제안 시점의 제안자 닉네임 스냅샷. */
    val creatorName: String = "",
    /** 검수 상태: pending(검수중) | approved(승인) | changes_requested(수정요청) | rejected(반려). 기존 문서는 필드가 없으므로 읽을 때 "approved"로 채운다. */
    val reviewStatus: String = "approved",
    /** 수정요청/반려 사유. 관리자가 검수 시 남긴다. */
    val reviewNote: String? = null,
    val likeCount: Int = 0,
    val bookmarkCount: Int = 0,
    /** 클라이언트에서 `mission_likes`/`mission_bookmarks` 조회로 병합만 하는 값. Firestore에는 저장하지 않는다. */
    val isLikedByMe: Boolean = false,
    val isBookmarkedByMe: Boolean = false
)

data class UserRank(
    val rank: Int,
    val name: String,
    val points: Int,
    val uid: String
)
