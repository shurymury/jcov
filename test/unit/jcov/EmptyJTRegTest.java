package jcov;

import org.testng.annotations.Test;

/**
 * Exists solely as the test run by the nested jtreg invocation in
 * {@link CompositeObserverTest}.
 */
public class EmptyJTRegTest {
    @Test
    void test() {}
}
