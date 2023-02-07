package app.cash.zipline.kotlin

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** Represents a package name without an associated class. */
@JvmInline
value class FqPackageName(val fqName: String)

fun FqPackageName(name: FqName): FqPackageName {
  return FqPackageName(name.asString())
}

fun FqPackageName.classId(name: String): ClassId {
  return ClassId(FqName(fqName), Name.identifier(name))
}

fun FqPackageName.callableId(name: String): CallableId {
  return CallableId(FqName(fqName), Name.identifier(name))
}

fun ClassId.callableId(name: String): CallableId {
  return CallableId(this, Name.identifier(name))
}
