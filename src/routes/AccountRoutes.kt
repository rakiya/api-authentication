package habanero.routes

import habanero.exceptions.HabaneroBusinessException
import habanero.exceptions.HabaneroUnexpectedException
import habanero.extensions.*
import habanero.mail.MailClient
import habanero.models.Account
import habanero.models.CertificationToken
import habanero.requests.PostAccountRequest
import habanero.responses.GetAccountResponse
import habanero.responses.PostAccountResponse
import habanero.utils.HabaneroError
import io.konform.validation.Invalid
import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.auth.principal
import io.ktor.http.HttpStatusCode
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject
import org.litote.kmongo.and
import org.litote.kmongo.eq
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.io.File

@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
fun Route.accountRoute() {

    val fqdnUi: String by inject(named("fqdn.ui"))
    val mailClient: MailClient by inject()

    post("/") {
        val request = call.receiveAsJson<PostAccountRequest>().also {
            PostAccountRequest.validate(it).apply {
                if (this is Invalid) throw HabaneroBusinessException(this.toHabaneroError())
            }
        }

        // 登録済みアカウントを検索
        val existedAccount = Account.collection.findOne(Account::email eq request.email)
        val existedCertificationToken = existedAccount?.run {
            CertificationToken.collection
                    .findOne(CertificationToken::accountId eq id)
                    ?.takeUnless { it.deleteIfExpired() } // 期限切れの場合削除し、nullを返す
        }

        // 登録済みのときは、エラーを返す
        if ((existedAccount is Account && existedAccount.isCertificated) // アカウントが認証済み
                || (existedCertificationToken is CertificationToken) // 認証待ち
        ) {
            throw HabaneroBusinessException(HabaneroError().add("email", "登録済みです"))
        } else if (existedCertificationToken == null) {
            Account.collection.deleteOne(Account::email eq request.email)
        }

        // 保存するデータを作成
        val account = Account(
                email = request.email,
                screenName = request.screenName,
                passwordDigest = BCryptPasswordEncoder().encode(request.password)
        )
        val certificationToken = call.application.environment.run {
            CertificationToken.create(account.id, config.getLong("ct.t_expired"))
        }

        // 認証メールの送信
        kotlin.runCatching {
            call.application.environment.run {
                val template = File(config.getString("mail.templates.account-certification")).readText()
                mailClient.sendMail(
                        account.email,
                        "会員登録を完了してください",
                        template,
                        account.screenName,
                        "https://$fqdnUi/account/certification?token=${certificationToken.token}"
                )
            }
        }.onFailure {
            it.printStackTrace()
            throw HabaneroUnexpectedException(it)
        }

        // データベースに登録
        kotlin.runCatching {
            Account.collection.insertOne(account)
            CertificationToken.collection.insertOne(certificationToken)
        }.onFailure {
            it.printStackTrace()
            throw HabaneroUnexpectedException(it)
        }

        call.respond(HttpStatusCode.Created, PostAccountResponse(account.id, account.screenName))
    }

    authenticate {
        get("/") {
            Account.collection.findOne(
                    and(
                            Account::id eq call.principal<CurrentAccount>()!!.id,
                            Account::isCertificated eq true
                    )
            ).let {
                when {
                    it != null -> call.respond(HttpStatusCode.OK, GetAccountResponse(it.id, it.screenName))
                    else -> call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        @Location("/{id}")
        data class GetAccountById(val id: String)
        get<GetAccountById> { param ->
            Account.collection.findOne(
                    and(Account::id eq param.id, Account::isCertificated eq true)
            ).let {
                when (it) {
                    is Account -> call.respond(HttpStatusCode.OK, GetAccountResponse(it.id, it.screenName))
                    else -> call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}
