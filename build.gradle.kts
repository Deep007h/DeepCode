// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  id("org.jetbrains.kotlin.android") version "2.0.0" apply false
  id("org.jetbrains.kotlin.kapt") version "2.0.0" apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
}