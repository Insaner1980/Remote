package com.emmah.colordock.remote.tv

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.*
import javax.xml.parsers.DocumentBuilderFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class DiscoveredTv(val address:String,val name:String,val model:String,val advertisedId:String)
internal data class SsdpAdvertisement(val address:String,val description:URI,val identity:String)
internal fun lanNetwork(context:Context):Network? {
    val manager=context.getSystemService(ConnectivityManager::class.java)
    return (listOfNotNull(manager.activeNetwork)+manager.allNetworks).distinct().firstOrNull {network->
        manager.getNetworkCapabilities(network)?.let {caps->caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)}==true
    }
}
class TvDiscovery(private val context:Context) {
    suspend fun find():List<DiscoveredTv> = withContext(Dispatchers.IO) {
        check(context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)==PackageManager.PERMISSION_GRANTED)
        val network=lanNetwork(context)?:throw java.io.IOException("Connect to the same Wi-Fi network as your TV.")
        val wifi=context.getSystemService(WifiManager::class.java)
        val lock=wifi.createMulticastLock("RemoteDiscovery").apply {setReferenceCounted(false);acquire()}
        val found=linkedMapOf<String,SsdpAdvertisement>();val deadline=System.nanoTime()+8_000_000_000L
        try {DatagramSocket(null).use {socket->
            network.bindSocket(socket);socket.bind(InetSocketAddress(0));socket.soTimeout=250
            val payload="M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $SERVICE\r\n\r\n".toByteArray(Charsets.US_ASCII)
            socket.send(DatagramPacket(payload,payload.size,InetAddress.getByName("239.255.255.250"),1900))
            val bytes=ByteArray(8193)
            while(System.nanoTime()<deadline-2_000_000_000L){coroutineContext.ensureActive();try{val packet=DatagramPacket(bytes,bytes.size);socket.receive(packet);if(packet.length<=8192)parsePacket(String(bytes,0,packet.length,Charsets.UTF_8),packet.address.hostAddress.orEmpty())?.let {if(found.size<16)found[it.address]=it}}catch(_:SocketTimeoutException){}}
        }}finally{if(lock.isHeld)lock.release()}
        val client=OkHttpClient.Builder().socketFactory(network.socketFactory).proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false).build()
        try {found.values.map {advertisement->coroutineContext.ensureActive();
            try {
                val remaining=TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime());require(remaining>0)
                val call=client.newCall(Request.Builder().url(advertisement.description.toString()).build());call.timeout().timeout(minOf(remaining,1000),TimeUnit.MILLISECONDS)
                call.execute().use {response->
                    require(response.isSuccessful&&response.body.contentLength()<=65536)
                    val bytes=response.body.byteStream().readNBytes(65537);require(bytes.size<=65536)
                    val (name,model)=parseDescription(bytes)
                    DiscoveredTv(advertisement.address,name,model,advertisement.identity)
                }
            }catch(_:Exception){DiscoveredTv(advertisement.address,"LG TV","",advertisement.identity)}
        }}finally{client.dispatcher.cancelAll();client.dispatcher.executorService.shutdown();client.connectionPool.evictAll()}
    }
    companion object {
        const val SERVICE="urn:lge-com:service:webos-second-screen:1"
        internal fun parsePacket(text:String,address:String):SsdpAdvertisement?=runCatching {
            require(text.length<=8192&&TvTarget.parse(address)!=null)
            val lines=text.split("\r\n");require(lines.first()=="HTTP/1.1 200 OK")
            val headers=mutableMapOf<String,String>()
            for(line in lines.drop(1)){if(line.isEmpty())break;val colon=line.indexOf(':');require(colon>0);val key=line.substring(0,colon).lowercase();require(key !in headers);headers[key]=line.substring(colon+1).trim()}
            require(headers["st"]==SERVICE)
            val url=URI(requireNotNull(headers["location"]));require(url.toString().length<=2048&&url.scheme in listOf("http","https")&&url.host==address&&url.userInfo==null&&url.fragment==null&&(url.port==-1||url.port in 1..65535))
            val identity=headers["usn"].orEmpty().take(512)
            SsdpAdvertisement(address,url,identity)
        }.getOrNull()
        internal fun parseDescription(bytes:ByteArray):Pair<String,String> {
            require(bytes.size<=65536)
            val factory=DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware=true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)
                setFeature("http://xml.org/sax/features/external-general-entities",false)
                setFeature("http://xml.org/sax/features/external-parameter-entities",false)
            }
            val document=factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
            fun text(name:String)=document.getElementsByTagNameNS("*",name).item(0)?.textContent?.trim()?.take(120).orEmpty()
            return text("friendlyName").ifBlank {"LG TV"} to text("modelName")
        }
    }
}
