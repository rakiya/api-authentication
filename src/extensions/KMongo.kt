package habanero.extensions

import com.mongodb.MongoClientSettings
import io.ktor.application.ApplicationEnvironment
import io.ktor.util.KtorExperimentalAPI
import org.litote.kmongo.reactivestreams.*
import org.litote.kmongo.coroutine.*

@KtorExperimentalAPI
fun KMongo.createClient(environment: ApplicationEnvironment): CoroutineClient {
    val host = environment.config.getString("database.host")
    val port = environment.config.getInt("database.port")
    val userName = environment.config.getString("database.user_name")
    val password = environment.config.getString("database.password")
    val dbName = environment.config.getString("database.db_name")

    return createClient("mongodb://$userName:$password@$host:$port/$dbName").coroutine
}