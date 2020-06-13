package habanero.exceptions

open class HabaneroException : Throwable()

class HabaneroBusinessException(val info: Any?) : HabaneroException()

open class HabaneroSystemException(open val info: Any?) : HabaneroException()

class HabaneroUnexpectedException(override val info: Any?) : HabaneroSystemException(info)