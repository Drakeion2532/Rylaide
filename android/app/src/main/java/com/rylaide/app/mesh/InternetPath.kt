package com.rylaide.app.mesh

/** A way to carry voice over the internet. Two exist: WebSocket ([InternetLink]) and plain UDP ([UdpLink]). */
interface InternetPath {
    interface Listener {
        fun onInternetState(up: Boolean)
        fun onInternetMessage(bytes: ByteArray)
    }

    val up: Boolean
    fun start(serverUrl: String, room: String)
    fun stop()
    fun reconnectNow()
    fun send(bytes: ByteArray): Boolean
}
