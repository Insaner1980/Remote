package com.emmah.colordock.remote

import org.robolectric.pluginapi.TestEnvironmentLifecyclePlugin
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** In-memory JVM fixture; encryption still uses the JDK's real AES/GCM implementation. */
class JvmKeyStore : TestEnvironmentLifecyclePlugin {
    override fun onSetupApplicationState() {
        keys.clear()
        Security.removeProvider("AndroidKeyStore")
        Security.addProvider(
            object : Provider("AndroidKeyStore", 1.0, "JVM test fixture") {
                init {
                    put("KeyStore.AndroidKeyStore", Store::class.java.name)
                    put("KeyGenerator.AES", Generator::class.java.name)
                }
            },
        )
    }

    class Generator : KeyGeneratorSpi() {
        private var alias = ""

        override fun engineInit(random: SecureRandom?) = error("Use a named Android key specification")

        override fun engineInit(
            keysize: Int,
            random: SecureRandom?,
        ) = error("Use a named Android key specification")

        override fun engineInit(
            params: AlgorithmParameterSpec,
            random: SecureRandom?,
        ) {
            alias = params.javaClass.getMethod("getKeystoreAlias").invoke(params) as String
        }

        override fun engineGenerateKey(): SecretKey =
            SecretKeySpec(ByteArray(32).also(SecureRandom()::nextBytes), "AES").also { keys[alias] = it }
    }

    class Store : KeyStoreSpi() {
        override fun engineGetKey(
            alias: String,
            password: CharArray?,
        ): Key? = keys[alias]

        override fun engineDeleteEntry(alias: String) {
            keys.remove(alias)
        }

        override fun engineContainsAlias(alias: String) = keys.containsKey(alias)

        override fun engineAliases() = Collections.enumeration(keys.keys)

        override fun engineSize() = keys.size

        override fun engineIsKeyEntry(alias: String) = keys.containsKey(alias)

        override fun engineIsCertificateEntry(alias: String) = false

        override fun engineGetCertificate(alias: String): Certificate? = null

        override fun engineGetCertificateChain(alias: String): Array<Certificate>? = null

        override fun engineGetCertificateAlias(cert: Certificate): String? = null

        override fun engineGetCreationDate(alias: String): Date? = if (keys.containsKey(alias)) Date(0) else null

        override fun engineSetKeyEntry(
            alias: String,
            key: Key,
            password: CharArray?,
            chain: Array<Certificate>?,
        ) {
            keys[alias] = key as SecretKey
        }

        override fun engineSetKeyEntry(
            alias: String,
            key: ByteArray,
            chain: Array<Certificate>?,
        ) = error("Use a generated secret key")

        override fun engineSetCertificateEntry(
            alias: String,
            cert: Certificate,
        ) = error("Certificates are not used")

        override fun engineLoad(
            stream: InputStream?,
            password: CharArray?,
        ) = Unit

        override fun engineStore(
            stream: OutputStream?,
            password: CharArray?,
        ) = Unit
    }

    companion object {
        private val keys = ConcurrentHashMap<String, SecretKey>()
    }
}
