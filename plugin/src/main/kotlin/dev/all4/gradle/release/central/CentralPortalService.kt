package dev.all4.gradle.release.central

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64
import java.util.UUID

internal class CentralPortalService(
    private val baseUrl: String,
    username: String,
    password: String,
) {
    private val authToken: String =
        Base64.getEncoder().encodeToString("$username:$password".toByteArray())

    fun upload(deploymentName: String, publishingType: String, file: File): String {
        val boundary = "----${UUID.randomUUID()}"
        val url =
            URI("${baseUrl}publisher/upload?name=$deploymentName&publishingType=$publishingType")
                .toURL()
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $authToken")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        connection.outputStream.use { out -> writeMultipart(out, boundary, file) }

        val code = connection.responseCode
        if (code in 200..299) {
            return connection.inputStream.bufferedReader().readText().trim().removeSurrounding("\"")
        } else {
            val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw IOException("Central Portal upload failed (HTTP $code): $error")
        }
    }

    fun publishDeployment(deploymentId: String) {
        val url = URI("${baseUrl}publisher/deployment/$deploymentId").toURL()
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $authToken")
        connection.setRequestProperty("Accept", "application/json")

        val code = connection.responseCode
        if (code !in 200..299) {
            val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw IOException(
                "Central Portal publish failed for deployment $deploymentId (HTTP $code): $error"
            )
        }
    }

    fun deleteDeployment(deploymentId: String) {
        val url = URI("${baseUrl}publisher/deployment/$deploymentId").toURL()
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = "DELETE"
        connection.setRequestProperty("Authorization", "Bearer $authToken")
        connection.setRequestProperty("Accept", "application/json")

        val code = connection.responseCode
        if (code !in 200..299) {
            val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw IOException(
                "Central Portal delete failed for deployment $deploymentId (HTTP $code): $error"
            )
        }
    }

    private fun writeMultipart(out: OutputStream, boundary: String, file: File) {
        val crlf = "\r\n"
        out.write("--$boundary$crlf".toByteArray())
        out.write(
            "Content-Disposition: form-data; name=\"bundle\"; filename=\"${file.name}\"$crlf"
                .toByteArray()
        )
        out.write("Content-Type: application/octet-stream$crlf".toByteArray())
        out.write(crlf.toByteArray())
        file.inputStream().use { it.copyTo(out) }
        out.write(crlf.toByteArray())
        out.write("--$boundary--$crlf".toByteArray())
    }
}
