package habanero.extensions

import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm
import habanero.utils.RSA
import io.ktor.util.KtorExperimentalAPI
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

@KtorExperimentalAPI
fun JWTCreator.Builder.signBy(privateKey: RSAPrivateKey, publicKey: RSAPublicKey): String =
    this.sign(Algorithm.RSA256(publicKey, privateKey))