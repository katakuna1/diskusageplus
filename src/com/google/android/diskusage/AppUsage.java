package com.google.android.diskusage;

import java.io.File;
import java.util.ArrayList;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;

public class AppUsage extends DiskUsage {
  private AppFilter pendingFilter;
  private static int blockSizeCache;

  public static int getDataBlockSize() {
    if (blockSizeCache != 0) return blockSizeCache;
    final File dataDir = Environment.getDataDirectory();
    StatFs data = new StatFs(dataDir.getAbsolutePath());
    int blockSize = data.getBlockSize();
    return blockSize;
  }
  
  @Override
  public int getBlockSize() {
    return getDataBlockSize();
  }
  
  FileSystemEntry wrapApps(FileSystemSpecial appsElement, AppFilter filter, int displayBlockSize) {
    long freeSize = 0;
    long allocatedSpace = 0;
    long systemSize = 0;
    int entryBlockSize = getBlockSize();
    if ((filter.useApk || filter.useData) && !filter.useSD) {
      StatFs data = new StatFs("/data");
      int dataBlockSize = data.getBlockSize();
      freeSize = data.getAvailableBlocks() * dataBlockSize;
      allocatedSpace = data.getBlockCount() * dataBlockSize - freeSize;
    }
    if (filter.useCache && ! filter.useSD) {
      StatFs cache = new StatFs("/cache");
      int cacheBlockSize = cache.getBlockSize();
      long cacheFreeSpace = cache.getAvailableBlocks() * cacheBlockSize; 
      freeSize += cacheFreeSpace;
      allocatedSpace += cache.getBlockCount() * cacheBlockSize - cacheFreeSpace;
    }
    
    if (allocatedSpace > 0) {
      systemSize = allocatedSpace - appsElement.getSizeInBlocks() * displayBlockSize;
    }
    
    if (filter.useSD) {
      FileSystemEntry newRoot = FileSystemEntry.makeNode(
          null, null).setChildren(new FileSystemEntry[] { appsElement });
      return newRoot;
    }
    
    ArrayList<FileSystemEntry> entries = new ArrayList<FileSystemEntry>();
    entries.add(appsElement);
    if (systemSize > 0) {
      entries.add(new FileSystemSystemSpace("System data", systemSize, entryBlockSize));
    }
    if (freeSize > 0) {
      entries.add(new FileSystemFreeSpace("Free space", freeSize, entryBlockSize));
    }

    FileSystemEntry[] internalArray = entries.toArray(new FileSystemEntry[] {});
    String name = "Data";
    if (filter.useCache) {
      name = "Cache";
      if (filter.useApk || filter.useData) {
        name = "Data and Cache";
      }
    }
    FileSystemEntry internalElement = FileSystemEntry.makeNode(null, name).setChildren(internalArray);
    
    FileSystemEntry newRoot = FileSystemEntry.makeNode(null, null).setChildren(
        new FileSystemEntry[] { internalElement });
    return newRoot;
  }

  @Override
  FileSystemEntry scan() {
    AppFilter filter  = pendingFilter;
    int blockSize = AppUsage.getDataBlockSize();
    FileSystemEntry[] appsArray = loadApps2SD(false, filter, blockSize);
    FileSystemSpecial appsElement = new FileSystemSpecial("Applications", appsArray, blockSize);
    appsElement.filter = filter;
    return wrapApps(appsElement, filter, blockSize);
  }

  @Override
  protected FileSystemView makeView(DiskUsage diskUsage, FileSystemEntry root) {
    return new AppView(this, root);
  }
  
  @Override
  protected void onCreate(Bundle icicle) {
    pendingFilter = AppFilter.loadSavedAppFilter(this);
    super.onCreate(icicle);
  }
  
  private FileSystemSpecial getAppsElement(FileSystemView view) {
    FileSystemEntry root = view.masterRoot;
    FileSystemEntry apps = root.children[0].children[0];
    if (apps instanceof FileSystemPackage) {
      apps = apps.parent;
    }
    return (FileSystemSpecial) apps;
  }
  
  private void updateFilter(AppFilter newFilter) {
    // FIXME: hack
    int blockSize = FileSystemEntry.blockSize;
    if (view == null) {
      pendingFilter = newFilter;
      return;
    }

    FileSystemSpecial appsElement = getAppsElement(view);
    if (newFilter.equals(appsElement.filter)) {
      return;
    }
    for (FileSystemEntry entry : appsElement.children) {
      FileSystemPackage pkg = (FileSystemPackage) entry;
      pkg.applyFilter(newFilter, getBlockSize());
    }
    java.util.Arrays.sort(appsElement.children, FileSystemEntry.COMPARE);
    
    appsElement = new FileSystemSpecial(appsElement.name, appsElement.children, getDataBlockSize());
    appsElement.filter = newFilter;
    
    FileSystemEntry newRoot = wrapApps(appsElement, newFilter, blockSize);
    getPersistantState().root = newRoot;
    view.rescanFinished(newRoot);
    view.startZoomAnimation();
  }
  
  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    if (view == null) return;
    FileSystemSpecial appsElement = getAppsElement(view);
    outState.putParcelable("filter", appsElement.filter);
  }
  
  @Override
  protected void onRestoreInstanceState(Bundle inState) {
    super.onRestoreInstanceState(inState);
    AppFilter newFilter = (AppFilter) inState.getParcelable("filter");
    if (newFilter != null) updateFilter(newFilter);
  }
  
  @Override
  public void onActivityResult(int a, int result, Intent i) {
    super.onActivityResult(a, result, i);
    AppFilter newFilter = AppFilter.loadSavedAppFilter(this);
    updateFilter(newFilter);
  }
}
