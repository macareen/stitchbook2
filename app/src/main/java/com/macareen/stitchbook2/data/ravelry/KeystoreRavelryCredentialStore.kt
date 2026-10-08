package com.macareen.stitchbook2.data.ravelry

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.macareen.stitchbook2.domain.ravelry.RavelryCredentialStore
import com.macareen.stitchbook2.domain.ravelry.RavelryCredentials
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONException
import org.json.JSONObject

/**
 * Stores the Ravelry key encrypted with an AES-GCM key held in the Android
 * Keystore, so the key never sits in plain text on disk. The preferences
 * file is excluded from Android backup and device transfer (see
 * `backup_rules.xml` and `data_extraction_rules.xml`), and the app's own
 * JSON backup never includes it. If the Keystore key is lost, the person just types the
 * Ravelry key again.
 */
class KeystoreRavelryCredentialStore(context: Context) : RavelryCredentialStore {

    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun load(): RavelryCredentials? {
        val stored = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        val iv = preferences.getString(KEY_IV, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)))
            val json = JSONObject(String(cipher.doFinal(Base64.decode(stored, Base64.NO_WRAP)), Charsets.UTF_8))
            RavelryCredentials(json.getString("access"), json.getString("personal"))
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: JSONException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override fun save(credentials: RavelryCredentials) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val plain = JSONObject().put("access", credentials.accessKey).put("personal", credentials.personalKey).toString()
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
    }

    override fun clear() {
        preferences.edit().clear().commit()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES = "ravelry_credentials"
        const val KEY_CIPHERTEXT = "ciphertext"
        const val KEY_IV = "iv"
        const val KEY_ALIAS = "stitchbook_ravelry_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val KEY_BITS = 256
    }
}
