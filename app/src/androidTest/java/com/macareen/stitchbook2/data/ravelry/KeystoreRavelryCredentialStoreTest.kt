package com.macareen.stitchbook2.data.ravelry

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.ravelry.RavelryCredentials
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreRavelryCredentialStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = KeystoreRavelryCredentialStore(context)

    @After
    fun tearDown() = store.clear()

    @Test
    fun aSavedKeyComesBackAndIsNotStoredInPlainText() {
        val credentials = RavelryCredentials("test-access", "test-personal-secret")

        store.save(credentials)

        assertEquals(credentials, store.load())
        val file = File(context.applicationInfo.dataDir, "shared_prefs/ravelry_credentials.xml")
        assertFalse(file.readText().contains("test-personal-secret"))
    }

    @Test
    fun forgettingRemovesTheKey() {
        store.save(RavelryCredentials("test-access", "test-personal"))

        store.clear()

        assertNull(store.load())
    }
}
