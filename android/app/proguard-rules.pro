# 아로새김(Arosaegim) ProGuard/R8 규칙
#
# 현재는 빈 기본값(minifyEnabled=false). 릴리스 압축 활성화 시 아래 방향으로 정리 필요.
#
# TODO(릴리스 시 정리, 규칙 미확정):
# Pleos Playground SDK(ai.pleos.playground)의 리플렉션/직렬화 사용 여부를
# 확인한 뒤 keep 규칙을 확정한다. 예시(확정 전 주석):
#
# -keep class ai.pleos.playground.** { *; }
# -keep class ai.pleos.playground.**$* { *; }
#
# kotlinx.serialization 사용 시 (kotlinx-serialization-json 공식 권장 규칙 참고):
# -keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
# -classeswithmembers,allowoptimization class * { @kotlinx.serialization.Serializable <methods>; }
