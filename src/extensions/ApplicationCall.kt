package habanero.extensions

import habanero.exceptions.HabaneroBusinessException
import habanero.utils.HabaneroError
import io.ktor.application.ApplicationCall
import io.ktor.request.receive

suspend inline fun <reified T : Any> ApplicationCall.receiveAsJson(): T = try {
    receive()
} catch (e: Exception) {
    throw HabaneroBusinessException(HabaneroError().add("body", "is invalid format"))
}
