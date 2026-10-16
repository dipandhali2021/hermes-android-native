package com.dipandhali.hermesnative.domain.modelpicker

import com.dipandhali.hermesnative.core.model.ModelDto
import com.dipandhali.hermesnative.core.model.ModelGroupDto
import com.dipandhali.hermesnative.core.model.ModelsResponse
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** A configured-model badge as the picker uses it. */
data class ConfiguredBadge(
    val provider: String? = null,
    val role: String? = null,
    val label: String? = null,
)

/** A model+provider pair the picker can select. */
data class ModelSelection(val model: String, val provider: String? = null)

/**
 * One picker row.
 *
 * [badgeRole] is the badge's display word (`label`, falling back to `role`);
 * [badgeLabel] is the configured entry's own spelling plus its provider — the
 * string the web app marks configured rows with.
 */
data class ModelRow(
    val model: String,
    val displayName: String,
    val providerId: String?,
    val badgeRole: String? = null,
    val badgeLabel: String? = null,
)

/** One provider group of the picker. [hidden] carries the overflow models. */
data class ModelGroup(
    val key: String,
    val label: String,
    val rows: List<ModelRow>,
    val hidden: List<ModelRow>,
    val endpointError: String? = null,
)

data class PickerCatalog(
    val configured: List<ModelRow>,
    val groups: List<ModelGroup>,
    val flat: List<ModelRow>,
    val defaultModel: String?,
    val activeProvider: String?,
)

/**
 * Badge lookup, `_getConfiguredModelBadge` (ui.js): exact spelling first when
 * the providers agree, then normalized-spelling matches. With a provider given
 * and several normalized matches but none from that provider there is no
 * badge — guessing would mark another provider's model as configured.
 *
 * Returns the badge map's raw key alongside the badge so configured rows can
 * show the config's own spelling.
 */
fun configuredBadgeFor(
    badges: Map<String, ConfiguredBadge>,
    value: String,
    providerId: String?,
): Pair<String, ConfiguredBadge>? {
    if (value.isEmpty()) return null
    val provider = (providerId ?: "").lowercase()
    badges[value]?.let { exact ->
        val exactProvider = (exact.provider ?: "").lowercase()
        if (provider.isEmpty() || exactProvider.isEmpty() || exactProvider == provider) {
            return value to exact
        }
    }
    val target = ModelIdentity.normalizeConfiguredKey(value)
    val matches = badges.entries.filter { ModelIdentity.normalizeConfiguredKey(it.key) == target }
    if (matches.isEmpty()) return null
    if (provider.isNotEmpty()) {
        val providerMatch = matches.firstOrNull {
            (it.value.provider ?: "").lowercase() == provider
        }
        if (providerMatch != null) return providerMatch.key to providerMatch.value
        return if (matches.size == 1) matches.first().key to matches.first().value else null
    }
    return matches.first().key to matches.first().value
}

/**
 * Builds the picker's catalog from `/api/models`.
 *
 * Ports `renderModelDropdown`'s configured-section logic (ui.js): every row is
 * badge-matched, configured candidates dedupe by provider + normalized
 * spelling with the bare spelling winning, badge-map entries with no equivalent
 * row are appended as their own rows, and configured winners are hoisted out
 * of their groups by model value (overflow by configured semantics).
 *
 * Without groups (older server) the same rows and configured section come from
 * the flat `models`/`extra_models` lists instead.
 */
object PickerCatalogBuilder {

    fun build(response: ModelsResponse): PickerCatalog {
        val badges = response.configuredModelBadges.mapValues { (_, dto) ->
            ConfiguredBadge(provider = dto.provider, role = dto.role, label = dto.label)
        }

        val renderableGroups = response.groups.filter {
            it.models.isNotEmpty() || it.extraModels.isNotEmpty()
        }

        if (renderableGroups.isEmpty()) {
            val entries = (response.models + response.extraModels)
                .map { entryFor(badges, it, null) }
            val configured = buildConfigured(entries, badges)
            val winners = configured.rows.map { it.model }.toSet()
            return PickerCatalog(
                configured = configured.rows,
                groups = emptyList(),
                flat = entries.map { it.row }.filter { it.model !in winners },
                defaultModel = response.defaultModel,
                activeProvider = response.activeProvider,
            )
        }

        val perGroup = response.groups
            .filter {
                it.models.isNotEmpty() || it.extraModels.isNotEmpty() ||
                    endpointErrorOf(it) != null
            }
            .map { group ->
                val normal = group.models.map { entryFor(badges, it, group.providerId) }
                val hidden = group.extraModels.map { entryFor(badges, it, group.providerId) }
                Triple(group, normal, hidden)
            }

        val allEntries = perGroup.flatMap { (_, normal, hidden) -> normal + hidden }
        val configured = buildConfigured(allEntries, badges)
        val winnerValues = configured.rows.map { it.model }.toSet()

        val groups = perGroup.map { (group, normal, hidden) ->
            ModelGroup(
                key = group.providerId?.takeIf { it.isNotEmpty() } ?: group.provider ?: "",
                label = plainGroupLabel(group.provider),
                rows = normal.map { it.row }.filter { it.model !in winnerValues },
                hidden = hidden
                    .filter {
                        configuredKey(it.row.model, it.badgeMatch?.second?.provider) !in
                            configured.semanticKeys
                    }
                    .map { it.row },
                endpointError = endpointErrorOf(group),
            )
        }

        return PickerCatalog(
            configured = configured.rows,
            groups = groups,
            flat = emptyList(),
            defaultModel = response.defaultModel,
            activeProvider = response.activeProvider,
        )
    }

    private class RowEntry(val row: ModelRow, val badgeMatch: Pair<String, ConfiguredBadge>?) {
        /** `_entryProvider`: structural provider, else the matched badge's. */
        val providerAuthority: String
            get() = (
                row.providerId?.takeIf { it.isNotEmpty() }
                    ?: badgeMatch?.second?.provider
                    ?: ""
                ).lowercase()
    }

    private class Candidate(val row: ModelRow, val rawKey: String, val badge: ConfiguredBadge)

    private class ConfiguredResult(val rows: List<ModelRow>, val semanticKeys: Set<String>)

    private fun entryFor(
        badges: Map<String, ConfiguredBadge>,
        dto: ModelDto,
        fallbackProvider: String?,
    ): RowEntry {
        val model = dto.id.orEmpty()
        val row = ModelRow(
            model = model,
            displayName = dto.displayLabel,
            providerId = fallbackProvider?.takeIf { it.isNotBlank() }
                ?: dto.provider?.takeIf { it.isNotBlank() },
        )
        return RowEntry(row, configuredBadgeFor(badges, model, row.providerId))
    }

    private fun buildConfigured(
        entries: List<RowEntry>,
        badges: Map<String, ConfiguredBadge>,
    ): ConfiguredResult {
        val candidates = buildList {
            for (entry in entries) {
                val match = entry.badgeMatch ?: continue
                add(Candidate(entry.row, match.first, match.second))
            }
            for ((rawKey, badge) in badges) {
                if (isEquivalentConfiguredEntry(rawKey, badge, entries)) continue
                add(
                    Candidate(
                        row = ModelRow(
                            model = rawKey,
                            displayName = rawKey,
                            providerId = badge.provider?.takeIf { it.isNotEmpty() },
                        ),
                        rawKey = rawKey,
                        badge = badge,
                    ),
                )
            }
        }

        val winners = LinkedHashMap<String, Candidate>()
        for (candidate in candidates) {
            val key = configuredKey(candidate.row.model, candidate.badge.provider)
            val existing = winners[key]
            if (existing == null ||
                displayPriority(candidate.row.model) > displayPriority(existing.row.model)
            ) {
                winners[key] = candidate
            }
        }

        val ordered = winners.values.sortedWith(
            compareBy({ configuredRank(it.badge) }, { it.row.displayName }),
        )
        val rows = ordered.map { candidate ->
            candidate.row.copy(
                badgeRole = candidate.badge.label?.takeIf { it.isNotEmpty() } ?: candidate.badge.role,
                badgeLabel = badgeChipLabel(candidate.rawKey, candidate.badge),
            )
        }
        return ConfiguredResult(rows, winners.keys.toSet())
    }

    /**
     * `_isEquivalentConfiguredModelEntry` (ui.js): is this badge-map entry
     * already a picker row? Same normalized spelling with provider agreement
     * covers the direct spellings; the `<provider>/` and `@provider:` checks
     * cover routing spellings whose prefix the normalizer strips.
     */
    private fun isEquivalentConfiguredEntry(
        modelId: String,
        badge: ConfiguredBadge,
        entries: List<RowEntry>,
    ): Boolean {
        val normalized = ModelIdentity.normalizeConfiguredKey(modelId)
        val provider = (badge.provider ?: "").lowercase()

        val sameNormalized = entries.filter {
            ModelIdentity.normalizeConfiguredKey(it.row.model) == normalized
        }
        if (sameNormalized.any { entry ->
                provider.isEmpty() || entry.providerAuthority.isEmpty() ||
                    entry.providerAuthority == provider
            }
        ) {
            return true
        }

        val slashPrefix = if (provider.isNotEmpty()) "$provider/" else ""
        if (slashPrefix.isNotEmpty() && modelId.lowercase().startsWith(slashPrefix)) {
            val routed = modelId.substring(slashPrefix.length)
            if (routed.isNotEmpty() && entries.any { entry ->
                    entry.providerAuthority == provider &&
                        ModelIdentity.normalizeConfiguredKey(entry.row.model) ==
                        ModelIdentity.normalizeConfiguredKey(routed)
                }
            ) {
                return true
            }
        }

        val prefix = if (provider.isNotEmpty()) "@$provider:" else ""
        if (prefix.isEmpty() || !modelId.lowercase().startsWith(prefix)) return false
        val routed = modelId.substring(prefix.length)
        return entries.any { entry ->
            entry.providerAuthority == provider &&
                ModelIdentity.normalizeConfiguredKey(entry.row.model) ==
                ModelIdentity.normalizeConfiguredKey(routed)
        }
    }

    /** `_configuredProviderKey :: _configuredModelKey` — the dedupe identity. */
    private fun configuredKey(model: String, badgeProvider: String?): String {
        val provider = (
            badgeProvider?.takeIf { it.isNotEmpty() }
                ?: ModelIdentity.providerFromModelValue(model)
                ?: ""
            ).lowercase()
        return "$provider::${ModelIdentity.normalizeConfiguredKey(model)}"
    }

    /** `_configuredDisplayPriority`: plain ids over qualified aliases. */
    private fun displayPriority(value: String): Int = when {
        value.startsWith("@") -> 0
        value.contains('/') -> 1
        else -> 2
    }

    /** `_configuredRank`: primary first, then Fallback N in order. */
    private fun configuredRank(badge: ConfiguredBadge): Int = when {
        badge.role == "primary" -> 0
        badge.role == "fallback" ->
            FALLBACK_INDEX.find(badge.label ?: "")?.groupValues?.get(1)?.toIntOrNull() ?: 999
        else -> 500
    }

    private fun badgeChipLabel(rawKey: String, badge: ConfiguredBadge): String {
        val provider = badge.provider
        if (provider.isNullOrEmpty()) return rawKey
        return rawKey + " (" + provider.removePrefix("custom:").split('/').first() + ")"
    }

    private fun plainGroupLabel(label: String?): String =
        (label ?: "").replace(GROUP_COUNT_SUFFIX, "")

    private fun endpointErrorOf(group: ModelGroupDto): String? =
        when (val element = group.modelsEndpointError) {
            null, is JsonNull -> null
            is JsonPrimitive ->
                if (element.isString) element.content.takeIf { it.isNotEmpty() } else UNREACHABLE
            else -> UNREACHABLE
        }

    private val FALLBACK_INDEX = Regex("fallback\\s+(\\d+)", RegexOption.IGNORE_CASE)
    private val GROUP_COUNT_SUFFIX = Regex("\\s*\\(\\d+\\s+of\\s+\\d+\\)\\s*$")
    private const val UNREACHABLE = "Models endpoint unreachable"
}
