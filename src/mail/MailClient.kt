package habanero.mail

import java.util.*
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class MailClient(
    host: String,
    port: String,
    username: String,
    password: String,
    private val senderMailAddress: String = username
) : MailClientIF {

    private val properties = Properties().also {
        it["mail.smtp.auth"] = "true"
        it["mail.smtp.starttls.enable"] = "true"
        it["mail.smtp.host"] = host
        it["mail.smtp.port"] = port
    }
    private var session = Session.getInstance(properties,
        object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(username, password)
            }
        })

    override suspend fun sendMail(
        recipient: String,
        subject: String,
        textTemplate: String,
        vararg args: String
    ) {
        val message: Message = MimeMessage(session).apply {
            setFrom(InternetAddress(senderMailAddress))
            setRecipients(
                Message.RecipientType.TO,
                InternetAddress.parse(recipient)
            )
            setSubject(subject)
            setText(String.format(textTemplate, *args))
        }

        Transport.send(message)
    }
}