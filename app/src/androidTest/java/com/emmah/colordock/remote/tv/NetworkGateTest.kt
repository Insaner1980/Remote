package com.emmah.colordock.remote.tv

import android.net.ConnectivityManager
import android.system.ErrnoException
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.*
import java.net.InetSocketAddress
import java.net.Socket

class NetworkGateTest {
    @Test fun selectedLanRouteCanOpenGateway(){
        val args=InstrumentationRegistry.getArguments();val address=args.getString("tvAddress");assumeTrue(address!=null)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val manager=context.getSystemService(ConnectivityManager::class.java)
        fun report(value:String){InstrumentationRegistry.getInstrumentation().sendStatus(0,Bundle().apply {putString("stream","\n$value\n")})}
        val active=manager.activeNetwork;val caps=active?.let(manager::getNetworkCapabilities)
        report("App active=$active wifi=${caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)} vpn=${caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)} selected=${lanNetwork(context)}")
        if(caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true)assertEquals("Preserve the app's active Wi-Fi/VPN route",active,lanNetwork(context))
        for((label,factory)in listOf("default" to javax.net.SocketFactory.getDefault(),"selected" to lanNetwork(context)?.socketFactory,"active" to manager.activeNetwork?.socketFactory)) {
            if(factory==null)continue
            try {factory.createSocket().use {it.connect(InetSocketAddress(address,3001),3000)};report("LAN route $label: connected")}
            catch(error:Exception){val errno=generateSequence<Throwable>(error){it.cause}.filterIsInstance<ErrnoException>().firstOrNull()?.errno;report("LAN route $label: ${error.javaClass.simpleName}; errno=$errno");if(label=="selected")throw error}
        }
    }
}
