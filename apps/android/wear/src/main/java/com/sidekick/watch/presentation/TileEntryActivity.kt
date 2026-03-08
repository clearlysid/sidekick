package com.sidekick.watch.presentation

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class TileEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_ASSIST
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
        )

        finish()
    }
}
