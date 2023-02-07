package app.cash.zipline.kotlin

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** Represents a package name without an associated class. */
@JvmInline
value class FqPackageName(val fqName: FqName)

fun FqPackageName(name: String): FqPackageName {
  return FqPackageName(FqName(name))
}

fun FqPackageName.classId(name: String): ClassId {
  return ClassId(fqName, Name.identifier(name))
}

fun FqPackageName.callableId(name: String): CallableId {
  return CallableId(fqName, Name.identifier(name))
}

fun ClassId.callableId(name: String): CallableId {
  return CallableId(this, Name.identifier(name))
}
