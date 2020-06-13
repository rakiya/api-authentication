package habanero.models

import com.github.guepardoapps.kulid.ULID
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.koin.core.qualifier.named
import org.litote.kmongo.and
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.eq
import org.litote.kmongo.setValue
import org.springframework.security.crypto.bcrypt.BCrypt
import java.time.ZonedDateTime

data class Account(
    val id: String = ULID.random(),
    val email: String,
    val screenName: String,
    val passwordDigest: String,
    val isCertificated: Boolean = false,
    val signedUpAt: ZonedDateTime = ZonedDateTime.now()
) {
    companion object : KoinComponent {
        val collection: CoroutineCollection<Account> by inject(named("accounts"))

        /**
         * ログイン時の認証を行う。
         *
         * @param email String ログインするアカウントのメールアドレス
         * @param password String ログインするアカウントのパスワード
         * @return Boolean 認証結果
         */
        suspend fun authenticate(email: String, password: String?): Boolean {
            val account = collection
                .findOne(and(Account::email eq email, Account::isCertificated eq true)) ?: return false

            return password != null && BCrypt.checkpw(password, account.passwordDigest)
        }

        /**
         * アカウントを認証する。
         *
         * @param id String 認証するアカウントのID
         * @return Boolean 成功したか
         */
        suspend fun certificate(id: String): Boolean = collection
            .updateOne(Account::id eq id, setValue(Account::isCertificated, true))
            .let { return it.modifiedCount == 1L }

        /**
         * アカウントが登録済みか調べる。
         *
         * @param email String 対象のアカウントのメールアドレス
         * @return Boolean 登録済みか
         */
        suspend fun isExisted(email: String): Boolean {
            collection.findOne(Account::email eq email).also {
                return if (it is Account) { // アカウントが存在する
                    // 認証済み
                    if (it.isCertificated) true
                    // 認証待ち
                    else it.onCertification()
                } else { // アカウントが存在しない
                    false
                }
            }
        }
    }

    /**
     * 認証中か調べる。
     *
     * @return Boolean 認証中か
     */
    suspend fun onCertification(): Boolean = CertificationToken.collection
        .findOne(CertificationToken::accountId eq this.id)
        .let { return it is CertificationToken && !it.isExpired() }

}