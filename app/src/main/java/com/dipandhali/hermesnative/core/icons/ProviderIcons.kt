package com.dipandhali.hermesnative.core.icons

/** Which LobeHub static-PNG variant to render. */
enum class ProviderIconVariant(val dirName: String) {
    LIGHT("light"),
    DARK("dark"),
}

/**
 * LobeHub AI-provider icons (MIT), pinned to a tag so the artwork cannot
 * change under a build. The known set is closed on purpose: an unknown
 * provider renders the monogram rather than a broken or wrong logo.
 *
 * Precedence: the vendor segment of the model id (`vendor/rest`, with
 * `@provider:` routing tags stripped), then the owning provider id, then
 * nothing.
 */
object ProviderIcons {

    const val CDN_BASE =
        "https://cdn.jsdelivr.net/gh/lobehub/lobe-icons@v6.0.0/packages/static-png"

    private val KNOWN = setOf(
        "openai", "anthropic", "google", "gemini", "deepseek", "mistral", "xai", "grok",
        "qwen", "moonshot", "zhipu", "zai", "minimax", "upstage", "openrouter", "groq",
        "ollama", "together", "cohere", "perplexity", "meta", "nvidia", "nousresearch",
        "hermesagent",
    )

    private val PROVIDER_ALIASES = mapOf(
        "nous" to "nousresearch",
        "hermes" to "hermesagent",
        "moonshotai" to "moonshot",
        "z-ai" to "zai",
    )

    private val VENDOR_ALIASES = mapOf(
        "x-ai" to "xai",
        "mistralai" to "mistral",
        "meta-llama" to "meta",
        "z-ai" to "zai",
        "google" to "google",
        "grok" to "xai",
    )

    /** The icon id for a row, or null when no known icon matches. */
    fun iconId(modelId: String?, providerId: String?): String? {
        vendorFrom(modelId)?.let { return it }
        val provider = (providerId ?: "").trim().lowercase()
        if (provider.isNotEmpty()) {
            val resolved = PROVIDER_ALIASES[provider] ?: provider
            if (resolved in KNOWN) return resolved
        }
        return null
    }

    fun url(
        iconId: String,
        variant: ProviderIconVariant,
        base: String = CDN_BASE,
    ): String = "$base/${variant.dirName}/$iconId.png"

    /**
     * The letter for the fallback circle: model id, then provider, then the
     * display label — first alphanumeric character, so `@nous:` routing reads
     * as the provider rather than as punctuation.
     */
    fun monogramText(modelId: String?, providerId: String?, fallback: String?): String =
        firstLetter(modelId) ?: firstLetter(providerId) ?: firstLetter(fallback) ?: "?"

    private fun vendorFrom(modelId: String?): String? {
        var id = (modelId ?: "").trim().lowercase()
        if (id.isEmpty()) return null
        if (id.startsWith("@") && id.contains(':')) {
            val stripped = id.substring(id.indexOf(':') + 1)
            if (stripped.isNotEmpty()) id = stripped
        }
        val slash = id.indexOf('/')
        if (slash <= 0) return null
        val vendor = id.substring(0, slash)
        val resolved = VENDOR_ALIASES[vendor] ?: vendor
        return if (resolved in KNOWN) resolved else null
    }

    private fun firstLetter(value: String?): String? =
        value?.firstOrNull { it.isLetterOrDigit() }?.uppercase()
}
