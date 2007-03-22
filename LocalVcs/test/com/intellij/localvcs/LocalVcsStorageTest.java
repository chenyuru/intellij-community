package com.intellij.localvcs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

public class LocalVcsStorageTest extends TempDirTestCase {
  LocalVcsStorage s;
  LocalVcs.Memento m = new LocalVcs.Memento();

  @Before
  public void setUp() {
    initStorage();
  }

  @After
  public void tearDown() {
    if (s != null) s.close();
  }

  @Test
  public void testCleanStorage() {
    m = s.load();

    assertTrue(m.myRoot.getChildren().isEmpty());
    assertEquals(0, m.myEntryCounter);
    assertTrue(m.myChangeList.getChangeSets().isEmpty());
  }

  @Test
  public void testSaving() {
    ChangeSet cs = cs(new CreateDirectoryChange(1, "dir", null));
    cs.applyTo(m.myRoot);
    m.myChangeList.addChangeSet(cs);
    m.myEntryCounter = 11;

    s.store(m);

    initStorage();
    m = s.load();

    assertEquals(1, m.myRoot.getChildren().size());
    assertEquals(11, m.myEntryCounter);
    assertEquals(1, m.myChangeList.getChangeSets().size());
  }

  @Test
  public void testCreatingAbsentDirs() {
    File dir = new File(tempDir, "dir1/dir2/dir3");
    assertFalse(dir.exists());

    m.myEntryCounter = 111;

    initStorage(dir);
    s.store(m);

    assertTrue(dir.exists());
  }

  @Test
  public void testCleaningStorageOnVersionChange() {
    initStorage(123);

    m.myEntryCounter = 111;
    s.store(m);

    initStorage(666);

    m = s.load();
    assertEquals(0, m.myEntryCounter);
  }

  @Test
  public void testDoesNotCleanStorageWithProperVersion() {
    initStorage(123);

    m.myEntryCounter = 111;
    s.store(m);

    initStorage(123);

    m = s.load();
    assertEquals(111, m.myEntryCounter);
  }

  @Test
  public void testCreatingContent() {
    Content c = s.storeContent(new byte[]{1, 2, 3});
    assertEquals(new byte[]{1, 2, 3}, c.getBytes());
  }

  @Test
  public void testCreatingLongContent() {
    Content c = s.storeContent(new byte[IContentStorage.MAX_CONTENT_LENGTH + 1]);
    assertEquals(UnavailableContent.class, c.getClass());
  }

  @Test
  public void testPurgingContents() {
    Content c1 = s.storeContent(b("1"));
    Content c2 = s.storeContent(b("2"));
    Content c3 = s.storeContent(b("3"));
    s.purgeContents(Arrays.asList(c1, c3));

    assertTrue(s.isContentPurged(c1));
    assertFalse(s.isContentPurged(c2));
    assertTrue(s.isContentPurged(c3));
  }

  @Test
  public void testRecreationOfStorageOnLoadingError() {
    Content c = s.storeContent("abc".getBytes());
    m.myEntryCounter = 10;
    s.store(m);
    s.close();

    corruptFile("storage");

    initStorage();
    m = s.load();
    assertEquals(0, m.myEntryCounter);

    assertEquals(c.getId(), s.storeContent("abc".getBytes()).getId());
  }

  @Test
  public void testRecreationOfStorageOnContentLoadingError() {
    Content c = s.storeContent("abc".getBytes());
    m.myEntryCounter = 10;
    s.store(m);
    s.close();

    corruptFile("contents");
    initStorage();
    try {
      s.loadContentData(c.getId());
      fail();
    }
    catch (IOException e) {
    }

    initStorage();
    m = s.load();

    assertEquals(0, m.myEntryCounter);
  }

  @Test
  public void testThrowingExceptionForGoodContentWhenContentStorageIsBroken() {
    Content c = s.storeContent("abc".getBytes());
    try {
      s.loadContentData(123);
    }
    catch (IOException e) {
    }

    try {
      s.loadContentData(c.getId());
      fail();
    }
    catch (IOException e) {
    }
  }

  @Test
  public void testReturningBrokenContentWhenContentStorageBreaksOnSave() {
    s.storeContent("abc".getBytes());
    s.close();

    corruptFile("contents");
    initStorage();

    Content c = s.storeContent("def".getBytes());
    assertEquals(UnavailableContent.class, c.getClass());
  }

  @Test
  public void testReturningBrokenContentWhenContentStorageIsBroken() {
    try {
      s.loadContentData(123);
    }
    catch (IOException e) {
    }

    Content c = s.storeContent("abc".getBytes());
    assertEquals(UnavailableContent.class, c.getClass());
  }

  private void corruptFile(String name) {
    try {
      File f = new File(tempDir, name);
      assertTrue(f.exists());

      f.delete();
      f.createNewFile();

      FileWriter w = new FileWriter(f);
      w.write("bla-bla-bla");
      w.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void initStorage() {
    initStorage(tempDir);
  }

  private void initStorage(File dir) {
    if (s != null) s.close();
    s = new LocalVcsStorage(dir);
  }

  private void initStorage(final int version) {
    if (s != null) s.close();
    s = new LocalVcsStorage(tempDir) {
      @Override
      protected int getVersion() {
        return version;
      }
    };
  }
}
