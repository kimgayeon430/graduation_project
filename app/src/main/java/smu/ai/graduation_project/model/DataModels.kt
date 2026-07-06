package smu.ai.graduation_project.model

data class Mission(
    val id: String = "",
    val title: String = "",
    val desc: String = "",
    val points: Int = 0,
    val category: String = "투어",
    val status: String = "미 진행",
    val progress: Float = 0f,
    val progressText: String = "0/1"
)

data class UserRank(
    val rank: Int,
    val name: String,
    val points: Int,
    val uid: String
)
