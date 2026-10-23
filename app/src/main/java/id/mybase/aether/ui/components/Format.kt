package id.mybase.aether.ui.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formats an epoch-millis timestamp for display in history/metadata lists. */
fun formatEpochMillis(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
