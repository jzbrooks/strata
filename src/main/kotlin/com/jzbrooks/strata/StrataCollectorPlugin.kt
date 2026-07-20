package com.jzbrooks.strata

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

public class StrataCollectorPlugin : Plugin<Settings> {
  override fun apply(settings: Settings) {
    settings.gradle.sharedServices.registerIfAbsent(
        DEPENDENCY_EDGES_SERVICE,
        DependencyEdgesService::class.java,
    ) {
      it.parameters.collectorApplied.set(true)
    }
    settings.gradle.lifecycle.afterProject(CollectProjectDependenciesAction())
  }
}
