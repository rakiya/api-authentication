package habanero.extensions

import io.ktor.auth.Principal

class CurrentAccount(val id: String) : Principal {
}