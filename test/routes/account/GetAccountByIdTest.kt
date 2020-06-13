package routes.account

import com.google.gson.Gson
import habanero.extensions.getLong
import habanero.models.Account
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
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
@ExperimentalTime
class GetAccountByIdTest : HabaneroRouteTest() {

    companion object {
        private val myAccount = TestAccount.createBy(1).apply { isCertification = true }
        private val myAccessToken = engine.application.environment.config.run {
            commonOperations.login(myAccount, TestRefreshToken.createBy(myAccount, getLong("rft.t_expired")))
        }

        private val yourAccount = TestAccount.createBy(2).apply { isCertification = true }

        @JvmStatic
        @BeforeClass
        fun initialize() {
            commonOperations.resetCollections("accounts", "refreshTokens")
            runBlocking {
                Account.collection.insertMany(listOf(myAccount.toAccount(), yourAccount.toAccount()))
            }
        }
    }


    /**
     * 正常系
     */
    @Test
    fun `You can get the detail of an account by ID`() {
        sendRequest(yourAccount.id, myAccessToken).apply {
            validateSuccessResponse(response, yourAccount)
        }
    }

    /**
     * ヘッダにアクセストークンが設定されていること
     */
    @Test
    fun `You cannot get the detail when you have not logged in`() {
        sendRequest(yourAccount.id, null).apply {
            validateFailureResponse(response, HttpStatusCode.Unauthorized)
        }
    }

    /**
     * ヘッダに設定されたアクセストークンが正しいこと
     */
    @Test
    fun `You cannot get the detail with an invalid access token`() {
        sendRequest(yourAccount.id, myAccessToken + "a").apply {
            validateFailureResponse(response, HttpStatusCode.Unauthorized)
        }
    }

    /**
     * 認証が完了していないアカウントを取得できないこと
     */
    @Test
    fun `You cannot get the detail of an account not certificated`() {
        val nonCertificatedAccount = TestAccount.createBy(3)

        runBlocking {
            Account.collection.insertOne(nonCertificatedAccount.toAccount())
        }

        sendRequest(nonCertificatedAccount.id, myAccessToken).apply {
            validateFailureResponse(response, HttpStatusCode.NotFound)
        }
    }

    /**
     * 存在しないアカントを取得できないこと
     */
    @Test
    fun `You cannot get the detail of an account not existed`() {
        sendRequest("NotExistedAccountId", myAccessToken).apply {
            validateFailureResponse(response, HttpStatusCode.NotFound)
        }
    }

    /**
     * リクエストを送る
     *
     * @param account TestAccount 登録したいアカウント
     * @return TestApplicationCall テスト環境
     */
    private fun sendRequest(id: String, accessToken: String?) = with(engine) {
        handleRequest(HttpMethod.Get, "/account/$id") {
            addHeader("Content-Type", "application/json")
            if (accessToken != null) {
                addHeader("Authorization", "Bearer $accessToken")
            }
        }
    }

    override fun <T> validateSuccessResponse(response: TestApplicationResponse, expected: T?) {
        // ステータスコードを検証
        assertEquals(HttpStatusCode.OK, response.status())

        // レスポンスボディを検証
        val expectedBody = expected as TestAccount
        val actualBody = Gson().fromJson(response.content, TestGetAccountResponse::class.java)

        assertEquals(expectedBody.id, actualBody.id)
        assertEquals(expectedBody.screenName, actualBody.screenName)
    }
}