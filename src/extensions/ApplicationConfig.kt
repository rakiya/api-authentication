package habanero.extensions

import io.ktor.config.ApplicationConfig
import io.ktor.util.KtorExperimentalAPI

@KtorExperimentalAPI
fun ApplicationConfig.getString(path: String): String = this.property(path).getString()

@KtorExperimentalAPI
fun ApplicationConfig.getInt(path: String): Int = getString(path).toInt()


@KtorExperimentalAPI
fun ApplicationConfig.getLong(path: String): Long = getString(path).toLong()