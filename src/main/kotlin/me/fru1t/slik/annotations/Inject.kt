package me.fru1t.slik.annotations

/**
 * Identifies injectable constructors and fields. A class must have 1 constructor be marked with
 * [Inject] OR the class must have [Inject] with 0 constructors marked as injectable in order to use
 * the no-parameter constructor.
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Inject
