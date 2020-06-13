package habanero.routes

import com.auth0.jwt.JWT
import habanero.extensions.*
import habanero.models.Account
import habanero.models.RefreshToken
import habanero.requests.LoginRequest
import habanero.responses.LoginResponse
import habanero.utils.HabaneroError
import habanero.utils.RSA
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location
import io.ktor.locations.put
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import org.koin.ktor.ext.inject
import org.litote.kmongo.eq
import java.time.ZonedDateTime

/**
 * アクセストークンに関するパス
 *
 * @receiver Route
 */
@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
fun Route.accessTokenRoute() {
    post("/login") {
        val request = call.receiveAsJson<LoginRequest>()

        // ログインに失敗
        if (!Account.authenticate(request.email, request.password)) {
            call.respond(
                HttpStatusCode.BadRequest,
                HabaneroError().add("account", "メールアドレスまたはパスワードが違います")
            )
            return@post
        }

        // ログイン対象のアカウント
        val account = Account.collection.findOne(Account::email eq request.email)!!

        // リフレッシュトークンを作成
        val refreshToken = context.application.environment.run {
            RefreshToken.create(account.id, config.getLong("rft.t_expired"))
        }
        // JWTを作成
        val jwt = call.application.createJWT(account.id, refreshToken)

        // リフレッシュトークンを保存
        RefreshToken.collection.insertOne(refreshToken)

        call.respond(HttpStatusCode.OK, LoginResponse(jwt))
    }

    @Location("/refresh")
    data class RefreshParams(val token: String)
    put<RefreshParams> {
        val refreshToken = RefreshToken.collection.findOne(RefreshToken::token eq it.token)

        when {
            // 存在しない場合
            refreshToken !is RefreshToken -> {
                call.respond(HttpStatusCode.NotAcceptable, HabaneroError().add("token", "無効なトークンです"))
            }

            // 期限切れで失効していた場合
            refreshToken.isExpired() -> {
                RefreshToken.collection.deleteOne(RefreshToken::token eq refreshToken.token)
                call.respond(HttpStatusCode.NotAcceptable, HabaneroError().add("token", "無効なトークンです"))
            }

            // 有効の場合
            else -> {
                // 新しいリフレッシュトークンを作成
                val newRefreshToken = context.application.environment.run {
                    RefreshToken.create(refreshToken.accountId, config.getLong("rft.t_expired"))
                }
                // JWTを作成
                val newJwt = call.application.createJWT(refreshToken.accountId, newRefreshToken)

                // 古いリフレッシュトークンを削除
                RefreshToken.collection.deleteOne(RefreshToken::token eq refreshToken.token)
                // 新しいリフレッシュトークンを登録
                RefreshToken.collection.insertOne(newRefreshToken)

                call.respond(HttpStatusCode.OK, LoginResponse(newJwt))
            }
        }
    }
}


/**
 * JWTを作成する。
 *
 * @receiver Application アプリケーション環境
 * @param accountId String アカウントID
 * @param refreshToken RefreshToken リフレッシュトークン
 * @return String アクセストークン
 */
@KtorExperimentalAPI
fun Application.createJWT(accountId: String, refreshToken: RefreshToken): String {
    val issuedDate = ZonedDateTime.now()
    val expiredDate = issuedDate.plusSeconds(environment.config.getLong("jwt.t_expired"))
    return JWT.create()
        .withIssuer(environment.config.getString("jwt.issuer"))
        .withSubject(accountId)
        .withNotBefore(issuedDate.toDate())
        .withIssuedAt(issuedDate.toDate())
        .withExpiresAt(expiredDate.toDate())
        .withClaim("rft", refreshToken.token)
        .signBy(RSA.privateKey, RSA.publicKey)
}
