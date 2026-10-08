package com.phonelink.app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * Finds other PhoneLink phones on the same Wi-Fi / hotspot using mDNS (NSD) and
 * announces this phone. (The "Scan network" button in Hub is a second, brute-force method.)
 */
object Discovery {
    private const val TAG = "PhoneLinkNSD"
    private const val TYPE = "_phonelink._tcp."

    private var nsd: NsdManager? = null
    private var regListener: NsdManager.RegistrationListener? = null
    private var discListener: NsdManager.DiscoveryListener? = null
    private var ownName = ""

    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    @Synchronized
    fun start(ctx: Context) {
        if (nsd != null) return
        val m = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
        nsd = m

        val info = NsdServiceInfo().apply {
            serviceName = "PhoneLink-" + Hub.myName + "-" + Hub.myId.take(4)
            serviceType = TYPE
            port = PORT
        }
        val reg = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) { ownName = i.serviceName }
            override fun onRegistrationFailed(i: NsdServiceInfo, code: Int) { Log.w(TAG, "register failed $code") }
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, code: Int) {}
        }
        regListener = reg
        try { m.registerService(info, NsdManager.PROTOCOL_DNS_SD, reg) } catch (e: Exception) { Log.w(TAG, "$e") }

        val disc = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onStartDiscoveryFailed(t: String, code: Int) { Log.w(TAG, "discovery failed $code") }
            override fun onStopDiscoveryFailed(t: String, code: Int) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onServiceFound(i: NsdServiceInfo) {
                if (i.serviceName == ownName) return
                synchronized(this@Discovery) { queue.addLast(i) }
                pump()
            }
            override fun onServiceLost(i: NsdServiceInfo) { Hub.removeDiscovered(display(i.serviceName)) }
        }
        discListener = disc
        try { m.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, disc) } catch (e: Exception) { Log.w(TAG, "$e") }
    }

    @Synchronized
    fun stop() {
        val m = nsd ?: return
        try { regListener?.let { m.unregisterService(it) } } catch (_: Exception) {}
        try { discListener?.let { m.stopServiceDiscovery(it) } } catch (_: Exception) {}
        nsd = null
        regListener = null
        discListener = null
        queue.clear()
        resolving = false
    }

    /** Resolves one service at a time (Android allows only one active resolve). */
    @Synchronized
    @Suppress("DEPRECATION")
    private fun pump() {
        if (resolving) return
        val next = queue.removeFirstOrNull() ?: return
        val m = nsd ?: return
        resolving = true
        try {
            m.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, code: Int) { done() }
                override fun onServiceResolved(i: NsdServiceInfo) {
                    val host = i.host?.hostAddress
                    if (host != null && !host.contains(':')) {   // IPv4 only
                        Hub.addDiscovered(Discovered(display(i.serviceName), host, i.port))
                    }
                    done()
                }
            })
        } catch (e: Exception) {
            resolving = false
        }
    }

    private fun done() {
        synchronized(this) { resolving = false }
        pump()
    }

    private fun display(serviceName: String): String =
        serviceName.removePrefix("PhoneLink-").substringBeforeLast('-')
}
