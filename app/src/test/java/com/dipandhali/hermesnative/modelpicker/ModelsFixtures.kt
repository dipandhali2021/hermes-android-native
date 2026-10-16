package com.dipandhali.hermesnative.modelpicker

/**
 * `/api/models` payload with three spellings of one configured model, a
 * badge-only configured entry, one group whose only visible row is that
 * configured model (hoisted), and two overflow models.
 */
val MODELS_JSON = """
{
  "active_provider": "nous",
  "default_model": "solar-mini4:free",
  "configured_model_badges": {
    "solar-mini4:free": {"role": "primary", "label": "Primary", "provider": "nous"},
    "@nous:solar-mini4:free": {"role": "primary", "label": "Primary", "provider": "nous"},
    "nous/solar-mini4:free": {"role": "primary", "label": "Primary", "provider": "nous"},
    "openai/gpt-5.4": {"role": "fallback", "label": "Fallback 1", "provider": "openai"}
  },
  "groups": [
    {"provider": "Nous (1 of 3)", "provider_id": "nous",
     "models": [{"id": "@nous:solar-mini4:free", "label": "Solar Mini4 (free)"}],
     "extra_models": [{"id": "@nous:anthropic/claude-sonnet-4.6", "label": "Claude Sonnet 4.6"},
                      {"id": "@nous:moonshot/kimi-k2.5", "label": "Kimi K2.5"}]},
    {"provider": "OpenRouter", "provider_id": "openrouter",
     "models": [{"id": "openrouter/solar-pro4", "label": "Solar Pro4"}]}
  ],
  "models": [], "extra_models": []
}
""".trimIndent()
