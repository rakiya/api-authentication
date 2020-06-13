package habanero.extensions

import habanero.exceptions.HabaneroBusinessException
import habanero.utils.HabaneroError
import io.konform.validation.Invalid
import io.konform.validation.Validation
import io.konform.validation.ValidationBuilder
import kotlin.reflect.full.declaredMembers

inline fun <reified T> Invalid<T>.toHabaneroError(): HabaneroError {
    val members = T::class.declaredMembers.toList()
    val errors = HabaneroError()

    members.forEach {
        if (this[it] != null) errors.add(it.name, this[it]!!)
    }

    return errors
}

inline operator fun <reified T> Validation.Companion.invoke(value: T, noinline init: ValidationBuilder<T>.() -> Unit) {
    val validator = Validation(init)
    validator(value).let {
        if (it is Invalid) {
            throw HabaneroBusinessException(it.toHabaneroError())
        }
    }
}

