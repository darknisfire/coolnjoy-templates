package bateaux.spt.coolnjoy.core.site

import bateaux.spt.coolnjoy.core.model.BoardLayout

/** 게시판 정의. [id]는 사이트의 `bo_table`. */
data class Board(
    val id: String,
    val name: String,
    val layout: BoardLayout,
    val group: String? = null,
    /** 로그인해야 목록을 볼 수 있는 게시판. */
    val requiresLogin: Boolean = false,
)
