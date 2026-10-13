package com.vn.pixelpet

import android.content.SharedPreferences
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Premium license-key activation (Gumroad manual-delivery pattern).
 *
 * Scheme: `VNPP-XXXXXXX-XXXXXXX-XXXXXX-XXXXXX` (Crockford base32, 26 chars =
 * 10-byte key id + 6-byte HMAC-SHA256 tag). Validation is LOCAL:
 * recompute HMAC-SHA256(key id, embedded secret), constant-time compare.
 *
 * HONEST NOTE: this is obfuscation-tier, not DRM — right-sized for indie
 * manual sales, not for stopping determined piracy. Play Billing is the
 * documented future path (see PLAN.md). The key itself is never stored;
 * only the key id + activated flag are persisted.
 *
 * Key minting: `tools/genkey.py` (repo root, not shipped in the APK).
 */
object PremiumManager {

    private const val PREFIX = "VNPP"
    private const val K_PREMIUM = "premium_active"
    private const val K_KEY_ID = "premium_key_id"
    private const val K_ATTEMPTS = "key_attempts"
    private const val K_LOCKOUT_UNTIL = "key_lockout_until"
    private const val MAX_ATTEMPTS = 5
    private const val LOCKOUT_MS = 3600 * 1000L

    // Embedded secret, split + XOR'd so it isn't a single grep-able string.
    // (Speed bump only — see honest note above.)
    private fun secret(): ByteArray {
        val a = "VnP1x3lP3t".toByteArray()
        val b = "S3cr3tS33d".toByteArray()
        val k = "K3yM4st3r!".toByteArray()
        return ByteArray(16) { i -> (a[i % a.size].toInt() xor b[i % b.size].toInt() xor k[i % k.size].toInt()).toByte() }
    }

    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ" // Crockford, no I/L/O/U

    private fun b32decode(s: String): ByteArray {
        var bits = 0; var value = 0; val out = mutableListOf<Byte>()
        for (ch in s) {
            val v = ALPHABET.indexOf(ch)
            if (v < 0) throw IllegalArgumentException("bad char")
            value = (value shl 5) or v; bits += 5
            if (bits >= 8) { bits -= 8; out.add(((value shr bits) and 0xFF).toByte()) }
        }
        return out.toByteArray()
    }

    private fun hmac(id: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret(), "HmacSHA256"))
        return mac.doFinal(id)
    }

    sealed class Result {
        object Ok : Result()
        object BadFormat : Result()
        object BadKey : Result()
        data class LockedOut(val untilMs: Long) : Result()
    }

    /** Validate + activate. Returns the outcome for the UI. */
    fun activate(prefs: SharedPreferences, rawInput: String): Result {
        val now = System.currentTimeMillis()
        if (now < prefs.getLong(K_LOCKOUT_UNTIL, 0L)) {
            return Result.LockedOut(prefs.getLong(K_LOCKOUT_UNTIL, 0L))
        }
        val clean = rawInput.trim().uppercase().replace("-", "")
        if (!clean.startsWith(PREFIX) || clean.length != PREFIX.length + 26) {
            return recordMiss(prefs)
        }
        val payload = try {
            b32decode(clean.substring(PREFIX.length))
        } catch (t: Throwable) {
            return recordMiss(prefs)
        }
        if (payload.size != 16) return recordMiss(prefs)
        val id = payload.sliceArray(0 until 10)
        val tag = payload.sliceArray(10 until 16)
        val expected = hmac(id).sliceArray(0 until 6)
        if (!MessageDigest.isEqual(tag, expected)) return recordMiss(prefs)
        // valid — activate
        prefs.edit()
            .putBoolean(K_PREMIUM, true)
            .putString(K_KEY_ID, id.joinToString("") { "%02x".format(it) })
            .putInt(K_ATTEMPTS, 0)
            .apply()
        return Result.Ok
    }

    private fun recordMiss(prefs: SharedPreferences): Result {
        val attempts = prefs.getInt(K_ATTEMPTS, 0) + 1
        val ed = prefs.edit().putInt(K_ATTEMPTS, attempts)
        if (attempts >= MAX_ATTEMPTS) {
            val until = System.currentTimeMillis() + LOCKOUT_MS
            ed.putLong(K_LOCKOUT_UNTIL, until).putInt(K_ATTEMPTS, 0).apply()
            return Result.LockedOut(until)
        }
        ed.apply()
        return Result.BadKey
    }

    fun isPremium(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(K_PREMIUM, false)

    fun keyId(prefs: SharedPreferences): String =
        prefs.getString(K_KEY_ID, "") ?: ""
}
