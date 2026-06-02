Act as a Senior QA Engineer. Your goal is to increase the Mutation Score  and Coverage Score for `org.apache.openjpa.util.CacheMap` by killing surviving mutants in the constructor, `put()`, and `get()` methods.



### Context:

- Target Class: `src/main/java/org/apache/openjpa/util/CacheMap.java`

- Existing Tests: `src/test/java/org/apache/openjpa/util/Isw2CacheMap*`

- Current Status: Mutation coverage is at 63%% with 79 uncovered mutations.



### Task:

1. Analyze `CacheMap.java` and the existing `src/test/java/org/apache/openjpa/util/Isw2CacheMap*`.

2. Identify the "surviving mutants" logic (e.g., negated conditionals in the constructor, ignored hook calls in put/get).

3. Generate new JUnit 5 test cases focusing ONLY on:

    - Constructor: `CacheMap(boolean lru, int max, int size, float load)`

    - Method: `put(Object key, Object value)`

    - Method: `get(Object key)`





### Constraints:

- Use JUnit 5 assertions (`org.junit.jupiter.api.Assertions`).

- Follow the naming convention `test[Scenario]_[ExpectedBehavior]`.

- Provide only the Java code for the new test methods or a complete test class if more efficient.

- Ensure the tests address boundary conditions (e.g., max size, load factor thresholds).



After generating the tests, tell me which specific PIT mutators (e.g., NEGATE_CONDITIONALS, VOID_METHOD_CALLS) each new test is designed to kill.

Generate the tests on pattern class src/test/java/org/apache/openjpa/util/Isw2LLMCacheMap*`
