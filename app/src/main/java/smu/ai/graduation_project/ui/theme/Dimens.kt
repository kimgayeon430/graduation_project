package smu.ai.graduation_project.ui.theme

import androidx.compose.ui.unit.dp

/** 화면 전반에 쓰는 여백 스케일. 화면마다 12/14/16/18/20dp 를 임의로 섞어 쓰던 걸 통일한다. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

/** 카드·칩·이미지의 모서리 반경 스케일. */
object Radius {
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    /** 완전히 둥근 캡슐 칩용(높이보다 큰 값이면 충분). */
    val pill = 999.dp
}
