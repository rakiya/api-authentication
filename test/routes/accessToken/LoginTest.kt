package routes.accessToken

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.google.gson.Gson
import habanero.extensions.getLong
import habanero.extensions.getString
import habanero.models.Account
import habanero.models.RefreshToken
import habanero.models.TestAccount
import routes.HabaneroRouteTest
import habanero.utils.HabaneroError
import habanero.utils.RSA
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.TestApplicationResponse
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.litote.kmongo.eq
import java.time.ZonedDateTime
import kotlin.test.*
import kotlin.time.ExperimentalTime

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
@ExperimentalTime
class LoginTest : HabaneroRouteTest() {

    data class Request(val email: String?, val password: String?)

    data class Response(val token: String)

    @Before
    fun setup() {
        commonOperations.resetCollections("accounts", "refreshTokens")
    }

    /**
     * ログインできること
     */
    @Test
    fun `You can login`() {
        val account = TestAccount.createBy(1).apply { isCertification = true }

        runBlocking {
            Account.collection.insertOne(account.toAccount())
        }

        sendRequest(account.email, account.password).apply {
            validateSuccessResponse(response, null)

            val refreshToken = runBlocking {
                RefreshToken.collection.findOne(RefreshToken::accountId eq account.id)
            }
            assertNotNull(refreshToken)
            engine.application.environment.config.run {
                assertNotNull(refreshToken.token)
                assertTrue(refreshToken.token.isNotEmpty())
                assertTrue(ZonedDateTime.now().plusDays(getLong("rft.t_expired")).isAfter(refreshToken.expireAt))
            }
        }
    }

    @Test
    fun `You can login many times`() {
        val account = TestAccount.createBy(1).apply { isCertification = true }

        runBlocking {
            Account.collection.insertOne(account.toAccount())
        }

        (1..3).forEach { i ->
            sendRequest(account.email, account.password).apply {
                validateSuccessResponse(response, null)
            }
        }

        val refreshTokens = runBlocking {
            RefreshToken.collection.find(RefreshToken::accountId eq account.id).toList()
        }

        refreshTokens.forEach {refreshToken ->
            assertNotNull(refreshToken)
            engine.application.environment.config.run {
                assertNotNull(refreshToken.token)
                assertTrue(refreshToken.token.isNotEmpty())
                assertTrue(ZonedDateTime.now().plusDays(getLong("rft.t_expired")).isAfter(refreshToken.expireAt))
            }
        }
    }

    @Test
    fun `A email in a request shouldn't be empty`() {
        val account = TestAccount.createBy(1).apply { isCertification = true }

        runBlocking {
            Account.collection.insertOne(account.toAccount())
        }

        sendRequest(null, account.password).apply {
            validateFailureResponse(
                response, HttpStatusCode.BadRequest,
                HabaneroError().add("account", "メールアドレスまたはパスワードが違います")
            )

            val refreshToken = runBlocking {
                RefreshToken.collection.findOne(RefreshToken::accountId eq account.id)
            }
            assertNull(refreshToken)
        }
    }

    @Test
    fun `A password in a request shouldn't be empty`() {
        val account = TestAccount.createBy(1).apply { isCertification = true }

        runBlocking {
            Account.collection.insertOne(account.toAccount())
        }

        sendRequest(account.email, null).apply {
            validateFailureResponse(
                response, HttpStatusCode.BadRequest,
                HabaneroError().add("account", "メールアドレスまたはパスワードが違います")
            )

            val refreshToken = runBlocking {
                RefreshToken.collection.findOne(RefreshToken::accountId eq account.id)
            }
            assertNull(refreshToken)
        }
    }

    @Test
    fun `A email in a request should be correct`() {
        val account = TestAccount.createBy(1).apply { isCertification = true }

        runBlocking {
            Account.collection.insertOne(account.toAccount())
        }

        sendRequest(account.email + "a", account.password).apply {
            validateFailureResponse(
                response, HttpStatusCode.BadRequest,
                HabaneroError().add("account", "メールアドレスまたはパスワードが違います")
            )

            val refreshToken = runBlocking {
                RefreshToken.collection.findOne(RefreshToken::accountId eq account.id)
            }
            assertNull(refreshToken)
        }
    }

    @Test
    fun `A password in a request should be correct`() {
        val account = TestAccount.createBy(1).apply { isCertification = true }

        runBlocking {
            Account.collection.insertOne(account.toAccount())
        }

        sendRequest(account.email, account.password + "a").apply {
            validateFailureResponse(
                response, HttpStatusCode.BadRequest,
                HabaneroError().add("account", "メールアドレスまたはパスワードが違います")
            )

            val refreshToken = runBlocking {
                RefreshToken.collection.findOne(RefreshToken::accountId eq account.id)
            }
            assertNull(refreshToken)
        }
    }

    @Test
    fun `Your account should have been certificated`() {
        val account = TestAccount.createBy(1)

        runBlocking {
            Account.collection.insertOne(account.toAccount())
        }

        sendRequest(account.email, account.password).apply {
            validateFailureResponse(
                response, HttpStatusCode.BadRequest,
                HabaneroError().add("account", "メールアドレスまたはパスワードが違います")
            )

            val refreshToken = runBlocking {
                RefreshToken.collection.findOne(RefreshToken::accountId eq account.id)
            }
            assertNull(refreshToken)
        }
    }

    private fun sendRequest(email: String?, password: String?) = with(engine) {
        handleRequest(HttpMethod.Post, "/login") {
            setBody(Gson().toJson(Request(email, password)))
            addHeader("Content-Type", "application/json")
        }
    }


    override fun <T> validateSuccessResponse(response: TestApplicationResponse, expected: T?) {
        assertEquals(HttpStatusCode.OK, response.status())

        val actualBody = Gson().fromJson(response.content, Response::class.java)
        kotlin.runCatching {
            val jwtVerifier = engine.application.environment.config.run {
                JWT.require(Algorithm.RSA256(RSA.publicKey, RSA.privateKey))
                    .withIssuer(getString("jwt.issuer"))
                    .build()
            }

            jwtVerifier.verify(actualBody.token)
        }.onFailure {
            it.printStackTrace()
            fail()
        }
    }

}