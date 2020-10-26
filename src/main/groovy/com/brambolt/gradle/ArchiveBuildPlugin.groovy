/*
 * Copyright 2017-2020 Brambolt ehf.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.brambolt.gradle

import com.brambolt.gradle.velocity.tasks.Velocity
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

import static com.brambolt.gradle.PluginBuildPlugin.ARTIFACTORY_PLUGIN_ID
import static com.brambolt.gradle.PluginBuildPlugin.BINTRAY_PLUGIN_ID
import static com.brambolt.gradle.PluginBuildPlugin.checkProjectProperties
import static com.brambolt.gradle.PluginBuildPlugin.configureArtifactory
import static com.brambolt.gradle.PluginBuildPlugin.configureBintray
import static com.brambolt.gradle.PluginBuildPlugin.configureDefaultTasks
import static com.brambolt.gradle.PluginBuildPlugin.configureDependencies
import static com.brambolt.gradle.PluginBuildPlugin.configureDerivedPropertiesWithoutPlugins
import static com.brambolt.gradle.PluginBuildPlugin.configureDerivedPropertiesWithPlugins
import static com.brambolt.gradle.PluginBuildPlugin.configureJarTask
import static com.brambolt.gradle.PluginBuildPlugin.configureJavadocJarTask
import static com.brambolt.gradle.PluginBuildPlugin.configureJavaPlugin
import static com.brambolt.gradle.PluginBuildPlugin.configurePluginInclusion
import static com.brambolt.gradle.PluginBuildPlugin.configurePlugins
import static com.brambolt.gradle.PluginBuildPlugin.configureJavaPublishing
import static com.brambolt.gradle.PluginBuildPlugin.configureRepositories
import static com.brambolt.gradle.PluginBuildPlugin.configureSourceJarTask
import static com.brambolt.gradle.PluginBuildPlugin.logProperties

import static com.brambolt.gradle.velocity.tasks.Velocity.DEFAULT_VELOCITY_INPUT_PATH
import static com.brambolt.gradle.velocity.tasks.Velocity.DEFAULT_VELOCITY_TASK_NAME
import static com.brambolt.util.Maps.asMap
import static java.util.Arrays.asList
import static java.util.Collections.EMPTY_LIST
import static java.util.Collections.EMPTY_MAP

/**
 * Configures a Gradle build to build and publish an archive.
 */
class ArchiveBuildPlugin implements Plugin<Project> {

  /**
   * The plugins to apply to the project.
   */
  List<String> PLUGIN_IDS = [
    'java-library',
    'groovy',
    'maven-publish',
    ARTIFACTORY_PLUGIN_ID,
    BINTRAY_PLUGIN_ID,
    'org.ajoberstar.grgit'
  ]

  /**
   * The project-specific properties that must be set.
   */
  final static List<String> REQUIRED_PROPERTIES = [
    'artifactId',
    'developers',
    'inceptionYear',
    'licenses',
    'release',
    'vcsUrl'
  ]

  /**
   * Maps plugin identifier to project property. If a plugin identifier
   * is included in this map then the plugin will only be applied if the
   * project property is present with a non-empty value that does not
   * evaluate to false.
   */
  Map<String, Closure<Boolean>> pluginInclusion = [:]

  /**
   * Applies the plugin and configures the build.
   * @param project The project to configure
   */
  @Override
  void apply(Project project) {
    project.logger.debug("Applying ${getClass().getCanonicalName()}.")
    configureDerivedPropertiesWithoutPlugins(project)
    configurePluginInclusion(project, pluginInclusion)
    configurePlugins(project, PLUGIN_IDS, pluginInclusion)
    checkProjectProperties(project, REQUIRED_PROPERTIES)
    configureDerivedPropertiesWithPlugins(project)
    logProperties(project)
    configureRepositories(project)
    configureDependencies(project)
    configureVelocityTask(project)
    configureResourcePaths(project)
    configureProcessResourcesTask(project, [DEFAULT_VELOCITY_TASK_NAME])
    configureJavaPlugin(project)
    configureJarTask(project)
    configureJavadocJarTask(project)
    configureSourceJarTask(project)
    configureJavaPublishing(project)
    configureArtifactory(project)
    configureBintray(project)
    configureDefaultTasks(project)
  }

  static Velocity configureVelocityTask(Project project, String taskName, String inputPath, File outputDir, Map<String, Object> context, List<String> taskDependencies) {
    Velocity velocity = (Velocity) project.tasks.create(taskName, Velocity) {
      outputs.upToDateWhen { false }
      contextValues = context
      delegate.inputPath = inputPath
      delegate.outputDir = outputDir
      doFirst {
        outputDir.mkdirs()
      }
    }
    if (null != taskDependencies)
      taskDependencies.each { String taskDependency -> velocity.dependsOn(taskDependency) }
    return velocity
  }

  static Velocity configureVelocityTask(Project project, String taskName, String inputPath, File outputDir, Map<String, Object> context, String... taskDependencies) {
    return configureVelocityTask(project, taskName, inputPath, outputDir,
      context, asList(taskDependencies))
  }

  static Velocity configureVelocityTask(Project project, String inputPath, Map<String, Object> context, List<String> taskDependencies) {
    return configureVelocityTask(project, DEFAULT_VELOCITY_TASK_NAME, inputPath,
      getDefaultOutputDir(project), context, taskDependencies)
  }

  static File getDefaultOutputDir(Project project) {
    new File(project.buildDir, 'templates')
  }

  static Velocity configureVelocityTask(Project project, Map<String, Object> context, List<String> taskDependencies) {
    return configureVelocityTask(project, DEFAULT_VELOCITY_INPUT_PATH, context, taskDependencies)
  }

  static Velocity configureVelocityTask(Project project, String inputPath, Map<String, Object> context, String... taskDependencies) {
    return configureVelocityTask(project, inputPath, context, asList(taskDependencies))
  }

  static Velocity configureVelocityTask(Project project, Map<String, Object> context, String... taskDependencies) {
    return configureVelocityTask(project, context, asList(taskDependencies))
  }

  static Velocity configureVelocityTask(Project project, Map<String, Object> context) {
    return configureVelocityTask(project, context, new ArrayList<>())
  }

  static Velocity configureVelocityTask(Project project) {
    return configureVelocityTask(project, getDefaultVelocityContext(project))
  }

  static Map<String, Object> getDefaultVelocityContext(Project project) {
    [
      bramboltVersion    : project.bramboltVersion,
      buildNumber        : project.buildNumber,
      vcsBranch          : project.vcsBranch,
      vcsCommit          : project.vcsCommit,
      vcsTag             : project.ext.hasProperty('vcsTag') ? project.vcsTag : '',
      vcsToken           : project.vcsToken
    ]
  }

  static void configureResourcePaths(Project project) {
    configureResourcePaths(project, getResourcePaths(project))
  }

  static void configureResourcePaths(Project project, List<String> resourcePaths) {
    configureMainSourceSetResourcePaths(project, getResourcePaths(project))
  }

  static List<String> getResourcePaths(Project project) {
    [
      'src/main/resources',
      "${project.buildDir}/templates"
    ]
  }

  static void configureMainSourceSetResourcePaths(Project project, String... srcDirsPaths) {
    configureMainSourceSetResourcePaths(project, asList(srcDirsPaths))
  }

  static void configureMainSourceSetResourcePaths(Project project, List<String> srcDirsPaths) {
    getMainSourceSet(project).getResources().setSrcDirs(srcDirsPaths)
  }

  static SourceSetContainer getSourceSets(Project project) {
    SourceSetContainer sourceSets = (SourceSetContainer)project.property("sourceSets")
    if (null == sourceSets)
      throw new GradleException("No source sets available for project " + project.getPath())
    else
      return sourceSets
  }

  static SourceSet getMainSourceSet(Project project) {
    return getSourceSet(project, "main")
  }

  static SourceSet getTestSourceSet(Project project) {
    return getSourceSet(project, "test")
  }

  static SourceSet getSourceSet(Project project, String name) {
    return (SourceSet) getSourceSets(project).getByName(name)
  }

  static void configureProcessResourcesTask(Project project, Map<String, Object> replaceTokens) {
    configureProcessResourcesTask(project, replaceTokens, EMPTY_LIST)
  }

  static void configureProcessResourcesTask(Project project, String... taskDependencies) {
    configureProcessResourcesTask(project, asList(taskDependencies))
  }

  static void configureProcessResourcesTask(Project project, List<String> taskDependencies) {
    configureProcessResourcesTask(project, EMPTY_MAP, taskDependencies)
  }

  static void configureProcessResourcesTask(Project project, Map<String, Object> replaceTokens, List<String> taskDependencies) {
    Task processResources = project.getTasks().getByName("processResources")
    if (null != taskDependencies && !taskDependencies.isEmpty())
      processResources.dependsOn(taskDependencies)
    if (null != replaceTokens && !replaceTokens.isEmpty())
      processResources.configure {
        delegate.filter(asMap("tokens", replaceTokens), ReplaceTokens.class)
      }
  }
}
