package routes.account

import com.google.gson.Gson
import habanero.extensions.getLong
import habanero.models.Account
import habanero.models.RefreshToken
import habanero.models.TestAccount
import habanero.models.TestRefreshToken
import habanero.responses.account.TestGetAccountResponse
import routes.HabaneroRouteTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.TestApplicationResponse
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
@ExperimentalTime
class GetAccountTest : HabaneroRouteTest() {

    @Before
    fun setup() {
        commonOperations.resetCollections("accounts", "refreshTokens")
    }

    /**
     * ログインしたあとに自分のアカウントの情報を取得できること
     */
    @Test
    fun `You can get the detail of your account`() {
        val account = TestAccount.createBy(1).apply { isCertification = true }
        val refreshToken = engine.application.environment.config.run {
            TestRefreshToken.createBy(account, getLong("rft.t_expired"))
        }
        val accessToken = commonOperations.login(account, refreshToken)

        runBlocking {
            Account.collection.insertOne(account.toAccount())
            RefreshToken.collection.insertOne(refreshToken.toRefreshToken())
        }

        sendRequest(accessToken).apply {
            validateSuccessResponse(response, account)
        }
    }

    /**
     * ヘッダにアクセストークンが設定されていること
     */
    @Test
    fun `You cannot get the detail when you have not logged in`() {
        val account = TestAccount.createBy(1)
        val refreshToken = engine.application.environment.config.run {
            TestRefreshToken.createBy(account, getLong("rft.t_expired"))
        }

        runBlocking {
            Account.collection.insertOne(account.toAccount())
            RefreshToken.collection.insertOne(refreshToken.toRefreshToken())
        }

        sendRequest(null).apply {
            validateFailureResponse(response, HttpStatusCode.Unauthorized)
        }
    }

    /**
     * ヘッダに設定されているアクセストークンが正しいこと
     */
    @Test
    fun `You cannot get the detail with an invalid access token`() {
        val account = TestAccount.createBy(1)
        val refreshToken = engine.application.environment.config.run {
            TestRefreshToken.createBy(account, getLong("rft.t_expired"))
        }
        val invalidAccessToken = commonOperations.login(account, refreshToken) + "a"

        runBlocking {
            Account.collection.insertOne(account.toAccount())
            RefreshToken.collection.insertOne(refreshToken.toRefreshToken())
        }

        sendRequest(invalidAccessToken).apply {
            validateFailureResponse(response, HttpStatusCode.Unauthorized)
        }
    }

    /**
     *  認証していないアカウントの詳細を取得できないこと
     */
    @Test
    fun `You cannot get the detail of an account not certificated`() {
        val account = TestAccount.createBy(1).apply { isCertification = false }
        val refreshToken = engine.application.environment.config.run {
            TestRefreshToken.createBy(account, getLong("rft.t_expired"))
        }
        val accessToken = commonOperations.login(account, refreshToken)

        runBlocking {
            Account.collection.insertOne(account.toAccount())
            RefreshToken.collection.insertOne(refreshToken.toRefreshToken())
        }

        sendRequest(accessToken).apply {
            validateFailureResponse(response, HttpStatusCode.NotFound)
        }
    }

    /**
     * リクエストを送る
     *
     * @return TestApplicationCall テスト環境
     */
    private fun sendRequest(accessToken: String?) = with(engine) {
        handleRequest(HttpMethod.Get, "/account") {
            addHeader("Content-Type", "application/json")
            if (accessToken != null) {
                addHeader("Authorization", "Bearer $accessToken")
            }
        }
    }

    override fun <T> validateSuccessResponse(response: TestApplicationResponse, expected: T?) {
        // ステータスコードを検証
        assertEquals(HttpStatusCode.OK, response.status())

        // 内容を検証
        val actualBody = Gson().fromJson(response.content, TestGetAccountResponse::class.java)!!
        val expectedBody = expected as TestAccount
        assertEquals(expectedBody.id, actualBody.id)
        assertEquals(expectedBody.screenName, actualBody.screenName)
    }

}