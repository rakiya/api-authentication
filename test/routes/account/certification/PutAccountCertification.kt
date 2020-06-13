package routes.account.certification

import habanero.extensions.getLong
import habanero.models.Account
import habanero.models.CertificationToken
import habanero.models.TestAccount
import habanero.models.TestCertificationToken
import routes.HabaneroRouteTest
import habanero.utils.HabaneroError
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

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
@ExperimentalTime
class PutAccountCertification : HabaneroRouteTest() {

    @Before
    fun setup() {
        commonOperations.resetCollections("accounts", "certificationTokens")
    }

    /**
     * 正しいリクエストで、アカウントを認証できること
     */
    @Test
    fun `You can make your account certificated`() {
        val account = TestAccount.createBy(1)
        val certificationToken = engine.application.environment.run {
            TestCertificationToken.createBy(account, config.getLong("ct.t_expired"))
        }

        runBlocking {
            Account.collection.insertOne(account.toAccount())
            CertificationToken.collection.insertOne(certificationToken.toCertificationToken())
        }

        sendRequest(certificationToken.token).apply {
            // レスポンスを検証
            validateSuccessResponse(response, null)

            // データベースを検証
            val accountInDb = runBlocking { Account.collection.findOne(Account::email eq account.email) }
            assertNotNull(accountInDb)
            assertTrue(accountInDb.isCertificated)

            val certificationTokenInDb = runBlocking {
                CertificationToken.collection.findOne(CertificationToken::accountId eq accountInDb.id)
            }
            assertNull(certificationTokenInDb)
        }
    }

    /**
     * アカウントの認証済みであり、認証用トークンが有効の時、認証に成功すること
     */
    @Test
    fun `You can make an account certification when you already do and certification token is valid`() {
        val account = TestAccount.createBy(1).apply { isCertification = true }
        val certificationToken = engine.application.environment.run {
            TestCertificationToken.createBy(account, config.getLong("ct.t_expired"))
        }

        runBlocking {
            Account.collection.insertOne(account.toAccount())
            CertificationToken.collection.insertOne(certificationToken.toCertificationToken())
        }

        sendRequest(certificationToken.token).apply {
            // レスポンスを検証
            validateSuccessResponse(response, null)

            // データベースを検証
            val accountInDb = runBlocking { Account.collection.findOne(Account::email eq account.email) }
            assertNotNull(accountInDb)
            assertTrue(accountInDb.isCertificated)

            val countCertificationTokenInDb = runBlocking {
                CertificationToken.collection.countDocuments(CertificationToken::accountId eq accountInDb.id)
            }
            assertEquals(0, countCertificationTokenInDb)
        }

    }

    /**
     * 不正なトークンで認証できないこと
     */
    @Test
    fun `A certification token in the path should be valid`() {
        val account = TestAccount.createBy(1)
        val certificationToken = engine.application.environment.run {
            TestCertificationToken.createBy(account, config.getLong("ct.t_expired"))
        }

        runBlocking {
            Account.collection.insertOne(account.toAccount())
            CertificationToken.collection.insertOne(certificationToken.toCertificationToken())
        }

        sendRequest(certificationToken.token + "a").apply {
            // レスポンスを検証
            validateFailureResponse(
                response, HttpStatusCode.NotFound,
                HabaneroError().add("token", "不正なトークンです")
            )

            // データベースを検証
            val accountInDb = runBlocking { Account.collection.findOne(Account::email eq account.email) }
            assertNotNull(accountInDb)
            assertFalse(accountInDb.isCertificated)

            val countCertificationTokenInDb = runBlocking {
                CertificationToken.collection.countDocuments(CertificationToken::accountId eq accountInDb.id)
            }
            assertEquals(1L, countCertificationTokenInDb)
        }
    }

    /**
     * 認証済みのアカウントのトークンの有効期限が過ぎていた場合、失敗になること
     */
    @Test
    fun `A certification token in the path should not have been expired`() {
        val account = TestAccount.createBy(1).apply { isCertification = true }
        val certificationToken = engine.application.environment.run {
            TestCertificationToken.createBy(account, 0L)
        }

        runBlocking {
            Account.collection.insertOne(account.toAccount())
            CertificationToken.collection.insertOne(certificationToken.toCertificationToken())
        }

        sendRequest(certificationToken.token).apply {
            // レスポンスを検証
            validateFailureResponse(
                response, HttpStatusCode.NotAcceptable,
                HabaneroError().add("token", "期限切れのトークンです")
            )

            // データベースを検証
            val accountInDb = runBlocking { Account.collection.findOne(Account::email eq account.email) }
            assertNotNull(accountInDb)
            assertTrue(accountInDb.isCertificated)

            val countCertificationTokenInDb = runBlocking {
                CertificationToken.collection.countDocuments(CertificationToken::accountId eq accountInDb.id)
            }
            assertEquals(0, countCertificationTokenInDb)
        }
    }

    /**
     * アカウントの認証期限が過ぎていたとき、アカウントは削除されること
     */
    @Test
    fun `An account not certificated should be deleted when the token have been expired`() {
        val account = TestAccount.createBy(1)
        val certificationToken = engine.application.environment.run {
            TestCertificationToken.createBy(account, 0L)
        }

        runBlocking {
            Account.collection.insertOne(account.toAccount())
            CertificationToken.collection.insertOne(certificationToken.toCertificationToken())
        }

        sendRequest(certificationToken.token).apply {
            // レスポンスを検証
            validateFailureResponse(
                response, HttpStatusCode.NotFound,
                HabaneroError().add("account", "もう一度会員登録をしてください")
            )

            // データベースを検証
            val accountInDb = runBlocking { Account.collection.findOne(Account::email eq account.email) }
            assertNull(accountInDb)

            val countCertificationTokenInDb = runBlocking {
                CertificationToken.collection.countDocuments()
            }
            assertEquals(0, countCertificationTokenInDb)
        }
    }
    private fun sendRequest(token: String) = with(engine) {
        handleRequest(HttpMethod.Put, "/account/certification/$token") {}
    }

    override fun <T> validateSuccessResponse(response: TestApplicationResponse, expected: T?) {
        assertEquals(HttpStatusCode.NoContent, response.status())
        assertNull(response.content)
    }

}