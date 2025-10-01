/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package app.cash.zipline.kotlin

import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrStarProjection
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

/** Inspired by [org.jetbrains.kotlin.ir.backend.js.utils.asString]. */
fun IrSimpleType.asString(): String = classifier.asString() +
    (
      arguments.ifNotEmpty {
      joinToString(separator = ",", prefix = "<", postfix = ">") { it.asString() }
    } ?: ""
    ) +
    (if (isMarkedNullable()) "?" else "")

/** Copied from [org.jetbrains.kotlin.ir.backend.js.utils.asString]. */
private fun IrTypeArgument.asString(): String = when (this) {
  is IrStarProjection -> "*"
  is IrTypeProjection -> variance.label + (if (variance != Variance.INVARIANT) " " else "") + (type as IrSimpleType).asString()
  else -> error("Unexpected kind of IrTypeArgument: " + javaClass.simpleName)
}

/** Copied from [org.jetbrains.kotlin.ir.backend.js.utils.asString]. */
private fun IrClassifierSymbol.asString() = when (this) {
  is IrTypeParameterSymbol -> this.owner.name.asString()
  is IrClassSymbol -> this.owner.fqNameWhenAvailable!!.asString()
  else -> error("Unexpected kind of IrClassifierSymbol: " + javaClass.typeName)
}
