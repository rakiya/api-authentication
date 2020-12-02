package habanero

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import habanero.exceptions.HabaneroBusinessException
import habanero.exceptions.HabaneroSystemException
import habanero.exceptions.HabaneroUnexpectedException
import habanero.extensions.CurrentAccount
import habanero.extensions.createClient
import habanero.extensions.getString
import habanero.mail.MailClient
import habanero.models.Account
import habanero.models.CertificationToken
import habanero.models.RefreshToken
import habanero.routes.*
import habanero.utils.RSA
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.util.*
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.ktor.ext.Koin
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.reactivestreams.KMongo
import org.slf4j.event.Level
import java.text.DateFormat
import kotlin.time.ExperimentalTime

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@KtorExperimentalAPI
fun Application.injectDependency() {
    install(Koin) {
        modules(module {
            // FQDN
            single(named("fqdn.own")) {
                environment.config.getString("fqdn.own")
            }
            single(named("fqdn.ui")) {
                environment.config.getString("fqdn.ui")
            }

            // メールクライアント
            single {
                environment.config.run {
                    MailClient(
                            getString("mail.host"), getString("mail.port"),
                            getString("mail.username"), getString("mail.password"),
                            getString("mail.address.no-reply")
                    )
                }
            }

            // データベース関係
            single(override = true) { KMongo.createClient(environment).getDatabase("habanero") }
            single(named("accounts")) {
                inject<CoroutineDatabase>().value.getCollection<Account>("accounts")
            }
            single(named("refreshTokens")) {
                inject<CoroutineDatabase>().value.getCollection<RefreshToken>("refreshTokens")
            }
            single(named("certificationTokens")) {
                inject<CoroutineDatabase>().value.getCollection<CertificationToken>("certificationTokens")
            }
        })
    }
}

@ExperimentalTime
@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    install(Locations) { }

    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Get)
        method(HttpMethod.Post)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        method(HttpMethod.Patch)
        header(HttpHeaders.Host)
        header(HttpHeaders.Origin)
        header(HttpHeaders.Accept)
        header(HttpHeaders.AccessControlRequestHeaders)
        header(HttpHeaders.AccessControlRequestMethod)
        header(HttpHeaders.Connection)
        header(HttpHeaders.Authorization)
        header(HttpHeaders.ContentType)
        header(HttpHeaders.Referrer)
        header(HttpHeaders.UserAgent)
        anyHost()
        allowCredentials = true
    }

    install(CallLogging) {
        level = Level.DEBUG
        filter { call -> call.request.path().startsWith("/") }
    }

    install(Authentication) {
        jwt {
            val issuer = environment.config.getString("jwt.issuer")
            verifier(JWT.require(Algorithm.RSA256(RSA.publicKey, RSA.privateKey)).withIssuer(issuer).build())
            validate { credentials -> credentials.payload.subject.let { CurrentAccount(it) } }
        }
    }

    install(ContentNegotiation) {
        gson {
            setDateFormat(DateFormat.LONG)
        }
    }

    routing {

        install(StatusPages) {
            exception<HabaneroBusinessException> { case ->
                call.respond(HttpStatusCode.BadRequest, case.info ?: "")
            }
            exception<HabaneroUnexpectedException> {
                call.respond(HttpStatusCode.InternalServerError)
            }
            exception<HabaneroSystemException> {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }

        get("/") {
            call.respondText("HELLO WORLD!\n", contentType = ContentType.Text.Plain)
        }

        route("/account") {
            accountRoute()

            route("/certification") {
                accountCertificationRoute()

                route("/token") {
                    certificationTokenRoute()
                }
            }
        }

        route("/publicKey") { publicKeyRoute() }

        accessTokenRoute()

    }
}

