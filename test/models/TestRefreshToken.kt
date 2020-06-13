package habanero.models

import org.koin.core.KoinComponent
import org.koin.core.inject
import org.koin.core.qualifier.named
import org.litote.kmongo.coroutine.CoroutineCollection
import java.time.ZonedDateTime
import java.util.*

data class TestRefreshToken(
    var accountId: String,
    var expireAt: ZonedDateTime,
    var token: String = UUID.randomUUID().toString()
) {
    companion object : KoinComponent {
        val collection: CoroutineCollection<RefreshToken> by inject(named("refreshTokens"))

        fun createBy(account: TestAccount, term: Long): TestRefreshToken {
            return TestRefreshToken(account.id, ZonedDateTime.now().plusSeconds(term))
        }
    }

    fun toRefreshToken() = RefreshToken(accountId, expireAt, token)
}