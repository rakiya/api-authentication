package habanero.routes

import habanero.extensions.getLong
import habanero.extensions.getString
import habanero.mail.MailClient
import habanero.models.Account
import habanero.models.CertificationToken
import habanero.utils.HabaneroError
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location
import io.ktor.locations.put
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.util.KtorExperimentalAPI
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject
import org.litote.kmongo.eq
import java.io.File

@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
fun Route.certificationTokenRoute() {

    val mailClient: MailClient by inject()

    val fqdnUi: String by inject(named("fqdn.ui"))

    @Location("/{accountId}")
    data class PutCertificationToken(val accountId: String)
    put<PutCertificationToken> { params ->
        // アカウントを検証
        val account = Account.collection.findOne(Account::id eq params.accountId)
        when {
            // アカウントが存在しない
            account !is Account -> {
                CertificationToken.collection.deleteOne(CertificationToken::accountId eq params.accountId)
                call.respond(HttpStatusCode.NotFound, HabaneroError().add("accountId", "正しくありません"))
                return@put
            }
            // 既に認証済み
            account.isCertificated -> {
                CertificationToken.collection.deleteOne(CertificationToken::accountId eq account.id)
                call.respond(HttpStatusCode.NotAcceptable, HabaneroError().add("accountId", "既に認証済みです"))
                return@put
            }
        }

        // 認証用トークンを検証
        val oldCertificationToken =
            CertificationToken.collection.findOne(CertificationToken::accountId eq account!!.id)
        if (oldCertificationToken == null || oldCertificationToken.deleteIfExpired()) {
            Account.collection.deleteOne(Account::id eq account.id)
            call.respond(HttpStatusCode.NotFound, HabaneroError().add("account", "もう一度会員登録をしてください"))
            return@put
        }

        call.application.environment.run {
            // トークンの置き換え
            val certificationToken =
                CertificationToken.replace(account.id, config.getLong("ct.t_expired"))!!

            // 認証メールの送信
            val template = File(config.getString("mail.templates.account-certification")).readText()
            mailClient.sendMail(
                account.email,
                "会員登録を完了してください",
                template,
                account.screenName,
                "https://$fqdnUi/account/certification?token=${certificationToken.token}"
            )
        }

        call.respond(HttpStatusCode.NoContent)
    }
}