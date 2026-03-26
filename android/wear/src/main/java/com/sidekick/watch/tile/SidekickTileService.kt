package com.sidekick.watch.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.TypeBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.sidekick.watch.R
import com.sidekick.watch.presentation.TileEntryActivity

class SidekickTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val keyboardButton = buildButton(
            iconId = ICON_KEYBOARD,
            clickId = "open_keyboard",
            inputMode = "keyboard",
            bgColor = PRIMARY_COLOR,
            iconTint = ON_PRIMARY_COLOR,
        )

        val micButton = buildButton(
            iconId = ICON_MIC,
            clickId = "open_voice",
            inputMode = "voice",
            bgColor = TERTIARY_COLOR,
            iconTint = ON_TERTIARY_COLOR,
        )

        val spacer = LayoutElementBuilders.Spacer.Builder()
            .setWidth(DimensionBuilders.DpProp.Builder(8f).build())
            .build()

        val title = LayoutElementBuilders.Text.Builder()
            .setText(TypeBuilders.StringProp.Builder("Sidekick").build())
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.SpProp.Builder().setValue(16f).build())
                    .setColor(ColorBuilders.ColorProp.Builder(0xFFFFFFFF.toInt()).build())
                    .build(),
            )
            .build()

        val titleSpacer = LayoutElementBuilders.Spacer.Builder()
            .setHeight(DimensionBuilders.DpProp.Builder(8f).build())
            .build()

        val row = LayoutElementBuilders.Row.Builder()
            .addContent(keyboardButton)
            .addContent(spacer)
            .addContent(micButton)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .build()

        val column = LayoutElementBuilders.Column.Builder()
            .addContent(title)
            .addContent(titleSpacer)
            .addContent(row)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .build()

        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.ExpandedDimensionProp.Builder().build())
            .setHeight(DimensionBuilders.ExpandedDimensionProp.Builder().build())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(column)
            .build()

        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(root).build())
                    .build(),
            ).build()

        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(timeline)
                .build(),
        )
    }

    private fun buildButton(
        iconId: String,
        clickId: String,
        inputMode: String,
        bgColor: Int,
        iconTint: Int,
    ): LayoutElementBuilders.Box {
        val action = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(TileEntryActivity::class.java.name)
                    .addKeyToExtraMapping(
                        EXTRA_INPUT_MODE,
                        ActionBuilders.AndroidStringExtra.Builder().setValue(inputMode).build(),
                    )
                    .build(),
            ).build()

        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId(clickId)
            .setOnClick(action)
            .build()

        val icon = LayoutElementBuilders.Image.Builder()
            .setResourceId(iconId)
            .setWidth(DimensionBuilders.DpProp.Builder(24f).build())
            .setHeight(DimensionBuilders.DpProp.Builder(24f).build())
            .setColorFilter(
                LayoutElementBuilders.ColorFilter.Builder()
                    .setTint(ColorBuilders.ColorProp.Builder(iconTint).build())
                    .build(),
            )
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.DpProp.Builder(BUTTON_SIZE).build())
            .setHeight(DimensionBuilders.DpProp.Builder(BUTTON_SIZE).build())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(ColorBuilders.ColorProp.Builder(bgColor).build())
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(DimensionBuilders.DpProp.Builder(BUTTON_SIZE / 2f).build())
                                    .build(),
                            )
                            .build(),
                    )
                    .setClickable(clickable)
                    .build(),
            )
            .addContent(icon)
            .build()
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping(
                    ICON_KEYBOARD,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_keyboard)
                                .build(),
                        ).build(),
                )
                .addIdToImageMapping(
                    ICON_MIC,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_shortcut_mic)
                                .build(),
                        ).build(),
                )
                .build(),
        )

    companion object {
        private const val RESOURCES_VERSION = "2"
        private const val ICON_KEYBOARD = "keyboard"
        private const val ICON_MIC = "mic"
        private const val BUTTON_SIZE = 52f
        const val EXTRA_INPUT_MODE = "input_mode"

        // Default theme colors (Wear M3 purple)
        private const val PRIMARY_COLOR = 0xFFD0BCFF.toInt()
        private const val ON_PRIMARY_COLOR = 0xFF381E72.toInt()
        private const val TERTIARY_COLOR = 0xFFEFB8C8.toInt()
        private const val ON_TERTIARY_COLOR = 0xFF492532.toInt()
    }
}
