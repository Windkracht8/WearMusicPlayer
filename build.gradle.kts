/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

buildscript {
	repositories {
		google()
		mavenCentral()
	}
	dependencies {
		classpath("com.android.tools.build:gradle:8.11.1")
		classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.0")
		classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.2.0")
	}
}

allprojects {
	repositories {
		google()
		mavenCentral()
	}
	//tasks.withType(JavaCompile).tap{configureEach{options.compilerArgs << "-Xlint:unchecked" << "-Xlint:deprecation"}}
}
