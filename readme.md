# Slik for Kotlin
Slik: A Lightweight Dependency Injection Framework

### About
I got tired of working with Dagger modules and components, so I made something everyone can
understand.

### Features
#### Annotations
+ `@Inject`
  + Marks a class as injectable so Slik knows it can create instances of it (constructor injection).
    + Example:
      ```
      @Inject
      class ExampleClass(private val dep: SomeDependency)
      ```
  + Marks fields as injectable so Slik knows to fill them out (field injection). Note that
    `Slik#inject(Any)` must be called in order for Slik to inject these fields. See examples below.
    + Example: `@Inject private lateinit var dep: SomeDependency`
+ `@Named`
  + Allows for disambiguation when injecting the same type.
    + Example:
      ```
      @Inject
      class ExampleClass(
          private @Named("EnableFoo") val enableFoo: Boolean,
          private @Named("EnableBar") val enableBar: Boolean)
      ```
+ `@Singleton`
  + Marks a class as a singleton so Slik will reuse a single instance of this class.
    + Example:
      ```
      @Singleton
      @Inject
      class ExampleSingletonClass
      ```
+ `@ImplementedBy`
  + Marks interfaces and abstract classes with an implementation so Slik knows how to resolve these
    dependencies.
    + Example:
      ```
      // Foo.kt
      @ImplementedBy(FooImpl::class)
      interface Foo { }
      
      // FooImpl.kt
      class FooImpl : Foo
      ```
  + Note 1: This is not type safe. Yes, you may bind a Circle::class to a Square::class. No it
    will not work. It will throw a SlikException.
  + Note 2: Slik will only look at `@ImplementedBy` if the dependency is an `interface` or `abstract
    class`. Marking a `class` with `@ImplementedBy` won't do anything.

#### Methods
+ `Slik.get(KClass)`
  + Fetch a Slik instance, keyed by classes, no setup required. This is your entrypoint to Slik.
    Instances are your scope. Singletons and bindings (more on this later) are stored within
    instances (hence why it's your scope).
  + Example: `Slik#get(MyApplication::class)`. This is the most common standard -- to use the class
    containing the main method as the single scope.
+ `Slik#provide(Any, String? = null)`
  + Give a Slik scope an instance of an object, optionally providing a name. Slik will treat this
    like a singleton without requiring the class being marked with `@Singleton`. This is useful
    for library classes or resources you have no control over.
    + Example: `Slik.get(MyApplication::class).provide(new File("resources.json"), "resources")`
  + Also used to override `@ImplementedBy` annotations. This is because Slik looks at singletons
    before trying to create instances by itself. Most often, this will only be done in Test cases.
+ `Slik#bind(KClass, KClass)`
  + Does the same thing as `@ImplementedBy` except it has static type safety analysis. This is
    useful for classes you have no control over (framework, library, etc).
    + Example:
      `Slik#get(MyApplication::class).bind(Android.content.SharedPreferences::class, com.example.MySharedPreferences::class)`
+ `Slik#inject(Any, KClass? = null)`
  + Entrypoint for field injection. This method only injects fields for the given class (and
    no children classes). Children are expected to inject their own dependencies.
    + Example:
      ```
      open class Parent : Activity {
        // Our field we want to inject
        @Inject private lateinit var foo: Foo
        
        // Android controls the lifecycle, so we have to use what it provides us
        override fun onCreate(state: Bundle?) {
          // Just Android things
          super.onCreate(state)
          
          // Because this class will be extended, we must specify we want to inject this class's
          // fields. We do this by passing both the instance and this class to #inject.
          Slik.get(MyApplication::class).inject(this, Parent::class)
        }
      }
      
      class Child : Parent() {
        @Inject private lateinit var bar: Bar
        
        override fun onCreate(state: Bundle?) {
          super.onCreate(state)
          
          // Because this is the top level class, we don't need to pass the class name to inject,
          // just our instance.
          Slik.get(MyApplication::class).inject(this)
        }
      }
      ```

### Example Application
```
// ExampleApplication.kt
fun main(args: Array<String>) {
  ExampleApplication()
}

class ExampleApplication {
  companion object {
    val IS_DEBUGGING = "isDebugging"
    val ENABLE_X = "enableY"
    val ENABLE_Y = "enableY"
  }
  
  @Inject private lateinit var foo: Foo
  
  init {
    FlagReader flagReader = new FlagReader("http://example.com/flagreader")
    
    // Get our scope
    Slik.get(ExampleApplication::class)
    
    // Pass stuff to our scope
        .provide(new File("resources.json"))
        .provide(false, IS_DEBUGGING)
        .provide(flagReader.getBool(ENABLE_X), ENABLE_X)
        .provide(flagReader.getBool(ENABLE_Y), ENABLE_Y)
        .bind(Bar::class, BarImpl::class)
        
    // Finally, create the dependencies of this class to automatically create Foo
        .inject(this)
    
    // Now we can make our main application looper
    while (true) {
      foo.doSomething()
      // et al
    }
  }
}
```

```
// Foo.kt
class Foo(
    private val resource: File,
    private val @Named(ExampleApplication.IS_DEBUGGING) isDebugging: Boolean,
    private val @Named(ExampleApplication.ENABLE_X) enableX: Boolean,
    private val @Named(ExampleApplication.ENABLE_Y) enableY: Boolean) {
  
  
  fun doSomething() {
    // Does useful things.
  }
}
```
