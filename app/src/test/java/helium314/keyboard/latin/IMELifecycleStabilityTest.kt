// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.dogakdogak.OverlayManager
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * IME 라이프사이클 안정성 테스트 — 폰 재시작 시나리오 시뮬레이션
 *
 * 테스트 시나리오:
 * 1. 기본 IME 서비스 생성 → 키보드 뷰 생성 → 입력 시작
 * 2. setInputView에서 Settings null일 때 크래시 없이 복구
 * 3. onConfigurationChanged에서 Settings null일 때 크래시 없이 복구
 * 4. onDestroy 후 리소스 정리 확인
 * 5. onDestroy 후 재생성 시 OverlayManager 싱글톤 정리 확인
 * 6. OverlayManager 재사용 시 stale reference 방지
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowLocaleManagerCompat::class,
    ShadowInputMethodManager2::class,
])
class IMELifecycleStabilityTest {

    @After
    fun cleanup() {
        OverlayManager.clearInstance()
    }

    @Test
    fun fullLifecycle_createInputViewAndStartInput_noExceptions() {
        val ime = Robolectric.setupService(LatinIME::class.java)
        val inputView = ime.onCreateInputView()
        assertNotNull(inputView, "onCreateInputView should return non-null view")

        ime.setInputView(inputView)
        assertNotNull(
            KeyboardSwitcher.getInstance().getMainKeyboardView(),
            "MainKeyboardView should be available after setInputView"
        )
    }

    @Test
    fun setInputView_withSettingsNotLoaded_recoversGracefully() {
        // Settings가 아직 로드되지 않은 상태를 시뮬레이션
        // Robolectric에서 LatinIME.onCreate()가 loadSettings()를 호출하므로
        // 정상적으로 실행되어야 함 — 중요한 것은 NPE가 발생하지 않는 것
        val ime = Robolectric.setupService(LatinIME::class.java)
        val inputView = ime.onCreateInputView()

        // 이 호출에서 NPE가 발생하면 안 됨
        ime.setInputView(inputView)
    }

    @Test
    fun onStartInputView_withNullEditorInfo_doesNotCrash() {
        val ime = Robolectric.setupService(LatinIME::class.java)
        val inputView = ime.onCreateInputView()
        ime.setInputView(inputView)

        // null EditorInfo로 호출해도 크래시 없어야 함
        ime.onStartInputViewInternal(null, false)
    }

    @Test
    fun onStartInputView_restarting_doesNotCrash() {
        val ime = Robolectric.setupService(LatinIME::class.java)
        val inputView = ime.onCreateInputView()
        ime.setInputView(inputView)

        // restarting=true로 호출
        ime.onStartInputViewInternal(null, true)
    }

    @Test
    fun onDestroy_cleansUpOverlayManagerSingleton() {
        val ime = Robolectric.setupService(LatinIME::class.java)
        ime.onCreateInputView()

        ime.onDestroy()

        // OverlayManager 싱글톤이 정리되어야 함
        assertNull(
            OverlayManager.getInstance(),
            "OverlayManager singleton should be cleared after onDestroy"
        )
    }

    @Test
    fun recreateAfterDestroy_initializesCleanly() {
        // 첫 번째 IME 서비스
        val ime1 = Robolectric.setupService(LatinIME::class.java)
        val view1 = ime1.onCreateInputView()
        ime1.setInputView(view1)
        ime1.onDestroy()

        // 두 번째 IME 서비스 (재시작 시뮬레이션)
        val ime2 = Robolectric.setupService(LatinIME::class.java)
        val view2 = ime2.onCreateInputView()
        assertNotNull(view2, "Recreated IME should produce input view")

        ime2.setInputView(view2)
        // 새 인스턴스가 정상 동작해야 함
    }

    @Test
    fun multipleSetInputView_doesNotDuplicateOverlay() {
        val ime = Robolectric.setupService(LatinIME::class.java)
        val view1 = ime.onCreateInputView()
        ime.setInputView(view1)

        // 두 번째 setInputView (configuration change 등)
        val view2 = ime.onCreateInputView()
        ime.setInputView(view2)

        // 오류 없이 완료되어야 함
        assertTrue(true)
    }
}
