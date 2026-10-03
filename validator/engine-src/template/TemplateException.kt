package bateaux.spt.coolnjoy.core.template

/** 템플릿 JSON이 스키마/연산/엔진 버전 규칙에 맞지 않을 때 던진다. 메시지에 문제 위치(경로)를 포함한다. */
class TemplateException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
