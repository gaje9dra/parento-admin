package com.parento.admin.config

object AdminApplicationConfig {
    @Volatile
    private var current: AppConfig? = null

    fun initialize(config: AppConfig = BuildConfiguration.load()) {
        current = config
    }

    fun get(): AppConfig =
        current ?: error("Parento Admin application configuration has not been initialized.")
}
