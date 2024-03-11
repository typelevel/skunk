// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

import cats.{ Eq, Monad }
import cats.syntax.all._

/**
 * A type has a name and a list of component types. So it's a rose tree, isomorphic to
 * `Cofree[List, String]`, but we specialize here for simplicity.
 */
final case class Type(name: String, componentTypes: List[Type] = Nil) {

  /** Catamorphism for `Type`. */
  def fold[B](f: (String, List[B]) => B): B =
    f(name, componentTypes.map(_.fold(f)))

  /** Monadic catamorphism for `Type`. */
  def foldM[F[_]: Monad, B](f: (String, List[B]) => F[B]): F[B] =
    componentTypes.traverse(_.foldM(f)).flatMap(f(name, _))

  override def toString: String =
    fold[String] {
      case (s, Nil) => s
      case (s, ss)  => s + ss.mkString(" { ", ", ", " }")
    }

}

object Type {

  /** Anamorphism for `Type`. */
  def unfold[A](a: A)(f: A => List[A], g: A => String): Type =
    Type(g(a), f(a).map(unfold(_)(f, g)))

  /** Monadic anamorphism for `Type`. */
  def unfoldM[F[_]: Monad, A](a: A)(f: A => F[List[A]], g: A => F[String]): F[Type] =
    f(a).flatMap { as =>
      g(a).flatMap { s =>
        as.traverse(unfoldM(_)(f, g)).map(ts => Type(s, ts))
      }
    }

  implicit val EqType: Eq[Type] =
    Eq.fromUniversalEquals

  // Built-in Parameterized Types
  def bpchar(n: Int)          = Type(s"bpchar($n)")
  def varchar(n: Int)         = Type(s"varchar($n)")
  def numeric(p: Int, s: Int) = Type(s"numeric($p,$s)")
  def time(n: Int)            = Type(s"time($n)")
  def timetz(n: Int)          = Type(s"timetz($n)")
  def timestamp(n: Int)       = Type(s"timestamp($n)")
  def timestamptz(n: Int)     = Type(s"timestamptz($n)")
  def interval(n: Int)        = Type(s"interval($n)")
  def bit(n: Int)             = Type(s"bit($n)")
  def varbit(n: Int)          = Type(s"varbit($n)")

  // Built-in Base Types
  val abstime          = Type("abstime")
  val aclitem          = Type("aclitem")
  val any              = Type("any")
  val anyarray         = Type("anyarray")
  val anyelement       = Type("anyelement")
  val anyenum          = Type("anyenum")
  val anynonarray      = Type("anynonarray")
  val anyrange         = Type("anyrange")
  val bit              = Type("bit")
  val bool             = Type("bool")
  val box              = Type("box")
  val bpchar           = Type("bpchar")
  val bytea            = Type("bytea")
  val char             = Type("char")
  val cid              = Type("cid")
  val cidr             = Type("cidr")
  val circle           = Type("circle")
  val cstring          = Type("cstring")
  val date             = Type("date")
  val daterange        = Type("daterange")
  val event_trigger    = Type("event_trigger")
  val fdw_handler      = Type("fdw_handler")
  val float4           = Type("float4")
  val float8           = Type("float8")
  val gtsvector        = Type("gtsvector")
  val index_am_handler = Type("index_am_handler")
  val inet             = Type("inet")
  val int2             = Type("int2")
  val int2vector       = Type("int2vector")
  val int4             = Type("int4")
  val int4range        = Type("int4range")
  val int8             = Type("int8")
  val int8range        = Type("int8range")
  val internal         = Type("internal")
  val interval         = Type("interval")
  val json             = Type("json")
  val jsonb            = Type("jsonb")
  val language_handler = Type("language_handler")
  val line             = Type("line")
  val lseg             = Type("lseg")
  val macaddr          = Type("macaddr")
  val macaddr8         = Type("macaddr8")
  val money            = Type("money")
  val name             = Type("name")
  val numeric          = Type("numeric")
  val numrange         = Type("numrange")
  val oid              = Type("oid")
  val oidvector        = Type("oidvector")
  val opaque           = Type("opaque")
  val path             = Type("path")
  val point            = Type("point")
  val polygon          = Type("polygon")
  val record           = Type("record")
  val refcursor        = Type("refcursor")
  val regclass         = Type("regclass")
  val regconfig        = Type("regconfig")
  val regdictionary    = Type("regdictionary")
  val regnamespace     = Type("regnamespace")
  val regoper          = Type("regoper")
  val regoperator      = Type("regoperator")
  val regproc          = Type("regproc")
  val regprocedure     = Type("regprocedure")
  val regrole          = Type("regrole")
  val regtype          = Type("regtype")
  val reltime          = Type("reltime")
  val smgr             = Type("smgr")
  val text             = Type("text")
  val tid              = Type("tid")
  val time             = Type("time")
  val timestamp        = Type("timestamp")
  val timestamptz      = Type("timestamptz")
  val timetz           = Type("timetz")
  val tinterval        = Type("tinterval")
  val trigger          = Type("trigger")
  val tsm_handler      = Type("tsm_handler")
  val tsquery          = Type("tsquery")
  val tsrange          = Type("tsrange")
  val tstzrange        = Type("tstzrange")
  val tsvector         = Type("tsvector")
  val txid_snapshot    = Type("txid_snapshot")
  val unknown          = Type("unknown")
  val uuid             = Type("uuid")
  val varbit           = Type("varbit")
  val varchar          = Type("varchar")
  val void             = Type("void")
  val xid              = Type("xid")
  val xml              = Type("xml")

  // Built-in Array Types
  val _bool            = Type("_bool",          List(Type("bool")))
  val _bytea           = Type("_bytea",         List(Type("bytea")))
  val _char            = Type("_char",          List(Type("char")))
  val _name            = Type("_name",          List(Type("name")))
  val _int8            = Type("_int8",          List(Type("int8")))
  val _int2            = Type("_int2",          List(Type("int2")))
  val _int2vector      = Type("_int2vector",    List(Type("int2vector")))
  val _int4            = Type("_int4",          List(Type("int4")))
  val _regproc         = Type("_regproc",       List(Type("regproc")))
  val _text            = Type("_text",          List(Type("text")))
  val _oid             = Type("_oid",           List(Type("oid")))
  val _tid             = Type("_tid",           List(Type("tid")))
  val _xid             = Type("_xid",           List(Type("xid")))
  val _cid             = Type("_cid",           List(Type("cid")))
  val _oidvector       = Type("_oidvector",     List(Type("oidvector")))
  val _json            = Type("_json",          List(Type("json")))
  val _xml             = Type("_xml",           List(Type("xml")))
  val _point           = Type("_point",         List(Type("point")))
  val _lseg            = Type("_lseg",          List(Type("lseg")))
  val _path            = Type("_path",          List(Type("path")))
  val _box             = Type("_box",           List(Type("box")))
  val _polygon         = Type("_polygon",       List(Type("polygon")))
  val _line            = Type("_line",          List(Type("line")))
  val _float4          = Type("_float4",        List(Type("float4")))
  val _float8          = Type("_float8",        List(Type("float8")))
  val _abstime         = Type("_abstime",       List(Type("abstime")))
  val _reltime         = Type("_reltime",       List(Type("reltime")))
  val _tinterval       = Type("_tinterval",     List(Type("tinterval")))
  val _circle          = Type("_circle",        List(Type("circle")))
  val _money           = Type("_money",         List(Type("money")))
  val _macaddr         = Type("_macaddr",       List(Type("macaddr")))
  val _inet            = Type("_inet",          List(Type("inet")))
  val _cidr            = Type("_cidr",          List(Type("cidr")))
  val _aclitem         = Type("_aclitem",       List(Type("aclitem")))
  val _bpchar          = Type("_bpchar",        List(Type("bpchar")))
  val _varchar         = Type("_varchar",       List(Type("varchar")))
  val _date            = Type("_date",          List(Type("date")))
  val _time            = Type("_time",          List(Type("time")))
  val _timestamp       = Type("_timestamp",     List(Type("timestamp")))
  val _timestamptz     = Type("_timestamptz",   List(Type("timestamptz")))
  val _interval        = Type("_interval",      List(Type("interval")))
  val _timetz          = Type("_timetz",        List(Type("timetz")))
  val _bit             = Type("_bit",           List(Type("bit")))
  val _varbit          = Type("_varbit",        List(Type("varbit")))
  val _numeric         = Type("_numeric",       List(Type("numeric")))
  val _refcursor       = Type("_refcursor",     List(Type("refcursor")))
  val _regprocedure    = Type("_regprocedure",  List(Type("regprocedure")))
  val _regoper         = Type("_regoper",       List(Type("regoper")))
  val _regoperator     = Type("_regoperator",   List(Type("regoperator")))
  val _regclass        = Type("_regclass",      List(Type("regclass")))
  val _regtype         = Type("_regtype",       List(Type("regtype")))
  val _regrole         = Type("_regrole",       List(Type("regrole")))
  val _regnamespace    = Type("_regnamespace",  List(Type("regnamespace")))
  val _uuid            = Type("_uuid",          List(Type("uuid")))
  val _pg_lsn          = Type("_pg_lsn",        List(Type("pg_lsn")))
  val _tsvector        = Type("_tsvector",      List(Type("tsvector")))
  val _gtsvector       = Type("_gtsvector",     List(Type("gtsvector")))
  val _tsquery         = Type("_tsquery",       List(Type("tsquery")))
  val _regconfig       = Type("_regconfig",     List(Type("regconfig")))
  val _regdictionary   = Type("_regdictionary", List(Type("regdictionary")))
  val _jsonb           = Type("_jsonb",         List(Type("jsonb")))
  val _txid_snapshot   = Type("_txid_snapshot", List(Type("txid_snapshot")))
  val _int4range       = Type("_int4range",     List(Type("int4range")))
  val _numrange        = Type("_numrange",      List(Type("numrange")))
  val _tsrange         = Type("_tsrange",       List(Type("tsrange")))
  val _tstzrange       = Type("_tstzrange",     List(Type("tstzrange")))
  val _daterange       = Type("_daterange",     List(Type("daterange")))
  val _int8range       = Type("_int8range",     List(Type("int8range")))
  val _record          = Type("_record",        List(Type("record")))
  val _cstring         = Type("_cstring",       List(Type("cstring")))

}


