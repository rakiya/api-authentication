package habanero.utils

data class Error(val field: String, val descriptions: MutableList<String>) {
    fun add(message: String) {
        this.descriptions.add(message)
    }

    fun add(messages: List<String>) {
        this.descriptions.addAll(messages)
    }

    override fun toString(): String = "$field[$descriptions]"
}

data class HabaneroError(val errors: MutableList<Error> = mutableListOf()) {
    fun add(field: String, message: String): HabaneroError {
        val error = errors.find { it.field == field }

        if (error == null) {
            errors.add(Error(field, mutableListOf(message)))
        } else {
            error.add(message)
        }

        return this
    }

    fun add(field: String, messages: List<String>): HabaneroError {
        val error = errors.find { it.field == field }

        if (error == null) {
            errors.add(Error(field, messages.toMutableList()))
        } else {
            error.add(messages)
        }

        return this
    }

    override fun toString(): String = errors.joinToString("\n")
}