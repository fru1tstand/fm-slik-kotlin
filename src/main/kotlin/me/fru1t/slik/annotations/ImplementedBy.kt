package me.fru1t.slik.annotations

import kotlin.reflect.KClass

/** Denotes an interface or abstract class's default implementation. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ImplementedBy(val value: KClass<*>)
