package indent.rainbow

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import indent.rainbow.IrColorsPalette.Companion.DEFAULT_ERROR_COLOR
import indent.rainbow.settings.IrColorsPaletteType
import indent.rainbow.settings.IrConfig
import java.awt.Color

fun Color?.toStringWithAlpha(): String {
    if (this == null) return "null"
    return "Color[r=$red,g=$green,b=$blue,a=$alpha]"
}

fun applyAlpha(color: Color, background: Color, increaseOpacity: Boolean): Color {
    check(background.alpha == 255)
    { "expect editor background color to have alpha=255, but got: ${background.toStringWithAlpha()}" }
    check(color.alpha != 255)
    { "expect indent color to have alpha<255, but got: ${color.toStringWithAlpha()}" }

    val backgroundF = background.getRGBComponents(null)
    val colorF = color.getRGBComponents(null)
    val alpha = adjustAlpha(colorF[3] + if (increaseOpacity) 0.05F else 0F, IrConfig.INSTANCE.opacityMultiplier)

    val resultF = (0..2).map { i -> interpolate(colorF[i], backgroundF[i], alpha) }
    val result = Color(resultF[0], resultF[1], resultF[2])
    debug {
        "[applyAlpha] " +
                "input: ${color.toStringWithAlpha()}, " +
                "output: ${result.toStringWithAlpha()}, " +
                "alpha: $alpha, " +
                "opacityMultiplier: ${IrConfig.INSTANCE.opacityMultiplier}"
    }
    return result
}

interface IrColorsPalette {
    val errorTextAttributes: TextAttributesKey
    val indentsTextAttributes: Array<TextAttributesKey>

    companion object {
        const val DEFAULT_ERROR_COLOR: Int = 0x4D802020
    }
}

class IrBuiltinColorsPalette(errorColor: Int, indentColors: Array<Int>) : IrColorsPalette {

    // Base TextAttributes are computed by plugin based on color scheme and settings
    private val errorTaBase = createTextAttributesKey("INDENT_RAINBOW_ERROR")
    private val indentsTaBase = (1..indentColors.size)
        .map { createTextAttributesKey("INDENT_RAINBOW_COLOR_$it") }
        .toTypedArray()

    // Derived TextAttributes are set by user
    private val errorTaDerived = createTextAttributesKey("INDENT_RAINBOW_ERROR_DERIVED", errorTaBase)
    private val indentsTaDerived = indentsTaBase
        .mapIndexed { i, ta -> createTextAttributesKey("INDENT_RAINBOW_COLOR_${i + 1}_DERIVED", ta) }
        .toTypedArray()

    val colorsBase: Map<TextAttributesKey, Color> = mapOf(
        errorTaBase to errorColor,
        *(indentsTaBase zip indentColors).toTypedArray()
    ).mapValues { Color(it.value, true) }

    override val errorTextAttributes: TextAttributesKey get() = errorTaDerived
    override val indentsTextAttributes: Array<TextAttributesKey> get() = indentsTaDerived

    companion object {
        val DEFAULT = IrBuiltinColorsPalette(
            DEFAULT_ERROR_COLOR,
            arrayOf(0x12FFFF40, 0x127FFF7F, 0x12FF7FFF, 0x124FECEC)
        )
        val PASTEL = IrBuiltinColorsPalette(
            DEFAULT_ERROR_COLOR,
            // https://github.com/oderwat/vscode-indent-rainbow/pull/64
            arrayOf(0x26C7CEEA, 0x26B5EAD7, 0x26E2F0CB, 0x26FFDAC1, 0x26FFB7B2, 0x26FF9AA2)
        )
        val SPECTRUM = IrBuiltinColorsPalette(
            DEFAULT_ERROR_COLOR,
            // Original RRGGBBAA colors: 1200BFFF, 121E90FF, 127B68EE, 128A2BE2, 12C71585, 12FF1493, 12FF0000, 12FF8C00, 12FFD700, 12ADFF2F, 1232CD32, 1220B2AA, 1200CED1
            arrayOf(
                0x1200BFFF,
                0x121E90FF,
                0x127B68EE,
                0x128A2BE2,
                0x12C71585,
                0x12FF1493,
                0x12FF0000,
                0x12FF8C00,
                0x12FFD700,
                0x12ADFF2F,
                0x1232CD32,
                0x1220B2AA,
                0x1200CED1
            )
        )
        val NIGHTFALL = IrBuiltinColorsPalette(
            DEFAULT_ERROR_COLOR,
            arrayOf(
                0x120052A2,
                0x120065B4,
                0x1254589F,
                0x12D47796,
                0x12FFA3A1,
                0x12FEE9D6,
                0x12FFB9AD,
                0x12FFDA8B,
                0x12FFC07A,
                0x12FFAC8A
            )
        )
    }
}

class IrCustomColorsPalette(private val numberColors: Int) : IrColorsPalette {

    private val errorTaCustom = createTextAttributesKey("INDENT_RAINBOW_ERROR_CUSTOM")
    private val indentsTaCustom = (1..numberColors)
        .map { createTextAttributesKey("INDENT_RAINBOW_COLOR_${it}_CUSTOM") }
        .toTypedArray()

    init {
        for (scheme in EditorColorsManager.getInstance().allSchemes) {
            scheme.setTaBackground(errorTaCustom, Color(DEFAULT_ERROR_COLOR, true))

            val indentsColor = scheme.defaultBackground.darker()
            for (taKey in indentsTaCustom) {
                scheme.setTaBackground(taKey, indentsColor)
            }
        }
    }

    private fun EditorColorsScheme.setTaBackground(taKey: TextAttributesKey, background: Color) {
        val ta = getAttributes(taKey)
        if (ta.backgroundColor != null) return
        val taCopy = ta.clone()
        taCopy.backgroundColor = background
        setAttributes(taKey, taCopy)
    }

    override val errorTextAttributes: TextAttributesKey get() = errorTaCustom
    override val indentsTextAttributes: Array<TextAttributesKey> get() = indentsTaCustom

    companion object {
        private var cachedValue: IrCustomColorsPalette? = null
        fun getInstance(config: IrConfig): IrCustomColorsPalette {
            cachedValue
                ?.takeIf { it.numberColors == config.customPaletteNumberColors }
                ?.let { return it }
            return IrCustomColorsPalette(config.customPaletteNumberColors)
                .also { this.cachedValue = it }
        }
    }
}

object IrColors {

    val currentPalette: IrColorsPalette
        get() {
            val config = serviceOrNull<IrConfig>() ?: return IrBuiltinColorsPalette.DEFAULT
            return when (config.paletteType) {
                IrColorsPaletteType.DEFAULT -> IrBuiltinColorsPalette.DEFAULT
                IrColorsPaletteType.PASTEL -> IrBuiltinColorsPalette.PASTEL
                IrColorsPaletteType.SPECTRUM -> IrBuiltinColorsPalette.SPECTRUM
                IrColorsPaletteType.NIGHTFALL -> IrBuiltinColorsPalette.NIGHTFALL
                IrColorsPaletteType.CUSTOM -> IrCustomColorsPalette.getInstance(config)
            }
        }

    fun getErrorTextAttributes(): TextAttributesKey = currentPalette.errorTextAttributes

    fun getTextAttributes(tabIndex: Int): TextAttributesKey {
        if (tabIndex == -1) return getErrorTextAttributes()
        val indentsTa = currentPalette.indentsTextAttributes
        return indentsTa[tabIndex % indentsTa.size]
    }

    fun onSchemeChange() = updateTextAttributesForAllSchemes()

    private fun updateTextAttributesForAllSchemes() {
        if (!IrConfig.INSTANCE.useFormatterHighlighter) return

        val allSchemes = EditorColorsManager.getInstance().allSchemes
        val currentPalette = currentPalette as? IrBuiltinColorsPalette ?: return
        for (scheme in allSchemes) {
            debug { "[updateTextAttributesForAllSchemes] scheme: $scheme, defaultBackground: ${scheme.defaultBackground}" }
            var anyColorChanged = false
            for ((taKey, color) in currentPalette.colorsBase) {
                val ta = scheme.getAttributes(taKey)

                val backgroundColor = ta.backgroundColor
                check(backgroundColor == null || backgroundColor.alpha == 255)
                { "unexpected TextAttributes value: $ta (${backgroundColor.toStringWithAlpha()})" }

                val taNew = ta.clone()
                val increaseOpacity = isColorLight(scheme.defaultBackground) && !taKey.externalName.contains("ERROR")
                val colorMixed = applyAlpha(color, scheme.defaultBackground, increaseOpacity)
                taNew.backgroundColor = colorMixed
                if (taNew.backgroundColor != ta.backgroundColor) {
                    debug {
                        "Changing color of $taKey in scheme $scheme " +
                                "from ${ta.backgroundColor.toStringWithAlpha()} to ${taNew.backgroundColor.toStringWithAlpha()}"
                    }
                    scheme.setAttributes(taKey, taNew)
                    anyColorChanged = true
                }
            }

            if (anyColorChanged && scheme is AbstractColorsScheme) {
                scheme.setSaveNeeded(true)
            }
        }
    }

    fun refreshEditorIndentColors() {
        (EditorColorsManager.getInstance() as EditorColorsManagerImpl).schemeChangedOrSwitched(null)
    }
}
