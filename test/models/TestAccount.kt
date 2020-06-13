package habanero.models

import com.github.guepardoapps.kulid.ULID
import org.koin.core.KoinComponent
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.ZonedDateTime

data class TestAccount(
    var id: String = ULID.random(),
    var email: String = "account_1@example.com",
    var screenName: String = "アカウント1",
    var password: String = "Password_1",
    var passwordDigest: String = BCryptPasswordEncoder().encode("Password_1"),
    var isCertification: Boolean = false,
    var signedUpAt: ZonedDateTime = ZonedDateTime.now()
) {
    companion object : KoinComponent {
        fun createBy(seed: Int) = TestAccount(
            email = "account_${seed}@example.com",
            screenName = "アカウント${seed}",
            password = "Password_${seed}",
            passwordDigest = BCryptPasswordEncoder().encode("Password_${seed}")
        )
    }

    fun toAccount(): Account =
        Account(
            id = this.id,
            email = this.email,
            screenName = this.screenName,
            passwordDigest = passwordDigest,
            isCertificated = this.isCertification,
            signedUpAt = this.signedUpAt
        )
}