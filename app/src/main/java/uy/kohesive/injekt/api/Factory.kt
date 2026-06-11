package uy.kohesive.injekt.api

import java.lang.reflect.Type

interface InjektFactory {
    fun <R : Any> getInstance(forType: Type): R
    fun <R : Any> getInstanceOrNull(forType: Type): R?
}
