package com.jzbrooks.strata

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/** Configures the logical architecture for a Gradle build. */
public abstract class StrataExtension
@Inject
constructor(
    private val objects: ObjectFactory,
) {
  private val declaredLayers = mutableListOf<LayerSpec>()
  private val declaredAllowances = mutableListOf<AllowanceSpec>()

  public abstract val ignoredProjectPaths: SetProperty<String>
  public abstract val ignoredConfigurationNames: SetProperty<String>
  public abstract val unclassifiedProjects: Property<UnclassifiedProjectPolicy>

  init {
    ignoredProjectPaths.convention(emptySet())
    ignoredConfigurationNames.convention(emptySet())
    unclassifiedProjects.convention(UnclassifiedProjectPolicy.FAIL)
  }

  public fun layer(projectPath: String, configure: Action<LayerSpec>) {
    val layer = objects.newInstance(DefaultLayerSpec::class.java, projectPath)
    configure.execute(layer)
    declaredLayers += layer
  }

  public fun ignoreProject(path: String) {
    ignoredProjectPaths.add(path)
  }

  public fun ignoreConfiguration(name: String) {
    ignoredConfigurationNames.add(name)
  }

  public fun allow(from: String, to: String, because: String) {
    declaredAllowances += AllowanceSpec(from, to, because)
  }

  internal fun layers(): List<LayerSpec> = declaredLayers.toList()

  internal fun allowances(): List<AllowanceSpec> = declaredAllowances.toList()
}

public interface LayerSpec {
  public val projectPath: String
  public val dependencyPaths: SetProperty<String>

  public fun dependsOn(vararg layerProjectPaths: String)
}

internal abstract class DefaultLayerSpec
@Inject
constructor(
    final override val projectPath: String,
) : LayerSpec {
  abstract override val dependencyPaths: SetProperty<String>

  override fun dependsOn(vararg layerProjectPaths: String) {
    dependencyPaths.addAll(layerProjectPaths.toList())
  }
}

public enum class UnclassifiedProjectPolicy {
  FAIL,
  IGNORE,
}

internal data class AllowanceSpec(
    val from: String,
    val to: String,
    val because: String,
)
