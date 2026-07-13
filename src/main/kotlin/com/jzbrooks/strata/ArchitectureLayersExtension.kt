package com.jzbrooks.strata

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/** Configures the ordered logical architecture for a Gradle build. */
abstract class ArchitectureLayersExtension
@Inject
constructor(
    private val objects: ObjectFactory,
) {
  private val declaredLayers = mutableListOf<LayerSpec>()
  private val declaredAllowances = mutableListOf<AllowanceSpec>()

  abstract val ignoredProjectPaths: SetProperty<String>
  abstract val ignoredConfigurationNames: SetProperty<String>
  abstract val unclassifiedProjects: Property<UnclassifiedProjectPolicy>

  init {
    ignoredProjectPaths.convention(emptySet())
    ignoredConfigurationNames.convention(emptySet())
    unclassifiedProjects.convention(UnclassifiedProjectPolicy.FAIL)
  }

  fun layer(name: String, configure: Action<LayerSpec>) {
    val layer = objects.newInstance(DefaultLayerSpec::class.java, name)
    configure.execute(layer)
    declaredLayers += layer
  }

  fun ignoreProject(path: String) {
    ignoredProjectPaths.add(path)
  }

  fun ignoreConfiguration(name: String) {
    ignoredConfigurationNames.add(name)
  }

  fun allow(from: String, to: String, because: String) {
    declaredAllowances += AllowanceSpec(from, to, because)
  }

  internal fun layers(): List<LayerSpec> = declaredLayers.toList()

  internal fun allowances(): List<AllowanceSpec> = declaredAllowances.toList()
}

interface LayerSpec {
  val name: String
  val projectRoots: SetProperty<String>

  fun projects(vararg projectRoots: String)
}

internal abstract class DefaultLayerSpec
@Inject
constructor(
    final override val name: String,
) : LayerSpec {
  abstract override val projectRoots: SetProperty<String>

  override fun projects(vararg projectRoots: String) {
    this.projectRoots.addAll(projectRoots.toList())
  }
}

enum class UnclassifiedProjectPolicy {
  FAIL,
  IGNORE,
}

internal data class AllowanceSpec(
    val from: String,
    val to: String,
    val because: String,
)
