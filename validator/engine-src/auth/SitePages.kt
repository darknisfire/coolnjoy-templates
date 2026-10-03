package bateaux.spt.coolnjoy.core.auth

/**
 * 그누보드(BS4-Basic) 응답 내용 판별. 마커는 2026-10-03 실사이트 응답에서 확인했다(docs/PLAN.md §2.2).
 *
 * - 로그인 상태: 모든 페이지 head의 `var g5_is_member = "1"`(비로그인은 `""`), 헤더 `#hd_login_msg` 안의 `/bbs/logout.php` 링크.
 * - 오류 페이지(alert 후 JS 이동): 본문에 `<script>alert("..."); document.location.replace("...")</script>`와
 *   `<div id="validation_check">`(noscript 대체 본문). HTTP 상태는 200이다.
 * - 세션(PHPSESSID) 없이 auto_login 쿠키만 있으면 첫 응답이 `<script> window.location.reload(); </script>` 한 줄이다.
 */
internal object SitePages {
    private val memberVar = Regex("""g5_is_member\s*=\s*"([^"]*)"""")
    private val loginMsgLogout = Regex("""id="hd_login_msg"[^>]*>[\s\S]{0,400}?href="[^"]*/bbs/logout\.php""")
    private val reloadStub = Regex("""^\s*<script[^>]*>\s*window\.location\.reload\(\s*\)\s*;?\s*</script>\s*$""", RegexOption.IGNORE_CASE)
    private val alertCall = Regex("""alert\(\s*(?:"((?:[^"\\]|\\.)*)"|'((?:[^'\\]|\\.)*)')\s*\)""")
    private val jsRedirect = Regex("""location\s*(?:\.\s*replace\s*\(|\.\s*href\s*=|=)\s*(?:"([^"]*)"|'([^']*)')""")

    /** 로그인 후 페이지 마커. 쿠키 개수가 아니라 내용으로 판정한다. */
    fun isLoggedIn(html: String): Boolean {
        val member = memberVar.find(html)?.groupValues?.get(1)
        if (!member.isNullOrBlank()) return true
        return loginMsgLogout.containsMatchIn(html)
    }

    fun isReloadStub(html: String): Boolean = html.length < 400 && reloadStub.matches(html)

    fun isErrorPage(html: String): Boolean = html.contains("id=\"validation_check\"")

    /** 오류 페이지의 alert 문구(JS 이스케이프 해제). 오류 페이지가 아니면 null. */
    fun errorMessage(html: String): String? {
        if (!isErrorPage(html)) return null
        return alertMessage(html)
    }

    /** 첫 `alert("...")`의 문구. 로그인 실패 응답처럼 오류 페이지 판별 없이 쓸 때. */
    fun alertMessage(html: String): String? {
        val m = alertCall.find(html) ?: return null
        val raw = m.groups[1]?.value ?: m.groups[2]?.value ?: return null
        return unescapeJs(raw).trim().ifEmpty { null }
    }

    /** 오류 페이지 JS 이동 대상(`location.replace("...")`, `location.href = "..."`)의 첫 값. 템플릿 엔진의 오류 판정용. */
    fun redirectTarget(html: String): String? =
        jsRedirect.find(html)?.let { it.groups[1]?.value ?: it.groups[2]?.value }

    /** 로그인 페이지 자체이거나, 로그인 안내 alert/이동을 가진 오류 페이지인가. */
    fun isLoginRequired(html: String): Boolean {
        if (html.contains("name=\"flogin\"")) return true
        if (!isErrorPage(html)) return false
        val message = alertMessage(html)
        if (message != null && message.contains("로그인")) return true
        val target = jsRedirect.find(html)?.let { it.groups[1]?.value ?: it.groups[2]?.value }
        return target != null && target.contains("/bbs/login.php")
    }

    fun unescapeJs(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\' || i + 1 >= s.length) {
                out.append(c); i++; continue
            }
            val n = s[i + 1]
            when (n) {
                'n' -> { out.append('\n'); i += 2 }
                'r' -> { out.append('\r'); i += 2 }
                't' -> { out.append('\t'); i += 2 }
                'u' -> {
                    val hex = if (i + 6 <= s.length) s.substring(i + 2, i + 6) else null
                    val cp = hex?.toIntOrNull(16)
                    if (cp != null) { out.append(cp.toChar()); i += 6 } else { out.append('u'); i += 2 }
                }
                'x' -> {
                    val hex = if (i + 4 <= s.length) s.substring(i + 2, i + 4) else null
                    val cp = hex?.toIntOrNull(16)
                    if (cp != null) { out.append(cp.toChar()); i += 4 } else { out.append('x'); i += 2 }
                }
                else -> { out.append(n); i += 2 }
            }
        }
        return out.toString()
    }
}
