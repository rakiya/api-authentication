package habanero.routes

import habanero.exceptions.HabaneroUnexpectedException
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
import org.litote.kmongo.and
import org.litote.kmongo.eq
import org.litote.kmongo.setValue

@KtorExperimentalLocationsAPI
fun Route.accountCertificationRoute() {

    @Location("/{token}")
    data class PutAccountCertification(val token: String)
    put<PutAccountCertification> { params ->
        val certificationToken =
            CertificationToken.collection.findOneAndDelete(CertificationToken::token eq params.token)

        // 不正な認証用トークンのとき
        if (certificationToken !is CertificationToken) {
            call.respond(HttpStatusCode.NotFound, HabaneroError().add("token", "不正なトークンです"))
            return@put
        } else if (certificationToken.deleteIfExpired()) {
            Account.collection.deleteOne(
                and(
                    Account::id eq certificationToken.accountId,
                    Account::isCertificated eq false
                )
            ).run {
                if (deletedCount == 0L) {
                    call.respond(HttpStatusCode.NotAcceptable, HabaneroError().add("token", "期限切れのトークンです"))
                } else {
                    call.respond(HttpStatusCode.NotFound, HabaneroError().add("account", "もう一度会員登録をしてください"))
                }
                return@put
            }
        }

        // アカウントを認証
        Account.collection.updateOne(
            Account::id eq certificationToken.accountId,
            setValue(Account::isCertificated, true)
        )

        call.respond(HttpStatusCode.NoContent)
    }
}