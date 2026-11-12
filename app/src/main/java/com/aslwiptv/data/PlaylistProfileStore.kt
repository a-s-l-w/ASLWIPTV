package com.aslwiptv.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

enum class PlaylistSourceType { XTREAM, M3U_URL, M3U_FILE }

data class PlaylistProfile(
    val id: String,
    val name: String,
    val sourceType: PlaylistSourceType,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val playlistUrl: String = "",
    val channels: List<MediaItem>
)

data class PlaylistLibrary(
    val profiles: List<PlaylistProfile>,
    val activeProfileId: String?
)

/** Stores source details and cached channels encrypted with a key held by Android Keystore. */
object PlaylistProfileStore {
    private const val KEY_ALIAS = "aslwiptv_playlist_profiles_v1"
    private const val FILE_NAME = "playlist_profiles_v1.enc"
    private const val FORMAT_VERSION = 1

    suspend fun load(context: Context): PlaylistLibrary = withContext(Dispatchers.IO) {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        if (!file.exists()) return@withContext PlaylistLibrary(emptyList(), null)

        val input = DataInputStream(FileInputStream(file))
        val plaintext = input.use { data ->
            require(data.readInt() == FORMAT_VERSION) { "Formato profili non supportato." }
            val ivSize = data.readInt()
            require(ivSize in 12..16) { "Archivio profili non valido." }
            val iv = ByteArray(ivSize).also(data::readFully)
            val ciphertext = data.readBytes()
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), javax.crypto.spec.GCMParameterSpec(128, iv))
                doFinal(ciphertext)
            }
        }
        decode(String(plaintext, StandardCharsets.UTF_8))
    }

    suspend fun save(context: Context, profiles: List<PlaylistProfile>, activeProfileId: String?) = withContext(Dispatchers.IO) {
        require(activeProfileId == null || profiles.any { it.id == activeProfileId }) {
            "La lista attiva non è presente nei profili salvati."
        }
        val plain = encode(profiles, activeProfileId).toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain)
        val directory = context.applicationContext.filesDir
        val target = File(directory, FILE_NAME)
        val temporary = File(directory, "$FILE_NAME.tmp")
        val backup = File(directory, "$FILE_NAME.bak")

        val stream = FileOutputStream(temporary)
        DataOutputStream(stream).use { output ->
            output.writeInt(FORMAT_VERSION)
            output.writeInt(cipher.iv.size)
            output.write(cipher.iv)
            output.write(encrypted)
            output.flush()
            stream.fd.sync()
        }

        if (!temporary.renameTo(target)) {
            if (backup.exists()) check(backup.delete()) { "Impossibile aggiornare l’archivio profili." }
            val hadPrevious = target.exists()
            if (hadPrevious) check(target.renameTo(backup)) { "Impossibile conservare l’archivio profili precedente." }
            if (!temporary.renameTo(target)) {
                if (hadPrevious) backup.renameTo(target)
                error("Impossibile salvare l’archivio profili.")
            }
            if (backup.exists()) backup.delete()
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encode(profiles: List<PlaylistProfile>, activeProfileId: String?): String {
        val profileArray = JSONArray()
        profiles.forEach { profile ->
            val channelArray = JSONArray()
            profile.channels.forEach { item ->
                channelArray.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("title", item.title)
                        .put("streamUrl", item.streamUrl)
                        .put("category", item.category)
                        .put("logoUrl", item.logoUrl ?: JSONObject.NULL)
                        .put("kind", item.kind.name)
                        .put("epgId", item.epgId ?: JSONObject.NULL)
                        .put("seriesId", item.seriesId ?: JSONObject.NULL)
                        .put("requestHeaders", JSONObject(item.requestHeaders))
                )
            }
            profileArray.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("sourceType", profile.sourceType.name)
                    .put("serverUrl", profile.serverUrl)
                    .put("username", profile.username)
                    .put("password", profile.password)
                    .put("playlistUrl", profile.playlistUrl)
                    .put("channels", channelArray)
            )
        }
        return JSONObject()
            .put("activeProfileId", activeProfileId ?: JSONObject.NULL)
            .put("profiles", profileArray)
            .toString()
    }

    private fun decode(content: String): PlaylistLibrary {
        val root = JSONObject(content)
        val profiles = buildList {
            val profileArray = root.optJSONArray("profiles") ?: JSONArray()
            for (i in 0 until profileArray.length()) {
                val profile = profileArray.optJSONObject(i) ?: continue
                val channels = buildList {
                    val channelArray = profile.optJSONArray("channels") ?: JSONArray()
                    for (j in 0 until channelArray.length()) {
                        val channel = channelArray.optJSONObject(j) ?: continue
                        val kind = runCatching { MediaKind.valueOf(channel.optString("kind")) }
                            .getOrDefault(MediaKind.UNKNOWN)
                        val id = channel.optString("id")
                        val title = channel.optString("title")
                        val streamUrl = channel.optString("streamUrl")
                        val seriesId = channel.optNullableString("seriesId")
                        val headers = channel.optJSONObject("requestHeaders")?.let { headerObject ->
                            buildMap {
                                val keys = headerObject.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    val value = headerObject.optNullableString(key) ?: continue
                                    if (key in setOf("User-Agent", "Referer", "Origin", "Cookie")) put(key, value)
                                }
                            }
                        }.orEmpty()
                        if (id.isBlank() || title.isBlank() || (streamUrl.isBlank() && seriesId == null)) continue
                        add(
                            MediaItem(
                                id = id,
                                title = title,
                                streamUrl = streamUrl,
                                category = channel.optString("category", "Senza categoria"),
                                logoUrl = channel.optNullableString("logoUrl"),
                                kind = kind,
                                epgId = channel.optNullableString("epgId"),
                                seriesId = seriesId,
                                requestHeaders = headers
                            )
                        )
                    }
                }
                val id = profile.optString("id")
                val name = profile.optString("name")
                val sourceType = runCatching { PlaylistSourceType.valueOf(profile.optString("sourceType")) }
                    .getOrDefault(PlaylistSourceType.M3U_FILE)
                if (id.isBlank() || name.isBlank() || channels.isEmpty()) continue
                add(
                    PlaylistProfile(
                        id = id,
                        name = name,
                        sourceType = sourceType,
                        serverUrl = profile.optString("serverUrl"),
                        username = profile.optString("username"),
                        password = profile.optString("password"),
                        playlistUrl = profile.optString("playlistUrl"),
                        channels = channels
                    )
                )
            }
        }
        return PlaylistLibrary(profiles, root.optNullableString("activeProfileId")?.takeIf { id -> profiles.any { it.id == id } })
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)
}

