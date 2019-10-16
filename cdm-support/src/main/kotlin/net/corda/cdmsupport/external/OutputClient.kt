package net.corda.cdmsupport.external

import net.corda.core.identity.Party
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class OutputClient(val identity: Party) {


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
            val outputStream: DataOutputStream = DataOutputStream(connection.outputStream)
            outputStream.write(postData)
            outputStream.flush()
        } catch (exception: Exception) {
        }
    }

    private fun getURL(): String {
        when (identity.name.commonName) {
            "" -> return "http://localhost/service/CordaAck"
            else -> return "http://localhost/service/CordaAck"
        }
    }

}