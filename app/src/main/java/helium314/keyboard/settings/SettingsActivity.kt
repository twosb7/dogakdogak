// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import helium314.keyboard.compat.locale
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.dogakdogak.AppThemeType
import helium314.keyboard.latin.dogakdogak.ClickCountRepository
import helium314.keyboard.latin.dogakdogak.DogakdogakMainScreen
import helium314.keyboard.latin.dogakdogak.DogakdogakTheme
import helium314.keyboard.latin.dogakdogak.OnboardingScreen
import helium314.keyboard.latin.dogakdogak.PurchaseRepository
import helium314.keyboard.latin.dogakdogak.RankingRepository
import helium314.keyboard.latin.dogakdogak.SupabaseModule
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.cleanUnusedMainDicts
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.NewDictionaryDialog
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.handleDeeplinks
import io.github.jan.supabase.gotrue.providers.Google
import io.github.jan.supabase.gotrue.providers.Kakao
import io.github.jan.supabase.gotrue.providers.builtin.IDToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// todo: with compose, app startup is slower and UI needs some "warmup" time to be snappy
//  maybe baseline profiles help?
//  https://developer.android.com/codelabs/android-baseline-profiles-improve
//  https://developer.android.com/codelabs/jetpack-compose-performance#2
//  https://developer.android.com/topic/performance/baselineprofiles/overview
// todo: consider viewModel, at least for LanguageScreen and ColorsScreen it might help making them less awkward and complicated
open class SettingsActivity : ComponentActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs by lazy { this.prefs() }
    val prefChanged = MutableStateFlow(0) // simple counter, as the only relevant information is that something changed
    fun prefChanged() = prefChanged.value++
    private val dictUriFlow = MutableStateFlow<Uri?>(null)
    private val cachedDictionaryFile by lazy { File(this.cacheDir.path + File.separator + "temp_dict") }
    private val crashReportFiles = MutableStateFlow<List<File>>(emptyList())
    private var paused = true

    // 도각도각 기능 리포지토리
    val rankingRepository = RankingRepository()
    var purchaseRepository: PurchaseRepository? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.getValues() == null) {
            val inputAttributes = InputAttributes(EditorInfo(), false, packageName)
            Settings.getInstance().loadSettings(this, resources.configuration.locale(), inputAttributes)
        }
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute { cleanUnusedMainDicts(this) }
        crashReportFiles.value = findCrashReports(!BuildConfig.DEBUG && !DebugFlags.DEBUG_ENABLED)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

        settingsContainer = SettingsContainer(this)
        purchaseRepository = PurchaseRepository(this)
        SupabaseModule.client.handleDeeplinks(intent)

        val spellchecker = intent?.getBooleanExtra("spellchecker", false) ?: false

        val cv = ComposeView(context = this)
        setContentView(cv)
        cv.setContent {
            Theme {
                Surface {
                    val dictUri by dictUriFlow.collectAsState()
                    val crashReports by crashReportFiles.collectAsState()
                    val crashFilePicker = filePicker { saveCrashReports(it) }
                    // prefChanged를 구독하여 테마 변경 시 recompose
                    val prefVersion by prefChanged.collectAsState()
                    val onboardingCompleted = prefs.getBoolean("dogakdogak_onboarding_completed", false)

                    // 기존 사용자 마이그레이션: 키보드 스타일 적용
                    if (onboardingCompleted && !prefs.getBoolean("dogakdogak_kb_style_v5", false)) {
                        val currentDogakTheme = prefs.getString("dogakdogak_theme", AppThemeType.MAISON.name) ?: AppThemeType.MAISON.name
                        val kbColors = when (currentDogakTheme) {
                            AppThemeType.FORGE.name -> "dogakdogak_dark"
                            AppThemeType.BLACK.name -> "dogakdogak_dark"
                            else -> "dogakdogak_light"
                        }
                        prefs.edit()
                            .putString("theme_style", "Rounded")
                            .putBoolean("theme_key_borders", true)
                            .putBoolean("show_number_row", true)
                            .putBoolean("theme_auto_day_night", false)
                            .putString("theme_colors", kbColors)
                            .putString("theme_colors_night", kbColors)
                            .putString("toolbar_mode", "HIDDEN")
                            .putBoolean("show_hints", false)
                            .putBoolean("show_language_switch_key", true)
                            .putBoolean("show_emoji_key", true)
                            .putBoolean("auto_cap", false)
                            .putBoolean("dogakdogak_kb_style_v5", true)
                            .apply()
                        // 한국어 + 영어 서브타입 활성화
                        ensureKoreanEnglishSubtypes(this@SettingsActivity)
                    }

                    if (spellchecker)
                        Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
                            Column(Modifier.padding(innerPadding)) {
                                TopAppBar(
                                    title = { Text(stringResource(R.string.android_spell_checker_settings)) },
                                    windowInsets = WindowInsets(0),
                                    navigationIcon = {
                                        BackButton { this@SettingsActivity.finish() }
                                    },
                                )
                                settingsContainer[Settings.PREF_USE_CONTACTS]!!.Preference()
                                settingsContainer[Settings.PREF_USE_APPS]!!.Preference()
                                settingsContainer[Settings.PREF_BLOCK_POTENTIALLY_OFFENSIVE]!!.Preference()
                            }
                        }
                    else {
                        // prefVersion을 참조하여 prefs 변경 시 테마가 즉시 반영되도록
                        @Suppress("UNUSED_EXPRESSION") prefVersion
                        val themeStr = prefs.getString("dogakdogak_theme", AppThemeType.MAISON.name) ?: AppThemeType.MAISON.name
                        val themeType = try { AppThemeType.valueOf(themeStr) } catch (_: Exception) { AppThemeType.MAISON }

                        // Google Sign-In 설정
                        val context = LocalContext.current
                        val scope = rememberCoroutineScope()
                        val googleSignInOptions = remember {
                            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestIdToken(SupabaseModule.GOOGLE_WEB_CLIENT_ID)
                                .requestEmail()
                                .build()
                        }
                        val googleSignInClient = remember(context, googleSignInOptions) {
                            GoogleSignIn.getClient(context, googleSignInOptions)
                        }
                        val googleSignInLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.StartActivityForResult()
                        ) { result ->
                            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                            try {
                                val account = task.getResult(ApiException::class.java)
                                val googleIdToken = account.idToken
                                if (!googleIdToken.isNullOrBlank()) {
                                    scope.launch {
                                        try {
                                            SupabaseModule.auth.signInWith(IDToken) {
                                                provider = Google
                                                idToken = googleIdToken
                                            }
                                        } catch (e: Exception) {
                                            Log.e("dogakdogak", "Supabase sign-in failed", e)
                                        }
                                    }
                                }
                            } catch (e: ApiException) {
                                Log.e("dogakdogak", "Google sign-in failed: ${e.statusCode}", e)
                            }
                        }

                        val onLoginAction: (String) -> Unit = { provider ->
                            when (provider) {
                                "google" -> {
                                    googleSignInClient.signOut().addOnCompleteListener {
                                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                    }
                                }
                                "kakao" -> {
                                    scope.launch {
                                        try {
                                            SupabaseModule.auth.signInWith(
                                                provider = Kakao,
                                                redirectUrl = SupabaseModule.AUTH_REDIRECT_URL
                                            )
                                        } catch (e: Exception) {
                                            Log.e("dogakdogak", "Kakao sign-in failed", e)
                                        }
                                    }
                                }
                            }
                        }

                        val onLogoutAction: () -> Unit = {
                            scope.launch {
                                try {
                                    SupabaseModule.auth.signOut()
                                    googleSignInClient.signOut()
                                    ClickCountRepository.getInstance(context).setCurrentUserId("guest")
                                    rankingRepository.clearProfileCache()
                                } catch (e: Exception) {
                                    Log.e("dogakdogak", "Logout failed", e)
                                }
                            }
                        }

                        val onDeleteAccountAction: () -> Unit = {
                            scope.launch {
                                try {
                                    rankingRepository.deleteUserData()
                                    SupabaseModule.deleteAccount()
                                    googleSignInClient.signOut()
                                } catch (e: Exception) {
                                    Log.e("dogakdogak", "Delete account failed", e)
                                }
                            }
                        }

                        // 세션 상태 변화 관찰: 로그인/로그아웃 시 계정별 데이터 전환 + Supabase 동기화
                        LaunchedEffect(Unit) {
                            SupabaseModule.client.auth.sessionStatus.collect { status ->
                                when (status) {
                                    is SessionStatus.Authenticated -> {
                                        val uid = SupabaseModule.auth.currentUserOrNull()?.id ?: return@collect
                                        val repo = ClickCountRepository.getInstance(context)
                                        // 1. 현재 사용자 전환 (계정별 분리 기록)
                                        repo.setCurrentUserId(uid)
                                        // 2. Supabase에서 프로필 로드
                                        rankingRepository.refreshProfile()
                                        // 3. daily 데이터를 Supabase에 동기화
                                        rankingRepository.syncDailyClicks(repo.getDailyScoreValue())
                                        rankingRepository.syncDailyTouches(repo.getDailyTouchesValue())
                                    }
                                    is SessionStatus.NotAuthenticated -> {
                                        ClickCountRepository.getInstance(context).setCurrentUserId("guest")
                                    }
                                    else -> {}
                                }
                            }
                        }

                        if (!onboardingCompleted) {
                            // 도각도각 온보딩 화면
                            DogakdogakTheme(themeType = themeType) {
                                OnboardingScreen(
                                    prefs = prefs,
                                    onComplete = {
                                        prefs.edit()
                                            .putBoolean("dogakdogak_onboarding_completed", true)
                                            .putBoolean("dogakdogak_overlay_visible", true)
                                            .putString("theme_style", "Rounded")
                                            .putBoolean("theme_key_borders", true)
                                            .putBoolean("show_number_row", true)
                                            .putBoolean("theme_auto_day_night", false)
                                            .putString("toolbar_mode", "HIDDEN")
                                            .putBoolean("show_hints", false)
                                            .putBoolean("show_language_switch_key", true)
                                            .putBoolean("show_emoji_key", true)
                                            .putBoolean("auto_cap", false)
                                            .putBoolean("dogakdogak_kb_style_v5", true)
                                            .apply()
                                        // 한국어 + 영어 서브타입 활성화
                                        ensureKoreanEnglishSubtypes(this@SettingsActivity)
                                        prefChanged()
                                    },
                                    onLogin = onLoginAction,
                                )
                            }
                        } else {
                            var showKeyboardSettings by rememberSaveable { mutableStateOf(false) }

                            if (showKeyboardSettings) {
                                // HeliBoard 기본 키보드 설정 화면
                                SettingsNavHost(onClickBack = { showKeyboardSettings = false })
                            } else {
                                // 도각도각 메인 화면
                                DogakdogakTheme(themeType = themeType) {
                                    DogakdogakMainScreen(
                                        onNavigateToKeyboardSettings = { showKeyboardSettings = true },
                                        prefs = prefs,
                                        rankingRepository = rankingRepository,
                                        purchaseRepository = purchaseRepository,
                                        onLogin = onLoginAction,
                                        onLogout = onLogoutAction,
                                        onDeleteAccount = onDeleteAccountAction,
                                    )
                                }
                            }

                            if (crashReports.isNotEmpty()) {
                                ConfirmationDialog(
                                    cancelButtonText = "ignore",
                                    onDismissRequest = { crashReportFiles.value = emptyList() },
                                    neutralButtonText = "delete",
                                    onNeutral = { crashReports.forEach { it.delete() }; crashReportFiles.value = emptyList() },
                                    confirmButtonText = "get",
                                    onConfirmed = {
                                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                                        intent.addCategory(Intent.CATEGORY_OPENABLE)
                                        intent.putExtra(Intent.EXTRA_TITLE, "crash_reports.zip")
                                        intent.type = "application/zip"
                                        crashFilePicker.launch(intent)
                                    },
                                    content = { Text("Crash report files found") },
                                )
                            }
                        }
                    }
                    if (dictUri != null) {
                        NewDictionaryDialog(
                            onDismissRequest = { dictUriFlow.value = null },
                            cachedFile = cachedDictionaryFile,
                            mainLocale = null
                        )
                    }
                }
            }
        }

        if (intent?.action == Intent.ACTION_VIEW) {
            intent?.data?.let {
                cachedDictionaryFile.delete()
                FileUtils.copyContentUriToNewFile(it, this, cachedDictionaryFile)
                dictUriFlow.value = it
            }
            intent = null
        }

        enableEdgeToEdge()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        SupabaseModule.client.handleDeeplinks(intent)
    }

    override fun onStart() {
        super.onStart()
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        setForceTheme(null, null)
        paused = true
    }

    override fun onResume() {
        super.onResume()
        paused = false
    }

    override fun onDestroy() {
        super.onDestroy()
        purchaseRepository?.destroy()
    }

    fun setForceTheme(theme: String?, night: Boolean?) {
        if (paused) return
        if (forceTheme == theme && forceNight == night)
            return
        forceTheme = theme
        forceNight = night
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }

    private fun findCrashReports(onlyUnprotected: Boolean): List<File> {
        val unprotected = DeviceProtectedUtils.getFilesDir(this)?.listFiles().orEmpty()
        if (onlyUnprotected)
            return unprotected.filter { it.name.startsWith("crash_report") }

        val dir = getExternalFilesDir(null)
        val allFiles = dir?.listFiles()?.toList().orEmpty() + unprotected
        return allFiles.filter { it.name.startsWith("crash_report") }
    }

    private fun saveCrashReports(uri: Uri) {
        val files = findCrashReports(false)
        if (files.isEmpty()) return
        runCatching {
            contentResolver.openOutputStream(uri)?.use {
                val bos = BufferedOutputStream(it)
                val z = ZipOutputStream(bos)
                for (file in files) {
                    val f = FileInputStream(file)
                    z.putNextEntry(ZipEntry(file.name))
                    FileUtils.copyStreamToOtherStream(f, z)
                    f.close()
                    z.closeEntry()
                }
                z.close()
                bos.close()
                for (file in files) {
                    file.delete()
                }
            }
        }
    }

    companion object {
        // public write so compose previews can show the screens
        // having it in a companion object is not ideal as it will stay in memory even after settings are closed
        // but it's small enough to not care
        lateinit var settingsContainer: SettingsContainer

        var forceNight: Boolean? = null
        var forceTheme: String? = null

        /** 한국어(ko) + 영어(en_US) 서브타입이 모두 활성화되어 있지 않으면 추가 */
        fun ensureKoreanEnglishSubtypes(context: android.content.Context) {
            try {
                val prefs = context.prefs()
                val enabledSubtypes = SubtypeSettings.getEnabledSubtypes(false)
                val hasKorean = enabledSubtypes.any { it.locale().language == "ko" }
                val hasEnglish = enabledSubtypes.any { it.locale().language == "en" }
                if (!hasKorean) {
                    val koreanSubtype = SubtypeSettings.getResourceSubtypesForLocale(java.util.Locale("ko")).firstOrNull()
                    if (koreanSubtype != null) SubtypeSettings.addEnabledSubtype(prefs, koreanSubtype)
                }
                if (!hasEnglish) {
                    val englishSubtype = SubtypeSettings.getResourceSubtypesForLocale(java.util.Locale.US).firstOrNull()
                    if (englishSubtype != null) SubtypeSettings.addEnabledSubtype(prefs, englishSubtype)
                }
            } catch (e: Exception) {
                Log.e("dogakdogak", "Failed to enable Korean/English subtypes", e)
            }
        }
    }

    override fun onSharedPreferenceChanged(prefereces: SharedPreferences?, key: String?) {
        prefChanged()
    }
}

// duplicate of SettingsActivity so we can launch it when the app icon is disabled in Android 9 and older
class SettingsActivity2 : SettingsActivity()
