# 엔진 스냅샷

`validator/engine-src/` 는 앱 저장소 core 의 템플릿 엔진 소스를 복사한 것이다. 직접 수정하지 말고 `node scripts/sync-engine.mjs <앱 core 경로>` 로 갱신한다.

- 출처 경로: `D:/Projects/CoolnJoy-Simple-App/core` (`src/main/kotlin/bateaux/spt/coolnjoy/core`)
- 복사 시각: 2026-10-03T04:59:12.918Z
- ENGINE_VERSION: 1
- 앱 저장소 커밋: f21b264f7e09823f2746d02cf05ed3e3ac37ceb7 (작업 트리에 미커밋 변경 있음)

## 파일

| 파일 | sha256 |
|---|---|
| template/Op.kt | b958bc6f4ebe512a600dbbc2efef006fdb399fa960d11b5b46fae341731d0cc7 |
| template/Schema.kt | 9a3d87384caad238203fddae92deb4bcf5ccf238b18dccd669ab92e291d216db |
| template/TemplateEngine.kt | db3426996f04ef4a9939e0db8f1900bdc53741881d066c647ed5c93177621620 |
| template/TemplateException.kt | 4eaec852a79f82c32209fd9233f2b2e3f494f39052683e8f6926b7b6ed5ba7bc |
| template/TemplateLoader.kt | 5bdcce29644641d0929318bcc1591b0172b14eae6fde6a69bba53f7422d399cd |
| model/Article.kt | b757401f2f9e3005f11974772ac5d26c769032ca7b9aff5460061458eeae2b7a |
| model/ArticleResult.kt | c1d45b152620df4cbc7b39bd5a5415d59f47a2616e202f129397830dae94161a |
| model/BoardLayout.kt | 474801ac8f4b4509be1929eb9768f6f65bce6121f31fbf48af24d2c1f55b13f6 |
| model/CommentItem.kt | caaa06fe807d1b501ea61ff3ecf13d39703a533aece69f1e20fc494e1bf84d91 |
| model/ListItem.kt | cd93a9d99d04329bbc91733279aa2f834efd6a662309d104bf7e794420e6d4d5 |
| parse/DateNormalizer.kt | 0d87f08e63f8562af08527b0d7ef9680e5e665b4cf62c406f00028c1c99b046f |
| parse/ParseResult.kt | 19d63ff66da6a0cae30b28012e39836ee6dfaebb379913df5ef62dbbfe346905 |

템플릿의 `minEngineVersion` 은 이 ENGINE_VERSION 이하여야 검증 테스트가 통과한다.
