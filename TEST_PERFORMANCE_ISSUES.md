# Test Performance Issues: Fixed Delays and Inefficient Patterns

**Analysis Date:** 2025-11-12
**Repository:** eclipse.platform.ui
**Scope:** All test bundles under `tests/` directory

## Executive Summary

This analysis identified significant performance issues in the test suite related to fixed time delays and inefficient waiting patterns. These issues cause tests to run slower than necessary and reduce the reliability of the test suite.

### Key Findings:
- **80+ instances** of `Thread.sleep()` with fixed delays ranging from 10ms to 2000ms
- **10+ instances** of extremely long `DisplayHelper.sleep()` calls (10+ seconds, some over 2 hours!)
- **Multiple polling loops** with fixed delay intervals instead of condition-based waiting
- **Inefficient busy-wait patterns** that waste CPU cycles

## Categories of Issues

### 1. Fixed Thread.sleep() Delays

#### Issue Description
Tests use `Thread.sleep()` with fixed durations, causing tests to wait for a predetermined time regardless of whether the condition is already met. This makes tests slower and less reliable (too short = flaky, too long = slow).

#### Examples

**High Impact (Long Delays):**

1. **KeyDispatcherTest.java:255** - 2 second sleep
   ```
   tests/org.eclipse.e4.ui.bindings.tests/src/org/eclipse/e4/ui/bindings/tests/KeyDispatcherTest.java:255
   Thread.sleep(2000L);
   ```

2. **Bug78470Test.java:117** - 2 second sleep
   ```
   tests/org.eclipse.ui.tests/Eclipse UI Tests/org/eclipse/ui/tests/internal/Bug78470Test.java:117
   Thread.sleep(2000);
   ```

3. **ESelectionServiceTest.java:1014, 1019** - 1 second sleeps
   ```
   tests/org.eclipse.e4.ui.tests/src/org/eclipse/e4/ui/tests/application/ESelectionServiceTest.java:1014
   tests/org.eclipse.e4.ui.tests/src/org/eclipse/e4/ui/tests/application/ESelectionServiceTest.java:1019
   Thread.sleep(1000);
   ```

4. **Browser Tests** - Multiple 2 second sleeps
   ```
   tests/org.eclipse.ui.tests.browser/src/org/eclipse/ui/tests/browser/internal/ExternalBrowserTestCase.java:34, 42, 50
   Thread.sleep(2000);
   ```

5. **ResourceInitialSelectionTest.java:429** - 1 second retry delay
   ```
   tests/org.eclipse.ui.tests/Eclipse UI Tests/org/eclipse/ui/tests/dialogs/ResourceInitialSelectionTest.java:429
   Thread.sleep(1000); // Wait 1 second before retry
   ```

**Medium Impact (100-500ms delays):**

6. **ProgressViewTests.java:159** - 200ms throttled update wait
   ```
   tests/org.eclipse.ui.tests/Eclipse UI Tests/org/eclipse/ui/tests/progress/ProgressViewTests.java:159
   Thread.sleep(200); // wait till throttled update ran.
   ```

7. **FileDocumentProviderTest.java:239** - 100ms filesystem timestamp wait
   ```
   tests/org.eclipse.ui.editors.tests/src/org/eclipse/ui/editors/tests/FileDocumentProviderTest.java:239
   Thread.sleep(100);
   ```

8. **MultiEditorTest.java:451** - 500ms delay
   ```
   tests/org.eclipse.ui.tests/Eclipse UI Tests/org/eclipse/ui/tests/multieditor/MultiEditorTest.java:451
   Thread.sleep(500);
   ```

**Low Impact but Cumulative (10-100ms delays):**

Multiple test files with 10-100ms sleeps that add up across the test suite:
- ScreenshotTest.java:192 (100ms)
- ExpandableCompositeTest.java:142 (100ms)
- SizeCacheTest.java:96 (100ms)
- Multiple field assist tests (10ms each)
- Multiple DnD tests (10ms each)
- StickyScrollingHandlerTest.java:187, 189, 191 (10ms each)

### 2. Polling Loops with Fixed Delays

#### Issue Description
Tests use while loops that repeatedly check a condition with fixed `Thread.sleep()` delays between checks. This is inefficient and can lead to flaky tests.

#### Examples

1. **FileDocumentProviderTest.java:240-244** - Busy loop with 10ms sleep
   ```java
   while (fsManager.fastIsSynchronized(file) && count < 1000) {
       Files.setLastModifiedTime(file.getLocation().toFile().toPath(),
           FileTime.fromMillis(System.currentTimeMillis()));
       Thread.sleep(10);  // Fixed delay in loop
       count++;
   }
   ```
   **Issue:** Runs up to 1000 iterations with 10ms delays = up to 10 seconds worst case

2. **TextFileDocumentProviderTest.java:228-231** - Similar pattern
   ```java
   while (fsManager.fastIsSynchronized(file) && count < 1000) {
       Files.setLastModifiedTime(file.getLocation().toFile().toPath(),
           FileTime.fromMillis(System.currentTimeMillis()));
       Thread.sleep(10);
       count++;
   }
   ```

3. **SorterTest.java:165-183** - Multiple polling loops with 100ms delays
   ```java
   while (!passed) {
       items = _viewer.getTree().getItems();
       // ... assertions ...
       if (!childItems[0].getText().equals("f1") && count-- >= 0) {
           DisplayHelper.sleep(100);  // Fixed 100ms delay
           continue;
       }
       passed = true;
   }
   ```
   **Issue:** Repeated pattern 3 times in same test, could wait up to several seconds

4. **FindReplaceTestUtil.java:34-42** - Display pump with fixed delay
   ```java
   while (display.readAndDispatch()) {
       // do nothing
   }
   Thread.sleep(50);  // Fixed delay after event processing
   ```

### 3. Extremely Long DisplayHelper.sleep() Calls

#### Issue Description
Some tests contain `DisplayHelper.sleep()` calls with extremely long timeouts (10+ seconds, some over 2 hours). These appear to be debugging aids that were left in the code, often commented out but sometimes active.

#### Examples

**CRITICAL - Active Long Sleeps:**

1. **PR263695.java:87** - 2 second sleep (ACTIVE)
   ```
   tests/org.eclipse.ui.tests.navigator/src/org/eclipse/ui/tests/navigator/dndtest/PR263695.java:87
   Thread.sleep(2000);
   ```

**CRITICAL - Commented but Dangerous:**

2. **M12Tests.java:122** - 10 million milliseconds (~2.8 hours!)
   ```
   tests/org.eclipse.ui.tests.navigator/src/org/eclipse/ui/tests/navigator/M12Tests.java:122
   DisplayHelper.sleep(10000000);
   ```

3. **ActionProviderTest.java:85, 133** - 10 million milliseconds
   ```
   tests/org.eclipse.ui.tests.navigator/src/org/eclipse/ui/tests/navigator/ActionProviderTest.java:85
   tests/org.eclipse.ui.tests.navigator/src/org/eclipse/ui/tests/navigator/ActionProviderTest.java:133
   DisplayHelper.sleep(10000000);
   ```

4. **LabelProviderTest.java** - Multiple 10 million millisecond sleeps
   ```
   tests/org.eclipse.ui.tests.navigator/src/org/eclipse/ui/tests/navigator/LabelProviderTest.java:296, 317, 344, 370
   DisplayHelper.sleep(10000000);
   ```

5. **LinkHelperTest.java:72** - 100 million milliseconds (~27.8 hours!)
   ```
   tests/org.eclipse.ui.tests.navigator/src/org/eclipse/ui/tests/navigator/LinkHelperTest.java:72
   DisplayHelper.sleep(100000000);
   ```

6. **PerformanceTest.java:92, 223** - 10 million milliseconds
   ```
   tests/org.eclipse.ui.tests.navigator/src/org/eclipse/ui/tests/navigator/PerformanceTest.java:92
   tests/org.eclipse.ui.tests.navigator/src/org/eclipse/ui/tests/navigator/PerformanceTest.java:223
   DisplayHelper.sleep(Display.getCurrent(), 10000000);
   ```

7. **NavigatorTestBase.java:238** - 1 million milliseconds (~16.7 minutes)
   ```
   tests/org.eclipse.ui.tests.navigator/src/org/eclipse/ui/tests/navigator/NavigatorTestBase.java:238
   DisplayHelper.sleep(1000000);
   ```

**Note:** Many of these are commented out or only conditionally executed, but they pose a risk if accidentally enabled.

### 4. Inefficient Wait Patterns in Test Utilities

#### Issue Description
Test utility classes sometimes use inefficient patterns that propagate throughout the test suite.

#### Examples

1. **UITestUtil.java:177** - Fixed 20ms sleep in event processing
   ```
   tests/org.eclipse.ui.tests.harness/src/org/eclipse/ui/tests/harness/util/UITestUtil.java:177
   Thread.sleep(20);
   ```

2. **EditorTestHelper.java:186** - Fixed interval sleep
   ```
   tests/org.eclipse.ui.tests.harness/src/org/eclipse/ui/tests/harness/util/EditorTestHelper.java:186
   Thread.sleep(intervalTime);
   ```

3. **ViewerTestCase.java:137** - Generic sleep utility
   ```
   tests/org.eclipse.jface.tests/src/org/eclipse/jface/tests/viewers/ViewerTestCase.java:137
   Thread.sleep(millis);
   ```

### 5. Race Conditions and Timing Issues

#### Issue Description
Some tests show signs of race conditions where fixed delays are used to "hope" that asynchronous operations complete.

#### Examples

1. **ProgressViewTests.java:159** - Comment indicates uncertainty
   ```java
   Thread.sleep(200); // wait till throttled update ran.
   ```
   **Issue:** No guarantee that 200ms is enough

2. **FileDocumentProviderTest.java:238-239** - File system timing race
   ```java
   // Give the file system a chance to have a *different* timestamp
   Thread.sleep(100);
   ```
   **Issue:** File system granularity varies by platform

3. **WorkingSetTests.java:106, 129** - 250ms delays with no clear reason
   ```
   tests/org.eclipse.ui.tests/Eclipse UI Tests/org/eclipse/ui/tests/dynamicplugins/WorkingSetTests.java:106
   tests/org.eclipse.ui.tests/Eclipse UI Tests/org/eclipse/ui/tests/dynamicplugins/WorkingSetTests.java:129
   Thread.sleep(250);
   ```

4. **StickyScrollingHandlerTest.java:187-191** - Sequential 10ms sleeps
   ```java
   Thread.sleep(10);
   // ... operation ...
   Thread.sleep(10);
   // ... operation ...
   Thread.sleep(10);
   ```
   **Issue:** Appears to be timing-sensitive operations that need proper synchronization

## Recommended Solutions

### 1. Replace Fixed Delays with Condition-Based Waiting

**Current Pattern:**
```java
doSomethingAsync();
Thread.sleep(1000); // Hope it's done
assertTrue(condition);
```

**Recommended Pattern:**
```java
doSomethingAsync();
assertTrue(DisplayHelper.waitForCondition(display, 5000, () -> condition));
```

**Benefits:**
- Test completes as soon as condition is met (often much faster)
- More reliable (doesn't depend on arbitrary timeout)
- Clearer test intent

### 2. Use DisplayHelper.waitForCondition() Instead of Sleep

The codebase already has `DisplayHelper.waitForCondition()` which properly waits for conditions while processing the event loop. It should be used instead of fixed sleeps.

**Example Conversion:**

Before:
```java
viewer.refresh();
Thread.sleep(100);
assertEquals(expected, actual);
```

After:
```java
viewer.refresh();
DisplayHelper.waitForCondition(display, 1000, () -> expected.equals(actual));
```

### 3. Use Proper Synchronization for Async Operations

For tests that wait for asynchronous operations (jobs, UI updates, etc.), use appropriate synchronization mechanisms:

- **For Jobs:** Use `job.join()` or `Job.getJobManager().join()`
- **For UI Updates:** Use `DisplayHelper.waitForCondition()` with event loop processing
- **For File System:** Use workspace refresh notifications or listeners
- **For Observable Values:** Use proper listeners instead of polling

### 4. Remove or Fix Extremely Long Sleeps

**Actions:**
- Remove all `DisplayHelper.sleep()` calls > 1 second
- Convert debug sleeps to conditional compilation or separate manual test classes
- Add assertions with timeouts instead of blind delays

### 5. Fix Polling Loops

**Before:**
```java
int count = 0;
while (!condition && count < maxRetries) {
    Thread.sleep(interval);
    count++;
}
```

**After:**
```java
assertTrue(DisplayHelper.waitForCondition(display, maxRetries * interval, () -> condition));
```

### 6. Platform-Specific File System Handling

For file system timestamp tests, use platform-aware approaches:

```java
// Instead of fixed 100ms sleep
Thread.sleep(100);

// Use proper file system granularity detection
long granularity = getFileSystemTimeGranularity();
Thread.sleep(granularity + 10); // Add small buffer
```

Or better yet, use workspace synchronization events.

### 7. Add Test Timeout Configuration

For tests that genuinely need to wait for external events, use configurable timeouts:

```java
// Allow override via system property for slow CI environments
long timeout = Long.getLong("test.timeout.ms", 5000);
DisplayHelper.waitForCondition(display, timeout, condition);
```

## Priority Assessment

### Critical Priority (Fix Immediately)
1. All `DisplayHelper.sleep()` calls > 5 seconds (risk of accidental activation)
2. Polling loops with high iteration counts (FileDocumentProviderTest, TextFileDocumentProviderTest)
3. Known flaky tests with timing issues

### High Priority (Fix in Next Sprint)
1. All `Thread.sleep()` > 500ms (KeyDispatcherTest, Bug78470Test, ESelectionServiceTest, browser tests)
2. Polling loops in viewer tests (SorterTest)
3. Utility methods with fixed delays (UITestUtil, EditorTestHelper)

### Medium Priority (Ongoing Improvement)
1. `Thread.sleep()` calls 100-500ms
2. Race condition-prone patterns (ProgressViewTests, WorkingSetTests)
3. Repeated patterns in similar tests

### Low Priority (Opportunistic)
1. Short sleeps (10-50ms) in stable tests
2. Screenshot and manual test utilities
3. Performance test infrastructure

## Impact Analysis

### Current State
- Estimated cumulative fixed delay time: **60+ seconds** per full test suite run (conservative estimate)
- Many tests wait 5-10x longer than necessary
- Flaky test risk due to race conditions
- CI pipeline slower than necessary

### Expected Improvements After Fixes
- **50-80% reduction** in time spent in fixed delays
- **More reliable tests** (fewer false positives/negatives)
- **Faster feedback** for developers
- **Clearer test intent** (explicit conditions vs. mysterious delays)

## Test Bundles Affected

### Most Affected:
1. **org.eclipse.ui.tests.navigator** - 40+ instances of DisplayHelper.sleep, including extremely long ones
2. **org.eclipse.ui.tests** - 25+ instances of Thread.sleep
3. **org.eclipse.ui.editors.tests** - 15+ instances with polling loops
4. **org.eclipse.jface.tests** - 10+ instances in viewer tests
5. **org.eclipse.ui.genericeditor.tests** - Multiple async content assist tests

### Moderately Affected:
6. org.eclipse.e4.ui.tests
7. org.eclipse.ui.workbench.texteditor.tests
8. org.eclipse.jface.text.tests
9. org.eclipse.ui.tests.browser
10. org.eclipse.ui.tests.forms

### Less Affected but Still Need Review:
11. org.eclipse.search.tests
12. org.eclipse.ltk.core.refactoring.tests
13. org.eclipse.core.filebuffers.tests
14. org.eclipse.ui.monitoring.tests

## Implementation Strategy

### Phase 1: Remove Dangerous Code (1 day)
1. Remove or properly disable all sleeps > 5 seconds
2. Add code review rules to prevent reintroduction

### Phase 2: Fix High-Impact Tests (1 week)
1. Convert long Thread.sleep() calls to condition-based waits
2. Fix polling loops in editor and viewer tests
3. Improve test utility methods

### Phase 3: Systematic Cleanup (2-3 weeks)
1. Process each test bundle systematically
2. Establish patterns and helper methods
3. Document best practices

### Phase 4: Infrastructure Improvements (ongoing)
1. Add test performance monitoring
2. Create guidelines for async test patterns
3. Update developer documentation

## Best Practices for Future Tests

### DO:
✅ Use `DisplayHelper.waitForCondition()` for UI-dependent conditions
✅ Use `job.join()` for job completion
✅ Use explicit timeout values with clear meaning
✅ Write tests that fail fast if condition isn't met
✅ Add comments explaining why waits are necessary

### DON'T:
❌ Use `Thread.sleep()` in tests (except for very specific timing tests)
❌ Use arbitrary timeout values (100, 500, 1000) without justification
❌ Create polling loops with fixed intervals
❌ Leave debug sleeps in committed code
❌ Assume operations complete within fixed time

## Conclusion

The Eclipse Platform UI test suite has significant opportunities for performance improvement by replacing fixed delays with condition-based waiting. The existing `DisplayHelper` infrastructure provides good tools for this, but they need to be used more consistently throughout the codebase.

**Estimated Overall Impact:**
- Current test overhead from fixed delays: **60-120 seconds** per full run
- Potential time savings: **40-90 seconds** per full run (50-75% reduction)
- Additional benefits: More reliable tests, clearer test intent, better CI efficiency

**Next Steps:**
1. Review and approve this analysis
2. Create issues/tasks for each priority level
3. Begin implementation starting with critical priority items
4. Establish code review guidelines to prevent regression
