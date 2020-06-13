package habanero.db

import com.auth0.jwt.JWT
import com.github.guepardoapps.kulid.ULID
import habanero.extensions.*
import habanero.models.Account
import habanero.models.RefreshToken
import habanero.models.TestAccount
import habanero.models.TestRefreshToken
import habanero.utils.RSA
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import org.litote.kmongo.reactivestreams.KMongo
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.ZoneId
import java.time.ZonedDateTime

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
class CommonOperations(private val engine: TestApplicationEngine) {
    companion object {
        fun createWith(engine: TestApplicationEngine) = CommonOperations(engine)
    }

    val database = with(engine) {
        KMongo.createClient(environment).getDatabase("habanero")
    }

    fun resetCollections(vararg collections: String) = runBlocking {
        collections.forEach { database.getCollection<Any>(it).deleteMany() }
    }

    fun login(account: TestAccount, refreshToken: TestRefreshToken): String {
        engine.application.environment.run {
            val issuedDate = ZonedDateTime.now()
            val expiredDate = issuedDate.plusSeconds(config.getLong("jwt.t_expired"))

            return JWT.create()
                .withIssuer(config.getString("jwt.issuer"))
                .withSubject(account.id)
                .withNotBefore(issuedDate.toDate())
                .withIssuedAt(issuedDate.toDate())
                .withExpiresAt(expiredDate.toDate())
                .withClaim("rft", refreshToken.token)
                .signBy(RSA.privateKey, RSA.publicKey)
        }
    }
}
