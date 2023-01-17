package app.cash.zipline.kotlin

import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.util.isLocal
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

val IrType.classId: ClassId?
  get() {
    val irClass = getClass()
    val packageName = irClass?.packageFqName
    val relativeClassName = classFqName
    return if (packageName != null && relativeClassName != null) {
      ClassId(packageName, relativeClassName, irClass.isLocal)
    } else {
      null
    }
  }

fun FqName.classId(name: String): ClassId {
  return ClassId(this, Name.identifier(name))
}

fun FqName.callableId(name: String): CallableId {
  return CallableId(this, Name.identifier(name))
}

fun ClassId.callableId(name: String): CallableId {
  return CallableId(this, Name.identifier(name))
}
