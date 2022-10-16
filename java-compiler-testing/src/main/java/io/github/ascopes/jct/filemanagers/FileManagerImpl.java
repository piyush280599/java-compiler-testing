/*
 * Copyright (C) 2022 - 2022 Ashley Scopes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.ascopes.jct.filemanagers;

import static java.util.Objects.requireNonNull;

import io.github.ascopes.jct.annotations.Nullable;
import io.github.ascopes.jct.annotations.WillClose;
import io.github.ascopes.jct.containers.ContainerGroup;
import io.github.ascopes.jct.containers.ModuleContainerGroup;
import io.github.ascopes.jct.containers.OutputContainerGroup;
import io.github.ascopes.jct.containers.PackageContainerGroup;
import io.github.ascopes.jct.containers.impl.ModuleContainerGroupImpl;
import io.github.ascopes.jct.containers.impl.OutputContainerGroupImpl;
import io.github.ascopes.jct.containers.impl.PackageContainerGroupImpl;
import io.github.ascopes.jct.paths.PathLike;
import io.github.ascopes.jct.paths.SubPath;
import io.github.ascopes.jct.utils.AsyncResourceCloser;
import io.github.ascopes.jct.utils.ToStringBuilder;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import org.apiguardian.api.API;
import org.apiguardian.api.API.Status;

/**
 * Simple implementation of a {@link FileManager}.
 *
 * @author Ashley Scopes
 * @since 0.0.1
 */
@API(since = "0.0.1", status = Status.EXPERIMENTAL)
public class FileManagerImpl implements FileManager {

  private static final Cleaner CLEANER = Cleaner.create();

  private final String release;
  private final Map<Location, @WillClose PackageContainerGroup> packages;
  private final Map<Location, @WillClose ModuleContainerGroup> modules;
  private final Map<Location, @WillClose OutputContainerGroup> outputs;

  /**
   * Initialize this file manager.
   *
   * @param release the release to use for multi-release JARs internally.
   */
  @SuppressWarnings("ThisEscapedInObjectConstruction")
  public FileManagerImpl(String release) {
    this.release = requireNonNull(release, "release");
    packages = new ConcurrentHashMap<>();
    modules = new ConcurrentHashMap<>();
    outputs = new ConcurrentHashMap<>();

    CLEANER.register(this, new AsyncResourceCloser(packages));
    CLEANER.register(this, new AsyncResourceCloser(modules));
    CLEANER.register(this, new AsyncResourceCloser(outputs));
  }

  /**
   * Add a path to the given location.
   *
   * @param location the location to use for the path.
   * @param path     the path to add.
   */
  public void addPath(Location location, PathLike path) {
    if (location instanceof ModuleLocation) {
      var moduleLocation = (ModuleLocation) location;

      if (location.isOutputLocation()) {
        getOrCreateOutput(moduleLocation.getParent())
            .addModule(moduleLocation.getModuleName(), path);
      } else {
        getOrCreateModule(moduleLocation.getParent())
            .addModule(moduleLocation.getModuleName(), path);
      }

    } else if (location.isOutputLocation()) {
      getOrCreateOutput(location)
          .addPackage(path);

    } else if (location.isModuleOrientedLocation()) {
      // Attempt to find modules.
      var moduleGroup = getOrCreateModule(location);

      for (var ref : ModuleFinder.of(path.getPath()).findAll()) {
        var module = ref.descriptor().name();
        moduleGroup.getOrCreateModule(module)
            .addPackage(new SubPath(path, module));
      }

    } else {
      getOrCreatePackage(location)
          .addPackage(path);
    }
  }

  @Override
  public void ensureEmptyLocationExists(Location location) {
    if (location instanceof ModuleLocation) {
      var moduleLocation = (ModuleLocation) location;

      if (location.isOutputLocation()) {
        getOrCreateOutput(moduleLocation.getParent())
            .getOrCreateModule(moduleLocation.getModuleName());

      } else {
        getOrCreateModule(moduleLocation.getParent())
            .getOrCreateModule(moduleLocation.getModuleName());
      }
    } else if (location.isOutputLocation()) {
      getOrCreateOutput(location);
    } else if (location.isModuleOrientedLocation()) {
      getOrCreateModule(location);
    } else {
      getOrCreatePackage(location);
    }
  }

  @Override
  public void copyContainers(Location from, Location to) {
    if (from.isOutputLocation()) {
      if (!to.isOutputLocation()) {
        throw new IllegalArgumentException(
            "Expected " + from.getName() + " and " + to.getName() + " to both be output locations"
        );
      }
    }

    if (from.isModuleOrientedLocation()) {
      if (!to.isModuleOrientedLocation()) {
        throw new IllegalArgumentException(
            "Expected " + from.getName() + " and " + to.getName() + " to both be "
                + "module-oriented locations"
        );
      }
    }

    if (from.isOutputLocation()) {
      var toOutputs = getOrCreateOutput(to);
      var fromOutput = outputs.get(from);

      if (fromOutput != null) {
        fromOutput.getPackages().forEach(toOutputs::addPackage);
        fromOutput.getModules().forEach((module, containers) -> containers
            .getPackages()
            .forEach(container -> toOutputs.addModule(module.getModuleName(), container)));
      }

    } else if (from.isModuleOrientedLocation()) {
      var toModules = getOrCreateModule(to);
      var fromModule = modules.get(from);

      if (fromModule != null) {
        fromModule.getModules().forEach((moduleLocation, containers) -> containers
            .getPackages()
            .forEach(container -> toModules.addModule(moduleLocation.getModuleName(), container)));
      }

    } else {
      var toPackages = getOrCreatePackage(to);
      var fromPackage = packages.get(from);

      if (fromPackage != null) {
        fromPackage.getPackages().forEach(toPackages::addPackage);
      }
    }
  }

  @Override
  @Nullable
  public PackageContainerGroup getPackageContainerGroup(Location location) {
    return packages.get(location);
  }

  @Override
  public Collection<PackageContainerGroup> getPackageContainerGroups() {
    return packages.values();
  }

  @Override
  @Nullable
  public ModuleContainerGroup getModuleContainerGroup(Location location) {
    return modules.get(location);
  }

  @Override
  public Collection<ModuleContainerGroup> getModuleContainerGroups() {
    return modules.values();
  }

  @Override
  @Nullable
  public OutputContainerGroup getOutputContainerGroup(Location location) {
    return outputs.get(location);
  }

  @Override
  public Collection<OutputContainerGroup> getOutputContainerGroups() {
    return outputs.values();
  }

  @Nullable
  @Override
  public ClassLoader getClassLoader(Location location) {
    var group = getExistingPackageOrientedOrOutputGroup(location);

    return group == null
        ? null
        : group.getClassLoader();
  }

  @Override
  public Iterable<JavaFileObject> list(
      Location location,
      String packageName,
      Set<Kind> kinds,
      boolean recurse
  ) throws IOException {
    var group = getExistingPackageOrientedOrOutputGroup(location);

    if (group == null) {
      return List.of();
    }

    var files = new ArrayList<JavaFileObject>();
    group.listFileObjects(packageName, kinds, recurse, files);
    return files;
  }

  @Nullable
  @Override
  public String inferBinaryName(Location location, JavaFileObject file) {
    if (!(file instanceof PathFileObject)) {
      return null;
    }

    var pathFileObject = (PathFileObject) file;

    var group = getExistingPackageOrientedOrOutputGroup(location);

    return group == null
        ? null
        : group.inferBinaryName(pathFileObject);
  }

  @Override
  public boolean isSameFile(@Nullable FileObject a, @Nullable FileObject b) {
    // Some annotation processors provide null values here for some reason.
    if (a == null || b == null) {
      return false;
    }

    return Objects.equals(a.toUri(), b.toUri());
  }

  @Override
  public boolean handleOption(String current, Iterator<String> remaining) {
    return false;
  }

  @Override
  public boolean hasLocation(Location location) {
    if (location instanceof ModuleLocation) {
      var moduleLocation = (ModuleLocation) location;
      var group = getExistingModuleOrientedOrOutputGroup(moduleLocation.getParent());

      return group != null && group.hasLocation(moduleLocation);
    }

    return packages.containsKey(location)
        || modules.containsKey(location)
        || outputs.containsKey(location);
  }

  @Nullable
  @Override
  public JavaFileObject getJavaFileForInput(
      Location location,
      String className,
      Kind kind
  ) {
    var group = getExistingPackageOrientedOrOutputGroup(location);

    return group == null
        ? null
        : group.getJavaFileForInput(className, kind);
  }

  @Nullable
  @Override
  public JavaFileObject getJavaFileForOutput(
      Location location,
      String className,
      Kind kind,
      FileObject sibling
  ) {
    var group = getExistingPackageOrientedOrOutputGroup(location);

    return group == null
        ? null
        : group.getJavaFileForOutput(className, kind);
  }

  @Nullable
  @Override
  public FileObject getFileForInput(
      Location location,
      String packageName,
      String relativeName
  ) {
    var group = getExistingPackageOrientedOrOutputGroup(location);

    return group == null
        ? null
        : group.getFileForInput(packageName, relativeName);
  }

  @Nullable
  @Override
  public FileObject getFileForOutput(
      Location location,
      String packageName,
      String relativeName,
      FileObject sibling
  ) {
    var group = getExistingPackageOrientedOrOutputGroup(location);

    return group == null
        ? null
        : group.getFileForOutput(packageName, relativeName);
  }

  @Override
  public void flush() {
    // Do nothing.
  }

  @Override
  public void close() throws IOException {
    // We explicitly close all resources on garbage collection rather than here. This prevents
    // the compiler implementation making our resources unavailable while we are still using them
    // to assert further outcomes in tests.
  }

  @Override
  public Location getLocationForModule(Location location, String moduleName) {
    return new ModuleLocation(location, moduleName);
  }

  @Override
  public Location getLocationForModule(Location location, JavaFileObject fo) {
    if (fo instanceof PathFileObject) {
      var pathFileObject = (PathFileObject) fo;
      var moduleLocation = pathFileObject.getLocation();

      if (moduleLocation instanceof ModuleLocation) {
        return moduleLocation;
      }

      throw new IllegalArgumentException("File object " + fo + " is not for a module");
    }

    throw new IllegalArgumentException(
        "File object " + fo + " does not appear to be registered to a module"
    );
  }

  @Override
  public <S> ServiceLoader<S> getServiceLoader(Location location, Class<S> service) {
    var group = getExistingGroup(location);

    if (group == null) {
      throw new NoSuchElementException(
          "No container group for location " + location.getName() + " exists"
      );
    }

    return group.getServiceLoader(service);
  }

  @Nullable
  @Override
  public String inferModuleName(Location location) {
    requirePackageOrientedLocation(location);

    return location instanceof ModuleLocation
        ? ((ModuleLocation) location).getModuleName()
        : null;
  }

  @Override
  public Iterable<Set<Location>> listLocationsForModules(Location location) {
    requireOutputOrModuleOrientedLocation(location);

    var group = getExistingModuleOrientedOrOutputGroup(location);

    if (group == null) {
      return List.of();
    }

    return group.getLocationsForModules();
  }

  @Override
  public boolean contains(Location location, FileObject fo) throws IOException {
    if (!(fo instanceof PathFileObject)) {
      return false;
    }

    var group = getExistingGroup(location);

    return group != null && group.contains((PathFileObject) fo);
  }

  @Override
  public int isSupportedOption(String option) {
    return 0;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .attribute("release", release)
        .toString();
  }

  @Nullable
  private ContainerGroup getExistingGroup(Location location) {
    var group = getExistingPackageOrientedOrOutputGroup(location);

    return group == null
        ? getExistingModuleOrientedOrOutputGroup(location)
        : group;
  }

  @Nullable
  private ModuleContainerGroup getExistingModuleOrientedOrOutputGroup(Location location) {
    if (location instanceof ModuleLocation) {
      throw new IllegalArgumentException(
          "Cannot get a module-oriented group from a ModuleLocation"
      );
    }

    var group = modules.get(location);

    if (group == null) {
      group = outputs.get(location);
    }

    return group;
  }

  @Nullable
  private PackageContainerGroup getExistingPackageOrientedOrOutputGroup(Location location) {
    if (location instanceof ModuleLocation) {
      var moduleLocation = (ModuleLocation) location;

      var module = modules.get(moduleLocation.getParent());

      if (module == null) {
        module = outputs.get(moduleLocation.getParent());
      }

      if (module == null) {
        return null;
      }

      return module.getOrCreateModule(moduleLocation.getModuleName());
    }

    var group = packages.get(location);

    if (group == null) {
      group = outputs.get(location);
    }

    return group;
  }

  private PackageContainerGroup getOrCreatePackage(Location location) {
    if (location instanceof ModuleLocation) {
      throw new IllegalArgumentException("Cannot get a package for a module location");
    }

    return packages
        .computeIfAbsent(
            location,
            unused -> new PackageContainerGroupImpl(location, release)
        );
  }

  private ModuleContainerGroup getOrCreateModule(Location location) {
    return modules
        .computeIfAbsent(
            location,
            unused -> new ModuleContainerGroupImpl(location, release)
        );
  }

  private OutputContainerGroup getOrCreateOutput(Location location) {
    return outputs
        .computeIfAbsent(
            location,
            unused -> new OutputContainerGroupImpl(location, release)
        );
  }

  private void requireOutputOrModuleOrientedLocation(Location location) {
    if (!location.isOutputLocation() && !location.isModuleOrientedLocation()) {
      throw new IllegalArgumentException(
          "Location " + location.getName() + " must be output or module-oriented"
      );
    }
  }

  private void requirePackageOrientedLocation(Location location) {
    if (location.isModuleOrientedLocation()) {
      throw new IllegalArgumentException(
          "Location " + location.getName() + " must be package-oriented"
      );
    }
  }
}