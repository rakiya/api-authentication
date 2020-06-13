package routes

import com.google.gson.Gson
import com.typesafe.config.ConfigFactory
import habanero.db.CommonOperations
import habanero.extensions.getString
import habanero.mail.MailClient
import habanero.models.Account
import habanero.models.CertificationToken
import habanero.models.RefreshToken
import habanero.utils.HabaneroError
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.config.HoconApplicationConfig
import io.ktor.http.HttpStatusCode
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationResponse
import io.ktor.server.testing.createTestEnvironment
import io.ktor.util.KtorExperimentalAPI
import org.koin.core.KoinComponent
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.ktor.ext.Koin
import org.litote.kmongo.coroutine.CoroutineDatabase
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.ExperimentalTime

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
@ExperimentalTime
abstract class HabaneroRouteTest : KoinComponent {
    companion object {
        val engine = TestApplicationEngine(createTestEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseFile(File("resources/application.dev.conf"))
            )
        }).apply {
            start(wait = true)

            // 依存性注入
            stopKoin()
            application.injectDependencyForTest()
        }

        val commonOperations = CommonOperations.createWith(engine)

        private fun Application.injectDependencyForTest() {
            install(Koin) {
                modules(module {
                    // FQDN
                    single(named("fqdn.own")) { "authentication.habanero.work" }
                    single(named("fqdn.ui")) { "www.habanero.work" }

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

                    // データベース
                    single { commonOperations.database }
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
    }

    fun validateFailureResponse(
        response: TestApplicationResponse,
        expectedStatus: HttpStatusCode,
        expectedBody: HabaneroError? = null
    ) {
        assertEquals(expectedStatus, response.status())

        if (expectedBody == null) return

        val actualBody = Gson().fromJson(response.content, HabaneroError::class.java)
        assertEquals(expectedBody.errors.size, actualBody.errors.size)
        actualBody.errors.forEach { actualError ->
            val expectedError = expectedBody.errors.find { it.field == actualError.field }
            assertNotNull(expectedError)
            assertEquals(expectedError.descriptions, actualError.descriptions)
        }
    }

    abstract fun <T> validateSuccessResponse(response: TestApplicationResponse, expected: T?)
}
