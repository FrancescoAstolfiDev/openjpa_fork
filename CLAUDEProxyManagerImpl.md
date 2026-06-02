Act as a Senior QA Engineer. Your goal is to increase the Strength Score for `org.apache.openjpa.util.ProxyManagerImpl` 

### Context:

- Target Class: `src/main/java/org/apache/openjpa/util/ProxyManagerImpl.java`

- Existing Tests: `src/test/java/org/apache/openjpa/util/Isw2ProxyManagerImpl*`

- Current Status: Test strength is at 66%% with 51 uncovered mutations.



### Task:

1. Analyze `ProxyManagerImpl.java` and the existing `src/test/java/org/apache/openjpa/util/Isw2ProxyManagerImpl*`.

2. Identify the "surviving mutants" logic 

3. Generate new JUnit 5 test cases focusing ONLY on:
    - Method: ` newCustomProxy(Object orig, boolean autoOff)`





### Constraints:

- Use JUnit 5 assertions (`org.junit.jupiter.api.Assertions`).

- Follow the naming convention `test[Scenario]_[ExpectedBehavior]`.

- Provide only the Java code for the new test methods or a complete test class if more efficient.

- Ensure the tests address boundary conditions (e.g., max size, load factor thresholds).



After generating the tests, tell me which specific PIT mutators (e.g., NEGATE_CONDITIONALS, VOID_METHOD_CALLS) each new test is designed to kill.

Generate the tests on pattern class src/test/java/org/apache/openjpa/util/Isw2LLMProxyManagerImpl
