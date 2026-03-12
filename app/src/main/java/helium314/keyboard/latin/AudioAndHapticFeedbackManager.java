/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

import helium314.keyboard.event.HapticEvent;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.latin.dogakdogak.AudioEngine;
import helium314.keyboard.latin.dogakdogak.AppClickCountRepository;
import helium314.keyboard.latin.dogakdogak.ClickCountRepository;
import helium314.keyboard.latin.dogakdogak.ComboCalculator;
import helium314.keyboard.latin.dogakdogak.ComboMilestone;
import helium314.keyboard.latin.dogakdogak.ComboTier;
import helium314.keyboard.latin.dogakdogak.OverlayManager;
import helium314.keyboard.latin.dogakdogak.PrefsKeys;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import helium314.keyboard.latin.dogakdogak.SwitchType;
import helium314.keyboard.latin.settings.SettingsValues;
import helium314.keyboard.latin.utils.DeviceProtectedUtils;

/**
 * This class gathers audio feedback and haptic feedback functions.
 * <p>
 * It offers a consistent and simple interface that allows LatinIME to forget about the
 * complexity of settings and the like.
 */
public final class AudioAndHapticFeedbackManager {
    private AudioManager mAudioManager;
    private Vibrator mVibrator;
    private AudioEngine mAudioEngine;

    // 콤보 이펙트
    private final ComboCalculator mComboCalculator = new ComboCalculator();
    private OverlayManager mOverlayManager;
    private boolean mComboEnabled = true;
    private static final int BASE_SCORE = 20;
    private static final double LUCKY_STRIKE_CHANCE = 0.08;
    private final Random mRandom = new Random();

    // 카운터
    private ClickCountRepository mClickCountRepo;
    private AppClickCountRepository mAppClickCountRepo;
    private volatile String mCurrentAppPackage;
    private android.content.SharedPreferences mPrefs;
    private final Handler mCounterFlushHandler = new Handler(Looper.getMainLooper());
    private boolean mCounterFlushScheduled = false;
    private long mPendingScoreDelta = 0L;
    private long mPendingTouchDelta = 0L;
    private final HashMap<String, Long> mPendingAppScoreDeltas = new HashMap<>();
    private final HashMap<String, Long> mPendingAppTouchDeltas = new HashMap<>();

    private SettingsValues mSettingsValues;
    private boolean mSoundOn;
    private boolean mDoNotDisturb;

    private static final AudioAndHapticFeedbackManager sInstance =
            new AudioAndHapticFeedbackManager();

    public static AudioAndHapticFeedbackManager getInstance() {
        return sInstance;
    }

    private AudioAndHapticFeedbackManager() {
        // Intentional empty constructor for singleton.
    }

    public static void init(final Context context) {
        sInstance.initInternal(context);
    }

    private void initInternal(final Context context) {
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        // Device-protected SharedPreferences (Direct Boot safe)
        var prefs = DeviceProtectedUtils.getSharedPreferences(context);
        mPrefs = prefs;
        // AudioEngine 초기화 — Direct Boot 중 실패해도 키보드 서비스는 유지
        try {
            mAudioEngine = new AudioEngine(context);
            String switchName = prefs.getString("dogakdogak_switch_type", SwitchType.PEBBLE_1.name());
            try {
                mAudioEngine.setCurrentSwitch(SwitchType.valueOf(switchName));
            } catch (IllegalArgumentException e) {
                mAudioEngine.setCurrentSwitch(SwitchType.PEBBLE_1);
            }
            float volume = prefs.getFloat("dogakdogak_volume", 0.5f);
            mAudioEngine.setVolume(volume);
        } catch (Exception e) {
            android.util.Log.w("dogakdogak", "AudioEngine init failed (Direct Boot?)", e);
            mAudioEngine = null;
        }
        // ClickCountRepository 초기화 — Direct Boot 중 실패해도 키보드 서비스는 유지
        try {
            mClickCountRepo = ClickCountRepository.Companion.getInstance(context);
        } catch (Exception e) {
            android.util.Log.w("dogakdogak", "ClickCountRepository init failed (Direct Boot?)", e);
            mClickCountRepo = null;
        }
        // AppClickCountRepository 초기화
        try {
            mAppClickCountRepo = AppClickCountRepository.Companion.getInstance(context);
        } catch (Exception e) {
            android.util.Log.w("dogakdogak", "AppClickCountRepository init failed (Direct Boot?)", e);
            mAppClickCountRepo = null;
        }
    }

    public AudioEngine getAudioEngine() {
        return mAudioEngine;
    }

    public void setCurrentAppPackage(String packageName) {
        mCurrentAppPackage = packageName;
    }

    public void setOverlayManager(OverlayManager manager) {
        mOverlayManager = manager;
    }

    public OverlayManager getOverlayManager() {
        return mOverlayManager;
    }

    public void setComboEnabled(boolean enabled) {
        mComboEnabled = enabled;
    }

    public ComboCalculator getComboCalculator() {
        return mComboCalculator;
    }

    public void performHapticAndAudioFeedback(
        final int code,
        final View viewToPerformHapticFeedbackOn,
        final HapticEvent hapticEvent
    ) {
        performHapticFeedback(viewToPerformHapticFeedbackOn, hapticEvent);
        performAudioFeedback(code, hapticEvent);
    }

    public boolean hasVibrator() {
        return mVibrator != null && mVibrator.hasVibrator();
    }

    public void vibrate(final long milliseconds) {
        if (mVibrator == null || milliseconds <= 0) {
            return;
        }
        mVibrator.vibrate(milliseconds);
    }

    private boolean reevaluateIfSoundIsOn() {
        if (mSettingsValues == null || !mSettingsValues.mSoundOn || mAudioManager == null || mDoNotDisturb) {
            return false;
        }
        return mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL;
    }

    public void performAudioFeedback(final int code, final HapticEvent hapticEvent) {
        if (hapticEvent != HapticEvent.KEY_PRESS) {
            return;
        }
        // ASMR 도각도각 사운드: 무음/진동 모드에서는 각각 설정에 따라 재생
        // 단, 자사 앱 내 미리보기에서는 항상 재생 (모드 설정 무시)
        boolean shouldPlayAsmr = mAudioEngine != null && mAudioEngine.getVolume() > 0f;
        boolean isOwnApp = "com.dogakdogak.keyboard".equals(mCurrentAppPackage);
        if (shouldPlayAsmr && !isOwnApp && mAudioManager != null) {
            int ringerMode = mAudioManager.getRingerMode();
            if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
                String silentBehavior = mPrefs != null
                        ? mPrefs.getString("dogakdogak_silent_mode_behavior", null) : null;
                if (silentBehavior == null) {
                    // 이전 boolean 설정 호환
                    shouldPlayAsmr = mPrefs != null
                            && mPrefs.getBoolean("dogakdogak_sound_in_silent", true);
                } else if ("sound_on".equals(silentBehavior)) {
                    shouldPlayAsmr = true;
                } else {
                    shouldPlayAsmr = false;
                    if ("vibrate_only".equals(silentBehavior)) {
                        vibrate(mSettingsValues != null && mSettingsValues.mKeypressVibrationDuration >= 0
                                ? mSettingsValues.mKeypressVibrationDuration : 20);
                    }
                }
            } else if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                shouldPlayAsmr = mPrefs != null
                        && mPrefs.getBoolean("dogakdogak_sound_in_vibrate", true);
            }
        }
        if (shouldPlayAsmr) {
            switch (code) {
                case KeyCode.DELETE:
                    mAudioEngine.playDelete();
                    break;
                case Constants.CODE_ENTER:
                    mAudioEngine.playEnter();
                    break;
                case Constants.CODE_SPACE:
                    mAudioEngine.playSpace();
                    break;
                default:
                    mAudioEngine.playClick();
                    break;
            }
        } else {
            // fallback: 시스템 사운드 (AudioEngine 없거나 볼륨 0일 때)
            if (mSoundOn && mAudioManager != null && mSettingsValues != null) {
                final int sound = switch (code) {
                    case KeyCode.DELETE -> AudioManager.FX_KEYPRESS_DELETE;
                    case Constants.CODE_ENTER -> AudioManager.FX_KEYPRESS_RETURN;
                    case Constants.CODE_SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR;
                    default -> AudioManager.FX_KEYPRESS_STANDARD;
                };
                mAudioManager.playSoundEffect(sound, mSettingsValues.mKeypressSoundVolume);
            }
        }

        // DELETE 키: 정확도 추적 (콤보 이펙트는 제외)
        if (mComboEnabled && code == KeyCode.DELETE) {
            mComboCalculator.onDelete();
        }

        // 콤보 이펙트 업데이트 (DELETE 키 제외)
        if (mComboEnabled && mOverlayManager != null && code != KeyCode.DELETE) {
            ComboTier tier = mComboCalculator.onClick();
            int combo = mComboCalculator.getComboStreak();
            int rawScore = BASE_SCORE * tier.getSpeedMultiplier();
            double comboMultiplier = 1.0 + 0.14 * Math.pow(combo, 0.4);
            // 정확도 가중치: 삭제 비율이 높을수록 점수 감소 (최소 0.5배)
            double accuracyMultiplier = mComboCalculator.getAccuracyMultiplier();
            int score = (int) (rawScore * comboMultiplier * accuracyMultiplier);
            // 랜덤 변동: ±100 범위
            score += mRandom.nextInt(201) - 100;
            if (score < 1) score = 1;
            // 마일스톤 보너스 (일회성)
            score += ComboMilestone.Companion.getBonusScore(combo);
            // 럭키 스트라이크: 8% 확률로 점수 2배
            boolean luckyStrike = mRandom.nextDouble() < LUCKY_STRIKE_CHANCE;
            if (luckyStrike) score *= 2;
            boolean scoreMode = mPrefs != null && "score".equals(mPrefs.getString("dogakdogak_counter_mode", "score"));
            // Touch 모드에서는 팝업에 +1 표시 (스코어 팝업이 아닌 타수 팝업)
            mOverlayManager.onKeyPress(scoreMode ? score : 1, combo, luckyStrike);

            // 프리미엄/핑크큐티 콤보 햅틱: 콤보가 높아질수록 강해짐
            if ((mOverlayManager.getPremiumEffects() || mOverlayManager.getCutiePinkComboEffects() || mOverlayManager.getArcadeEffects()) && mVibrator != null && mVibrator.hasVibrator()) {
                int amplitude;  // 1-255
                long duration;  // ms
                if (combo >= 500) {
                    amplitude = 255; duration = 30;
                } else if (combo >= 200) {
                    amplitude = 200; duration = 25;
                } else if (combo >= 100) {
                    amplitude = 150; duration = 20;
                } else if (combo >= 50) {
                    amplitude = 100; duration = 15;
                } else if (combo >= 20) {
                    amplitude = 60; duration = 10;
                } else if (combo >= 6) {
                    amplitude = 35; duration = 8;
                } else {
                    amplitude = 0; duration = 0;
                }
                if (amplitude > 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mVibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude));
                    } else {
                        mVibrator.vibrate(duration);
                    }
                }
            }

            // Score/Touch 카운터 업데이트 (설정 모드에 따라 하나만 기록)
            if (mClickCountRepo != null) {
                if (scoreMode) {
                    mPendingScoreDelta += score;
                } else {
                    mPendingTouchDelta += 1;
                }
                // 앱별 카운터 업데이트
                String pkg = mCurrentAppPackage;
                boolean appTrackingAllowed = mPrefs != null
                        && mPrefs.getBoolean(PrefsKeys.RANKING_DISCLOSURE_ACCEPTED, false);
                if (mAppClickCountRepo != null && pkg != null
                        && appTrackingAllowed
                        && AppClickCountRepository.TRACKED_PACKAGES.contains(pkg)) {
                    if (scoreMode) {
                        mPendingAppScoreDeltas.put(pkg,
                                mPendingAppScoreDeltas.getOrDefault(pkg, 0L) + score);
                    } else {
                        mPendingAppTouchDeltas.put(pkg,
                                mPendingAppTouchDeltas.getOrDefault(pkg, 0L) + 1L);
                    }
                }
                scheduleCounterFlush();
                // 오버레이 카운터 갱신
                long displayCount = scoreMode
                    ? mClickCountRepo.getTotalScore().getValue() + mPendingScoreDelta
                    : mClickCountRepo.getTotalTouches().getValue() + mPendingTouchDelta;
                mOverlayManager.updateCount(displayCount);
            }
        }
    }

    /** Plays ASMR sound only, without updating combo/score counters.
     *  Used for hardware keyboard key repeats (e.g. holding Backspace/Space). */
    public void performAudioFeedbackOnly(final int code) {
        boolean shouldPlayAsmr = mAudioEngine != null && mAudioEngine.getVolume() > 0f;
        boolean isOwnApp = "com.dogakdogak.keyboard".equals(mCurrentAppPackage);
        if (shouldPlayAsmr && !isOwnApp && mAudioManager != null) {
            int ringerMode = mAudioManager.getRingerMode();
            if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
                String silentBehavior = mPrefs != null
                        ? mPrefs.getString("dogakdogak_silent_mode_behavior", null) : null;
                if (silentBehavior == null) {
                    shouldPlayAsmr = mPrefs != null
                            && mPrefs.getBoolean("dogakdogak_sound_in_silent", true);
                } else if (!"sound_on".equals(silentBehavior)) {
                    shouldPlayAsmr = false;
                }
            } else if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                shouldPlayAsmr = mPrefs != null
                        && mPrefs.getBoolean("dogakdogak_sound_in_vibrate", true);
            }
        }
        if (shouldPlayAsmr) {
            switch (code) {
                case KeyCode.DELETE:
                    mAudioEngine.playDelete();
                    break;
                case Constants.CODE_ENTER:
                    mAudioEngine.playEnter();
                    break;
                case Constants.CODE_SPACE:
                    mAudioEngine.playSpace();
                    break;
                default:
                    mAudioEngine.playClick();
                    break;
            }
        }
    }

    public void performHapticFeedback(final View viewToPerformHapticFeedbackOn, final HapticEvent hapticEvent) {
        if (mSettingsValues == null) return;
        if (!mSettingsValues.mVibrateOn || (mDoNotDisturb && !mSettingsValues.mVibrateInDndMode)) {
            return;
        }
        if (hapticEvent == HapticEvent.NO_HAPTICS) {
            // Avoid surprises with the handling of HapticFeedbackConstants.NO_HAPTICS
            return;
        }
        if (hapticEvent.allowCustomDuration && mSettingsValues.mKeypressVibrationDuration >= 0) {
            vibrate(mSettingsValues.mKeypressVibrationDuration);
            return;
        }
        // Go ahead with the system default
        if (viewToPerformHapticFeedbackOn != null) {
            viewToPerformHapticFeedbackOn.performHapticFeedback(
                    hapticEvent.feedbackConstant,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }

    public void onSettingsChanged(final SettingsValues settingsValues) {
        mSettingsValues = settingsValues;
        mSoundOn = reevaluateIfSoundIsOn();
        // AudioEngine 볼륨은 도각도각 자체 설정으로만 관리 (HeliBoard 설정과 독립)
        // mKeypressSoundVolume(-0.01f 기본값)으로 덮어쓰지 않음
    }

    public void onRingerModeChanged(boolean doNotDisturb) {
        mDoNotDisturb = doNotDisturb;
        mSoundOn = reevaluateIfSoundIsOn();
    }

    public void onDestroy() {
        flushPendingCounters();
        mCounterFlushHandler.removeCallbacksAndMessages(null);
        if (mAudioEngine != null) {
            mAudioEngine.release();
            mAudioEngine = null;
        }
    }

    private void scheduleCounterFlush() {
        if (mCounterFlushScheduled) {
            return;
        }
        mCounterFlushScheduled = true;
        mCounterFlushHandler.postDelayed(this::flushPendingCounters, 120L);
    }

    private void flushPendingCounters() {
        mCounterFlushScheduled = false;
        if (mClickCountRepo != null) {
            if (mPendingScoreDelta > 0L) {
                mClickCountRepo.incrementScore(mPendingScoreDelta);
            }
            if (mPendingTouchDelta > 0L) {
                mClickCountRepo.incrementTouch(mPendingTouchDelta);
            }
        }
        if (mAppClickCountRepo != null) {
            for (Map.Entry<String, Long> entry : mPendingAppScoreDeltas.entrySet()) {
                if (entry.getValue() > 0L) {
                    mAppClickCountRepo.incrementAppScore(entry.getKey(), entry.getValue());
                }
            }
            for (Map.Entry<String, Long> entry : mPendingAppTouchDeltas.entrySet()) {
                if (entry.getValue() > 0L) {
                    mAppClickCountRepo.incrementAppTouch(entry.getKey(), entry.getValue());
                }
            }
        }
        mPendingScoreDelta = 0L;
        mPendingTouchDelta = 0L;
        mPendingAppScoreDeltas.clear();
        mPendingAppTouchDeltas.clear();
    }
}
