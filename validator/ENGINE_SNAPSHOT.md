# 엔진 스냅샷

`validator/engine-src/` 는 앱 저장소 core 의 템플릿 엔진 소스를 복사한 것이다. 직접 수정하지 말고 `node scripts/sync-engine.mjs <앱 core 경로>` 로 갱신한다.

- 출처 경로: `D:/Projects/CoolnJoy-Simple-App/core` (`src/main/kotlin/bateaux/spt/coolnjoy/core`)
- 복사 시각: 2026-10-03T10:59:47.762Z
- ENGINE_VERSION: 4
- 앱 저장소 커밋: f21b264f7e09823f2746d02cf05ed3e3ac37ceb7 (작업 트리에 미커밋 변경 있음)

## 파일

| 파일 | sha256 |
|---|---|
| template/Op.kt | 6d0a3d8159ada960b6779cf299b2c6f09824d80c44cc85209bc94cc53d17af55 |
| template/Schema.kt | 59f1e3bea5fa49d0ff5c118b1efd86a0a21dea053bc6cb063e8e943c2897093d |
| template/TemplateArticle.kt | 94aa3c47fc3ef367372812e28c59cab3101197be2ff86e2aaaa331ef46050f36 |
| template/TemplateEngine.kt | a3855b29460e227291ba43bc4585ade2ec31e26b811ac0026a76e5ffc9659b59 |
| template/TemplateException.kt | 4eaec852a79f82c32209fd9233f2b2e3f494f39052683e8f6926b7b6ed5ba7bc |
| template/TemplateLoader.kt | 3149a6e990c1b07f12f3ee6e20ad994c9d26ab82ea2c09862ea56674ed9d6c83 |
| template/TemplateSearch.kt | 8e5c9094f536aeaf1bc093f7c8957ec0f75ed924091ca3d8f1685072fc0232b2 |
| model/Article.kt | 9b5a0a9d444041943359958b84e9f24a5a36a669280ec5b26d8cf07653abaa19 |
| model/ArticleResult.kt | f1b84961dd48a0bef6e43320b7e0fc5ef40c0903346cc844dc8d59664cca3e7e |
| model/BoardLayout.kt | 474801ac8f4b4509be1929eb9768f6f65bce6121f31fbf48af24d2c1f55b13f6 |
| model/CommentItem.kt | 6e0b78db1c1e51c757a8d2f53aaa10d28304036939677191c3b587ffaa865a8a |
| model/ListItem.kt | cd93a9d99d04329bbc91733279aa2f834efd6a662309d104bf7e794420e6d4d5 |
| model/Search.kt | d40bff4d8bb67fceb2fac266833714c32a48b78580603ef34392db4b2d52a32c |
| parse/DateNormalizer.kt | 0d87f08e63f8562af08527b0d7ef9680e5e665b4cf62c406f00028c1c99b046f |
| parse/ParseResult.kt | 19d63ff66da6a0cae30b28012e39836ee6dfaebb379913df5ef62dbbfe346905 |
| parse/ContentSanitizer.kt | a9a995f714774543e4a512a371765d137ca6f70bd90d8e3ff9e34f8da0c04b2e |
| auth/SitePages.kt | 09d902d4d25b692d3ee04a2c1736eb23fea76b34e047753d57376e16584af66f |
| site/ArticleUrls.kt | 274c9d11c1315fb4df4051c6404e1b4130fc200cdc1df978228feff2fc834718 |
| site/SiteConfig.kt | eae36563745f0ae8504299aa1385e61d61b15c2d366bc52cef2a794ab666f982 |
| site/Board.kt | a9c62eebd6d6294485476747e12513ccbb5eb156c61c12de32651d7406bac940 |
| parse/RowListParser.kt | a21702e66675f5cb0c7978cfa274712a71e6244852342c13410136c28a839229 |
| parse/ArticleExtras.kt | 9a0f316baf8b93197ea2fb21ae68353e2db42554ccdbdb2f9deebd0661250dc6 |
| parse/HtmlHelpers.kt | de49d73031d3e6edb8f1d434f9f6ecbe1626c95dc12616012fa2929cf1085226 |
| parse/CodeSearchParser.kt | 622051cf1c11333ce58005198557d081f103f8df1a379cedafbe1962b501b633 |

템플릿의 `minEngineVersion` 은 이 ENGINE_VERSION 이하여야 검증 테스트가 통과한다.
