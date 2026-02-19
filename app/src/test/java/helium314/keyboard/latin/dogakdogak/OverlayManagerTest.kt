// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dogakdogak

import org.junit.After
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * OverlayManager 싱글톤 라이프사이클 테스트
 *
 * 테스트 시나리오:
 * 1. clearInstance 호출 후 싱글톤 null 확인
 * 2. getInstance가 clearInstance 후 null 반환
 */
class OverlayManagerTest {

    @After
    fun cleanup() {
        OverlayManager.clearInstance()
    }

    @Test
    fun clearInstance_setsInstanceToNull() {
        // clearInstance 호출 후 getInstance는 null이어야 함
        OverlayManager.clearInstance()
        assertNull(OverlayManager.getInstance())
    }

    @Test
    fun getInstance_withoutCreation_returnsNull() {
        OverlayManager.clearInstance()
        assertNull(
            OverlayManager.getInstance(),
            "getInstance should return null when no instance has been created"
        )
    }
}
