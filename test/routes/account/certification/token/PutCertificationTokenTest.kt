package habanero.routes.account.certification.token

import com.dumbster.smtp.SimpleSmtpServer
import habanero.extensions.getLong
import habanero.extensions.getString
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
import io.ktor.util.InternalAPI
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.decodeBase64String
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Before
import org.junit.Test
import org.koin.core.inject
import org.koin.core.qualifier.named
import org.litote.kmongo.eq
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
@ExperimentalTime
class PutCertificationTokenTest : HabaneroRouteTest() {

    companion object {
        /**
         * メールサーバ
         */
        private val smtpServer = engine.application.environment.config.run {
            SimpleSmtpServer.start(getString("mail.port").toInt())
        }

        @JvmStatic
        @AfterClass
        fun finalize() {
            // メールサーバを停止
            smtpServer.stop()
        }
    }

    @Before
    fun setup() {
        commonOperations.resetCollections("accounts", "certificationTokens")
    }

    /**
     * 認証用トークンを置き換えることができること
     */
    @InternalAPI
    @Test
    fun `You can replace certification token`() {
        val account = TestAccount.createBy(1)
        val certificationToken = engine.application.environment.run {
            TestCertificationToken.createBy(account, config.getLong("ct.t_expired"))
        }
        insert(account, certificationToken)

        sendRequest(account.id).apply {
            validateSuccessResponse(response, null)

            validateCertificationEmail(account.toAccount(), certificationToken.toCertificationToken())

        }
    }

    /**
     * アカウントが存在すること
     */
    @Test
    fun `The account should have been existed`() {
        val account = TestAccount.createBy(1)
        val certificationToken = engine.application.environment.run {
            TestCertificationToken.createBy(account, config.getLong("ct.t_expired"))
        }
        insert(null, certificationToken)

        sendRequest(account.id).apply {
            validateFailureResponse(
                response, HttpStatusCode.NotFound,
                HabaneroError().add("accountId", "正しくありません")
            )

            val countCertificationTokenInDb = runBlocking {
                CertificationToken.collection.countDocuments()
            }
            assertEquals(0, countCertificationTokenInDb)

            assertEquals(0, smtpServer.receivedEmails.size)
        }
    }

    /**
     * アカウントが認証されていないこと
     */
    @Test
    fun `The account should not have been certificated`() {
        val account = TestAccount.createBy(1).apply { isCertification = true }
        insert(account, null)

        sendRequest(account.id).apply {
            validateFailureResponse(
                response, HttpStatusCode.NotAcceptable,
                HabaneroError().add("accountId", "既に認証済みです")
            )

            val accountInDb = runBlocking {
                Account.collection.findOne(Account::id eq account.id)
            }
            assertNotNull(accountInDb)
            assertTrue(accountInDb.isCertificated)

            assertEquals(0, smtpServer.receivedEmails.size)
        }
    }

    /**
     * アカウントが認証済みで認証用トークンを持っている時、認証用トークンが削除されること
     */
    @Test
    fun `The certification token should be deleted when a certificated account has it`() {
        val account = TestAccount.createBy(1).apply { isCertification = true }
        val certificationToken = engine.application.environment.run {
            TestCertificationToken.createBy(account, config.getLong("ct.t_expired"))
        }
        insert(account, certificationToken)

        sendRequest(account.id).apply {
            validateFailureResponse(
                response, HttpStatusCode.NotAcceptable,
                HabaneroError().add("accountId", "既に認証済みです")
            )

            val accountInDb = runBlocking {
                Account.collection.findOne(Account::id eq account.id)
            }
            assertNotNull(accountInDb)
            assertTrue(accountInDb.isCertificated)

            val countCertificationTokenInDb = runBlocking {
                CertificationToken.collection.countDocuments()
            }
            assertEquals(0, countCertificationTokenInDb)

            assertEquals(0, smtpServer.receivedEmails.size)
        }
    }

    /**
     * 認証されていないアカウントが認証用トークンを持たない時、そのアカウントは削除されること
     */
    @Test
    fun `An account who don't have a certification token should be deleted`() {
        val account = TestAccount.createBy(1)
        insert(account, null)

        sendRequest(account.id).apply {
            validateFailureResponse(
                response, HttpStatusCode.NotFound,
                HabaneroError().add("account", "もう一度会員登録をしてください")
            )

            val accountInDb = runBlocking {
                Account.collection.findOne(Account::id eq account.id)
            }
            assertNull(accountInDb)

            assertEquals(0, smtpServer.receivedEmails.size)
        }
    }

    /**
     * 認証期限が過ぎた、認証されていないアカウントは削除されること
     */
    @Test
    fun `An account who have an expired certification token should be deleted`() {
        val account = TestAccount.createBy(1)
        val certificationToken = TestCertificationToken.createBy(account, 0)
        insert(account, certificationToken)

        sendRequest(account.id).apply {
            validateFailureResponse(
                response, HttpStatusCode.NotFound,
                HabaneroError().add("account", "もう一度会員登録をしてください")
            )

            val accountInDb = runBlocking {
                Account.collection.findOne(Account::id eq account.id)
            }
            assertNull(accountInDb)
            val countCertificationTokenInDb = runBlocking {
                CertificationToken.collection.countDocuments()
            }
            assertEquals(0, countCertificationTokenInDb)

            assertEquals(0, smtpServer.receivedEmails.size)
        }
    }

    /**
     * リクエストを送信する。
     *
     * @param accountId String アカウントID
     * @return TestApplicationCall テスト環境
     */
    private fun sendRequest(accountId: String) = with(engine) {
        handleRequest(HttpMethod.Put, "/account/certification/token/$accountId")
    }

    /**
     * テストデータをデータベースに登録する。
     * @param account TestAccount? アカウント
     * @param certificationToken TestCertificationToken? 認証用トークン
     */
    private fun insert(account: TestAccount?, certificationToken: TestCertificationToken?) = runBlocking {
        if (account != null) Account.collection.insertOne(account.toAccount())
        if (certificationToken != null) CertificationToken.collection.insertOne(certificationToken.toCertificationToken())
    }

    /**
     * 認証のメールを検証する
     * @param account Account 認証メールが送られたアカウント
     * @param certificationToken CertificationToken 認証メールに含まれる認証トークン
     */
    @InternalAPI
    private fun validateCertificationEmail(account: Account, certificationToken: CertificationToken) {
        // 認証メール
        assertEquals(1, PutCertificationTokenTest.smtpServer.receivedEmails.size)
        PutCertificationTokenTest.smtpServer.receivedEmails[0].run {
            System.err.println(this.headerNames)
            this.headerNames.forEach {
                System.err.println(it + ":" + getHeaderValue(it))
            }

            engine.application.environment.config.run {
                assertEquals(getString("mail.address.no-reply"), getHeaderValue("From"))
                assertEquals(account.email, getHeaderValue("To"))

                val decodedSubject = Regex("=\\?UTF-8\\?B\\?(.+)\\?=")
                    .find(getHeaderValue("Subject"))
                    ?.groupValues
                    ?.get(1)
                    ?.decodeBase64String()
                assertNotNull(decodedSubject)
                assertEquals("会員登録を完了してください", decodedSubject)

                val fqdn: String by inject(named("fqdn.ui"))
                val decodedBody = body.decodeBase64String()
                assertEquals(
                    String.format(
                        File(getString("mail.templates.account-certification")).readText(),
                        account.screenName,
                        "https://$fqdn/account/certification?token=${certificationToken.token}"
                    ),
                    decodedBody
                )
            }
        }
    }

    override fun <T> validateSuccessResponse(response: TestApplicationResponse, expected: T?) {
        assertEquals(HttpStatusCode.NoContent, response.status())
        assertNull(response.content)
    }
}