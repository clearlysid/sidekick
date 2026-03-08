package com.sidekick.watch.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.sidekick.watch.presentation.TileEntryActivity

class SidekickTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val launchVoiceAction =
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(packageName)
                        .setClassName(TileEntryActivity::class.java.name)
                        .build(),
                ).build()

        val clickable =
            ModifiersBuilders.Clickable.Builder()
                .setId("open_voice")
                .setOnClick(launchVoiceAction)
                .build()

        val text =
            LayoutElementBuilders.Text.Builder()
                .setText("Talk to Sidekick")
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(clickable)
                        .build(),
                ).build()

        val root =
            LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.ExpandedDimensionProp.Builder().build())
                .setHeight(DimensionBuilders.ExpandedDimensionProp.Builder().build())
                .addContent(text)
                .build()

        val tileLayout =
            LayoutElementBuilders.Layout.Builder()
                .setRoot(root)
                .build()

        val timeline =
            TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(
                    TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(tileLayout)
                        .build(),
                ).build()

        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(timeline)
                .build(),
        )
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build(),
        )

    private companion object {
        const val RESOURCES_VERSION = "1"
    }
}
