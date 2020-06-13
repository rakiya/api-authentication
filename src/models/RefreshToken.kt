package habanero.models

import org.koin.core.KoinComponent
import org.koin.core.inject
import org.koin.core.qualifier.named
import org.litote.kmongo.coroutine.CoroutineCollection
import java.time.ZonedDateTime
import java.util.*

data class RefreshToken(
    val accountId: String,
    val expireAt: ZonedDateTime,
    val token: String = UUID.randomUUID().toString()
) {
    companion object : KoinComponent {
        val collection: CoroutineCollection<RefreshToken> by inject(named("refreshTokens"))

        /**
         * 新しいリフレッシュトークンを作成する。
         *
         * @param accountId String アカウントのID
         * @param term Long 期限
         * @return RefreshToken リフレッシュトークン
         */
        fun create(accountId: String, term: Long): RefreshToken {
            return RefreshToken(accountId, ZonedDateTime.now().plusDays(term))
        }
    }

    /**
     * 期限切れであるかを判定する
     *
     * @return Boolean 期限切れであるか
     */
    fun isExpired() = ZonedDateTime.now().isAfter(this.expireAt)
}