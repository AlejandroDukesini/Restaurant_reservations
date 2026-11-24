package com.restaurant.reservations.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.restaurant.reservations.exception.ValidationBusinessException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Personalizacion visual de un restaurante o grupo.
 *
 * Ademas de validar el formato (#RRGGBB), el servidor rechaza combinaciones ilegibles
 * calculando el contraste WCAG 2.1: asi ninguna peticion manipulada puede dejar la interfaz
 * de todo un grupo ilegible.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThemeSettings(
    @NotNull @Pattern(regexp = "dark|light") String mode,
    @NotNull @Pattern(regexp = HEX) String primary,
    @NotNull @Pattern(regexp = HEX) String secondary,
    @NotNull @Pattern(regexp = HEX) String accent,
    @NotNull @Pattern(regexp = HEX) String background,
    @NotNull @Pattern(regexp = HEX) String surface,
    @NotNull @Pattern(regexp = HEX) String card,
    @NotNull @Pattern(regexp = HEX) String text,
    @NotNull @Pattern(regexp = HEX) String textMuted,
    @NotNull @Pattern(regexp = HEX) String border,
    @NotNull @Pattern(regexp = HEX) String success,
    @NotNull @Pattern(regexp = HEX) String warning,
    @NotNull @Pattern(regexp = HEX) String danger,
    @NotNull @Pattern(regexp = HEX) String info,
    @NotNull @Pattern(regexp = HEX) String navBackground,
    @NotNull @Pattern(regexp = HEX) String buttonText,
    @Min(20) @Max(120) Integer logoHeight,
    @Pattern(regexp = "left|center") String logoAlign,
    @Size(max = 40) String preset
) {

    public static final String HEX = "^#[0-9a-fA-F]{6}$";

    /** Contraste minimo para texto normal (WCAG AA). */
    public static final double TEXT_CONTRAST = 4.5;
    /** Contraste minimo para texto secundario/grande y elementos graficos (WCAG AA). */
    public static final double UI_CONTRAST = 3.0;

    /** Tema por defecto: el negro y dorado original de la aplicacion. */
    public static ThemeSettings defaults() {
        return new ThemeSettings("dark", "#d8a943", "#8e6a2a", "#f8e7b0", "#050505", "#121212", "#0a0a0b",
            "#f4f4f5", "#a1a1aa", "#3f3f46", "#86efac", "#fbbf24", "#f87171", "#8ec5ff", "#050505", "#050505",
            40, "left", "noir");
    }

    public ThemeSettings normalized() {
        return new ThemeSettings(mode, lower(primary), lower(secondary), lower(accent), lower(background),
            lower(surface), lower(card), lower(text), lower(textMuted), lower(border), lower(success), lower(warning),
            lower(danger), lower(info), lower(navBackground), lower(buttonText),
            logoHeight != null ? logoHeight : 40, logoAlign != null ? logoAlign : "left", preset);
    }

    /** Lanza una excepcion con todos los problemas de legibilidad encontrados. */
    public void requireReadable() {
        List<String> problems = new ArrayList<>();
        check(problems, "texto/fondo", text, background, TEXT_CONTRAST);
        check(problems, "texto/paneles", text, surface, TEXT_CONTRAST);
        check(problems, "texto/tarjetas", text, card, TEXT_CONTRAST);
        check(problems, "texto/navegacion", text, navBackground, TEXT_CONTRAST);
        check(problems, "texto secundario/fondo", textMuted, background, UI_CONTRAST);
        check(problems, "texto secundario/tarjetas", textMuted, card, UI_CONTRAST);
        check(problems, "boton/texto del boton", primary, buttonText, TEXT_CONTRAST);
        check(problems, "color primario/fondo", primary, background, UI_CONTRAST);
        check(problems, "acento/fondo", accent, background, UI_CONTRAST);
        for (String[] state : new String[][] {{"exito", success}, {"aviso", warning}, {"peligro", danger}, {"info", info}}) {
            check(problems, "estado " + state[0] + "/tarjetas", state[1], card, UI_CONTRAST);
        }
        if (!problems.isEmpty()) {
            throw new ValidationBusinessException("Combinacion de colores ilegible (contraste insuficiente): "
                + String.join(", ", problems));
        }
    }

    private static void check(List<String> problems, String label, String fg, String bg, double min) {
        double ratio = contrast(fg, bg);
        if (ratio < min) {
            problems.add(String.format(Locale.ROOT, "%s %.2f:1 (minimo %.1f:1)", label, ratio, min));
        }
    }

    /** Relacion de contraste WCAG 2.1 entre dos colores #RRGGBB. */
    public static double contrast(String a, String b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        double r = channel((rgb >> 16) & 0xff);
        double g = channel((rgb >> 8) & 0xff);
        double b = channel(rgb & 0xff);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
