package habanero.models

import org.koin.core.KoinComponent
import org.koin.core.inject
import org.koin.core.qualifier.named
import org.litote.kmongo.coroutine.CoroutineCollection
import java.time.ZonedDateTime
import java.util.*

data class TestCertificationToken(
    var accountId: String,
    var expireAt: ZonedDateTime,
    var token: String = UUID.randomUUID().toString()
) {
    companion object : KoinComponent {
        fun createBy(seed: TestAccount, term: Long): TestCertificationToken {
            return TestCertificationToken(
                accountId = seed.id,
                expireAt = ZonedDateTime.now().plusSeconds(term)
            )
        }
    }

    fun toCertificationToken() =
        CertificationToken(accountId = accountId, token = token, expireAt = expireAt)
}