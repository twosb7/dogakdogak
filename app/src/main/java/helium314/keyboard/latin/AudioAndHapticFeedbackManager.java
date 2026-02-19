/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin;

import android.content.Context;
import android.media.AudioManager;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

import helium314.keyboard.event.HapticEvent;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.latin.dogakdogak.AudioEngine;
import helium314.keyboard.latin.dogakdogak.ClickCountRepository;
import helium314.keyboard.latin.dogakdogak.ComboCalculator;
import helium314.keyboard.latin.dogakdogak.ComboTier;
import helium314.keyboard.latin.dogakdogak.OverlayManager;
import helium314.keyboard.latin.dogakdogak.SwitchType;
import helium314.keyboard.latin.settings.SettingsValues;

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

    // 카운터
    private ClickCountRepository mClickCountRepo;
    private android.content.SharedPreferences mPrefs;

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
        mAudioEngine = new AudioEngine(context);
        // 저장된 스위치 타입 + 볼륨 + 음소거 로드
        var prefs = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        String switchName = prefs.getString("dogakdogak_switch_type", SwitchType.PEBBLE_1.name());
        try {
            mAudioEngine.setCurrentSwitch(SwitchType.valueOf(switchName));
        } catch (IllegalArgumentException e) {
            mAudioEngine.setCurrentSwitch(SwitchType.PEBBLE_1);
        }
        boolean muted = prefs.getBoolean("dogakdogak_muted", false);
        float volume = prefs.getFloat("dogakdogak_volume", 0.5f);
        mAudioEngine.setVolume(muted ? 0f : volume);
        mClickCountRepo = ClickCountRepository.Companion.getInstance(context);
        mPrefs = prefs;
    }

    public AudioEngine getAudioEngine() {
        return mAudioEngine;
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
        // ASMR 도각도각 사운드: AudioEngine은 항상 재생 (mSoundOn 무관)
        if (mAudioEngine != null && mAudioEngine.getVolume() > 0f) {
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

        // 콤보 이펙트 업데이트 (매 키 입력 시)
        if (mComboEnabled && mOverlayManager != null) {
            ComboTier tier = mComboCalculator.onClick();
            int combo = mComboCalculator.getComboStreak();
            int rawScore = BASE_SCORE * tier.getSpeedMultiplier();
            double comboMultiplier = 1.0 + combo * 0.01;
            int score = (int) (rawScore * comboMultiplier);
            mOverlayManager.onKeyPress(score, combo);

            // Score/Touch 카운터 업데이트
            if (mClickCountRepo != null) {
                mClickCountRepo.incrementScore(score);
                mClickCountRepo.incrementTouch(1);
                // 오버레이 카운터 갱신 (모드에 따라 Score 또는 Touch 표시)
                boolean showScore = mPrefs != null && "score".equals(mPrefs.getString("dogakdogak_counter_mode", "score"));
                long displayCount = showScore
                    ? mClickCountRepo.getTotalScore().getValue()
                    : mClickCountRepo.getTotalTouches().getValue();
                mOverlayManager.updateCount(displayCount);
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
        if (mAudioEngine != null) {
            mAudioEngine.release();
            mAudioEngine = null;
        }
    }
}
