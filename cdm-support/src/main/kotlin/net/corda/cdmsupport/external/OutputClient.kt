package net.corda.cdmsupport.external

import net.corda.core.identity.Party
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

class OutputClient(val identity: Party) {

    fun sendTextToFile(text: String) {
        val sdf = SimpleDateFormat("ddMMyyyyhhmmss")
        val currentDate = sdf.format(Date())
        File("C:/Temp/${identity.name.organisation}_${currentDate}.txt").writeText(text)
    }

    fun sendJsonToXceptor(json: String) {
        sendMessageToXceptor(json)
    }

    fun sendExceptionToXceptor(uniqueRef: String, exceptionMessage: String) {
        sendMessageToXceptor("${uniqueRef} - ${exceptionMessage}")
    }

    fun sendMessageToXceptor(message: String) {
        val serverURL: String = getURL()
        val url = URL(serverURL)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 300000
        connection.doOutput = true

        val postData: ByteArray = message.toByteArray(StandardCharsets.UTF_8)

        connection.setRequestProperty("charset", "utf-8")
        connection.setRequestProperty("Content-lenght", postData.size.toString())
        connection.setRequestProperty("Content-Type", "application/json")

        try {
            connection.connect()
            val outputStream: DataOutputStream = DataOutputStream(connection.outputStream)
            outputStream.write(postData)
            outputStream.flush()
            connection.responseCode
        } catch (exception: Exception) {
        }
    }

    private fun getURL(): String {
        when (identity.name.organisation) {
            "Client1" -> return "http://xcderivhack-client1.azurewebsites.net/service/CordaAck"
            "Broker1" -> return "http://xcderivhack-broker.azurewebsites.net/service/CordaAck"
            "Broker2" -> return "http://xcderivhack-broker2.azurewebsites.net/service/CordaAck"
            "Observery" -> return "http://xcderivhack-observer.azurewebsites.net/service/CordaAck"
            else -> return "http://localhost/service/CordaAck"
        }
    }

}