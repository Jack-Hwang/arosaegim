package app.arosaegim.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/*
 * 주행·정차 화면 분리 계약 시험(P2 — 안전, 2026-11-14).
 *
 * '주행 중 퀴즈 미노출'의 보증 구조는 두 층이다(ADR-07):
 *  1) 매니페스트 — DrivingActivity 만 distractionOptimized 를 선언하고, 퀴즈 화면을 담는
 *     ParkedActivity 는 선언하지 않아 주행 중 시스템이 가린다.
 *  2) 소스 — 퀴즈 UI(ui.parked)는 주행 화면 소스(DrivingActivity·DrivingScreen)에서
 *     참조하지 않는다.
 * 실제 파일을 읽는 계약 시험이다(SeedRepositoryTest 가 실제 시드 파일을 읽는 것과 같은 방식 —
 * 파일이 없는 환경에서는 건너뛴다).
 */
class DrivingScreenContractTest {

    /** 단위 시험 작업 디렉터리는 모듈(android/app) 기준 — 저장소 루트 실행 대비 두 후보를 본다. */
    private fun manifestText(): String? {
        for (path in listOf("src/main/AndroidManifest.xml", "android/app/src/main/AndroidManifest.xml")) {
            val f = File(path)
            if (f.exists()) return f.readText()
        }
        return null
    }

    /** 매니페스트에서 해당 Activity 의 <activity …> 블록을 잘라 낸다. */
    private fun activityBlock(manifest: String, className: String): String {
        val nameLiteral = "android:name=\"app.arosaegim.$className\""
        val start = manifest.indexOf(nameLiteral)
        check(start >= 0) { "매니페스트에 $className 이 없다" }
        val blockStart = manifest.lastIndexOf("<activity", start)
        val end = listOf(manifest.indexOf("</activity>", start), manifest.indexOf("/>", start))
            .filter { it > start }
            .min()
        return manifest.substring(blockStart, end)
    }

    @Test
    fun `주행 화면만 주행 최적화를 선언하고 퀴즈 화면은 선언하지 않는다`() {
        val manifest = manifestText() ?: return
        val driving = activityBlock(manifest, "ui.driving.DrivingActivity")
        val parked = activityBlock(manifest, "ui.parked.ParkedActivity")
        assertTrue(
            "DrivingActivity 는 distractionOptimized=true 를 선언해야 한다",
            driving.contains("distractionOptimized") && driving.contains("android:value=\"true\""),
        )
        assertFalse(
            "ParkedActivity 는 distractionOptimized 를 선언하지 않아야 한다(주행 중 시스템이 가린다)",
            parked.contains("distractionOptimized"),
        )
    }

    @Test
    fun `퀴즈 UI 소스는 주행 화면에서 참조하지 않는다`() {
        for (path in listOf(
            "src/main/java/app/arosaegim/ui/driving/DrivingActivity.kt",
            "src/main/java/app/arosaegim/ui/driving/DrivingScreen.kt",
        )) {
            val f = File(path)
            if (!f.exists()) continue
            val text = f.readText()
            assertFalse("$path 는 퀴즈 화면(ui.parked)을 참조하지 않아야 한다", text.contains("ui.parked"))
            assertFalse("$path 는 Quiz 를 참조하지 않아야 한다", text.contains("Quiz"))
        }
    }
}
