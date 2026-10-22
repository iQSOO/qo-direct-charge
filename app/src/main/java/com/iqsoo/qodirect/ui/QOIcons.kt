package com.iqsoo.qodirect.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Google Material icons, Apache-2.0. Only the actual used vectors are bundled.
object QOIcons {
    val ArrowBack: ImageVector by lazy {
        ImageVector.Builder("ArrowBack", 24.dp, 24.dp, 24f, 24f, autoMirror = true)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"
                        )
                        .toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
            .build()
    }
    val Refresh: ImageVector by lazy {
        ImageVector.Builder("Refresh", 24.dp, 24.dp, 24f, 24f, autoMirror = false)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"
                        )
                        .toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
            .build()
    }
    val Dashboard: ImageVector by lazy {
        ImageVector.Builder("Dashboard", 24.dp, 24.dp, 24f, 24f, autoMirror = false)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z"
                        )
                        .toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
            .build()
    }
    val Tune: ImageVector by lazy {
        ImageVector.Builder("Tune", 24.dp, 24.dp, 24f, 24f, autoMirror = false)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z"
                        )
                        .toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
            .build()
    }
    val Apps: ImageVector by lazy {
        ImageVector.Builder("Apps", 24.dp, 24.dp, 24f, 24f, autoMirror = false)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M4 8h4V4H4v4zm6 12h4v-4h-4v4zm-6 0h4v-4H4v4zm0-6h4v-4H4v4zm6 0h4v-4h-4v4zm6-10v4h4V4h-4zm-6 4h4V4h-4v4zm6 6h4v-4h-4v4zm0 6h4v-4h-4v4z"
                        )
                        .toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
            .build()
    }
    val Widgets: ImageVector by lazy {
        ImageVector.Builder("Widgets", 24.dp, 24.dp, 24f, 24f, autoMirror = false)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M13 13v8h8v-8h-8zM3 21h8v-8H3v8zM3 3v8h8V3H3zm13.66-1.31L11 7.34 16.66 13l5.66-5.66-5.66-5.65z"
                        )
                        .toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
            .build()
    }
    val Bolt: ImageVector by lazy {
        ImageVector.Builder("Bolt", 24.dp, 24.dp, 24f, 24f, autoMirror = false)
            .apply {
                addPath(
                    PathParser().parsePathString("M7 2v11h3v9l7-12h-4l4-8z").toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
            .build()
    }
    val BatteryChargingFull: ImageVector by lazy {
        ImageVector.Builder("BatteryChargingFull", 24.dp, 24.dp, 24f, 24f, autoMirror = false)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M15.67 4H14V2h-4v2H8.33C7.6 4 7 4.6 7 5.33v15.33C7 21.4 7.6 22 8.33 22h7.33c.74 0 1.34-.6 1.34-1.33V5.33C17 4.6 16.4 4 15.67 4zM11 20v-5.5H9L13 7v5.5h2L11 20z"
                        )
                        .toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
            .build()
    }
    val Science: ImageVector by lazy {
        ImageVector.Builder("Science", 24.dp, 24.dp, 24f, 24f, autoMirror = false)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M22.7 19l-9.1-9.1c.9-2.3.4-5-1.5-6.9-2-2-5-2.4-7.4-1.3L9 6 6 9 1.6 4.7C.4 7.1.9 10.1 2.9 12.1c1.9 1.9 4.6 2.4 6.9 1.5l9.1 9.1c.4.4 1 .4 1.4 0l2.3-2.3c.5-.4.5-1.1.1-1.4z"
                        )
                        .toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
            .build()
    }
    val ReceiptLong: ImageVector by lazy {
        ImageVector.Builder("ReceiptLong", 24.dp, 24.dp, 24f, 24f, autoMirror = false)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M18 17H6v-2h12v2zm0-4H6v-2h12v2zm0-4H6V7h12v2zM3 22l1.5-1.5L6 22l1.5-1.5L9 22l1.5-1.5L12 22l1.5-1.5L15 22l1.5-1.5L18 22l1.5-1.5L21 22V2l-1.5 1.5L18 2l-1.5 1.5L15 2l-1.5 1.5L12 2l-1.5 1.5L9 2 7.5 3.5 6 2 4.5 3.5 3 2v20z"
                        )
                        .toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
            .build()
    }
    val Settings: ImageVector by lazy {
        ImageVector.Builder("Settings", 24.dp, 24.dp, 24f, 24f, autoMirror = false).apply {}.build()
    }
    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder("ChevronRight", 24.dp, 24.dp, 24f, 24f, autoMirror = false)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString("M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z")
                        .toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
            .build()
    }
    val ExpandMore: ImageVector by lazy {
        ImageVector.Builder("ExpandMore", 24.dp, 24.dp, 24f, 24f, autoMirror = false)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString("M16.59 8.59L12 13.17 7.41 8.59 6 10l6 6 6-6z")
                        .toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
            .build()
    }
    val Search: ImageVector by lazy {
        ImageVector.Builder("Search", 24.dp, 24.dp, 24f, 24f, autoMirror = false)
            .apply {
                addPath(
                    PathParser()
                        .parsePathString(
                            "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"
                        )
                        .toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
            .build()
    }
    val Add: ImageVector by lazy {
        ImageVector.Builder("Add", 24.dp, 24.dp, 24f, 24f, autoMirror = false)
            .apply {
                addPath(
                    PathParser().parsePathString("M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z").toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
            .build()
    }
}
