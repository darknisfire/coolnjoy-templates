# 엔진 스냅샷

`validator/engine-src/` 는 앱 저장소 core 의 템플릿 엔진 소스를 복사한 것이다. 직접 수정하지 말고 `node scripts/sync-engine.mjs <앱 core 경로>` 로 갱신한다.

- 출처 경로: `D:/Projects/CoolnJoy-Simple-App/core` (`src/main/kotlin/bateaux/spt/coolnjoy/core`)
- 복사 시각: 2026-10-03T05:34:27.075Z
- ENGINE_VERSION: 2
- 앱 저장소 커밋: f21b264f7e09823f2746d02cf05ed3e3ac37ceb7 (작업 트리에 미커밋 변경 있음)

## 파일

| 파일 | sha256 |
|---|---|
| template/Op.kt | 6d0a3d8159ada960b6779cf299b2c6f09824d80c44cc85209bc94cc53d17af55 |
| template/Schema.kt | d7e2f82dd5252ebbc341ccc2ed47b56000953b050040b0f96140ee57705532eb |
| template/TemplateArticle.kt | 80bccd2da5ae96ef6992dc183ee9102cc31690baecaf14fca717288ca1a6a611 |
| template/TemplateEngine.kt | f9ac1edab71abf45b2fa9e13b79313caf17036d6fb9705d5069ebd1d8cb209b6 |
| template/TemplateException.kt | 4eaec852a79f82c32209fd9233f2b2e3f494f39052683e8f6926b7b6ed5ba7bc |
| template/TemplateLoader.kt | d0d1c07ce0bbd88b97eb675d9c8aec7fdbaba58a7e1e1418ae2f119f76673040 |
| model/Article.kt | b757401f2f9e3005f11974772ac5d26c769032ca7b9aff5460061458eeae2b7a |
| model/ArticleResult.kt | c1d45b152620df4cbc7b39bd5a5415d59f47a2616e202f129397830dae94161a |
| model/BoardLayout.kt | 474801ac8f4b4509be1929eb9768f6f65bce6121f31fbf48af24d2c1f55b13f6 |
| model/CommentItem.kt | caaa06fe807d1b501ea61ff3ecf13d39703a533aece69f1e20fc494e1bf84d91 |
| model/ListItem.kt | cd93a9d99d04329bbc91733279aa2f834efd6a662309d104bf7e794420e6d4d5 |
| parse/DateNormalizer.kt | 0d87f08e63f8562af08527b0d7ef9680e5e665b4cf62c406f00028c1c99b046f |
| parse/ParseResult.kt | 19d63ff66da6a0cae30b28012e39836ee6dfaebb379913df5ef62dbbfe346905 |
| parse/ContentSanitizer.kt | a9a995f714774543e4a512a371765d137ca6f70bd90d8e3ff9e34f8da0c04b2e |
| auth/SitePages.kt | 09d902d4d25b692d3ee04a2c1736eb23fea76b34e047753d57376e16584af66f |
| site/ArticleUrls.kt | 274c9d11c1315fb4df4051c6404e1b4130fc200cdc1df978228feff2fc834718 |
| site/SiteConfig.kt | 5fd04f56b2536d0bdca21e142bc089ad337ae0eb8ebf4de7d40e31fb5937c683 |
| site/Board.kt | a9c62eebd6d6294485476747e12513ccbb5eb156c61c12de32651d7406bac940 |
| parse/RowListParser.kt | a21702e66675f5cb0c7978cfa274712a71e6244852342c13410136c28a839229 |

템플릿의 `minEngineVersion` 은 이 ENGINE_VERSION 이하여야 검증 테스트가 통과한다.
