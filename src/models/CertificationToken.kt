package habanero.models

import org.koin.core.KoinComponent
import org.koin.core.inject
import org.koin.core.qualifier.named
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.CoroutineCollection
import java.time.ZonedDateTime
import java.util.*

data class CertificationToken(
    val accountId: String,
    val expireAt: ZonedDateTime,
    val token: String = UUID.randomUUID().toString()
) {
    companion object : KoinComponent {
        val collection: CoroutineCollection<CertificationToken> by inject(named("certificationTokens"))

        /**
         * 新しいアカウント認証用トークンを作成する。
         *
         * @param accountId String アカウントのID
         * @param term Long 期限(秒)
         * @return CertificationToken 新しい認証用トークン
         */
        fun create(accountId: String, term: Long): CertificationToken {
            return CertificationToken(accountId, ZonedDateTime.now().plusSeconds(term))
        }

        suspend fun replace(accountId: String, term: Long): CertificationToken? {
            val newCertificationToken = CertificationToken.create(accountId, term)

            return collection.findOneAndUpdate(
                CertificationToken::accountId eq accountId,
                set(
                    SetTo(CertificationToken::token, newCertificationToken.token),
                    SetTo(CertificationToken::expireAt, newCertificationToken.expireAt)
                )
            )
        }
    }

    fun isExpired(): Boolean = this.expireAt.isBefore(ZonedDateTime.now())

    /**
     * 認証用トークンの期限が切れていた場合、データベースからトークンを削除する。
     *
     * @return Boolean 削除されたか
     */
    suspend fun deleteIfExpired(): Boolean {
        return if (this.isExpired()) {
            collection.deleteOne(CertificationToken::accountId eq this.accountId)
            true
        } else {
            false
        }
    }
}
