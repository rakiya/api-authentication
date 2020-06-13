package habanero.routes

import habanero.responses.PublicKeyResponse
import habanero.utils.RSA
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import org.koin.ktor.ext.inject
import java.util.*

@KtorExperimentalAPI
fun Route.publicKeyRoute() {


    get("/") {
        val publicKey = Base64.getEncoder().encodeToString(RSA.publicKey.encoded)
        call.respond(HttpStatusCode.OK, PublicKeyResponse.Index(publicKey))
    }
}
