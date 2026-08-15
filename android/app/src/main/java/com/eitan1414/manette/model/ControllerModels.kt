package com.eitan1414.manette.model

enum class ControllerButton(val bit: Int, val label: String) {
    A(0, "A"),
    B(1, "B"),
    X(2, "X"),
    Y(3, "Y"),
    UP(4, "↑"),
    DOWN(5, "↓"),
    LEFT(6, "←"),
    RIGHT(7, "→"),
    L(8, "L"),
    R(9, "R"),
    ZL(10, "ZL"),
    ZR(11, "ZR"),
    PLUS(12, "+"),
    MINUS(13, "−"),
    HOME(14, "HOME"),
    STICK_L(15, "L3"),
    STICK_R(16, "R3");

    val mask: Int get() = 1 shl bit
}

enum class ControlKind { BUTTON, STICK_LEFT, STICK_RIGHT }

data class ControlPlacement(
    val id: String,
    val label: String,
    val kind: ControlKind,
    val button: ControllerButton? = null,
    val x: Float,
    val y: Float,
    val sizeDp: Float
)

data class InputSnapshot(
    val buttons: Int = 0,
    val leftX: Short = 0,
    val leftY: Short = 0,
    val rightX: Short = 0,
    val rightY: Short = 0
)

object DefaultLayout {
    val controls = listOf(
        ControlPlacement("zl", "ZL", ControlKind.BUTTON, ControllerButton.ZL, 0.08f, 0.12f, 76f),
        ControlPlacement("l", "L", ControlKind.BUTTON, ControllerButton.L, 0.20f, 0.12f, 76f),
        ControlPlacement("r", "R", ControlKind.BUTTON, ControllerButton.R, 0.80f, 0.12f, 76f),
        ControlPlacement("zr", "ZR", ControlKind.BUTTON, ControllerButton.ZR, 0.92f, 0.12f, 76f),

        ControlPlacement("dpad_up", "↑", ControlKind.BUTTON, ControllerButton.UP, 0.16f, 0.40f, 64f),
        ControlPlacement("dpad_left", "←", ControlKind.BUTTON, ControllerButton.LEFT, 0.10f, 0.51f, 64f),
        ControlPlacement("dpad_right", "→", ControlKind.BUTTON, ControllerButton.RIGHT, 0.22f, 0.51f, 64f),
        ControlPlacement("dpad_down", "↓", ControlKind.BUTTON, ControllerButton.DOWN, 0.16f, 0.62f, 64f),

        ControlPlacement("stick_left", "L", ControlKind.STICK_LEFT, null, 0.31f, 0.66f, 126f),
        ControlPlacement("stick_right", "R", ControlKind.STICK_RIGHT, null, 0.69f, 0.66f, 126f),
        ControlPlacement("l3", "L3", ControlKind.BUTTON, ControllerButton.STICK_L, 0.31f, 0.87f, 54f),
        ControlPlacement("r3", "R3", ControlKind.BUTTON, ControllerButton.STICK_R, 0.69f, 0.87f, 54f),

        ControlPlacement("x", "X", ControlKind.BUTTON, ControllerButton.X, 0.84f, 0.40f, 68f),
        ControlPlacement("y", "Y", ControlKind.BUTTON, ControllerButton.Y, 0.77f, 0.51f, 68f),
        ControlPlacement("a", "A", ControlKind.BUTTON, ControllerButton.A, 0.91f, 0.51f, 68f),
        ControlPlacement("b", "B", ControlKind.BUTTON, ControllerButton.B, 0.84f, 0.62f, 68f),

        ControlPlacement("minus", "−", ControlKind.BUTTON, ControllerButton.MINUS, 0.44f, 0.42f, 52f),
        ControlPlacement("plus", "+", ControlKind.BUTTON, ControllerButton.PLUS, 0.56f, 0.42f, 52f),
        ControlPlacement("home", "HOME", ControlKind.BUTTON, ControllerButton.HOME, 0.50f, 0.58f, 58f)
    )
}
