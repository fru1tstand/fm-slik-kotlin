package me.fru1t.slik

import com.google.common.truth.Truth.assertThat
import me.fru1t.slik.annotations.ImplementedBy
import me.fru1t.slik.annotations.Inject
import me.fru1t.slik.annotations.Named
import me.fru1t.slik.annotations.Singleton
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SlikTest {
  companion object {
    private val TEST_CLASS = Slik::class
  }

  private lateinit var testSlik: Slik

  @Before
  fun setup() {
    Slik.clear()
    testSlik = Slik.get(TEST_CLASS)
  }

  @Test
  fun get() {
    val result1 = Slik.get(Slik::class)
    val result2 = Slik.get(SlikTest::class)
    assertThat(testSlik).isEqualTo(result1)
    assertThat(testSlik).isNotEqualTo(result2)
  }

  @Test
  fun get_legacy() {
    val result1 = Slik.get(Slik::class.java)
    val result2 = Slik.get(SlikTest::class.java)
    assertThat(testSlik).isEqualTo(result1)
    assertThat(testSlik).isNotEqualTo(result2)
  }

  @Test
  fun provide() {
    // Each TestDependency() provide will add TestDependency and TestDependencyInterface
    testSlik.provide(TestDependency())
    testSlik.provide(TestDependency(), "named")
    assertThat(testSlik.singletons).hasSize(4)
  }

  @Test
  fun provide_sameClassErrors() {
    try {
      testSlik.provide(TestDependency())
      testSlik.provide(TestDependency())
      fail("Expected SlikException for providing two of the same unnamed class")
    } catch (e: SlikException) {
      // Expected
    }
  }

  @Test
  fun bind() {
    testSlik.bind(TestDependencyInterface::class, TestDependency::class)
    assertThat(testSlik.bindings).hasSize(1)
  }

  @Test
  fun bind_sameInterfaceErrors() {
    try {
      testSlik.bind(TestDependencyInterface::class, TestDependency::class)
      testSlik.bind(TestDependencyInterface::class, TestDependency::class)
      fail("Expected SlikException for binding two of the same interface.")
    } catch (e: SlikException) {
      // Expected
    }
  }

  @Test
  fun inject_legacy() {
    val testClass = SimpleTestClass()
    testSlik.inject(testClass)
    assertThat(testClass.testField).isNotNull()
    assertThat(testClass.testNonInjectField).isNull()
  }

  @Test
  fun inject_legacy_binding() {
    testSlik.bind(TestDependencyInterface::class, TestDependency::class)
    val testClass = BindingTestClass()
    testSlik.inject(testClass)
    assertThat(testClass.testField).isInstanceOf(TestDependency::class.java)
  }

  @Test
  fun inject_legacy_singleton() {
    val singleton = TestDependency()
    testSlik.provide(singleton)
    val testClass = SimpleTestClass()
    testSlik.inject(testClass)
    assertThat(testClass.testField).isSameAs(singleton)
  }

  @Test
  fun inject_legacy_createdSingleton() {
    val testClass1 = SingletonTestClass()
    val testClass2 = SingletonTestClass()
    testSlik.inject(testClass1)
    testSlik.inject(testClass2)
    assertThat(testClass1.testField).isSameAs(testClass2.testField)
  }

  @Test
  fun inject_legacy_failWhenDependencyIsNotMet() {
    testSlik.provide("This is a string", "this is a name")
    val testClass = UnmetDependencyTestClass()

    try {
      testSlik.inject(testClass)
      fail("Expected SlikException for trying to instantiate a class that has unmet " +
          "dependencies. Returned '${testClass.testField}' instead.")
    } catch (e: SlikException) {
      // Expected
    }
  }

  @Test
  fun inject() {
    val result = testSlik.inject<TestInterface>()
    assertThat(result).isNotNull()
  }

  @Test
  fun resolve() {
    val result = testSlik.resolve(InjectableClassWithDepenency::class, null as String?)
    assertThat(result).isNotNull()
  }

  @Test
  fun resolve_singleton() {
    val result1 = testSlik.resolve(TestSingletonDependency::class, null as String?)
    val result2 = testSlik.resolve(TestSingletonDependency::class, null as String?)
    assertThat(result1).isNotNull()
    assertThat(result1).isSameAs(result2)
  }

  @Test
  fun resolve_invalidClassType() {
    try {
      testSlik.resolve(TestDataClass::class, null as String?)
      fail("Expected to fail resolving data class.")
    } catch (e: SlikException) {
      assertThat(e.message).contains(TestDataClass::class.qualifiedName!!)
    }
  }

  @Test
  fun resolve_unknownImplementationOfInterface() {
    try {
      testSlik.resolve(TestDependencyInterface::class, null as String?)
      fail("Expected to fail resolving interface with no @ImplementedBy annotation")
    } catch (e: SlikException) {
      assertThat(e.message).contains(TestDependencyInterface::class.qualifiedName!!)
    }
  }

  @Test
  fun resolve_implementedBy() {
    val result = testSlik.resolve(TestInterface::class, null as String?)
    assertThat(result).isNotNull()
    assertThat(result).isInstanceOf(TestInterfaceImplementation::class.java)
  }

  @Test
  fun resolve_unmetDependencies() {
    try {
      testSlik.resolve(InjectableClassWithUnmetDependencies::class, null as String?)
    } catch (e: SlikException) {
      // Caller shouldn't care about the actual dependency that can't be created, just that we
      // couldn't create InjectableClassWithUnmetDependencies.
      assertThat(e.message).contains(InjectableClassWithUnmetDependencies::class.qualifiedName!!)
    }
  }
}

private interface TestDependencyInterface
@Inject private class TestDependency : TestDependencyInterface
@Inject @Singleton private class TestSingletonDependency
@ImplementedBy(TestInterfaceImplementation::class) private interface TestInterface
@Inject private class TestInterfaceImplementation : TestInterface

private data class TestDataClass(val test: String)
private class NonInjectAnnotatedClass
@Inject private class InjectableClassWithUnmetDependencies(val dependency: NonInjectAnnotatedClass)
@Inject private class InjectableClassWithDepenency(val dependency: TestDependency)

private class SimpleTestClass {
  @Inject internal lateinit var testField: TestDependency
  internal var testNonInjectField: TestDependency? = null
}

private class BindingTestClass {
  @Inject internal lateinit var testField: TestDependencyInterface
}

private class SingletonTestClass {
  @Inject internal lateinit var testField: TestSingletonDependency
}

private class UnmetDependencyTestClass {
  @Inject @Named("test") internal lateinit var testField: String
}
