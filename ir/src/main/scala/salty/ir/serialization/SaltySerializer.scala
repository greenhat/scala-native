package salty.ir
package serialization

import scala.collection.mutable
import java.nio.ByteBuffer
import salty.ir.{Desc => D, Tags => T}

class SaltySerializer(buffer: ByteBuffer) {
  import buffer._

  private val offsets  = mutable.Map[Node, Int]()
  private val worklist = mutable.Stack[(Int, Node)]()

  def serialize(scope: Scope) = {
    putInt(scope.entries.size)
    scope.entries.foreach {
      case (n, node) =>
        putName(n)
        worklist.push(position -> node)
        putInt(0)
    }
    while (worklist.nonEmpty) {
      val (pos, node) = worklist.pop()
      val offset =
        if (offsets.contains(node))
          offsets(node)
        else
          putNode(node)
      mark
      putInt(pos, offset)
      reset
    }
  }

  private def putNode(n: Node): Int =  {
    val pos = position
    offsets += (n -> pos)
    n.desc match {
      case Desc.Empty | _: Desc.Prim =>
        putDesc(n.desc)
      case _ =>
        putDesc(n.desc)
        putAttrs(n.attrs)
        if (n.desc.schema.nonEmpty) {
          putSeq(n._offsets)(putInt(_))
          putSeq(n._slots)(putSlot(_))
        }
    }
    pos
  }

  private def putDesc(desc: Desc) = desc match {
    case plain: D.Plain => putInt(T.plain2tag(plain))
    case D.Lit.I8(v)    => putInt(T.I8Lit); put(v)
    case D.Lit.I16(v)   => putInt(T.I16Lit); putShort(v)
    case D.Lit.I32(v)   => putInt(T.I32Lit); putInt(v)
    case D.Lit.I64(v)   => putInt(T.I64Lit); putLong(v)
    case D.Lit.F32(v)   => putInt(T.F32Lit); putFloat(v)
    case D.Lit.F64(v)   => putInt(T.F64Lit); putDouble(v)
    case D.Lit.Str(v)   => putInt(T.StrLit); putString(v)
  }

  private def putSeq[T](seq: Seq[T])(putT: T => Unit) = {
    putInt(seq.length)
    seq.foreach(putT)
  }

  private def putSlot(slot: Slot) = { putSchema(slot.schema); putNodeRef(slot.dep) }

  private def putSchema(schema: Schema): Unit = schema match {
    case Schema.Val      => putInt(T.ValSchema)
    case Schema.Cf       => putInt(T.CfSchema)
    case Schema.Ef       => putInt(T.EfSchema)
    case Schema.Ref      => putInt(T.RefSchema)
    case Schema.Many(sc) => putInt(T.ManySchema); putSchema(sc)
  }

  private def putNodeRef(n: Node) =
    if (offsets.contains(n))
      putInt(offsets(n))
    else {
      worklist.push(position -> n)
      putInt(0)
    }

  private def putAttrs(attrs: Seq[Attr]) = {
    val pattrs = attrs.collect { case pattr: PersistentAttr => pattr }
    putSeq(pattrs)(putPersistentAttr)
  }

  private def putPersistentAttr(attr: PersistentAttr) = attr match {
    case n: Name => putName(n)
  }

  private def putName(name: Name): Unit = name match {
    case Name.No =>
      putInt(T.NoName)
    case Name.Main =>
      putInt(T.MainName)
    case Name.Prim(v) =>
      putInt(T.PrimName); putString(v)
    case Name.Local(v) =>
      putInt(T.LocalName); putString(v)
    case Name.Class(v) =>
      putInt(T.ClassName); putString(v)
    case Name.Module(v) =>
      putInt(T.ModuleName); putString(v)
    case Name.Interface(v) =>
      putInt(T.InterfaceName); putString(v)
    case Name.Vtable(owner) =>
      putInt(T.VtableName); putName(owner)
    case Name.VtableConstant(owner) =>
      putInt(T.VtableConstantName); putName(owner)
    case Name.Accessor(owner) =>
      putInt(T.AccessorName); putName(owner)
    case Name.Data(owner) =>
      putInt(T.DataName); putName(owner)
    case Name.Slice(n) =>
      putInt(T.SliceName); putName(n)
    case Name.Field(owner, id) =>
      putInt(T.FieldName); putName(owner); putString(id)
    case Name.Constructor(owner, params) =>
      putInt(T.ConstructorName); putName(owner); putSeq(params)(putName)
    case Name.Method(owner, id, params, ret) =>
      putInt(T.MethodName); putName(owner); putString(id); putSeq(params)(putName); putName(ret)
  }

  private def putString(v: String) = {
    val bytes = v.getBytes
    putInt(bytes.length); put(bytes)
  }
}
