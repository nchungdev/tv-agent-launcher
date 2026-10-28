package com.gathin.tvhome.feature.settings.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec


class AgentHomeConfig(private val context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var agentUrl: String
        get() = preferences.getString(KEY_AGENT_URL, "") ?: ""
        set(address) { preferences.edit().putString(KEY_AGENT_URL, address).apply() }

    var agentToken: String
        get() = readSecret(KEY_AGENT_TOKEN_ENC)
        set(value) {
            val encrypted = encryptSecret(value)
            require(value.isEmpty() || encrypted.isNotEmpty()) { "Lỗi mã hóa token" }
            preferences.edit().putString(KEY_AGENT_TOKEN_ENC, encrypted).apply()
        }

    var homeAssistantUrl: String
        get() = preferences.getString(KEY_HA_URL, "") ?: ""
        set(value) { preferences.edit().putString(KEY_HA_URL, value).apply() }

    var homeAssistantToken: String
        get() = readSecret(KEY_HA_TOKEN_ENC)
        set(value) {
            val encrypted = encryptSecret(value)
            require(value.isEmpty() || encrypted.isNotEmpty()) { "Lỗi mã hóa token" }
            preferences.edit().putString(KEY_HA_TOKEN_ENC, encrypted).apply()
        }

    val homeAssistantConfigured: Boolean
        get() = homeAssistantUrl.isNotBlank() &&
            !preferences.getString(KEY_HA_TOKEN_ENC, "").isNullOrBlank()

    var homeAssistantTemperatureEntity: String
        get() = preferences.getString("ha_temperature_entity", "") ?: ""
        set(value) { preferences.edit().putString("ha_temperature_entity", value).apply() }

    fun clearHaTemperature() {
        preferences.edit().remove("ha_temperature_value").remove("ha_temperature_at").apply()
    }

    fun saveHaTemperature(value: String) {
        preferences.edit().putString("ha_temperature_value", value)
            .putLong("ha_temperature_at", System.currentTimeMillis()).apply()
    }

    fun cachedHaTemperature(): String? {
        val age = System.currentTimeMillis() - preferences.getLong("ha_temperature_at", 0)
        return if (age in 0..300_000) preferences.getString("ha_temperature_value", null) else null
    }

    fun saveHaSensorName(value: String) {
        preferences.edit().putString("ha_sensor_name", value).apply()
    }

    fun cachedHaSensorName(): String? = preferences.getString("ha_sensor_name", null)

    fun saveHaHumidity(value: String) {
        preferences.edit().putString("ha_humidity_value", value).apply()
    }

    fun cachedHaHumidity(): String? = preferences.getString("ha_humidity_value", null)

    fun saveHaDevicesSummary(value: String) {
        preferences.edit().putString("ha_devices_summary", value).apply()
    }

    fun cachedHaDevicesSummary(): String? = preferences.getString("ha_devices_summary", null)

    fun saveHaClimateSummary(value: String) {
        preferences.edit().putString("ha_climate_summary", value).apply()
    }

    fun cachedHaClimateSummary(): String? = preferences.getString("ha_climate_summary", null)

    fun clearHaCalendarEvent() {
        preferences.edit().remove("ha_calendar_event").remove("ha_calendar_at").apply()
    }

    fun saveHaCalendarEvent(value: String) {
        preferences.edit().putString("ha_calendar_event", value)
            .putLong("ha_calendar_at", System.currentTimeMillis()).apply()
    }

    fun cachedHaCalendarEvent(): String? {
        val age = System.currentTimeMillis() - preferences.getLong("ha_calendar_at", 0)
        return if (age in 0..900_000) preferences.getString("ha_calendar_event", null) else null
    }

    var hiddenEntities: Set<String>
        get() = preferences.getStringSet("ha_hidden_entities", emptySet()) ?: emptySet()
        set(value) { preferences.edit().putStringSet("ha_hidden_entities", value).apply() }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
        keyStore.load(null)

        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        )
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private fun getDeviceBoundKey(): SecretKey {
        val hardwareId = "${Build.BOARD}:${Build.FINGERPRINT}:${context.packageName}"
        val salt = context.packageName.toByteArray(StandardCharsets.UTF_8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(hardwareId.toCharArray(), salt, 10_000, 256)
        val raw = factory.generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    private fun encryptSecret(rawSecret: String?): String {
        if (rawSecret.isNullOrEmpty()) return ""
        return encryptWithKey(rawSecret, getOrCreateMasterKey(), PREFIX_TEE)
    }

    private fun encryptWithKey(rawSecret: String, secretKey: SecretKey, prefix: String): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val initializationVector = cipher.iv
        require(initializationVector.size == GCM_IV_LENGTH) { "unexpected IV length" }

        val cipherText = cipher.doFinal(rawSecret.toByteArray(StandardCharsets.UTF_8))
        val combined = ByteArray(initializationVector.size + cipherText.size)
        System.arraycopy(initializationVector, 0, combined, 0, initializationVector.size)
        System.arraycopy(cipherText, 0, combined, initializationVector.size, cipherText.size)

        return prefix + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun readSecret(key: String): String {
        val payload = preferences.getString(key, "")
        val value = decryptSecret(payload)
        if (!value.isNullOrEmpty() && payload?.startsWith(PREFIX_TEE) != true) {
            runCatching {
                preferences.edit().putString(key, encryptSecret(value)).apply()
            }
        }
        return value
    }

    private fun decryptSecret(payload: String?): String {
        if (payload.isNullOrEmpty()) return ""
        val (prefix, rawBase64) = when {
            payload.startsWith(PREFIX_TEE) -> PREFIX_TEE to payload.substring(PREFIX_TEE.length)
            payload.startsWith(PREFIX_DEV) -> PREFIX_DEV to payload.substring(PREFIX_DEV.length)
            else -> "" to payload
        }
        return try {
            val secretKey = if (prefix == PREFIX_DEV) getDeviceBoundKey() else getOrCreateMasterKey()
            decryptWithKey(rawBase64, secretKey)
        } catch (_: Exception) {
            if (prefix.isEmpty()) {
                runCatching { decryptWithKey(rawBase64, getDeviceBoundKey()) }.getOrDefault("")
            } else {
                ""
            }
        }
    }

    private fun decryptWithKey(rawBase64: String, secretKey: SecretKey): String {
        val combined = Base64.decode(rawBase64, Base64.NO_WRAP)
        if (combined.size <= GCM_IV_LENGTH) return ""

        val initializationVector = ByteArray(GCM_IV_LENGTH)
        val cipherTextLength = combined.size - GCM_IV_LENGTH
        val cipherText = ByteArray(cipherTextLength)

        System.arraycopy(combined, 0, initializationVector, 0, GCM_IV_LENGTH)
        System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherTextLength)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, initializationVector)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)

        return String(cipher.doFinal(cipherText), StandardCharsets.UTF_8)
    }

    companion object {
        private const val PREF_NAME = "agent_home_config"
        private const val KEY_ALIAS = "AgentHomeMasterKey"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        private const val PREFIX_TEE = "tee:"
        private const val PREFIX_DEV = "dev:"
        private const val KEY_AGENT_URL = "agent_server_url"
        private const val KEY_AGENT_TOKEN_ENC = "agent_token_enc"
        private const val KEY_HA_URL = "ha_url"
        private const val KEY_HA_TOKEN_ENC = "ha_token_enc"
    }
}
