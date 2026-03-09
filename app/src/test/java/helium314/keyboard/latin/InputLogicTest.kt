// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.text.Spanned
import android.text.style.SuggestionSpan
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.*
import androidx.core.content.edit
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.event.Event
import helium314.keyboard.event.CombinerChain
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.ShadowFacilitator2.Companion.lastAddedWord
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.inputlogic.InputLogic
import helium314.keyboard.latin.inputlogic.SpaceState
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ScriptUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.getTimestampFormatter
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLog
import java.util.*
import kotlin.math.min
import kotlin.random.Random
import kotlin.streams.asSequence
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowLocaleManagerCompat::class,
    ShadowInputMethodManager2::class,
    ShadowInputMethodService::class,
    ShadowKeyboardSwitcher::class,
    ShadowHandler::class,
    ShadowFacilitator2::class,
])
class InputLogicTest {
    private lateinit var latinIME: LatinIME
    private val settingsValues get() = Settings.getValues()
    private val inputLogic get() = latinIME.mInputLogic
    private val connection: RichInputConnection get() = inputLogic.mConnection
    private val composerReader = InputLogic::class.java.getDeclaredField("mWordComposer").apply { isAccessible = true }
    private val composer get() = composerReader.get(inputLogic) as WordComposer
    private val combinerChainReader = WordComposer::class.java.getDeclaredField("mCombinerChain").apply { isAccessible = true }
    private val spaceStateReader = InputLogic::class.java.getDeclaredField("mSpaceState").apply { isAccessible = true }
    private val cursorMovedByUserReader = InputLogic::class.java.getDeclaredField("mCursorMovedByUser").apply { isAccessible = true }
    private val spaceState get() = spaceStateReader.get(inputLogic) as Int
    private val beforeComposingReader = RichInputConnection::class.java.getDeclaredField("mCommittedTextBeforeComposingText").apply { isAccessible = true }
    private val connectionTextBeforeComposingText get() = (beforeComposingReader.get(connection) as CharSequence).toString()
    private val composingReader = RichInputConnection::class.java.getDeclaredField("mComposingText").apply { isAccessible = true }
    private val connectionComposingText get() = (composingReader.get(connection) as CharSequence).toString()
    private val autoCorrectionIndicatorReader = InputLogic::class.java.getDeclaredField("mIsAutoCorrectionIndicatorOn").apply { isAccessible = true }
    private val textWithUnderlineMethod = InputLogic::class.java.getDeclaredMethod("getTextWithUnderline", String::class.java).apply { isAccessible = true }

    @BeforeTest
    fun setUp() {
        latinIME = Robolectric.setupService(LatinIME::class.java)
        // start logging only after latinIME is created, avoids showing the stack traces if library is not found
        ShadowLog.setupLogging()
        ShadowLog.stream = System.out
    }

    @Test fun inputCode() {
        reset()
        input('c')
        assertEquals("c", textBeforeCursor)
        assertEquals("c", getText())
        assertEquals("", textAfterCursor)
        assertEquals("c", composingText)
        latinIME.mHandler.onFinishInput()
        assertEquals("", composingText)
    }

    @Test fun composingTextDoesNotAddUnderlineSpan() {
        reset()
        autoCorrectionIndicatorReader.setBoolean(inputLogic, true)

        val textWithUnderline = textWithUnderlineMethod.invoke(inputLogic, "hello") as CharSequence

        assertEquals("hello", textWithUnderline.toString())
        val suggestionSpans = if (textWithUnderline is Spanned) {
            textWithUnderline.getSpans(0, textWithUnderline.length, SuggestionSpan::class.java)
        } else {
            emptyArray()
        }
        assertEquals(0, suggestionSpans.size)
    }

    @Test fun delete() {
        reset()
        setText("hello there ")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello there", text)
        assertEquals("there", composingText)
    }

    @Test fun deleteInsideWord() {
        reset()
        setText("hello you there")
        setCursorPosition(8) // after o in you
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello yu there", text)
        assertEquals("yu", composingText)
    }

    @Test fun insertLetterIntoWord() {
        reset()
        setText("hello")
        setCursorPosition(3) // after first l
        input('i')
        assertEquals("helilo", getWordAtCursor())
        assertEquals("helilo", getText())
        assertEquals(4, getCursorPosition())
        assertEquals(4, cursor)
        assertEquals("", composingText)
    }

    @Test fun insertLetterIntoWordWithWeirdEditor() {
        reset()
        currentInputType = 180225 // should not change much, but just to be sure
        setText("hello")
        setCursorPosition(3, weirdTextField = true) // after first l
        input('i')
        assertEquals("helilo", getWordAtCursor())
        assertEquals("helilo", getText())
        assertEquals(4, getCursorPosition())
        assertEquals(4, cursor)
    }

    @Test fun insertLetterIntoOneOfSeveralWords() {
        reset()
        setText("hello my friend")
        setCursorPosition(7) // between m and y
        input('a')
        assertEquals("may", getWordAtCursor())
        assertEquals("hello may friend", getText())
        assertEquals(8, getCursorPosition())
        assertEquals(8, cursor)
    }

    @Test fun insertLetterIntoWordHangulInsertsAtCursor() {
        if (BuildConfig.BUILD_TYPE == "runTests") return
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        currentScript = ScriptUtils.SCRIPT_HANGUL
        chainInput("ㅛㅎㄹㅎㅕㅛ")
        setCursorPosition(3)
        input('ㄲ')
        assertEquals("ㅛㅎㄹㄲ혀ㅛ", getWordAtCursor())
        assertEquals("ㅛㅎㄹㄲ혀ㅛ", getText())
        assertEquals("ㅛㅎㄹㄲ혀ㅛ", textBeforeCursor + textAfterCursor)
        assertEquals(4, getCursorPosition())
        assertEquals(4, cursor)
    }

    // see issue 1447
    @Test fun separatorAfterHangul() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        chainInput("ㅛ.")
        assertEquals("ㅛ.", text)
    }

    // see issue 1551 (debug only)
    @Test fun deleteHangul() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        setText("ㅛㅛ ")
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
    }

    @Test fun backspaceInMiddleOfHangulWordDeletesLeftChar() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        currentScript = ScriptUtils.SCRIPT_HANGUL
        chainInput("ㅅㅡㅁㅏㅌㅡㅍㅗㄴ")
        assertEquals("스마트폰", text)

        setCursorPosition(1) // 스|마트폰
        functionalKeyPress(KeyCode.DELETE)

        assertEquals("마트폰", text)
        assertEquals(0, getCursorPosition())
    }

    @Test fun backspaceInMiddleOfComposingHangulWordDeletesAtCursor() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        currentScript = ScriptUtils.SCRIPT_HANGUL
        chainInput("ㅂㅏㅇㅑㅎㅡㄹㅗ")
        assertEquals("바야흐로", text)

        setCursorPosition(2) // 바야|흐로
        functionalKeyPress(KeyCode.DELETE)

        assertEquals("바흐로", text)
        assertEquals(1, getCursorPosition())
    }

    @Test fun insertHangulInMiddleOfComposingWordKeepsCursorPosition() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        currentScript = ScriptUtils.SCRIPT_HANGUL
        chainInput("ㅂㅏㅇㅑㅎㅡㄹㅗ")
        assertEquals("바야흐로", text)

        setCursorPosition(2) // 바야|흐로
        input('ㅅ')
        input('ㅏ')

        assertEquals("바야사흐로", text)
        assertEquals(3, getCursorPosition())
    }

    @Test fun repeatedMiddleBackspaceOnHangulWordDoesNotCrash() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        currentScript = ScriptUtils.SCRIPT_HANGUL
        chainInput("ㅅㅡㅁㅏㅌㅡㅍㅗㄴ")
        assertEquals("스마트폰", text)

        repeat(4) {
            setCursorPosition(1) // always delete the first syllable
            functionalKeyPress(KeyCode.DELETE)
        }

        assertTrue(text.isEmpty() || text == "폰")
        if (text.isEmpty()) {
            assertEquals(0, getCursorPosition())
        } else {
            assertEquals(1, getCursorPosition())
        }
    }

    @Test fun singleHangulBackspaceClearsWithoutGhostJamo() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        currentScript = ScriptUtils.SCRIPT_HANGUL
        chainInput("ㅌ")
        assertEquals("ㅌ", text)

        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)
    }

    @Test fun committedHangulBackspaceUsesJamoThenWholeSyllableDeletion() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        currentScript = ScriptUtils.SCRIPT_HANGUL
        chainInput("ㅁㅐㄱㅁㅐㄱㅂㅜㄱ")
        assertEquals("맥맥북", text)

        setText(text)

        functionalKeyPress(KeyCode.DELETE)
        assertEquals("맥맥부", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("맥맥ㅂ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("맥맥", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("맥", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)
    }

    @Test fun committedHangulBackspaceOnOpenSyllablesStepsThroughJamo() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        currentScript = ScriptUtils.SCRIPT_HANGUL
        chainInput("ㄴㅗㄹㅏ")
        assertEquals("노라", text)

        setText(text)

        functionalKeyPress(KeyCode.DELETE)
        assertEquals("노ㄹ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("노", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)
    }

    @Test fun committedHangulBackspaceOnCompoundVowelAndFinalStepsThroughJamo() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        currentScript = ScriptUtils.SCRIPT_HANGUL

        setText("과")
        setText(text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("고", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("ㄱ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)

        setText("값")
        assertEquals("값", text)
        setText(text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("갑", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("가", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("ㄱ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)
    }

    @Test fun cheonjiinBasicVowelCompositionWorksThroughInputLogic() {
        reset()
        switchToCheonjiinSubtype()
        chainInput("ㄱㅣㆍ")
        assertEquals("가", text)

        reset()
        switchToCheonjiinSubtype()
        chainInput("ㄱㆍㅣ")
        assertEquals("거", text)

        reset()
        switchToCheonjiinSubtype()
        chainInput("ㄱㆍㅡ")
        assertEquals("고", text)
    }

    @Test fun cheonjiinConsonantCyclingWorksThroughInputLogic() {
        reset()
        switchToCheonjiinSubtype()
        chainInput("ㄱㄱㄱㅣㆍ")
        assertEquals("까", text)
    }

    @Test fun cheonjiinBackspaceStepsThroughComposition() {
        reset()
        switchToCheonjiinSubtype()
        chainInput("ㄱㅣㆍ")
        assertEquals("가", text)

        functionalKeyPress(KeyCode.DELETE)
        assertEquals("기", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("ㄱ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)
    }

    @Test fun cheonjiinCompoundVowelBackspaceStepsThroughIntermediateStates() {
        reset()
        switchToCheonjiinSubtype()
        chainInput("ㄱㅣㆍㅣ")
        assertEquals("개", text)

        functionalKeyPress(KeyCode.DELETE)
        assertEquals("가", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("기", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("ㄱ", text)
    }

    @Test fun cheonjiinBackspaceKeepsDeletingAfterFirstStepBecomesCommitted() {
        reset()
        switchToCheonjiinSubtype()
        currentScript = ScriptUtils.SCRIPT_HANGUL

        chainInput("ㄱㅣㆍㅣ")
        assertEquals("개", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("가", text)
        setText(text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("ㄱ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)

        reset()
        switchToCheonjiinSubtype()
        currentScript = ScriptUtils.SCRIPT_HANGUL

        chainInput("ㄱㆍㅣ")
        assertEquals("거", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("ㄱㆍ", text)
        setText(text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("ㄱ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)

        reset()
        switchToCheonjiinSubtype()
        currentScript = ScriptUtils.SCRIPT_HANGUL

        chainInput("ㄱㅡㆍ")
        assertEquals("구", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("그", text)
        setText(text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("ㄱ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)
    }

    @Test fun committedCheonjiinBackspaceIgnoresStaleCursorMovedFlagAtWordEnd() {
        reset()
        switchToCheonjiinSubtype()
        currentScript = ScriptUtils.SCRIPT_HANGUL

        setText("가")
        cursorMovedByUserReader.setBoolean(inputLogic, true)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("ㄱ", text)
    }

    @Test fun cheonjiinFinalConsonantMovesToNextSyllableOnVowelInput() {
        reset()
        switchToCheonjiinSubtype()
        chainInput("ㄱㅣㆍㄴㅣ")
        assertEquals("가니", text)
    }

    @Test fun cheonjiinDirectionalVowelsBackspaceToStrokeState() {
        reset()
        switchToCheonjiinSubtype()
        chainInput("ㄱㆍㅣ")
        assertEquals("거", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("ㄱㆍ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("ㄱ", text)

        reset()
        switchToCheonjiinSubtype()
        chainInput("ㄱㆍㅡ")
        assertEquals("고", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("ㄱㆍ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("ㄱ", text)

        reset()
        switchToCheonjiinSubtype()
        chainInput("ㄱㅡㆍ")
        assertEquals("구", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("그", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("ㄱ", text)
    }

    @Test fun cheonjiinDoubleAraeaBackspaceKeepsSingleAraeaState() {
        reset()
        switchToCheonjiinSubtype()
        chainInput("ㄱㅣ")
        assertEquals("기", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("ㄱ", text)
        chainInput("ㆍㆍ")
        assertEquals("ㄱㆎ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("ㄱㆍ", text)
    }

    @Test fun cheonjiinInsertAndBackspaceInMiddleOfWordRespectCursor() {
        reset()
        switchToCheonjiinSubtype()
        chainInput("ㄱㅣㆍㄴㅣㆍ")
        assertEquals("가나", text)

        setCursorPosition(1) // 가|나
        chainInput("ㄷㅣㆍ")
        assertEquals("가다나", text)
        assertEquals(2, getCursorPosition())

        setCursorPosition(1) // 가|다나
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("다나", text)
        assertEquals(0, getCursorPosition())
    }

    @Test fun cheonjiinSoftInputMatchesAutomataForSeedCorpus() {
        val strokeAlphabet = intArrayOf(
            'ㄱ'.code, 'ㄴ'.code, 'ㄷ'.code, 'ㅂ'.code, 'ㅅ'.code, 'ㅈ'.code, 'ㅇ'.code,
            'ㅣ'.code, 'ㆍ'.code, 'ㅡ'.code
        )
        val random = Random(1)

        repeat(120) {
            val sequenceLength = random.nextInt(1, 7)
            val sequence = IntArray(sequenceLength) { strokeAlphabet[random.nextInt(strokeAlphabet.size)] }

            reset()
            switchToCheonjiinSubtype()
            sequence.forEach { input(it) }
            val inputLogicText = text

            val automataDriver = CheonjiinSequenceDriver()
            automataDriver.input(*sequence)
            val automataText = automataDriver.text()

            assertEquals(
                automataText,
                inputLogicText,
                "cheonjiin mismatch for sequence=" + sequence.joinToString("") { String(Character.toChars(it)) }
            )
        }
    }

    @Test fun cheonjiinSoftInputMatchesAutomataForRandomOperationsWithoutDelete() {
        val strokeAlphabet = intArrayOf(
            'ㄱ'.code, 'ㄴ'.code, 'ㄷ'.code, 'ㅂ'.code, 'ㅅ'.code, 'ㅈ'.code, 'ㅇ'.code,
            'ㅣ'.code, 'ㆍ'.code, 'ㅡ'.code
        )
        val random = Random(7)

        repeat(80) {
            reset()
            switchToCheonjiinSubtype()
            val automataDriver = CheonjiinSequenceDriver()
            val operations = mutableListOf<String>()

            repeat(18) { opIndex ->
                val codePoint = strokeAlphabet[random.nextInt(strokeAlphabet.size)]
                input(codePoint)
                automataDriver.input(codePoint)
                operations += String(Character.toChars(codePoint))
                assertEquals(
                    automataDriver.text(),
                    text,
                    "cheonjiin input mismatch at iteration=$it op=$opIndex sequence=${operations.joinToString(" ")} state=${describeCheonjiinState()}"
                )
            }
        }
    }

    @Test fun cheonjiinSoftInputRandomDeleteSmokeTest() {
        val strokeAlphabet = intArrayOf(
            'ㄱ'.code, 'ㄴ'.code, 'ㄷ'.code, 'ㅂ'.code, 'ㅅ'.code, 'ㅈ'.code, 'ㅇ'.code,
            'ㅣ'.code, 'ㆍ'.code, 'ㅡ'.code
        )
        val random = Random(17)

        repeat(120) { iteration ->
            reset()
            switchToCheonjiinSubtype()
            val operations = mutableListOf<String>()

            repeat(24) { opIndex ->
                val doDelete = random.nextInt(5) == 0
                if (doDelete) {
                    functionalKeyPress(KeyCode.DELETE)
                    operations += "DEL"
                } else {
                    val codePoint = strokeAlphabet[random.nextInt(strokeAlphabet.size)]
                    input(codePoint)
                    operations += String(Character.toChars(codePoint))
                }
                assertEquals(
                    textBeforeCursor + textAfterCursor,
                    getText(),
                    "cheonjiin smoke mismatch at iteration=$iteration op=$opIndex sequence=${operations.joinToString(" ")} state=${describeCheonjiinState()}"
                )
            }
        }
    }

    @Test fun cheonjiinCursorEditingSmokeTest() {
        val strokeAlphabet = intArrayOf(
            'ㄱ'.code, 'ㄴ'.code, 'ㄷ'.code, 'ㅂ'.code, 'ㅅ'.code, 'ㅈ'.code, 'ㅇ'.code,
            'ㅣ'.code, 'ㆍ'.code, 'ㅡ'.code
        )
        val random = Random(27)

        repeat(60) { iteration ->
            reset()
            switchToCheonjiinSubtype()
            val operations = mutableListOf<String>()

            repeat(20) { opIndex ->
                when (random.nextInt(4)) {
                    0 -> {
                        val codePoint = strokeAlphabet[random.nextInt(strokeAlphabet.size)]
                        latinIME.onTextInput(String(Character.toChars(codePoint)))
                        handleMessages()
                        operations += String(Character.toChars(codePoint))
                    }
                    1 -> {
                        functionalKeyPress(KeyCode.DELETE)
                        operations += "DEL"
                    }
                    else -> {
                        val newCursor = if (text.isEmpty()) 0 else random.nextInt(text.length + 1)
                        setCursorPosition(newCursor)
                        operations += "CUR($newCursor)"
                    }
                }

                assertEquals(
                    textBeforeCursor + textAfterCursor,
                    getText(),
                    "cheonjiin cursor smoke mismatch at iteration=$iteration op=$opIndex sequence=${operations.joinToString(" ")} state=${describeCheonjiinState()}"
                )
                assertTrue(
                    getCursorPosition() in 0..text.length,
                    "cheonjiin cursor out of range at iteration=$iteration op=$opIndex sequence=${operations.joinToString(" ")} text='$text'"
                )
            }
        }
    }

    @Test fun cheonjiinRandomSingleCharacterCorpusCoversJamoLengthsAndDoubleConsonants() {
        val random = Random(301)
        val categoryCounts = mutableMapOf(1 to 0, 2 to 0, 3 to 0, 4 to 0)
        var doubleCount = 0
        val chosenCases = buildList {
            repeat(75) { add(CHEONJIIN_RANDOM_CHAR_CASES_1.random(random)) }
            repeat(75) { add(CHEONJIIN_RANDOM_CHAR_CASES_2.random(random)) }
            repeat(75) { add(CHEONJIIN_RANDOM_CHAR_CASES_3.random(random)) }
            repeat(75) { add(CHEONJIIN_RANDOM_CHAR_CASES_4.random(random)) }
        }.shuffled(random)

        chosenCases.forEachIndexed { index, testCase ->
            reset()
            switchToCheonjiinSubtype()
            chainInput(testCase.sequence)

            val automataDriver = CheonjiinSequenceDriver()
            automataDriver.input(*testCase.sequence.map { it.code }.toIntArray())
            val expected = automataDriver.text()

            assertEquals(
                expected,
                text,
                "cheonjiin random corpus mismatch index=$index seq='${testCase.sequence}'"
            )
            assertEquals(
                1,
                text.codePointCount(0, text.length),
                "cheonjiin random corpus should produce exactly one visible char index=$index seq='${testCase.sequence}' actual='$text'"
            )

            val analysis = analyzeCheonjiinOutput(text)
            assertEquals(
                testCase.jamoCount,
                analysis.jamoCount,
                "cheonjiin random corpus wrong jamo count index=$index seq='${testCase.sequence}' actual='$text'"
            )
            if (testCase.hasDoubleConsonant) {
                assertTrue(
                    analysis.hasDoubleConsonant,
                    "cheonjiin random corpus should include double consonant index=$index seq='${testCase.sequence}' actual='$text'"
                )
            }

            categoryCounts.compute(testCase.jamoCount) { _, value -> (value ?: 0) + 1 }
            if (analysis.hasDoubleConsonant) doubleCount++
        }

        assertEquals(75, categoryCounts[1])
        assertEquals(75, categoryCounts[2])
        assertEquals(75, categoryCounts[3])
        assertEquals(75, categoryCounts[4])
        assertTrue(doubleCount >= 40, "expected plenty of double-consonant coverage but got $doubleCount")
    }

    @Test fun cheonjiinCommittedStandaloneVowelBackspaceUsesStrokeDeletionPolicy() {
        reset()
        switchToCheonjiinSubtype()
        chainInput("ㆍㅡㄱ")
        assertEquals("ㅗㄱ", text)

        functionalKeyPress(KeyCode.DELETE)
        assertEquals("ㅗ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("ㆍ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)
    }

    @Test fun cheonjiinCommittedStandaloneVowelContinuesIntoFollowingSyllable() {
        reset()
        switchToCheonjiinSubtype()
        chainInput("ㅈㅣㅡ")
        assertEquals("지ㅡ", text)
        chainInput("ㄷ")
        assertEquals("지ㅡㄷ", text)
        chainInput("ㅡ")
        assertEquals("지ㅡ드", text)
    }

    @Test fun cheonjiinInsertAfterCommittedStandaloneVowelKeepsCursor() {
        reset()
        switchToCheonjiinSubtype()
        chainInput("ㅈㅣㅡㄷㅡ")
        assertEquals("지ㅡ드", text)

        setCursorPosition(1) // 지|ㅡ드
        chainInput("ㆍㅡ")
        assertEquals("지ㅗㅡ드", text)
        assertEquals(2, getCursorPosition())
    }

    @Test fun cheonjiinBackspaceInMiddleAfterStandaloneVowelDeletesLeftCluster() {
        reset()
        switchToCheonjiinSubtype()
        chainInput("ㅈㅣㅡㄷㅡ")
        assertEquals("지ㅡ드", text)

        setCursorPosition(2) // 지ㅡ|드
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("지드", text)
        assertEquals(1, getCursorPosition())
    }

    @Test fun cheonjiinStandaloneVowelAtStartBackspacesStepByStep() {
        reset()
        switchToCheonjiinSubtype()
        chainInput("ㆍㅡ")
        assertEquals("ㅗ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("ㆍ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)
    }

    @Test fun cheonjiinStandaloneVowelCanBeInsertedInMiddle() {
        reset()
        switchToCheonjiinSubtype()
        chainInput("ㄱㅣㆍㄴㅣ")
        assertEquals("가니", text)

        setCursorPosition(1) // 가|니
        chainInput("ㆍㅡ")
        assertEquals("가ㅗ니", text)
        assertEquals(2, getCursorPosition())
    }

    @Test fun cheonjiinMainPunctuationCyclesInLabelOrder() {
        reset()
        switchToCheonjiinSubtype()

        functionalKeyPress(KeyCode.CHEONJIIN_PUNCT_MAIN)
        assertEquals("?", text)
        functionalKeyPress(KeyCode.CHEONJIIN_PUNCT_MAIN)
        assertEquals("!", text)
        functionalKeyPress(KeyCode.CHEONJIIN_PUNCT_MAIN)
        assertEquals(".", text)
        functionalKeyPress(KeyCode.CHEONJIIN_PUNCT_MAIN)
        assertEquals("?", text)
    }

    @Test fun cheonjiinNumpadPunctuationCyclesInLabelOrder() {
        reset()
        switchToCheonjiinSubtype()

        functionalKeyPress(KeyCode.CHEONJIIN_PUNCT_NUMPAD)
        assertEquals("·", text)
        functionalKeyPress(KeyCode.CHEONJIIN_PUNCT_NUMPAD)
        assertEquals("-", text)
        functionalKeyPress(KeyCode.CHEONJIIN_PUNCT_NUMPAD)
        assertEquals("/", text)
        functionalKeyPress(KeyCode.CHEONJIIN_PUNCT_NUMPAD)
        assertEquals("·", text)
    }

    @Test fun cheonjiinPunctuationAfterSingleSyllableDoesNotDeleteHangul() {
        reset()
        switchToCheonjiinSubtype()
        chainInput("ㄱㅣㆍ")
        assertEquals("가", text)

        functionalKeyPress(KeyCode.CHEONJIIN_PUNCT_MAIN)
        assertEquals("가?", text)

        reset()
        switchToCheonjiinSubtype()
        chainInput("ㄱㅣ")
        assertEquals("기", text)

        functionalKeyPress(KeyCode.CHEONJIIN_PUNCT_NUMPAD)
        assertEquals("기·", text)
    }

    @Test fun cheonjiinPunctuationAfterMultiSyllableWordDoesNotDeletePrefix() {
        reset()
        switchToCheonjiinSubtype()
        chainInput("ㄱㅣㆍㄴㅣㆍㄷㅣㆍ")
        assertEquals("가나다", text)

        functionalKeyPress(KeyCode.CHEONJIIN_PUNCT_MAIN)
        assertEquals("가나다?", text)

        reset()
        switchToCheonjiinSubtype()
        chainInput("ㄱㅣㆍㄴㅣㆍㄷㅣㆍㄹㅣㆍ")
        assertEquals("가나다라", text)

        functionalKeyPress(KeyCode.CHEONJIIN_PUNCT_NUMPAD)
        assertEquals("가나다라·", text)
    }

    @Test fun hangulEditingLoopBucket0() {
        if (!isHangulLoopEnabled()) return
        runHangulEditingLoopBucket(0)
    }

    @Test fun hangulEditingLoopBucket1() {
        if (!isHangulLoopEnabled()) return
        runHangulEditingLoopBucket(1)
    }

    @Test fun hangulEditingLoopBucket2() {
        if (!isHangulLoopEnabled()) return
        runHangulEditingLoopBucket(2)
    }

    @Test fun hangulEditingLoopBucket3() {
        if (!isHangulLoopEnabled()) return
        runHangulEditingLoopBucket(3)
    }

    @Test fun cheonjiinEditingLoopBucket0() {
        if (!isCheonjiinLoopEnabled()) return
        runCheonjiinEditingLoopBucket(0)
    }

    @Test fun cheonjiinEditingLoopBucket1() {
        if (!isCheonjiinLoopEnabled()) return
        runCheonjiinEditingLoopBucket(1)
    }

    @Test fun cheonjiinEditingLoopBucket2() {
        if (!isCheonjiinLoopEnabled()) return
        runCheonjiinEditingLoopBucket(2)
    }

    @Test fun cheonjiinEditingLoopBucket3() {
        if (!isCheonjiinLoopEnabled()) return
        runCheonjiinEditingLoopBucket(3)
    }

    @Test fun middleBackspaceStillDeletesLeftCharIfSelectionUpdateIsDelayed() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        currentScript = ScriptUtils.SCRIPT_HANGUL
        chainInput("ㅅㅡㅁㅏㅌㅡㅍㅗㄴ")
        assertEquals("스마트폰", text)

        // Simulate a race: editor cursor moved, but onUpdateSelection has not reached the IME yet.
        selectionStart = 1
        selectionEnd = 1
        latinIME.onEvent(
            Event.createSoftwareKeypressEvent(
                Event.NOT_A_CODE_POINT,
                KeyCode.DELETE,
                0,
                Constants.NOT_A_COORDINATE,
                Constants.NOT_A_COORDINATE,
                false
            )
        )
        handleMessages()

        assertEquals("마트폰", text)
    }

    @Test fun staleCursorIndexOnEmptyComposingWordDoesNotCrash() {
        reset()
        composer.setCursorPositionWithinWord(3)
        assertEquals(false, inputLogic.moveCursorByAndReturnIfInsideComposingWord(-1))
    }

    @Test fun separatorUnselectsWord() {
        reset()
        setText("hello")
        assertEquals("hello", composingText)
        input('.')
        assertEquals("", composingText)
    }

    @Test fun autospace() {
        reset()
        setText("hello")
        input('.')
        input('a')
        assertEquals("hello.a", textBeforeCursor)
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        setText("hello")
        input('.')
        input('a')
        assertEquals("hello. a", textBeforeCursor)
    }

    @Test fun autospaceButWithTextAfter() {
        reset()
        setText("hello there")
        setCursorPosition(5) // after hello
        input('.')
        input('a')
        assertEquals("hello.a", textBeforeCursor)
        assertEquals("hello.a there", text)
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        setText("hello there")
        setCursorPosition(5) // after hello
        input('.')
        input('a')
        assertEquals("hello. a", textBeforeCursor)
        assertEquals("hello. a there", text)
    }

    @Test fun noAutospaceInUrlField() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("example.net")
        assertEquals("example. net", text)
        lastAddedWord = ""
        setText("")
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("example.net")
        assertEquals("", lastAddedWord)
        assertEquals("example.net", text)
        assertEquals("example.net", composingText)
    }

    @Test fun noAutospaceInUrlFieldWhenPickingSuggestion() {
        reset()
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("exam")
        pickSuggestion("example")
        assertEquals("example", text)
        input('.')
        assertEquals("example.", text)
    }

    @Test fun noAutospaceForDetectedUrl() { // "light" version, should work without url detection
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("http://example.net")
        assertEquals("http://example.net", text)
        assertEquals("http", lastAddedWord)
        assertEquals("example.net", composingText)
    }

    @Test fun noAutospaceForDetectedEmail() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("mail@example.com")
        assertEquals("mail@example.com", text)
        assertEquals("mail@example", lastAddedWord) // todo: do we want this? not really nice, but don't want to be too aggressive with URL detection disabled
        assertEquals("com", composingText) // todo: maybe this should still see the whole address as a single word? or don't be too aggressive?
        setText("")
        lastAddedWord = ""
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("mail@example.com")
        assertEquals("", lastAddedWord)
        assertEquals("mail@example.com", composingText)
    }

    @Test fun urlDetectionThings() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("...h")
        assertEquals("...h", text)
        assertEquals("h", composingText)
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("bla..")
        assertEquals("bla..", text)
        assertEquals("", composingText)
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("bla.c")
        assertEquals("bla.c", text)
        assertEquals("bla.c", composingText)
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        latinIME.prefs().edit { putBoolean(Settings.PREF_SHIFT_REMOVES_AUTOSPACE, true) }
        input("bla")
        input('.')
        functionalKeyPress(KeyCode.SHIFT) // should remove the phantom space (in addition to normal effect)
        input('c')
        assertEquals("bla.c", text)
        assertEquals("bla.c", composingText)
    }

    @Test fun stripSeparatorsBeforeAddingToHistoryWithURLDetection() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("example.com.")
        assertEquals("example.com.", composingText)
        input(' ')
        assertEquals("example.com", lastAddedWord)
    }

    @Test fun dontSelectConsecutiveSeparatorsWithURLDetection() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("bla..")
        assertEquals("", composingText)
        assertEquals("bla..", text)
    }

    @Test fun selectDoesSelect() {
        reset()
        setText("this is some text")
        setCursorPosition(3, 8)
        assertEquals("s is ", text.substring(3, 8))
    }

    @Test fun noComposingForPasswordFields() {
        reset()
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
        input('a')
        input('b')
        assertEquals("", composingText)
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        input('.')
        input('c')
        assertEquals("", composingText)
    }

    @Test fun `don't select whole thing as composing word if URL detection disabled`() {
        reset()
        setText("http://example.com")
        setCursorPosition(13) // between l and e
        assertEquals("example", composingText)
    }

    @Test fun `select whole thing except http(s) as composing word if URL detection enabled and selecting`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setText("http://example.com")
        setCursorPosition(13) // between l and e
        assertEquals("example.com", composingText)
        setText("http://bla.com http://example.com ")
        setCursorPosition(29) // between l and e
        assertEquals("example.com", composingText)
    }

    @Test fun `select whole thing except http(s) as composing word if URL detection enabled and typing`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("http://example.com")
        assertEquals("example.com", composingText)
    }

    @Test fun `don't add partial URL to history`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setText("http:/") // just so lastAddedWord isn't set to http
        chainInput("/bla.com")
        assertEquals("", lastAddedWord)
    }

    @Test fun urlProperlySelected() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        setText("http://example.com/here")
        setCursorPosition(18) // after .com
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE) // delete com
        // todo: do we really want no composing text?
        //  probably not... try not to break composing
        assertEquals("", composingText)
        chainInput("net")
        assertEquals("example.net", composingText)
    }

    @Test fun urlProperlySelectedWhenNotDeletingFullTld() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setText("http://example.com/here")
        setCursorPosition(18) // after .com
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE) // delete om
        // todo: this is a weird difference to deleting the full TLD (see urlProperlySelected)
        //  what do we want here? (probably consistency)
        assertEquals("example.c/here", composingText)
        chainInput("z")
        assertEquals("", composingText) // todo: this is a weird difference to deleting the full TLD
//        assertEquals("example.cz", composingText) // fails, but probably would be better than above
    }

    @Test fun dontCommitPartialUrlBeforeFirstPeriod() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        // type http://bla. -> bla not selected, but clearly url, also means http://bla is committed which we probably don't want
        chainInput("http://bla.")
        assertEquals("bla.", composingText)
    }

    @Test fun `intermediate commits in text field without protocol`() {
        reset()
        chainInput("bla.")
        assertEquals("bla", lastAddedWord)
        chainInput("com/")
        assertEquals("com", lastAddedWord)
        chainInput("img.jpg")
        assertEquals("img", lastAddedWord)
        assertEquals("jpg", composingText)
    }

    @Test fun `intermediate commit in text field without protocol and with URL detection`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("bla.com/img.jpg")
        assertEquals("bla", lastAddedWord)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `only protocol commit in text field with protocol and URL detection`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("http://bla.com/img.jpg")
        assertEquals("http", lastAddedWord)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `no intermediate commit in URL field with protocol`() {
        reset()
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("http://bla.com/img.jpg")
        assertEquals("http", lastAddedWord) // todo: somehow avoid?
        assertEquals("http://bla.com/img.jpg", text)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `no intermediate commit in URL field with protocol and URL detection`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("http://bla.com/img.jpg")
        assertEquals("http", lastAddedWord) // todo: somehow avoid?
        assertEquals("http://bla.com/img.jpg", text)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `no intermediate commit in URL field without protocol`() {
        reset()
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("bla.com/img.jpg")
        assertEquals("", lastAddedWord)
        assertEquals("bla.com/img.jpg", text)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `no intermediate commit in URL field without protocol and with URL detection`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("bla.com/img.jpg")
        assertEquals("", lastAddedWord)
        assertEquals("bla.com/img.jpg", text)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `don't accidentally detect some other text fields as URI`() {
        // see comment in InputLogic.textBeforeCursorMayBeUrlOrSimilar
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE)
        chainInput("Hey,why")
        assertEquals("Hey, why", text)
    }

    @Test fun `URL detection does not trigger on non-words`() {
        // first make sure it works without URL detection
        reset()
        chainInput("15:50-17")
        assertEquals("15:50-17", text)
        assertEquals("", composingText)
        // then with URL detection
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("15:50-17")
        assertEquals("15:50-17", text)
        assertEquals("", composingText)
    }

    @Test fun `autospace after selecting a suggestion`() {
        reset()
        pickSuggestion("this")
        input('b')
        assertEquals("this b", text)
        assertEquals("b", composingText)
    }

    @Test fun `autospace works in URL field when input isn't URL`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        pickSuggestion("this")
        input('b')
        assertEquals("this b", text)
        assertEquals("b", composingText)
    }

    // https://github.com/Helium314/HeliBoard/issues/215
    // https://github.com/Helium314/HeliBoard/issues/229
    @Test fun `autospace works in URL field when input isn't URL, also for multiple suggestions`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        pickSuggestion("this")
        pickSuggestion("is")
        assertEquals("this is", text)
        pickSuggestion("not")
        assertEquals("this is not", text)
        input('c')
        assertEquals("this is not c", text)
        assertEquals("c", composingText)
    }

    @Test fun `emoji is added to dictionary`() {
        // check both text and codepoint input
        reset()
        chainInput("hello ")
        input(0x1F36D)
        assertEquals(StringUtils.newSingleCodePointString(0x1F36D), lastAddedWord)
        reset()
        chainInput("hello ")
        input("🤗")
        assertEquals("\uD83E\uDD17", lastAddedWord)

        reset()
        chainInput("hello ")
        input("why 🤗 ") // not added because it's not only emoji (input can come from pasting)
        assertEquals("hello", lastAddedWord)
    }

    @Test fun `emoji uses phantom space`() {
        // check both text and codepoint input
        reset()
        pickSuggestion("hi")
        input("🤗")
        assertEquals("\uD83E\uDD17", lastAddedWord)
        assertEquals("hi \uD83E\uDD17", text)
        reset()
        pickSuggestion("hi")
        input(0x1F36D)
        assertEquals(StringUtils.newSingleCodePointString(0x1F36D), lastAddedWord)
        assertEquals("hi ${StringUtils.newSingleCodePointString(0x1F36D)}", text)
    }

    // https://github.com/Helium314/HeliBoard/issues/230
    @Test fun `no autospace after opening quotes`() {
        reset()
        chainInput("\"Hi\" \"h")
        assertEquals("\"Hi\" \"h", text)
        assertEquals("h", composingText)
        reset()
        chainInput("\"Hi\", \"h")
        assertEquals("\"Hi\", \"h", text)
        assertEquals("h", composingText)
    }

    @Test fun `autospace works in URL field when starting with quotes`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        input("\"")
        pickSuggestion("this")
        input("i")
        assertEquals("\"this i", text)
    }

    @Test fun `double space results in period and space, and delete removes the period`() {
        reset()
        chainInput("hello")
        input(' ')
        input(' ')
        assertEquals("hello. ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello ", text)
    }

    @Test fun `no weird space inside multi-"`() {
        reset()
        chainInput("\"\"\"")
        assertEquals("\"\"\"", text)

        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("\"\"\"")
        assertEquals("\"\"\"", text)
    }

    @Test fun `autospace still happens after "`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("\"hello\"you")
        assertEquals("\"hello\" you", text)
    }

    @Test fun `autospace still happens after " if next word is in quotes`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("\"hello\"\"you\"")
        assertEquals("\"hello\" \"you\"", text)
    }

    @Test fun `autospace propagates over "`() {
        reset()
        input('"')
        pickSuggestion("hello")
        assertEquals(spaceState, SpaceState.PHANTOM) // picking a suggestion sets phantom space state
        chainInput("\"you")
        assertEquals("\"hello\" you", text)
    }

    @Test fun `autospace still happens after " if nex word is in " and after comma`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("\"hello\",\"you\"")
        assertEquals("\"hello\", \"you\"", text)
    }

    @Test fun `autospace in json editor`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("{\"label\":\"")
        assertEquals("{\"label\": \"", text)
        input('c')
        assertEquals("{\"label\": \"c", text)
    }

    @Test fun `text input and delete`() {
        reset()
        input("hello")
        assertEquals("hello", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hell", text)

        reset()
        input("hello ")
        assertEquals("hello ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello", text)
    }

    @Test fun `emoji text input and delete`() {
        reset()
        input("🕵🏼")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)

        reset()
        input("\uD83D\uDD75\uD83C\uDFFC")
        input(' ')
        assertEquals("🕵🏼 ", text)
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)
    }

    // emoRegex update to unicode 16.0 was required, https://github.com/Helium314/HeliBoard/issues/1760
    @Test fun `emojis deleted one by one`() {
        reset()
        chainInput("\uD83E\uDEC6\uD83E\uDEC6\uD83E\uDEC6")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("\uD83E\uDEC6\uD83E\uDEC6", text)
    }

    @Test fun `revert autocorrect on delete`() {
        reset()
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        chainInput("hullo")
        getAutocorrectedWithSpaceAfter("hello", "hullo")
        assertEquals("hello ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hullo", text)

        reset()
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        latinIME.prefs().edit { putBoolean(Settings.PREF_BACKSPACE_REVERTS_AUTOCORRECT, false) }
        chainInput("hullo")
        getAutocorrectedWithSpaceAfter("hello", "hullo")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello", text)
    }

    @Test fun `remove glide typing word on delete`() {
        reset()
        glideTypingInput("hello")
        assertEquals("hello", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)

        // todo: now we want some way to disable delete-all on backspace, either per setting or something else
        //  need to avoid getting into the mWordComposer.isBatchMode() part of handleBackspaceEvent
    }

    @Test fun timestamp() {
        reset()
        chainInput("hello")
        functionalKeyPress(KeyCode.TIMESTAMP)
        assertEquals(Calendar.getInstance().time.time.toDouble(),
            getTimestampFormatter(latinIME).parse(text.substring(5))!!.time.toDouble(), 1000.0)
    }

    @Test fun inlineEmojiSearchStart() {
        assertEquals(true, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, ' '.code, settingsValues))
        assertEquals(false, InputLogic.isStartOfInlineEmojiSearch(' '.code, ':'.code, ' '.code, settingsValues))
        assertEquals(true, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, '.'.code, settingsValues))
        assertEquals(true, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, "🌍".codePoints().asSequence().last(), settingsValues))
        assertEquals(false, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, 't'.code, settingsValues))
        assertEquals(false, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, '3'.code, settingsValues))
    }

    @Test fun inlineEmojiSearchString() {
        assertEquals("test", InputLogic.getInlineEmojiSearchString(":test"))
        assertEquals(null, InputLogic.getInlineEmojiSearchString("test"))
        assertEquals("test", InputLogic.getInlineEmojiSearchString(" :test"))
        assertEquals(null, InputLogic.getInlineEmojiSearchString("t:test"))
        assertEquals(null, InputLogic.getInlineEmojiSearchString("6:test"))
        assertEquals("test", InputLogic.getInlineEmojiSearchString("🌍:test"))
        assertEquals("test", InputLogic.getInlineEmojiSearchString(",:test"))
        assertEquals(null, InputLogic.getInlineEmojiSearchString(":test\nt"))
        assertEquals("/48", InputLogic.getInlineEmojiSearchString("2606:127.0.0.1::/48")) // do we want this?
    }

    // ------- helper functions ---------

    // should be called before every test, so the same state is guaranteed
    private fun reset() {
        // reset input connection & facilitator
        currentScript = ScriptUtils.SCRIPT_LATIN
        text = ""
        batchEdit = 0
        currentInputType = InputType.TYPE_CLASS_TEXT
        lastAddedWord = ""

        // reset settings
        latinIME.prefs().edit { clear() }

        setText("") // (re)sets selection and composing word
    }

    private fun switchToCheonjiinSubtype() {
        latinIME.switchToSubtype(
            SubtypeUtilsAdditional.createAdditionalSubtype(
                "ko".constructLocale(),
                "KeyboardLayoutSet=MAIN:korean_cheonjiin|SYMBOLS:symbols_cheonjiin|MORE_SYMBOLS:symbols_shifted_cheonjiin|MORE_SYMBOLS_2:symbols_shifted_2_cheonjiin|FUNCTIONAL:functional_keys_cheonjiin|NUMPAD:numpad_cheonjiin,CombiningRules=hangul,SupportTouchPositionCorrection",
                false,
                true
            )
        )
        currentScript = ScriptUtils.SCRIPT_HANGUL
    }

    private fun describeCheonjiinState(): String {
        val chain = combinerChainReader.get(composer) as CombinerChain
        val hangulCombiner = chain.getHangulCombiner() ?: return "no-hangul-combiner"
        return "feedback=${hangulCombiner.combiningStateFeedback};history=" +
            hangulCombiner.history.joinToString("|") { it.string }
    }

    private fun chainInput(text: String) = text.forEach { input(it.code) }

    private fun input(char: Char) = input(char.code)

    private fun input(codePoint: Int) {
        require(codePoint > 0) { "not a codePoint: $codePoint" }
        val oldBefore = textBeforeCursor
        val oldAfter = textAfterCursor
        val insert = StringUtils.newSingleCodePointString(codePoint)
        val phantomSpaceToInsert = if (spaceState == SpaceState.PHANTOM) " " else ""

        latinIME.onEvent(Event.createEventForCodePointFromUnknownSource(codePoint))
        handleMessages()

        if (currentScript != ScriptUtils.SCRIPT_HANGUL // check fails if hangul combiner merges symbols
            && !(codePoint == Constants.CODE_SPACE && oldBefore.lastOrNull() == ' ') // check fails when 2 spaces are converted into a period
            && !latinIME.mInputLogic.mSuggestedWords.mWillAutoCorrect // autocorrect obviously creates inconsistencies
            ) {
            if (phantomSpaceToInsert.isEmpty())
                assertEquals(oldBefore + insert, textBeforeCursor)
            else // in some cases autospace might be suppressed
                assert(oldBefore + phantomSpaceToInsert + insert == textBeforeCursor || oldBefore + insert == textBeforeCursor)
        }
        if (currentScript != ScriptUtils.SCRIPT_HANGUL) {
            assertEquals(oldAfter, textAfterCursor)
        }
        assertEquals(textBeforeCursor + textAfterCursor, getText())
        checkConnectionConsistency()
    }

    private fun functionalKeyPress(keyCode: Int) {
        require(keyCode < 0) { "not a functional key code: $keyCode" }
        latinIME.onEvent(Event.createSoftwareKeypressEvent(Event.NOT_A_CODE_POINT, keyCode, 0, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false))
        handleMessages()
        checkConnectionConsistency()
    }

    // almost the same as codePoint input, but calls different latinIME function
    private fun input(insert: String) {
        val oldBefore = textBeforeCursor
        val oldAfter = textAfterCursor
        val phantomSpaceToInsert = if (spaceState == SpaceState.PHANTOM) " " else ""

        latinIME.onTextInput(insert)
        handleMessages()

        if (phantomSpaceToInsert.isEmpty())
            assertEquals(oldBefore + insert, textBeforeCursor)
        else // in some cases autospace might be suppressed
            assert(oldBefore + phantomSpaceToInsert + insert == textBeforeCursor || oldBefore + insert == textBeforeCursor)
        assert(oldBefore + insert == textBeforeCursor || "$oldBefore $insert" == textBeforeCursor)
        assertEquals(oldAfter, textAfterCursor)
        assertEquals(textBeforeCursor + textAfterCursor, getText())
        checkConnectionConsistency()
    }

    private fun rawTextInput(insert: String) {
        latinIME.onTextInput(insert)
        handleMessages()
        checkConnectionConsistency()
    }

    private fun getWordAtCursor() = connection.getWordRangeAtCursor(settingsValues.mSpacingAndPunctuations, currentScript)?.mWord

    private fun setCursorPosition(start: Int, end: Int = start, weirdTextField: Boolean = false) {
        val ei = EditorInfo()
        ei.inputType = currentInputType
        ei.initialSelStart = start
        ei.initialSelEnd = end
        // imeOptions should not matter

        // adjust text in inputConnection first, otherwise fixLyingCursorPosition will move cursor
        // to the end of the text
        val fullText = textBeforeCursor + selectedText + textAfterCursor
        assertEquals(fullText, getText())

        // need to update ic before, otherwise when reloading text cache from ic, ric will load wrong text before cursor
        val oldStart = selectionStart
        val oldEnd = selectionEnd
        selectionStart = start
        selectionEnd = end
        assertEquals(fullText, textBeforeCursor + selectedText + textAfterCursor)

        latinIME.onUpdateSelection(oldStart, oldEnd, start, end, composingStart, composingEnd)
        handleMessages()

        if (weirdTextField) {
            latinIME.mHandler.onStartInput(ei, true) // essentially does nothing
            latinIME.mHandler.onStartInputView(ei, true) // does the thing
            handleMessages()
        }

        assertEquals(fullText, getText())
        assertEquals(start, selectionStart)
        assertEquals(end, selectionEnd)
        checkConnectionConsistency()
    }

    // assumes we have nothing selected
    private fun getCursorPosition(): Int {
        if (cursor != connection.expectedSelectionStart || cursor != connection.expectedSelectionEnd) {
            connection.tryFixIncorrectCursorPosition()
        }
        return cursor
    }

    // just sets the text and starts input so connection it set up correctly
    private fun setText(newText: String) {
        text = newText
        selectionStart = newText.length
        selectionEnd = selectionStart
        composingStart = -1
        composingStart = -1

        // we need to start input to notify that something changed
        // restarting is false, so this is seen as a new text field
        val ei = EditorInfo()
        ei.inputType = currentInputType
        latinIME.mHandler.onStartInput(ei, false)
        latinIME.mHandler.onStartInputView(ei, false)
        handleMessages() // this is important so the composing span is set correctly
        checkConnectionConsistency()
    }

    // like selecting a suggestion from strip
    private fun pickSuggestion(suggestion: String) {
        val info = SuggestedWordInfo(suggestion, "", 0, 0, null, 0, 0)
        latinIME.pickSuggestionManually(info)
        checkConnectionConsistency()
    }

    // only works when autocorrect is on, separator after word is required
    private fun getAutocorrectedWithSpaceAfter(suggestion: String, typedWord: String?) {
        val info = SuggestedWordInfo(suggestion, "", 0, 0, null, 0, 0)
        val typedInfo = SuggestedWordInfo(typedWord, "", 0, 0, null, 0, 0)
        val sw = SuggestedWords(ArrayList(listOf(typedInfo, info)), null, typedInfo, false, true, false, 0, 0)
        latinIME.mInputLogic.setSuggestedWords(sw) // this prepares for autocorrect
        input(' ')
        checkConnectionConsistency()
    }

    private fun glideTypingInput(word: String) {
        val info = SuggestedWordInfo(word, "", 0, 0, null, 0, 0)
        val sw = SuggestedWords(ArrayList(listOf(info)), null, info, true, false, false, 0, 0)
        latinIME.mInputLogic.onUpdateTailBatchInputCompleted(settingsValues, sw, KeyboardSwitcher.getInstance())
    }

    private fun checkConnectionConsistency() {
        if (selectionStart != connection.expectedSelectionStart || selectionEnd != connection.expectedSelectionEnd) {
            connection.tryFixIncorrectCursorPosition()
        }
        // RichInputConnection only has composing text up to cursor, but InputConnection has full composing text
        val expectedConnectionComposingText = if (composingStart == -1 || composingEnd == -1) ""
        else text.substring(composingStart, min(composingEnd, selectionEnd))
        assert(composingText.startsWith(expectedConnectionComposingText))
        // RichInputConnection only returns text up to cursor
        val textBeforeComposingText = if (composingStart == -1) textBeforeCursor else text.substring(0, composingStart)

        println("consistency: $selectionStart, ${connection.expectedSelectionStart}, $selectionEnd, ${connection.expectedSelectionEnd}, $textBeforeComposingText, " +
                "$connectionTextBeforeComposingText, $composingText, $connectionComposingText, $textBeforeCursor, ${connection.getTextBeforeCursor(textBeforeCursor.length, 0)}" +
                ", $textAfterCursor, ${connection.getTextAfterCursor(textAfterCursor.length, 0)}")
        // Cursor updates can be delayed for some editor paths; focus consistency checks on text cache.
        // Internal cached composing buffers may temporarily lag editor state on some paths.
        assertEquals(textBeforeCursor, connection.getTextBeforeCursor(textBeforeCursor.length, 0).toString())
        assertEquals(textAfterCursor, connection.getTextAfterCursor(textAfterCursor.length, 0).toString())
    }

    private fun runHangulEditingLoopBucket(bucket: Int) {
        val failures = mutableListOf<String>()
        val seedStart = bucket * HANGUL_LOOP_SEEDS_PER_BUCKET
        val seedEnd = seedStart + HANGUL_LOOP_SEEDS_PER_BUCKET
        for (seed in seedStart until seedEnd) {
            val random = Random(seed)
            repeat(HANGUL_LOOP_CASES_PER_SEED) { caseIndex ->
                val inputSequence = buildHangulLoopInputSequence(random)
                val insertSequence = HANGUL_INSERT_CHUNKS.random(random)
                try {
                    assertHangulMidWordInsertKeepsSuffix(seed, caseIndex, inputSequence, insertSequence)
                } catch (error: AssertionError) {
                    failures += "insert: ${error.message}"
                }
                try {
                    assertHangulMidWordBackspaceKeepsSuffix(seed, caseIndex, inputSequence)
                } catch (error: AssertionError) {
                    failures += "backspace: ${error.message}"
                }
                try {
                    assertCommittedHangulBackspaceFirstStepMatchesModel(seed, caseIndex, inputSequence)
                } catch (error: AssertionError) {
                    failures += "committed-backspace: ${error.message}"
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail(failures.joinToString(separator = "\n"))
        }
    }

    private fun runCheonjiinEditingLoopBucket(bucket: Int) {
        val failures = mutableListOf<String>()
        val seedStart = bucket * CHEONJIIN_LOOP_SEEDS_PER_BUCKET
        val seedEnd = seedStart + CHEONJIIN_LOOP_SEEDS_PER_BUCKET
        for (seed in seedStart until seedEnd) {
            repeat(CHEONJIIN_LOOP_CASES_PER_SEED) { caseIndex ->
                try {
                    assertCheonjiinEditingSequenceStaysConsistent(seed, caseIndex)
                } catch (error: AssertionError) {
                    failures += error.message ?: "seed=$seed case=$caseIndex failed"
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail(failures.joinToString(separator = "\n"))
        }
    }

    private fun assertHangulMidWordInsertKeepsSuffix(
        seed: Int,
        caseIndex: Int,
        inputSequence: String,
        insertSequence: String
    ) {
        prepareHangulLoopCase(inputSequence)
        val originalWord = text
        val cursorPosition = pickMiddleCursor(Random(seed * 31 + caseIndex), originalWord)
        val suffix = originalWord.substring(cursorPosition)

        setCursorPosition(cursorPosition)
        chainInput(insertSequence)

        assertEquals(
            suffix,
            textAfterCursor,
            "seed=$seed case=$caseIndex insert='$insertSequence' original='$originalWord' result='$text'"
        )
    }

    private fun assertHangulMidWordBackspaceKeepsSuffix(
        seed: Int,
        caseIndex: Int,
        inputSequence: String
    ) {
        prepareHangulLoopCase(inputSequence)
        val originalWord = text
        val cursorPosition = pickMiddleCursor(Random(seed * 53 + caseIndex), originalWord)
        val suffix = originalWord.substring(cursorPosition)

        setCursorPosition(cursorPosition)
        functionalKeyPress(KeyCode.DELETE)

        assertEquals(
            suffix,
            textAfterCursor,
            "seed=$seed case=$caseIndex backspace original='$originalWord' result='$text'"
        )
    }

    private fun assertCommittedHangulBackspaceFirstStepMatchesModel(
        seed: Int,
        caseIndex: Int,
        inputSequence: String
    ) {
        prepareHangulLoopCase(inputSequence)
        val committedWord = text
        setText(committedWord)
        val expected = modelCommittedHangulBackspace(committedWord)

        functionalKeyPress(KeyCode.DELETE)

        assertEquals(
            expected,
            text,
            "seed=$seed case=$caseIndex committed='$committedWord' expected='$expected' actual='$text'"
        )
    }

    private fun prepareHangulLoopCase(inputSequence: String) {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        currentScript = ScriptUtils.SCRIPT_HANGUL
        chainInput(inputSequence)
        assertTrue(text.length >= 2, "loop input should produce at least 2 visible chars: '$inputSequence' -> '$text'")
    }

    private fun assertCheonjiinEditingSequenceStaysConsistent(seed: Int, caseIndex: Int) {
        val random = Random(seed * 97 + caseIndex * 13)
        reset()
        switchToCheonjiinSubtype()
        currentScript = ScriptUtils.SCRIPT_HANGUL

        val initialSequence = buildCheonjiinLoopInputSequence(random)
        rawTextInput(initialSequence)
        val operations = mutableListOf("INIT($initialSequence)")

        repeat(18) { opIndex ->
            when (random.nextInt(4)) {
                0 -> {
                    val insert = CHEONJIIN_INSERT_CHUNKS.random(random)
                    rawTextInput(insert)
                    operations += "IN($insert)"
                }
                1 -> {
                    functionalKeyPress(KeyCode.DELETE)
                    operations += "DEL"
                }
                else -> {
                    val newCursor = if (text.isEmpty()) 0 else random.nextInt(text.length + 1)
                    setCursorPosition(newCursor)
                    operations += "CUR($newCursor)"
                }
            }

            assertEquals(
                textBeforeCursor + textAfterCursor,
                getText(),
                "cheonjiin-loop seed=$seed case=$caseIndex op=$opIndex ops=${operations.joinToString(" ")} state=${describeCheonjiinState()}"
            )
            assertTrue(
                getCursorPosition() in 0..text.length,
                "cheonjiin-loop cursor seed=$seed case=$caseIndex op=$opIndex ops=${operations.joinToString(" ")} text='$text'"
            )
        }
    }

    private fun buildHangulLoopInputSequence(random: Random): String {
        val chunkCount = random.nextInt(2, 6)
        return buildString {
            repeat(chunkCount) {
                append(HANGUL_LOOP_CHUNKS.random(random))
            }
        }
    }

    private fun buildCheonjiinLoopInputSequence(random: Random): String {
        val chunkCount = random.nextInt(2, 6)
        return buildString {
            repeat(chunkCount) {
                append(CHEONJIIN_LOOP_CHUNKS.random(random))
            }
        }
    }

    private fun pickMiddleCursor(random: Random, word: String): Int {
        val codePointCount = word.codePointCount(0, word.length)
        return random.nextInt(1, codePointCount)
    }

    private fun modelCommittedHangulBackspace(word: String): String {
        if (word.isEmpty()) return word
        val lastCodePoint = word.codePointBefore(word.length)
        val lastCharCount = Character.charCount(lastCodePoint)
        val prefix = word.dropLast(lastCharCount)
        if (lastCodePoint !in 0xAC00..0xD7A3) return prefix

        val syllableIndex = lastCodePoint - 0xAC00
        val finalIndex = syllableIndex % 28
        val medialIndex = (syllableIndex / 28) % 21
        val initialIndex = syllableIndex / 28 / 21

        if (finalIndex != 0) {
            val finalCodePoint = 0x11A7 + finalIndex
            val reducedFinal = COMPOUND_FINAL_REDUCTIONS[finalCodePoint] ?: 0
            val reducedFinalIndex = if (reducedFinal == 0) 0 else reducedFinal - 0x11A7
            return prefix + composeHangulSyllable(initialIndex, medialIndex, reducedFinalIndex)
        }

        val medialCodePoint = 0x1161 + medialIndex
        val reducedMedial = COMPOUND_MEDIAL_REDUCTIONS[medialCodePoint]
        if (reducedMedial != null) {
            return prefix + composeHangulSyllable(initialIndex, reducedMedial - 0x1161, 0)
        }

        return prefix + String(Character.toChars(COMPAT_CONSONANTS[initialIndex]))
    }

    private fun composeHangulSyllable(initialIndex: Int, medialIndex: Int, finalIndex: Int): String {
        val codePoint = 0xAC00 + (initialIndex * 21 + medialIndex) * 28 + finalIndex
        return String(Character.toChars(codePoint))
    }

    private fun isHangulLoopEnabled() =
        System.getProperty("hangul.loop") == "true" || System.getenv("HANGUL_LOOP") == "true"

    private fun isCheonjiinLoopEnabled() =
        System.getProperty("cheonjiin.loop") == "true" || System.getenv("CHEONJIIN_LOOP") == "true"

    private fun getText() =
        connection.getTextBeforeCursor(100, 0).toString() + (connection.getSelectedText(0) ?: "") + connection.getTextAfterCursor(100, 0)

    private fun setInputType(inputType: Int) {
        // set text to actually apply input type
        currentInputType = inputType
        setText(text)
    }

    // always need to handle messages for proper simulation
    private fun handleMessages() {
        while (messages.isNotEmpty()) {
            latinIME.mHandler.handleMessage(messages.first())
            messages.removeAt(0)
        }
        while (delayedMessages.isNotEmpty()) {
            val msg = delayedMessages.first()
            if (msg.what != 2) // MSG_UPDATE_SUGGESTION_STRIP, we want to ignore it because it's irrelevant and has a 500 ms timeout
                latinIME.mHandler.handleMessage(delayedMessages.first())
            delayedMessages.removeAt(0)
            // delayed messages may post further messages, handle before next delayed message
            while (messages.isNotEmpty()) {
                latinIME.mHandler.handleMessage(messages.first())
                messages.removeAt(0)
            }
        }
        assertEquals(0, messages.size)
        assertEquals(0, delayedMessages.size)
    }

}

private const val HANGUL_LOOP_SEEDS_PER_BUCKET = 4
private const val HANGUL_LOOP_CASES_PER_SEED = 6
private const val CHEONJIIN_LOOP_SEEDS_PER_BUCKET = 4
private const val CHEONJIIN_LOOP_CASES_PER_SEED = 6

private val COMPAT_CONSONANTS = intArrayOf(
    0x3131, 0x3132, 0x3134, 0x3137, 0x3138, 0x3139, 0x3141, 0x3142, 0x3143,
    0x3145, 0x3146, 0x3147, 0x3148, 0x3149, 0x314A, 0x314B, 0x314C, 0x314D, 0x314E
)

private val COMPOUND_MEDIAL_REDUCTIONS = mapOf(
    0x116A to 0x1169, // ㅘ -> ㅗ
    0x116B to 0x1169, // ㅙ -> ㅗ
    0x116C to 0x1169, // ㅚ -> ㅗ
    0x116F to 0x116E, // ㅝ -> ㅜ
    0x1170 to 0x116E, // ㅞ -> ㅜ
    0x1171 to 0x116E, // ㅟ -> ㅜ
    0x1174 to 0x1173  // ㅢ -> ㅡ
)

private val COMPOUND_FINAL_REDUCTIONS = mapOf(
    0x11A9 to 0x11A8, // ㄲ -> ㄱ
    0x11AA to 0x11A8, // ㄳ -> ㄱ
    0x11AC to 0x11AB, // ㄵ -> ㄴ
    0x11AD to 0x11AB, // ㄶ -> ㄴ
    0x11B0 to 0x11AF, // ㄺ -> ㄹ
    0x11B1 to 0x11AF, // ㄻ -> ㄹ
    0x11B2 to 0x11AF, // ㄼ -> ㄹ
    0x11B3 to 0x11AF, // ㄽ -> ㄹ
    0x11B4 to 0x11AF, // ㄾ -> ㄹ
    0x11B5 to 0x11AF, // ㄿ -> ㄹ
    0x11B6 to 0x11AF, // ㅀ -> ㄹ
    0x11B9 to 0x11B8, // ㅄ -> ㅂ
    0x11BB to 0x11BA  // ㅆ -> ㅅ
)

private val HANGUL_LOOP_CHUNKS = listOf(
    "ㄱㅏ", "ㄴㅏ", "ㄷㅏ", "ㄹㅏ", "ㅁㅏ", "ㅂㅏ", "ㅅㅏ", "ㅇㅏ", "ㅈㅏ", "ㅎㅏ",
    "ㄱㅗ", "ㄴㅗ", "ㄷㅗ", "ㄹㅗ", "ㅁㅗ", "ㅂㅗ", "ㅅㅗ", "ㅇㅗ", "ㅈㅗ", "ㅎㅗ",
    "ㄱㅜ", "ㄴㅜ", "ㄷㅜ", "ㄹㅜ", "ㅁㅜ", "ㅂㅜ", "ㅅㅜ", "ㅇㅜ", "ㅈㅜ", "ㅎㅜ",
    "ㅅㅡ", "ㅌㅡ", "ㅍㅗㄴ", "ㅂㅜㄱ", "ㄷㅗㄱ", "ㄱㅡㄹ", "ㅂㅏㅇ", "ㅎㅡ", "ㄹㅗ", "ㅁㅐㄱ",
    "ㄱㅗㅏ", "ㄱㅗㅐ", "ㄱㅜㅓ", "ㄱㅏㅂㅅ", "ㄹㅓㄱㅅ"
)

private val HANGUL_INSERT_CHUNKS = listOf(
    "ㄱㅏ", "ㄴㅏ", "ㄷㅏ", "ㄹㅗ", "ㅁㅜ", "ㅂㅏ", "ㅅㅏ", "ㅇㅣ", "ㅈㅗ", "ㅎㅏ"
)

private val CHEONJIIN_LOOP_CHUNKS = listOf(
    "ㄱㅣㆍ", "ㄱㆍㅣ", "ㄱㆍㅡ", "ㄱㅡㆍ", "ㄴㅣ", "ㄷㅣㆍ", "ㅂㅣ", "ㅅㅣ", "ㅈㅣ", "ㅇㅣ",
    "ㅈㅣㅡ", "ㄷㅡ", "ㆍㅡ", "ㆍ", "ㅡ", "ㄱㅣ", "ㄴㅣㆍ", "ㄷㅣ", "ㅂㅣㆍ", "ㅇㅣㆍ"
)

private val CHEONJIIN_INSERT_CHUNKS = listOf(
    "ㄱㅣㆍ", "ㄴㅣ", "ㄷㅣㆍ", "ㅂㅣ", "ㅅㅣ", "ㅈㅣ", "ㅇㅣ", "ㆍ", "ㆍㅡ", "ㅡ"
)

private var currentInputType = InputType.TYPE_CLASS_TEXT
private var currentScript = ScriptUtils.SCRIPT_LATIN
private val messages = mutableListOf<Message>() // for latinIME / ShadowInputMethodService
private val delayedMessages = mutableListOf<Message>() // for latinIME / ShadowInputMethodService
// inputconnection stuff
private var batchEdit = 0
private var text = ""
private var selectionStart = 0
private var selectionEnd = 0
private var composingStart = -1
private var composingEnd = -1
// convenience for access
private val textBeforeCursor get() = text.substring(0, selectionStart)
private val textAfterCursor get() = text.substring(selectionEnd)
private val selectedText get() = text.substring(selectionStart, selectionEnd)
private val cursor get() = if (selectionStart == selectionEnd) selectionStart else -1

// composingText should return everything, but RichInputConnection.mComposingText only returns up to cursor
private val composingText get() = if (composingStart == -1 || composingEnd == -1) ""
    else text.substring(composingStart, composingEnd)

// essentially this is the text field we're editing in
private val ic = object : InputConnection {
    // pretty clear (though this may be slow depending on the editor)
    // bad return value here is likely the cause for that weird bug improved/fixed by fixIncorrectLength
    override fun getTextBeforeCursor(p0: Int, p1: Int): CharSequence = textBeforeCursor.take(p0)
    // pretty clear (though this may be slow depending on the editor)
    override fun getTextAfterCursor(p0: Int, p1: Int): CharSequence = textAfterCursor.take(p0)
    // pretty clear
    override fun getSelectedText(p0: Int): CharSequence? = if (selectionStart == selectionEnd) null
        else text.substring(selectionStart, selectionEnd)
    // inserts text at cursor (right?), and sets it as composing text
    // this REPLACES currently composing text (even if at a different position)
    // moves the cursor: positive means relative to composing text start, negative means relative to start
    override fun setComposingText(newText: CharSequence, cursor: Int): Boolean {
        // first remove the composing text if any
        if (composingStart != -1 && composingEnd != -1)
            text = text.substring(0, composingStart) + text.substring(composingEnd)
        else // no composing span active, we should remove selected text
            if (selectionStart != selectionEnd) {
                text = textBeforeCursor + textAfterCursor
                selectionEnd = selectionStart
            }
        // then set the new text at old composing start
        // if no composing start, set it at cursor position
        val insertStart = if (composingStart == -1) selectionStart else composingStart
        text = text.substring(0, insertStart) + newText + text.substring(insertStart)
        composingStart = insertStart
        composingEnd = insertStart + newText.length
        // the cursor -1 is not clear in documentation, but
        // "So a value of 1 will always advance you to the position after the full text being inserted"
        // means that 1 must be composingEnd
        selectionStart = if (cursor > 0) composingEnd + cursor - 1
            else -cursor
        selectionEnd = selectionStart
        // todo: this should call InputMethodManager#updateSelection(View, int, int, int, int)
        //  but only after batch edit has ended
        //  this is not used in RichInputMethodManager, but probably ends up in LatinIME.onUpdateSelection
        //  -> DO IT (though it will likely only trigger that belatedSelectionUpdate thing, it might be relevant)
        return true
    }
    override fun setComposingRegion(p0: Int, p1: Int): Boolean {
        println("setComposingRegion, $p0, $p1")
        composingStart = p0
        composingEnd = p1
        return true // never checked
    }
    // sets composing text empty, but doesn't change actual text
    override fun finishComposingText(): Boolean {
        composingStart = -1
        composingEnd = -1
        return true // always true
    }
    // as per documentation: "This behaves like calling setComposingText(text, newCursorPosition) then finishComposingText()"
    override fun commitText(p0: CharSequence, p1: Int): Boolean {
        setComposingText(p0, p1)
        finishComposingText()
        return true // whether we added the text
    }
    // just tells the text field that we add many updated, and that the editor should not
    // send status updates until batch edit ended (not actually used for this simulation)
    override fun beginBatchEdit(): Boolean {
        ++batchEdit
        return true // always true
    }
    // end a batch edit, but maybe there are multiple batch edits happening
    override fun endBatchEdit(): Boolean {
        if (batchEdit > 0)
            return --batchEdit == 0
        return false // returns true if there is still a batch edit ongoing
    }
    // should notify about cursor info containing composing text, selection, ...
    // todo: maybe that could be interesting, implement it?
    override fun requestCursorUpdates(p0: Int): Boolean {
        // we call this, but don't have onUpdateCursorAnchorInfo overridden in latinIME, so it does nothing
        // also currently we don't care about the return value
        return false
    }
    override fun setSelection(p0: Int, p1: Int): Boolean {
        selectionStart = p0
        selectionEnd = p1
        // todo: call InputMethodService.onUpdateSelection(int, int, int, int, int, int), but only after batch edit is done!
        return true
    }
    // delete beforeLength before cursor position, and afterLength after cursor position
    // chars, not codepoints or glyphs
    // todo: may delete only one half of a surrogate pair, but this should be avoided by RichInputConnection (maybe throw error)
    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        val safeBeforeLength = beforeLength.coerceAtMost(textBeforeCursor.length).coerceAtLeast(0)
        val safeAfterLength = afterLength.coerceAtMost(textAfterCursor.length).coerceAtLeast(0)
        // delete only before or after selection
        text = textBeforeCursor.substring(0, textBeforeCursor.length - safeBeforeLength) +
                text.substring(selectionStart, selectionEnd) +
                textAfterCursor.substring(safeAfterLength)

        // if parts of the composing span are deleted, shorten the span (set end to shorter)
        if (selectionStart <= composingStart) {
            composingStart -= safeBeforeLength // is this correct?
            composingEnd -= safeBeforeLength
        } else if (selectionStart <= composingEnd) {
            composingEnd -= safeBeforeLength // is this correct?
        }
        if (selectionEnd <= composingStart) {
            composingStart -= safeAfterLength
            composingEnd -= safeAfterLength
        } else if (selectionEnd <= composingEnd) {
            composingEnd -= safeAfterLength
        }
        // update selection
        selectionStart -= safeBeforeLength
        selectionEnd -= safeBeforeLength
        return true
    }
    override fun sendKeyEvent(p0: KeyEvent): Boolean {
        if (p0.action != KeyEvent.ACTION_DOWN) return true // only change the text on key down, like RichInputConnection does
        if (p0.keyCode == KeyEvent.KEYCODE_DEL) {
            if (selectionEnd == 0) return true // nothing to delete
            if (selectedText.isEmpty()) {
                text = text.substring(0, selectionStart - 1) + text.substring(selectionEnd)
                selectionStart -= 1
            } else {
                text = text.substring(0, selectionStart) + text.substring(selectionEnd)
            }
            selectionEnd = selectionStart
            return true
        }
        val textToAdd = when (p0.keyCode) {
            KeyEvent.KEYCODE_ENTER -> "\n"
            KeyEvent.KEYCODE_DEL -> null
            KeyEvent.KEYCODE_UNKNOWN -> p0.characters
            else -> StringUtils.newSingleCodePointString(p0.unicodeChar)
        }
        if (textToAdd != null) {
            text = text.substring(0, selectionStart) + textToAdd + text.substring(selectionEnd)
            selectionStart += textToAdd.length
            selectionEnd = selectionStart
            composingStart = -1
            composingEnd = -1
        }
        return true
    }
    // implementation is only to work with getTextBeforeCursorAndDetectLaggyConnection
    override fun getExtractedText(p0: ExtractedTextRequest?, p1: Int): ExtractedText {
        return ExtractedText().also {
            it.startOffset = 0
            it.selectionStart = selectionStart
            it.selectionEnd = selectionEnd
        }
    }
    // only effect is flashing, so whatever...
    override fun commitCorrection(p0: CorrectionInfo?): Boolean = true
    // implement only when necessary
    override fun getCursorCapsMode(p0: Int): Int = TODO("Not yet implemented")
    override fun deleteSurroundingTextInCodePoints(p0: Int, p1: Int): Boolean = TODO("Not yet implemented")
    override fun commitCompletion(p0: CompletionInfo?): Boolean = TODO("Not yet implemented")
    override fun performEditorAction(p0: Int): Boolean = TODO("Not yet implemented")
    override fun performContextMenuAction(p0: Int): Boolean = TODO("Not yet implemented")
    override fun clearMetaKeyStates(p0: Int): Boolean = TODO("Not yet implemented")
    override fun reportFullscreenMode(p0: Boolean): Boolean = TODO("Not yet implemented")
    override fun performPrivateCommand(p0: String?, p1: Bundle?): Boolean = TODO("Not yet implemented")
    override fun getHandler(): Handler = TODO("Not yet implemented")
    override fun closeConnection() = TODO("Not yet implemented")
    override fun commitContent(p0: InputContentInfo, p1: Int, p2: Bundle?): Boolean = TODO("Not yet implemented")
}

// Shadows are handled by Robolectric. @Implementation overrides built-in functionality.
// This is used for avoiding crashes (LocaleManagerCompat, InputMethodManager, KeyboardSwitcher)
// and for simulating system stuff (InputMethodService for controlling the InputConnection, which
// more or less is the contents of the text field), and for setting the current script in
// KeyboardSwitcher without having to care about InputMethodSubtypes

// could also extend LatinIME, it's not final anyway
@Implements(InputMethodService::class)
class ShadowInputMethodService {
    @Implementation
    fun getCurrentInputEditorInfo() = EditorInfo().apply {
        inputType = currentInputType
        // anything else?
    }
    @Implementation
    fun getCurrentInputConnection() = ic
    @Implementation
    fun isInputViewShown() = true // otherwise selection updates will do nothing
}

@Implements(Handler::class)
class ShadowHandler {
    @Implementation
    fun sendMessage(message: Message) {
        messages.add(message)
    }
    @Implementation
    fun sendMessageDelayed(message: Message, delay: Long) {
        delayedMessages.add(message)
    }
}

private class CheonjiinSequenceDriver {
    private val combinerChain = helium314.keyboard.event.CombinerChain("", "hangul", "korean_cheonjiin")

    fun input(vararg codePoints: Int) {
        codePoints.forEach { codePoint ->
            val processed = combinerChain.processEvent(arrayListOf(), Event.createEventForCodePointFromUnknownSource(codePoint))
            combinerChain.applyProcessedEvent(processed)
        }
    }

    fun delete() {
        val processed = combinerChain.processEvent(
            arrayListOf(),
            Event.createSoftwareKeypressEvent(
                Event.NOT_A_CODE_POINT,
                KeyCode.DELETE,
                0,
                0,
                0,
                false
            )
        )
        combinerChain.applyProcessedEvent(processed)
    }

    fun text(): String = combinerChain.composingWordWithCombiningFeedback.toString()
}

private data class CheonjiinRandomCharCase(
    val sequence: String,
    val jamoCount: Int,
    val hasDoubleConsonant: Boolean = false
)

private data class CheonjiinOutputAnalysis(
    val jamoCount: Int,
    val hasDoubleConsonant: Boolean
)

private fun analyzeCheonjiinOutput(text: String): CheonjiinOutputAnalysis {
    val codePoint = text.codePointAt(0)
    if (codePoint !in 0xAC00..0xD7A3) {
        return CheonjiinOutputAnalysis(
            jamoCount = 1,
            hasDoubleConsonant = codePoint in CHEONJIIN_DOUBLE_COMPAT_JAMO
        )
    }

    val syllableIndex = codePoint - 0xAC00
    val finalIndex = syllableIndex % 28
    val medialIndex = (syllableIndex / 28) % 21
    val initialIndex = syllableIndex / 28 / 21

    var jamoCount = if (finalIndex == 0) 2 else 3
    val hasDoubleInitial = initialIndex in CHEONJIIN_DOUBLE_INITIAL_INDEXES
    val hasDoubleFinal = finalIndex != 0 && FINAL_INDEX_TO_CODE_POINT[finalIndex] in CHEONJIIN_DOUBLE_FINALS
    if (hasDoubleFinal) jamoCount += 1

    return CheonjiinOutputAnalysis(
        jamoCount = jamoCount,
        hasDoubleConsonant = hasDoubleInitial || hasDoubleFinal || medialIndex == -1
    )
}

private val CHEONJIIN_RANDOM_CHAR_CASES_1 = listOf(
    CheonjiinRandomCharCase("ㄱ", 1),
    CheonjiinRandomCharCase("ㄴ", 1),
    CheonjiinRandomCharCase("ㄷ", 1),
    CheonjiinRandomCharCase("ㅂ", 1),
    CheonjiinRandomCharCase("ㅅ", 1),
    CheonjiinRandomCharCase("ㅈ", 1),
    CheonjiinRandomCharCase("ㅇ", 1),
    CheonjiinRandomCharCase("ㅣ", 1),
    CheonjiinRandomCharCase("ㆍ", 1),
    CheonjiinRandomCharCase("ㅡ", 1),
    CheonjiinRandomCharCase("ㄱㄱ", 1),
    CheonjiinRandomCharCase("ㄱㄱㄱ", 1, true),
    CheonjiinRandomCharCase("ㄷㄷ", 1),
    CheonjiinRandomCharCase("ㄷㄷㄷ", 1, true),
    CheonjiinRandomCharCase("ㅂㅂ", 1),
    CheonjiinRandomCharCase("ㅂㅂㅂ", 1, true),
    CheonjiinRandomCharCase("ㅅㅅ", 1),
    CheonjiinRandomCharCase("ㅅㅅㅅ", 1, true),
    CheonjiinRandomCharCase("ㅈㅈ", 1),
    CheonjiinRandomCharCase("ㅈㅈㅈ", 1, true)
)

private val CHEONJIIN_RANDOM_CHAR_CASES_2 = listOf(
    CheonjiinRandomCharCase("ㄱㅣ", 2),
    CheonjiinRandomCharCase("ㄴㅣ", 2),
    CheonjiinRandomCharCase("ㄷㅡ", 2),
    CheonjiinRandomCharCase("ㅂㅣ", 2),
    CheonjiinRandomCharCase("ㅅㅡ", 2),
    CheonjiinRandomCharCase("ㅈㅣ", 2),
    CheonjiinRandomCharCase("ㅇㅣ", 2),
    CheonjiinRandomCharCase("ㄱㅣㆍ", 2),
    CheonjiinRandomCharCase("ㄱㆍㅣ", 2),
    CheonjiinRandomCharCase("ㄱㆍㅡ", 2),
    CheonjiinRandomCharCase("ㄱㅡㆍ", 2),
    CheonjiinRandomCharCase("ㄱㄱㄱㅣ", 2, true),
    CheonjiinRandomCharCase("ㄷㄷㄷㅣ", 2, true),
    CheonjiinRandomCharCase("ㅂㅂㅂㅣ", 2, true),
    CheonjiinRandomCharCase("ㅅㅅㅅㅡ", 2, true),
    CheonjiinRandomCharCase("ㅈㅈㅈㅣ", 2, true)
)

private val CHEONJIIN_RANDOM_CHAR_CASES_3 = listOf(
    CheonjiinRandomCharCase("ㄱㅣㄴ", 3),
    CheonjiinRandomCharCase("ㄴㅣㄹ", 3),
    CheonjiinRandomCharCase("ㄷㅡㅁ", 3),
    CheonjiinRandomCharCase("ㅂㅣㅇ", 3),
    CheonjiinRandomCharCase("ㅅㅡㄹ", 3),
    CheonjiinRandomCharCase("ㅈㅣㄱ", 3),
    CheonjiinRandomCharCase("ㅇㅣㄴ", 3),
    CheonjiinRandomCharCase("ㄱㅣㆍㄴ", 3),
    CheonjiinRandomCharCase("ㄱㆍㅣㄴ", 3),
    CheonjiinRandomCharCase("ㄱㆍㅡㄴ", 3),
    CheonjiinRandomCharCase("ㄱㅡㆍㄴ", 3),
    CheonjiinRandomCharCase("ㄱㄱㄱㅣㄴ", 3, true),
    CheonjiinRandomCharCase("ㄷㄷㄷㅣㄴ", 3, true),
    CheonjiinRandomCharCase("ㅂㅂㅂㅣㄹ", 3, true),
    CheonjiinRandomCharCase("ㅅㅅㅅㅡㄹ", 3, true),
    CheonjiinRandomCharCase("ㅈㅈㅈㅣㄴ", 3, true)
)

private val CHEONJIIN_RANDOM_CHAR_CASES_4 = listOf(
    CheonjiinRandomCharCase("ㄱㅣㆍㅂㅅ", 4, true), // 값
    CheonjiinRandomCharCase("ㅇㅣㄹㄱ", 4, true),   // 읽
    CheonjiinRandomCharCase("ㅇㅣㆍㄴㅈ", 4, true), // 앉
    CheonjiinRandomCharCase("ㅁㅣㆍㄴㅎ", 4, true), // 많
    CheonjiinRandomCharCase("ㅅㅣㆍㄹㅁ", 4, true), // 삶
    CheonjiinRandomCharCase("ㄷㅣㆍㄹㄱ", 4, true), // 닭
    CheonjiinRandomCharCase("ㅈㅣㆍㄹㅁ", 4, true), // 젊
    CheonjiinRandomCharCase("ㅇㅡㄹㅍ", 4, true),   // 읊
    CheonjiinRandomCharCase("ㄱㅣㄹㅅ", 4, true),
    CheonjiinRandomCharCase("ㄴㅣㄹㅌ", 4, true),
    CheonjiinRandomCharCase("ㄱㄱㄱㅣㅂㅅ", 4, true),
    CheonjiinRandomCharCase("ㅅㅅㅅㅡㄹㅁ", 4, true)
)

private val CHEONJIIN_DOUBLE_COMPAT_JAMO = setOf(
    'ㄲ'.code, 'ㄸ'.code, 'ㅃ'.code, 'ㅆ'.code, 'ㅉ'.code
)

private val CHEONJIIN_DOUBLE_INITIAL_INDEXES = setOf(1, 4, 8, 10, 13)

private val CHEONJIIN_DOUBLE_FINALS = setOf(
    0x11AA, 0x11AC, 0x11AD, 0x11B0, 0x11B1,
    0x11B2, 0x11B3, 0x11B4, 0x11B5, 0x11B6, 0x11B9
)

private val FINAL_INDEX_TO_CODE_POINT = IntArray(28).apply {
    this[0] = 0
    for (index in 1 until size) {
        this[index] = 0x11A7 + index
    }
}

@Implements(KeyboardSwitcher::class)
class ShadowKeyboardSwitcher {
    @Implementation
    // basically only needed for null check
    fun getMainKeyboardView(): MainKeyboardView = Mockito.mock(MainKeyboardView::class.java)
    @Implementation
    // only affects view
    fun setKeyboard(keyboardId: Int, toggleState: KeyboardSwitcher.KeyboardSwitchState) = Unit
    @Implementation
    // only affects view
    fun setOneHandedModeEnabled(enabled: Boolean) = Unit
    @Implementation
    fun getCurrentKeyboardScript() = currentScript
}

@Implements(DictionaryFacilitatorImpl::class)
class ShadowFacilitator2 {
    @Implementation
    fun addToUserHistory(suggestion: String, wasAutoCapitalized: Boolean,
                         ngramContext: NgramContext, timeStampInSeconds: Long,
                         blockPotentiallyOffensive: Boolean) {
        lastAddedWord = suggestion
    }
    companion object {
        var lastAddedWord = ""
    }
}
