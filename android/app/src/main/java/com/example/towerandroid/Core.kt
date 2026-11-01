package com.example.towerandroid

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

/** Narrow, fail-closed subscription subset. Errors never contain input or credentials. */
object Core {
    class ParseException(message: String) : IllegalArgumentException(message)

    enum class Protocol { SS, TROJAN, VLESS }

    data class Node(
        val name: String,
        val protocol: Protocol,
        val server: String,
        val port: Int,
        val secret: String,
        val cipher: String? = null,
        val sni: String? = null
    )

    private val ciphers = setOf("aes-128-gcm", "aes-256-gcm", "chacha20-ietf-poly1305", "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305")
    private const val MAX_INPUT = 1024 * 1024
    private const val MAX_NODES = 1000

    private fun fail(): Nothing = throw ParseException("Invalid or unsupported subscription")

    private fun utf8(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: Exception) { fail() }

    private fun decode64(text: String): String {
        if (text.isEmpty() || !text.matches(Regex("[A-Za-z0-9_+/=-]+"))) fail()
        if (text.contains('+') || text.contains('/')) {
            if (text.contains('-') || text.contains('_')) fail()
        }
        return try {
            val normalized = text.replace('-', '+').replace('_', '/')
            utf8(Base64.getDecoder().decode(normalized))
        } catch (_: Exception) { fail() }
    }

    // URI percent decoding is deliberately not form decoding: '+' remains '+'.
    private fun unescape(s: String): String {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            if (s[i] == '%') {
                if (i + 2 >= s.length) fail()
                val n = s.substring(i + 1, i + 3).toIntOrNull(16) ?: fail()
                out.write(n); i += 3
            } else {
                val cp = s.codePointAt(i)
                if (cp in 0xD800..0xDFFF) fail()
                val bytes = String(Character.toChars(cp)).toByteArray(StandardCharsets.UTF_8)
                out.write(bytes); i += Character.charCount(cp)
            }
        }
        return utf8(out.toByteArray())
    }

    private fun clean(s: String, max: Int = 1024): String {
        if (s.isEmpty() || s.length > max) fail()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c.isISOControl() || c == '\u2028' || c == '\u2029') fail()
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length || !Character.isLowSurrogate(s[i + 1])) fail()
                i += 2
            } else {
                if (Character.isLowSurrogate(c)) fail()
                i++
            }
        }
        return s
    }

    private fun host(s: String): String {
        val h = if (s.startsWith('[') && s.endsWith(']')) s.substring(1, s.length - 1) else s
        if (h.isEmpty() || h.length > 253 || h.any { !(it.isLetterOrDigit() && it.code < 128 || it == '.' || it == '-' || it == ':') }) fail()
        if (h.contains(':')) {
            // URI validates IPv6 literals without performing DNS lookups.
            val checked = try { URI("x://[$h]:1") } catch (_: Exception) { fail() }
            if (checked.host == null || checked.port != 1) fail()
        } else if (h.startsWith('.') || h.endsWith('.') || h.split('.').any { it.isEmpty() || it.length > 63 || it.startsWith('-') || it.endsWith('-') }) fail()
        return h
    }

    private fun query(raw: String?): Map<String, String> {
        if (raw == null) return emptyMap()
        if (raw.isEmpty()) fail()
        val result = linkedMapOf<String, String>()
        for (part in raw.split('&')) {
            val i = part.indexOf('=')
            if (i <= 0) fail()
            val key = unescape(part.substring(0, i))
            val value = unescape(part.substring(i + 1))
            if (result.put(key, value) != null) fail()
        }
        return result
    }

    fun parseUri(input: String): Node {
        if (input.length > 16384 || input != input.trim() || input.any { it.isISOControl() }) fail()
        val uri = try { URI(input) } catch (_: Exception) { fail() }
        val protocol = when (uri.scheme?.lowercase()) {
            "ss" -> Protocol.SS
            "trojan" -> Protocol.TROJAN
            "vless" -> Protocol.VLESS
            else -> fail()
        }
        if (uri.isOpaque || (uri.rawPath != null && uri.rawPath != "" && uri.rawPath != "/")) fail()
        val params = query(uri.rawQuery)
        val allowed = when (protocol) {
            Protocol.SS -> emptySet()
            Protocol.TROJAN -> setOf("sni")
            Protocol.VLESS -> setOf("security", "type", "sni")
        }
        if (params.keys.any { it !in allowed }) fail()
        if (protocol == Protocol.VLESS && (params["security"] != "tls" || (params["type"] != null && params["type"] != "tcp"))) fail()
        val sni = params["sni"]?.let { host(clean(it, 253)) }
        var authority = uri.rawAuthority ?: fail()
        val legacySs = !authority.contains('@') && protocol == Protocol.SS
        if (legacySs) {
            authority = decode64(authority)
        }
        val at = authority.lastIndexOf('@')
        if (at <= 0 || (!legacySs && at != authority.indexOf('@'))) fail()
        val user = authority.substring(0, at)
        val endpoint = authority.substring(at + 1)
        val ep = try { URI("x://$endpoint") } catch (_: Exception) { fail() }
        if (ep.rawUserInfo != null || ep.rawPath != "" || ep.rawQuery != null || ep.rawFragment != null) fail()
        val server = host(ep.host ?: fail())
        val port = ep.port
        if (port !in 1..65535) fail()
        val name = uri.rawFragment?.let { clean(unescape(it), 256) } ?: "$server:$port"
        return when (protocol) {
            Protocol.SS -> {
                val decoded = if (legacySs) user else if (user.contains(':') || user.contains("%3a", true)) unescape(user) else decode64(user)
                val colon = decoded.indexOf(':')
                if (colon <= 0) fail()
                val cipher = decoded.substring(0, colon)
                if (cipher !in ciphers) fail()
                Node(name, protocol, server, port, clean(decoded.substring(colon + 1)), cipher)
            }
            Protocol.TROJAN -> Node(name, protocol, server, port, clean(unescape(user)), sni = sni)
            Protocol.VLESS -> {
                val id = unescape(user)
                val uuid = try { UUID.fromString(id) } catch (_: Exception) { fail() }
                if (!uuid.toString().equals(id, ignoreCase = true)) fail()
                Node(name, protocol, server, port, uuid.toString(), sni = sni)
            }
        }
    }

    /** Plain URI lines or one base64-encoded UTF-8 URI list. No partial results. */
    fun parseSubscription(input: String): List<Node> {
        if (input.length > MAX_INPUT) fail()
        val trimmed = input.trim()
        if (trimmed.isEmpty()) fail()
        val text = if (trimmed.startsWith("ss://", true) || trimmed.startsWith("trojan://", true) || trimmed.startsWith("vless://", true)) trimmed else decode64(trimmed)
        if (text.length > MAX_INPUT) fail()
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty() || lines.size > MAX_NODES) fail()
        val nodes = lines.map { parseUri(it) }
        if (nodes.map { it.name }.toSet().size != nodes.size) fail()
        return nodes
    }

    private fun quote(s: String): String = buildString {
        append('"')
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c.code < 0x20 || c.code == 0x7f) append("\\u%04x".format(c.code)) else append(c)
        }
        append('"')
    }

    /** Exports a proxies-only mihomo YAML fragment. Validate even caller-created Nodes. */
    fun exportYaml(nodes: List<Node>): String {
        if (nodes.isEmpty() || nodes.size > MAX_NODES || nodes.map { it.name }.toSet().size != nodes.size) fail()
        return buildString {
            append("proxies:\n")
            for (n in nodes) {
                clean(n.name, 256); host(n.server); clean(n.secret)
                if (n.port !in 1..65535 || n.server.startsWith('[') || n.server.endsWith(']')) fail()
                if (n.sni != null) { clean(n.sni, 253); host(n.sni); if (n.sni.startsWith('[') || n.sni.endsWith(']')) fail() }
                when (n.protocol) {
                    Protocol.SS -> if (n.cipher !in ciphers || n.sni != null) fail()
                    Protocol.TROJAN -> if (n.cipher != null) fail()
                    Protocol.VLESS -> {
                        if (n.cipher != null) fail()
                        val id = try { UUID.fromString(n.secret) } catch (_: Exception) { fail() }
                        if (id.toString() != n.secret) fail()
                    }
                }
                append("  - name: ").append(quote(n.name)).append('\n')
                append("    type: ").append(n.protocol.name.lowercase()).append('\n')
                append("    server: ").append(quote(n.server)).append('\n')
                append("    port: ").append(n.port).append('\n')
                when (n.protocol) {
                    Protocol.SS -> { append("    cipher: ").append(quote(n.cipher!!)).append('\n'); append("    password: ").append(quote(n.secret)).append('\n') }
                    Protocol.TROJAN -> append("    password: ").append(quote(n.secret)).append('\n')
                    Protocol.VLESS -> { append("    uuid: ").append(quote(n.secret)).append('\n'); append("    tls: true\n") }
                }
                if (n.sni != null) append(if (n.protocol == Protocol.TROJAN) "    sni: " else "    servername: ").append(quote(n.sni)).append('\n')
            }
        }
    }
}
