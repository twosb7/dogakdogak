package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * 저지연 오디오 재생을 위한 AudioEngine
 *
 * SoundPool을 사용하여 모든 스위치 사운드 변형을 메모리에 미리 로드.
 * 스위치당 3~8개 변형을 랜덤으로 재생하여 자연스러운 사운드 제공.
 * SoundPool.play()는 thread-safe & non-blocking이므로 호출 스레드에서 직접 실행.
 */
class AudioEngine(private val context: Context) {

    companion object {
        private const val MAX_STREAMS = 32
        private const val PLAY_PRIORITY = 10
    }

    private var soundPool: SoundPool? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    // SwitchType → [soundId1, soundId2, ...]
    private val switchToSoundIds = ConcurrentHashMap<SwitchType, IntArray>()
    private val loadedSounds = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())

    @Volatile
    private var currentSwitch: SwitchType = SwitchType.getDefaultSwitch()

    @Volatile
    var volume: Float = 1.0f

    init {
        initializeSoundPool()
        loadAllSounds()
    }

    private fun initializeSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool?.setOnLoadCompleteListener { _, soundId, status ->
            if (status == 0) {
                loadedSounds.add(soundId)
            }
        }
    }

    private fun loadAllSounds() {
        SwitchType.entries.forEach { switchType ->
            val resolvedResIds = switchType.resolveSoundResIds(context)
            val ids = ArrayList<Int>(resolvedResIds.size)
            resolvedResIds.forEach { resId ->
                val soundId = soundPool?.load(context, resId, 1) ?: return@forEach
                if (soundId == 0) return@forEach
                ids.add(soundId)
            }
            switchToSoundIds[switchType] = ids.toIntArray()
        }
    }

    fun setCurrentSwitch(switchType: SwitchType) {
        currentSwitch = switchType
    }

    /**
     * 현재 선택된 스위치의 클릭 사운드를 즉시 재생.
     * 호출 스레드에서 직접 실행 (SoundPool.play()는 non-blocking).
     */
    fun playClick() {
        playSwitchSoundInternal(currentSwitch, fallbackEffect = AudioManager.FX_KEYPRESS_STANDARD)
    }

    /**
     * 삭제(Backspace) 사운드 재생 — 높은 피치로 입력음과 청각적 구분.
     */
    fun playDelete() {
        playSwitchSoundInternal(
            currentSwitch,
            pitchOverride = 1.35f,
            fallbackEffect = AudioManager.FX_KEYPRESS_DELETE
        )
    }

    /**
     * 공백(Space) 사운드 재생 — 낮은 피치로 단어 경계 청각 피드백.
     */
    fun playSpace() {
        playSwitchSoundInternal(
            currentSwitch,
            pitchOverride = 0.75f,
            fallbackEffect = AudioManager.FX_KEYPRESS_SPACEBAR
        )
    }

    /**
     * 엔터 사운드 재생 — 약간 낮은 피치.
     */
    fun playEnter() {
        playSwitchSoundInternal(
            currentSwitch,
            pitchOverride = 0.85f,
            fallbackEffect = AudioManager.FX_KEYPRESS_RETURN
        )
    }

    /** 특정 스위치 사운드 재생 (미리듣기용) */
    fun playSwitchSound(switchType: SwitchType) {
        playSwitchSoundInternal(switchType, fallbackEffect = AudioManager.FX_KEYPRESS_STANDARD)
    }

    /** pitchOverride가 null이면 0.95~1.05 랜덤 피치 사용. */
    private fun playSwitchSoundInternal(
        switchType: SwitchType,
        pitchOverride: Float? = null,
        fallbackEffect: Int
    ) {
        val pool = soundPool
        val ids = switchToSoundIds[switchType] ?: return
        if (ids.isEmpty()) {
            playSystemFallback(fallbackEffect)
            return
        }
        val soundId = pickLoadedSoundId(ids)
        if (pool == null || soundId == null) {
            playSystemFallback(fallbackEffect)
            return
        }

        val rate = pitchOverride ?: (0.95f + Random.nextFloat() * 0.10f)
        val gain = (volume * 2f * switchType.volumeBoost).coerceIn(0f, 2f)

        pool.play(soundId, gain, gain, PLAY_PRIORITY, 0, rate)
    }

    private fun playSystemFallback(effect: Int) {
        val playbackVolume = volume.coerceIn(0f, 1f)
        if (playbackVolume <= 0f) return
        audioManager?.playSoundEffect(effect, playbackVolume)
    }

    private fun pickLoadedSoundId(ids: IntArray, exclude: Int? = null): Int? {
        repeat(ids.size) {
            val candidate = ids[Random.nextInt(ids.size)]
            if (candidate != exclude && loadedSounds.contains(candidate)) return candidate
        }
        return ids.firstOrNull { it != exclude && loadedSounds.contains(it) }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        switchToSoundIds.clear()
        loadedSounds.clear()
    }
}
