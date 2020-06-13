package routes.account

import com.dumbster.smtp.SimpleSmtpServer
import com.github.guepardoapps.kulid.ULID
import com.google.gson.Gson
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
import io.ktor.server.testing.setBody
import io.ktor.util.InternalAPI
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.decodeBase64String
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.koin.core.inject
import org.koin.core.qualifier.named
import org.litote.kmongo.eq
import org.springframework.security.crypto.bcrypt.BCrypt
import java.io.File
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
@ExperimentalTime
class PostAccountTest : HabaneroRouteTest() {

    data class Request(val email: String, val screenName: String, val password: String)
    data class SuccessResponse(val id: String = "", val screenName: String)

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
        smtpServer.reset()
    }

    /**
     * 正しいパラメータのアカウントを登録できること
     */
    @InternalAPI
    @Test
    fun `You can create an account`() {
        // テストデータ
        val account = TestAccount.createBy(1)

        // ダミーデータ
        val dummy = TestAccount.createBy(2)
        runBlocking { Account.collection.insertOne(dummy.toAccount()) }

        // 実行
        sendRequest(account).apply {
            // データベースの内容を検証
            val accountInDb = runBlocking {
                Account.collection.findOne(Account::email eq account.email)
            }.also {
                validateAccountInDb(account, it)
            }
            val certificationToken = runBlocking {
                CertificationToken.collection.findOne(CertificationToken::accountId eq accountInDb!!.id)
            }.also {
                validateCertificationTokenInDb(accountInDb!!.id, it)
            }

            // 認証メールの検証
            validateCertificationEmail(accountInDb!!, certificationToken!!)

            // レスポンスを検証
            validateSuccessResponse(response, SuccessResponse(id = accountInDb.id, screenName = account.screenName))
        }
    }

    /**
     * 他のアカウントと同じ表示名でアカウントを登録できること
     */
    @InternalAPI
    @Test
    fun `You can create an account with the same screen name as other accounts`() {
        // テストデータ
        val account = TestAccount.createBy(1)

        // ダミーデータ
        val dummy = TestAccount.createBy(2).apply { screenName = account.screenName }
        runBlocking { Account.collection.insertOne(dummy.toAccount()) }

        // 実行
        sendRequest(account).apply {
            // データベースの内容を検証
            val accountInDb = runBlocking {
                Account.collection.findOne(Account::email eq account.email)
            }.also {
                validateAccountInDb(account, it)
            }
            val certificationToken = runBlocking {
                CertificationToken.collection.findOne(CertificationToken::accountId eq accountInDb!!.id)
            }.also {
                validateCertificationTokenInDb(accountInDb!!.id, it)
            }

            // 認証メールの検証
            validateCertificationEmail(accountInDb!!, certificationToken!!)

            // レスポンスを検証
            validateSuccessResponse(response, SuccessResponse(id = accountInDb.id, screenName = account.screenName))
        }
    }

    /**
     * 既に新規登録を行った後でも、アカウントが認証されておらず認証期限切れの場合は、再登録できること
     */
    @InternalAPI
    @Test
    fun `You can create an account again when the account isn't certificated and the term is expired`() {
        val account = TestAccount.createBy(1)
        val certificatedToken = TestCertificationToken.createBy(account, 0)

        runBlocking {
            Account.collection.insertOne(account.toAccount())
            CertificationToken.collection.insertOne(certificatedToken.toCertificationToken())
        }

        sendRequest(account).apply {
            // データベースの内容を検証
            val accountInDb = runBlocking {
                Account.collection.findOne(Account::email eq account.email)
            }.also {
                validateAccountInDb(account, it)
            }
            val certificationToken = runBlocking {
                CertificationToken.collection.findOne(CertificationToken::accountId eq accountInDb!!.id)
            }.also {
                validateCertificationTokenInDb(accountInDb!!.id, it)
            }

            // 認証メールの検証
            validateCertificationEmail(accountInDb!!, certificationToken!!)

            // レスポンスを検証
            validateSuccessResponse(response, SuccessResponse(id = accountInDb.id, screenName = account.screenName))
        }

    }

    /**
     * メールアドレスの形式が正しいこと
     */
    @Test
    fun `The email should be correct format`() {
        // テストデータ
        val account = TestAccount.createBy(1).apply { email = "www.example.com" }

        sendRequest(account).apply {
            val accountInDb = runBlocking { Account.collection.findOne(Account::email eq account.email) }
            assertNull(accountInDb)

            assertEquals(0, smtpServer.receivedEmails.size)

            val expectedBody = HabaneroError().add("email", "形式が違います")
            validateFailureResponse(response, HttpStatusCode.BadRequest, expectedBody)

        }
    }

    /**
     * メールアドレスの長さが255以下であること
     */
    @Test
    fun `The email's length should be less than 256`() {
        val account = TestAccount.createBy(1).apply { email = "a".repeat(200) + "@" + "b".repeat(55) }

        sendRequest(account).apply {
            val accountInDb = runBlocking { Account.collection.findOne(Account::email eq account.email) }
            assertNull(accountInDb)

            assertEquals(0, smtpServer.receivedEmails.size)

            val expectedBody = HabaneroError().add("email", "255文字以下で入力してください")
            validateFailureResponse(response, HttpStatusCode.BadRequest, expectedBody)
        }
    }

    /**
     * 表示名の名前の長さが255以下であること
     */
    @Test
    fun `The screen name's should be more than 0`() {
        val account = TestAccount.createBy(1).apply { screenName = "" }

        sendRequest(account).apply {
            val accountInDb = runBlocking { Account.collection.findOne(Account::email eq account.email) }
            assertNull(accountInDb)

            assertEquals(0, smtpServer.receivedEmails.size)

            val expectedBody = HabaneroError().add("screenName", "1文字以上で入力してください")
            validateFailureResponse(response, HttpStatusCode.BadRequest, expectedBody)
        }
    }

    /**
     * 表示名の名前の長さが255以下であること
     */
    @Test
    fun `The screen name's should be less than 256`() {
        val account = TestAccount.createBy(1).apply { screenName = "あいうえお".repeat(51) + "か" }

        sendRequest(account).apply {
            val accountInDb = runBlocking { Account.collection.findOne(Account::email eq account.email) }
            assertNull(accountInDb)

            assertEquals(0, smtpServer.receivedEmails.size)

            val expectedBody = HabaneroError().add("screenName", "255文字以下で入力してください")
            validateFailureResponse(response, HttpStatusCode.BadRequest, expectedBody)
        }
    }

    /**
     * パスワードが6文字以上であること
     */
    @Test
    fun `The password should be more than 5`() {
        val account = TestAccount.createBy(1).apply { password = "PsW_1" }

        sendRequest(account).apply {
            val accountInDb = runBlocking { Account.collection.findOne(Account::email eq account.email) }
            assertNull(accountInDb)

            assertEquals(0, smtpServer.receivedEmails.size)

            val expectedBody = HabaneroError().add("password", "6文字以上1024文字以下で入力してください")
            validateFailureResponse(response, HttpStatusCode.BadRequest, expectedBody)
        }
    }

    /**
     * パスワードが1024文字以下であること
     */
    @Test
    fun `The password should be less than 1025`() {
        val account = TestAccount.createBy(1).apply { password = "PW".repeat(1023) + "_1" }

        sendRequest(account).apply {
            val accountInDb = runBlocking { Account.collection.findOne(Account::email eq account.email) }
            assertNull(accountInDb)

            assertEquals(0, smtpServer.receivedEmails.size)

            val expectedBody = HabaneroError().add("password", "6文字以上1024文字以下で入力してください")
            validateFailureResponse(response, HttpStatusCode.BadRequest, expectedBody)
        }
    }

    /**
     * パスワードはASCII文字のみで構成されていること
     */
    @Test
    fun `The password should consist of ASCII`() {
        val account = TestAccount.createBy(1).apply { password += "å" }

        sendRequest(account).apply {
            val accountInDb = runBlocking { Account.collection.findOne(Account::email eq account.email) }
            assertNull(accountInDb)

            assertEquals(0, smtpServer.receivedEmails.size)

            val expectedBody = HabaneroError().add("password", "半角英数字と記号のみ利用できます")
            validateFailureResponse(response, HttpStatusCode.BadRequest, expectedBody)
        }
    }

    /**
     * パスワードに1文字以上大文字が含まれていること
     */
    @Test
    fun `The password should contain at least one uppercase letter`() {
        val account = TestAccount.createBy(1).apply { password = password.toLowerCase() }

        sendRequest(account).apply {
            val accountInDb = runBlocking { Account.collection.findOne(Account::email eq account.email) }
            assertNull(accountInDb)

            assertEquals(0, smtpServer.receivedEmails.size)

            val expectedBody = HabaneroError().add("password", "大文字を1文字以上お使いください")
            validateFailureResponse(response, HttpStatusCode.BadRequest, expectedBody)
        }
    }

    /**
     * パスワードに記号が1文字以上含まれていること
     */
    @Test
    fun `The password should contain at least one symbol`() {
        val account = TestAccount.createBy(1).apply { password = "paSsW0rd" }

        sendRequest(account).apply {
            val accountInDb = runBlocking { Account.collection.findOne(Account::email eq account.email) }
            assertNull(accountInDb)

            assertEquals(0, smtpServer.receivedEmails.size)

            val expectedBody = HabaneroError().add("password", "記号を1文字以上お使いください")
            validateFailureResponse(response, HttpStatusCode.BadRequest, expectedBody)
        }
    }

    /**
     * 既に登録されたメールアドレスは、登録できないこと
     */
    @Test
    fun `You cannot create an account with same email`() {
        val account = TestAccount.createBy(1)
            .also {
                // 認証済みのアカウントをデータベースに登録
                it.isCertification = true
                runBlocking { Account.collection.insertOne(it.toAccount()) }
            }

        sendRequest(account).apply {
            val count = runBlocking { Account.collection.countDocuments(Account::email eq account.email) }
            assertEquals(1, count)

            assertEquals(0, smtpServer.receivedEmails.size)

            val expectedBody = HabaneroError().add("email", "登録済みです")
            validateFailureResponse(response, HttpStatusCode.BadRequest, expectedBody)
        }
    }

    /**
     * 認証待ちのメールアドレスは、登録できないこと
     */
    @Test
    fun `You cannot create an account with same email when the email is waiting for certification`() {
        val account = TestAccount.createBy(1)
            .also {
                runBlocking {
                    Account.collection.insertOne(it.toAccount())
                    val accountId = Account.collection.findOne(Account::email eq it.email)!!.id
                    it.id = accountId
                    CertificationToken.collection.insertOne(CertificationToken.create(accountId, 30))
                }
            }

        sendRequest(account).apply {
            val accountCount = runBlocking { Account.collection.countDocuments(Account::email eq account.email) }
            assertEquals(1, accountCount)
            val certificationTokenCount =
                runBlocking { CertificationToken.collection.countDocuments(CertificationToken::accountId eq account.id) }
            assertEquals(1, certificationTokenCount)

            assertEquals(0, smtpServer.receivedEmails.size)

            val expectedBody = HabaneroError().add("email", "登録済みです")
            validateFailureResponse(response, HttpStatusCode.BadRequest, expectedBody)
        }
    }

    /**
     * リクエストを送る
     *
     * @param account TestAccount 登録したいアカウント
     * @return TestApplicationCall テスト環境
     */
    private fun sendRequest(account: TestAccount) = with(engine) {
        val body = Gson().toJson(Request(account.email, account.screenName, account.password))

        handleRequest(HttpMethod.Post, "/account") {
            setBody(body)
            addHeader("Content-Type", "application/json")
        }
    }

    override fun <T> validateSuccessResponse(response: TestApplicationResponse, expected: T?) {
        val actualResponse = Gson().fromJson(response.content, SuccessResponse::class.java)
        val expectedResponse = expected as SuccessResponse

        // ステータスコード
        assertEquals(HttpStatusCode.Created, response.status())

        // レスポンスボディ
        //// ID
        assertTrue(ULID.isValid(actualResponse.id))
        assertEquals(expectedResponse.id, expectedResponse.id)
        //// 表示名
        assertEquals(expectedResponse.screenName, actualResponse.screenName)
    }

    /**
     * 認証のメールを検証する
     * @param account Account 認証メールが送られたアカウント
     * @param certificationToken CertificationToken 認証メールに含まれる認証トークン
     */
    @InternalAPI
    private fun validateCertificationEmail(account: Account, certificationToken: CertificationToken) {
        // 認証メール
        assertEquals(1, smtpServer.receivedEmails.size)
        smtpServer.receivedEmails[0].run {
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

    /**
     * データベースに登録されたアカウントを検証する
     *
     * @param expected TestAccount 期待値
     * @param actual Account? 実際の値
     */
    private fun validateAccountInDb(expected: TestAccount, actual: Account?) {
        assertNotNull(actual)
        assertTrue(ULID.isValid(actual.id))
        assertEquals(expected.email, actual.email)
        assertEquals(expected.screenName, actual.screenName)
        assertTrue(BCrypt.checkpw(expected.password, actual.passwordDigest))
        assertFalse(actual.isCertificated)
        assertTrue(actual.signedUpAt.isAfter(ZonedDateTime.now().minusMinutes(1)))
    }

    /**
     * データベースに登録された認証トークンを検証する
     *
     * @param expectedAccountId String 期待値
     * @param actual CertificationToken? 実際の値
     */
    private fun validateCertificationTokenInDb(expectedAccountId: String, actual: CertificationToken?) {
        assertNotNull(actual)
        assertEquals(expectedAccountId, actual.accountId)
        assertNotNull(actual.token)
        engine.application.environment.config.run {
            assertTrue(actual.expireAt.isBefore(ZonedDateTime.now().plusSeconds(getLong("ct.t_expired"))))
        }
    }
}
