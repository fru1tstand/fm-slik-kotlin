package me.fru1t.slik

import com.google.common.annotations.VisibleForTesting
import me.fru1t.slik.annotations.ImplementedBy
import me.fru1t.slik.annotations.Inject
import me.fru1t.slik.annotations.Named
import me.fru1t.slik.annotations.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaConstructor

/**
 * Slik: Simple Lightweight dependency Injection frameworK.
 *
 * Entrypoint of a Slik application usually starts with a *Boot.kt or *Application.kt file which
 * manually injects dependencies into itself to start the dependency graph.
 * <code>
 *   // ExampleApplication.kt
 *   package com.example
 *
 *   class ExampleApplication {
 *     private @Inject lateinit var dep1: Dependency1
 *     private @Inject lateinit var dep2: Dependency2
 *
 *     init {
 *       Slik.get(ExampleApplication::class)
 *         .provide(new ExampleLibraryClass)
 *         .inject(this)
 *     }
 *   }
 * </code>
 *
 * Inversion of control is achieved via @[ImplementedBy] interface which is attached to the
 * interface or abstract class.
 *
 * Injectable classes should be marked as @[Inject]
 *
 * Classes that don't grant lifecycle privileges may have lateinit fields marked with @[inject]
 * with a call to [Slik.inject] to populate them.
 *
 * Singleton classes should be marked with @[Singleton]
 */
class Slik {
  companion object {
    private val scopes = HashMap<KClass<*>, Slik>()

    /** Retrieves the scoped instance of Slik for the given [kClass]. */
    fun get(kClass: KClass<*>): Slik {
      if (!scopes.containsKey(kClass)) {
        scopes.put(kClass, Slik())
      }
      return scopes[kClass]!!
    }

    /** Retrieves the scoped instance of Slik for the given [clazz] */
    fun get(clazz: Class<*>): Slik = get(clazz.kotlin)

    /** Removes all scopes. */
    @VisibleForTesting
    internal fun clear() {
      scopes.clear()
    }
  }

  @VisibleForTesting internal val singletons = HashMap<String, Any>()
  @VisibleForTesting internal val bindings = HashMap<KClass<*>, KClass<*>>()

  /**
   * Provide a singleton [instance] to slick with an optional [name] to use when resolving
   * dependencies. Objects passed as provided will be treated like the class has a
   * [Singleton] annotation.
   */
  fun provide(instance: Any, name: String? = null): Slik {
    instance::class.allSuperclasses.plus(instance::class).filterNot { it == Any::class }.forEach({
      currentClass -> singletons.put(makeClassKey(currentClass, name), instance)?.let {
        throw SlikException("${currentClass.qualifiedName} named \"$name\" is already provided " +
            "with ${it::class.qualifiedName} but is being provided again " +
            "with ${instance::class.qualifiedName}. " +
            "Add or change the @Named value in order to fix this.")
      }
    })
    return this
  }

  /**
   * Explicitly binds an [abstraction] to an [implementation] so that Slik may resolve a
   * dependency for the abstract class or interface. Usually this is done by adding an
   * [ImplementedBy] annotation to the [abstraction], but for classes that can't be touched
   * (eg. external libraries), use this method.
   */
  fun <T1 : Any, T2 : T1> bind(abstraction: KClass<T1>, implementation: KClass<T2>): Slik {
    bindings.put(abstraction, implementation)?.let {
      throw SlikException("${abstraction.qualifiedName} is already bound to " +
          "${it.qualifiedName} but is being re-bound to ${implementation.qualifiedName}")
    }
    return this
  }

  /**
   * Resolves dependencies for the given instance. This should only be used when constructor
   * injection is not an option (ie. when you have no control over the object lifecycle). In this
   * case, any subclass that requires injection must also call [inject].
   */
  @Deprecated("Use direct field injection instead. See #inject(String?)")
  fun <T : Any> inject(instance: T, abstractClass: KClass<T>? = null) {
    // Inject into annotated fields
    try {
      (abstractClass ?: instance::class).java.declaredFields.forEach {
        if (it.getAnnotation(Inject::class.java) == null) {
          return@forEach
        }
        if (!it.isAccessible) {
          it.isAccessible = true
        }
        it.set(instance, resolve(it.type.kotlin, it.getAnnotation(Named::class.java)))
      }
    } catch (e: SlikException) {
      throw SlikException(
          "${instance::class.qualifiedName} failed to inject its dependencies." +
              "\r\n\t ${e.message}")
    }
  }

  /**
   * Directly supplies an optionally [name]ed value to a field. This should only be used when
   * constructor injection is not an option (ie. when you have no control over the object
   * lifecycle). The most common pattern of using this is as follows:
   * ```
   * class MyClass {
   *   private val scope = Slik.get(MyApplication::class)
   *   private val myDependency = scope.inject<MyDependency>()
   *   ...
   * }
   * ```
   */
  inline fun <reified T : Any> inject(name: String? = null): T {
    return resolve(T::class, name)
  }

  /**
   * Retrieves an instance of [kClass] by following the injection rules of Slik. If the
   * class is marked as singleton, only a single instance per scope per value will be created.
   * Otherwise, a new instance will be attempted. The class must be marked as [Inject]able and
   * must have a primary constructor (ie. be a Kotlin class).
   *
   * One shouldn't invoke this method directly. Use [inject] instead.
   */
  fun <T : Any> resolve(kClass: KClass<T>, name: Named? = null): T =
      resolve(kClass, name?.value)

  /**
   * Retrieves an instance of [kClass] by following the injection rules of Slik. If the
   * class is marked as singleton, only a single instance per scope per value will be created.
   * Otherwise, a new instance will be attempted. The class must be marked as [Inject]able and
   * must have a primary constructor (ie. be a Kotlin class).
   *
   * One shouldn't invoke this method directly. Use [inject] instead.
   */
  fun <T : Any> resolve(kClass: KClass<T>, name: String? = null): T {
    val singletonName = makeClassKey(kClass, name)
    var injectedClass = kClass

    // If we have a reference, it's a singleton
    if (singletons.containsKey(singletonName)) {
      @Suppress("UNCHECKED_CAST")
      return singletons[singletonName] as T
    }

    // Sanity check
    if (injectedClass.isData
        || injectedClass.java.isEnum
        || injectedClass.java.isAnnotation
        || injectedClass.java.isArray) {
      throw SlikException("${injectedClass.qualifiedName} must be a regular kotlin class in" +
          " order for Slik to create an instance of it.")
    }

    // Check that we know how to implement if it's abstract or an interface
    if (injectedClass.java.isInterface || injectedClass.isAbstract) {
      @Suppress("UNCHECKED_CAST")
      injectedClass =
          (bindings[injectedClass]
              ?: injectedClass.findAnnotation<ImplementedBy>()?.value
              ?: throw SlikException("${injectedClass.qualifiedName} is an " +
              "interface or abstract class that must be #bound to an " +
              "implementation before Slik can inject it.")) as KClass<T>
    }

    // Is it injectable?
    if (injectedClass.findAnnotation<Inject>() == null) {
      throw SlikException("${injectedClass.qualifiedName} must be @Inject annotated.")
    }

    // Get the class's constructor
    val constructor = injectedClass.primaryConstructor?.javaConstructor
        ?: throw SlikException("${injectedClass.qualifiedName} must be a kotlin class.")

    // Fulfill dependencies
    val params = constructor.parameterTypes
    val paramAnnotations = constructor.parameterAnnotations
    val fulfillments = Array<Any>(params.size, {
      try {
        resolve(
            params[it].kotlin,
            paramAnnotations[it].firstOrNull { it is Named } as Named?)
      } catch (e: SlikException) {
        throw SlikException(
            "${injectedClass.qualifiedName}'s dependencies couldn't be fulfilled." +
                "\r\n\t ${e.message}")
      }
    })

    // Cache as singleton if required
    val result = constructor.newInstance(*fulfillments)!!
    if (injectedClass.findAnnotation<Singleton>() != null) {
      singletons.put(singletonName, result)
    }

    return result
  }

  private fun makeClassKey(kClass: KClass<*>, name: String?) =
      "${kClass.qualifiedName}:${name ?: ""}"
}
