package routes.accessToken

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.google.gson.Gson
import habanero.extensions.getLong
import habanero.extensions.getString
import habanero.models.Account
import habanero.models.RefreshToken
import habanero.models.TestAccount
import habanero.models.TestRefreshToken
import routes.HabaneroRouteTest
import habanero.utils.HabaneroError
import habanero.utils.RSA
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.TestApplicationResponse
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.litote.kmongo.eq
import kotlin.test.*
import kotlin.time.ExperimentalTime

@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
@ExperimentalTime
class RefreshTest : HabaneroRouteTest() {

    data class Response(val token: String)

    @Before
    fun setup() {
        commonOperations.resetCollections("accounts", "refreshTokens")
    }

    @Test
    fun `You can refresh your access token`() {
        val account = TestAccount.createBy(1)
        val refreshToken = engine.application.environment.config.run {
            TestRefreshToken.createBy(account, getLong("rft.t_expired"))
        }
        insertTestData(account, refreshToken)

        sendRequest(refreshToken.token).apply {
            validateSuccessResponse(response, null)

            val oldRefreshToken = runBlocking {
                RefreshToken.collection.findOne(RefreshToken::token eq refreshToken.token)
            }
            assertNull(oldRefreshToken)
            val newRefreshToken = runBlocking {
                RefreshToken.collection.findOne(RefreshToken::accountId eq account.id)
            }
            assertNotNull(newRefreshToken)
            assertTrue(newRefreshToken.token.isNotEmpty())
        }
    }

    @Test
    fun `You cannot refresh with an invalid token`() {
        val account = TestAccount.createBy(1)
        val refreshToken = engine.application.environment.config.run {
            TestRefreshToken.createBy(account, getLong("rft.t_expired"))
        }
        insertTestData(account, refreshToken)

        sendRequest(refreshToken.token + "a").apply {
            validateFailureResponse(
                response, HttpStatusCode.NotAcceptable,
                HabaneroError().add("token", "無効なトークンです")
            )
        }
    }

    @Test
    fun `You cannot refresh with an expired token`() {
        val account = TestAccount.createBy(1)
        val refreshToken = TestRefreshToken.createBy(account, 0)
        insertTestData(account, refreshToken)

        sendRequest(refreshToken.token).apply {
            validateFailureResponse(
                response, HttpStatusCode.NotAcceptable,
                HabaneroError().add("token", "無効なトークンです")
            )

            val countRefreshToken = runBlocking {
                RefreshToken.collection.countDocuments(RefreshToken::token eq refreshToken.token)
            }
            assertEquals(0, countRefreshToken)
        }
    }

    private fun insertTestData(account: TestAccount, refreshToken: TestRefreshToken) = runBlocking {
        Account.collection.insertOne(account.toAccount())
        RefreshToken.collection.insertOne(refreshToken.toRefreshToken())
    }

    private fun sendRequest(refreshToken: String) = with(engine) {
        handleRequest(HttpMethod.Put, "/refresh?token=$refreshToken")
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