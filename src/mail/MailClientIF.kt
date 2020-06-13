package habanero.mail

import org.koin.core.KoinComponent

interface MailClientIF : KoinComponent {
    suspend fun sendMail(
        recipient: String,
        subject: String,
        textTemplate: String,
        vararg args: String
    )
}