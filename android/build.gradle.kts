/*
 * 아로새김(Arosaegim) — 최상위 빌드 스크립트.
 * 플러그인 선언만 담당하며, 실제 적용은 각 모듈(build.gradle.kts)에서 수행한다.
 */

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
