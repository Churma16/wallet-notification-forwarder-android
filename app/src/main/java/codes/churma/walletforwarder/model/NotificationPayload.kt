package codes.churma.walletforwarder.model

import com.google.gson.annotations.SerializedName

/**
 * Standard data transfer object sent to the wallet-mcp webhook endpoint.
 */
data class NotificationPayload(
    @SerializedName("packageName")
    val packageName: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("text")
    val text: String,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
