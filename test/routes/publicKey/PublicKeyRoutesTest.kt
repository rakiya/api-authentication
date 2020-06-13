package habanero.routes.publicKey

import com.google.gson.Gson
import habanero.responses.PublicKeyResponse
import routes.HabaneroRouteTest
import habanero.utils.RSA
import io.ktor.http.HttpStatusCode
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.TestApplicationResponse
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
@ExperimentalTime
class PublicKeyRoutesTest : HabaneroRouteTest() {

    @Test
    fun `You can get a public key`(): Unit = with(engine) {
        handleRequest(io.ktor.http.HttpMethod.Get, "/publicKey").apply {
            val expected = Base64
                .getEncoder()
                .encodeToString(RSA.publicKey.encoded)

            validateSuccessResponse(response, expected)
        }
    }

    override fun <T> validateSuccessResponse(response: TestApplicationResponse, expected: T?) {
        assertEquals(HttpStatusCode.OK, response.status())

        val actual = Gson()
            .fromJson(response.content, PublicKeyResponse.Index::class.java)
            .publicKey
        assertEquals(expected as String, actual)
    }

}
