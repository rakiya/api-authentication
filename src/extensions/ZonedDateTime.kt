package habanero.extensions

import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

fun ZonedDateTime.toUtc() = this.withZoneSameLocal(ZoneId.of("UTC"))
fun ZonedDateTime.toDate() = Date.from(this.toInstant())!!