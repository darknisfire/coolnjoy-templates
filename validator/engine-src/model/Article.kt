package bateaux.spt.coolnjoy.core.model

/** 이름이 붙은 URL(첨부파일, 관련 링크). [url]은 절대 URL. */
data class ArticleLink(
    val name: String,
    val url: String,
)

/**
 * 게시글 상세. 실제 마크업(2026-10 BS4-Basic/Nariya)에서 얻을 수 있는 필드만 담는다.
 *
 * @property url 정식 URL(`/bbs/{bo_table}/{wr_id}`)
 * @property category `h1#bo_v_title`의 `분류 | 제목`에서 분류. 없으면 null
 * @property writerProfileImageUrl 작성자 카드의 프로필 이미지(절대 URL). 없으면 null
 * @property viewCount 조회수(쉼표 제거). 이 게시판이 표시하지 않으면 null
 * @property recommendCount 추천수. 추천 기능이 없는 게시판(예: 회원장터)은 null
 * @property preContentHtml 본문 앞에 스킨이 끼워 넣는 영역(회원장터 거래 정보 표, 자료실 요약 표). 정화됨. 없으면 null
 * @property contentHtml 본문(`div.view-content`) 정화된 HTML. script/style/iframe/이벤트 속성 제거, 상대 URL은 절대 URL
 * @property images 본문 이미지(절대 URL). 원본으로 연결되는 `a.view_image`가 감싼 이미지는 원본 URL, 아니면 `src`
 * @property attachments 첨부파일(그누보드 표준 `download.php` 링크). 이 사이트 자료실은 외부 iframe([embedUrls])을 쓴다
 * @property links 게시글 관련 링크(그누보드 표준 `link.php`)
 * @property embedUrls 제거된 iframe의 원래 `src`(유튜브, 자료실 다운로드 프레임 등)
 * @property comments 이 페이지에 실린 댓글(사이트는 페이지당 50개). 대댓글 들여쓰기 표식은 마크업에 없어 평면 목록이다
 * @property commentCount 전체 댓글 수
 * @property commentPage 이 페이지의 댓글 페이지(1부터)
 * @property commentPageCount 댓글 전체 페이지 수
 * @property extras writerId(mb_id) 등
 * @property poll 설문(투표) 결과. 상세 페이지의 Google Charts 스크립트 데이터에서 값만 읽는다(스크립트는 실행하지 않음). 없거나 형식이 다르면 null
 * @property specs 시스템 사양 표(`div.bo_system`)의 라벨/값. 없으면 빈 목록
 * @property infoRows 본문 앞 정보 표(신청형 이벤트 divTable, 회원장터 거래 정보, 자료실 요약)의 라벨/값. 회원장터의 판매자 이름·연락처·IP 행은
 *   결과와 [preContentHtml] 모두에서 제외한다(앱에 표시·저장하지 않음). 없으면 빈 목록
 */
data class Article(
    val boardId: String,
    val wrId: Long,
    val url: String,
    val title: String,
    val category: String? = null,
    val writer: String? = null,
    val writerProfileImageUrl: String? = null,
    val postedAt: PostedAt? = null,
    val viewCount: Int? = null,
    val recommendCount: Int? = null,
    val preContentHtml: String? = null,
    val contentHtml: String = "",
    val images: List<String> = emptyList(),
    val attachments: List<ArticleLink> = emptyList(),
    val links: List<ArticleLink> = emptyList(),
    val embedUrls: List<String> = emptyList(),
    val comments: List<CommentItem> = emptyList(),
    val commentCount: Int = 0,
    val commentPage: Int = 1,
    val commentPageCount: Int = 1,
    val extras: Map<String, String> = emptyMap(),
    val poll: Poll? = null,
    val specs: List<LabeledValue> = emptyList(),
    val infoRows: List<LabeledValue> = emptyList(),
) {
    /** 이 페이지에 실린 댓글 중 비밀 댓글 수. 게시글 전체 수는 `ArticleRepository.commentStats`. */
    val secretCommentCount: Int get() = comments.count { it.secret }
}

/** 라벨/값 한 줄(정보 표, 사양 표). 둘 다 공백 정리된 텍스트이며 비어 있지 않다. */
data class LabeledValue(val label: String, val value: String)

/** 설문 결과. [totalVotes]는 사이트가 표시한 총투표수, 없으면 항목 득표 합. */
data class Poll(val question: String?, val options: List<PollOption>, val totalVotes: Int)

data class PollOption(val label: String, val votes: Int)
