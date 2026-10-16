package com.dipandhali.hermesnative.domain.modelpicker

/**
 * The web picker's model-identity helpers, ported one-to-one from
 * `static/ui.js` (`_normalizeConfiguredModelKey`, `_providerFromModelValue`,
 * `_modelPickerOptionIdentity`).
 *
 * The configured section keys rows by normalized spelling because the server
 * reports the same configured model under several model-id spellings (bare,
 * `@provider:`-routed, catalog-matched) and the badge map uses yet another.
 * Collapse too little and the section shows duplicates; collapse too much and
 * distinct models merge. The web rules are the authority on where that line
 * sits, so they are ported exactly rather than re-derived.
 */
object ModelIdentity {

    private val SCHEME = Regex("^[a-z][a-z0-9+.-]*://")
    private val FIRST_SLASH_SEGMENT = Regex("^[^/]+/")

    /**
     * `_normalizeConfiguredModelKey` (ui.js).
     *
     * Lowercases, strips an `@provider:` routing prefix, drops the first slash
     * segment (unless the id is a URI scheme), and folds dashes to dots.
     */
    fun normalizeConfiguredKey(rawKey: String): String {
        var s = rawKey.trim().lowercase()
        var strippedAtProvider = false
        // Strip the @provider: prefix. A trailing-colon key falls back to the
        // original so malformed configs don't collapse distinct ids to ''.
        if (s.startsWith("@") && s.contains(':')) {
            val ci = s.indexOf(':', 1)
            val cand = s.substring(ci + 1)
            strippedAtProvider = cand.isNotEmpty()
            if (cand.isNotEmpty()) s = cand
        }
        // Slash-stripping is skipped for URI-scheme ids (#3429).
        if (!SCHEME.containsMatchIn(s)) {
            // Colon-qualified prefixes before the first slash are stripped
            // (#3360); only the first slash segment goes, so vendor hierarchy
            // survives.
            if (!strippedAtProvider && s.contains('/') &&
                s.indexOf(':') != -1 && s.indexOf(':') < s.indexOf('/')
            ) {
                val cut = s.substring(s.indexOf('/') + 1)
                if (cut.isNotEmpty()) s = cut
            }
            if (s.contains('/')) {
                val cut = FIRST_SLASH_SEGMENT.replaceFirst(s, "")
                if (cut.isNotEmpty()) s = cut
            }
        }
        return s.replace('-', '.')
    }

    /**
     * `_providerFromModelValue` (ui.js); `null` where the web returns `''`.
     *
     * `@custom:<host>:<port>` with a plausible host and port keeps the
     * endpoint in the provider id; other `@custom:` shapes keep only the first
     * slug segment.
     */
    fun providerFromModelValue(modelId: String?): String? {
        val value = (modelId ?: "").trim()
        if (!value.startsWith("@") || !value.contains(':')) return null
        if (value.startsWith("@custom:")) {
            val afterCustom = value.substring("@custom:".length)
            val parts = afterCustom.split(':')
            if (parts.size >= 3 && parts[1].isNotEmpty() && parts[1].all { it.isDigit() }) {
                val port = parts[1].toIntOrNull()
                val host = parts[0]
                val hl = host.lowercase()
                if (port != null && port in 1..65535 && (hl == "localhost" || host.contains('.'))) {
                    return "custom:$host:${parts[1]}"
                }
            }
            val firstColon = afterCustom.indexOf(':')
            return if (firstColon >= 0) "custom:" + afterCustom.substring(0, firstColon)
            else "custom:$afterCustom"
        }
        return value.substring(1, value.indexOf(':')).ifEmpty { null }
    }

    /**
     * `_modelPickerOptionIdentity` (ui.js).
     *
     * Collapses an `@provider:model` value and its bare `model` spelling to one
     * identity; `provider/<model>` spellings deliberately do NOT collapse here
     * (dedupe covers that pair via [normalizeConfiguredKey]).
     */
    fun optionIdentity(modelId: String?, providerId: String?): String {
        var value = modelId ?: ""
        val provider = (providerId ?: "").trim()
        if (value.startsWith("@") && value.contains(':')) {
            val exactPrefix = if (provider.isNotEmpty()) "@$provider:" else ""
            if (exactPrefix.isNotEmpty() && value.lowercase().startsWith(exactPrefix.lowercase())) {
                value = value.substring(exactPrefix.length)
            } else if (value.startsWith("@custom:")) {
                // For @custom:<host>:<port>:<model>, the split point is the end
                // of "<host>:<port>"; otherwise it is the first colon slug.
                val afterCustom = value.substring("@custom:".length)
                val parts = afterCustom.split(':')
                var splitAt = -1
                if (parts.size >= 3 && parts[1].isNotEmpty() && parts[1].all { it.isDigit() }) {
                    val port = parts[1].toIntOrNull()
                    val host = parts[0]
                    val hl = host.lowercase()
                    if (port != null && port in 1..65535 && (hl == "localhost" || host.contains('.'))) {
                        splitAt = host.length + 1 + parts[1].length
                    }
                }
                if (splitAt < 0) splitAt = afterCustom.indexOf(':')
                value = if (splitAt >= 0) afterCustom.substring(splitAt + 1) else afterCustom
            } else {
                value = value.substring(value.indexOf(':') + 1)
            }
        }
        return value.replace('-', '.').lowercase()
    }
}
