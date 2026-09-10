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
    val longitude: Double? = null
)

data class UserRank(
    val rank: Int,
    val name: String,
    val points: Int,
    val uid: String
)
