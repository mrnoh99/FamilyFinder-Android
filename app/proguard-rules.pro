# FamilyFinder R8/ProGuard rules.
#
# Compose, Room, Coil, Navigation, kotlinx-coroutines 등은 각 라이브러리가
# consumer ProGuard 규칙을 함께 배포하므로 AGP가 자동 적용한다. 아래는 그 위에
# 보수적으로 추가하는 안전망 규칙이다.
#
# ⚠️ 주의: 이 규칙은 빌드(R8) 통과까지만 검증했다. 릴리스 빌드의 실제 동작은
#         반드시 기기에서 확인한 뒤 배포할 것.

# ── Kotlin metadata (리플렉션/직렬화 안전) ──
-keepclassmembers class kotlin.Metadata { *; }

# ── Room ──
# Room이 생성하는 코드는 엔티티/DAO의 필드·메서드를 참조하므로 데이터 계층 전체를 보존한다.
# (작은 앱이라 data 패키지 전체 보존이 가장 안전하다.)
-keep class com.familyfinder.data.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# ── Coroutines (라이브러리 consumer 규칙 보완) ──
-dontwarn kotlinx.coroutines.**

# ── Coil ──
-dontwarn coil.**
