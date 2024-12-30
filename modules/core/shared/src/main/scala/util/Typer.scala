// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.util

import cats._
import cats.syntax.all._
import skunk._
import skunk.codec.all._
import skunk.data.Type
import skunk.net.Protocol
import skunk.util.Typer.Strategy

trait Typer { self =>

  def strategy: Strategy

  def typeForOid(oid: Int, typeMod: Int): Option[Type]

  def oidForType(tpe: Type): Option[Int]

  def orElse(that: Typer): Typer =
    new Typer {
      override def strategy: Strategy =
        self.strategy max that.strategy
      override def typeForOid(oid: Int, typeMod: Int): Option[Type] =
        self.typeForOid(oid, typeMod) orElse that.typeForOid(oid, typeMod)
      override def oidForType(tpe: Type): Option[Int] =
        self.oidForType(tpe) orElse that.oidForType(tpe)
    }

}

object Typer {

  sealed trait Strategy
  object Strategy {

    /**
     * This strategy supports built-in Postgres types only, and does not need a database round-trip
     * for initialization. This is the fastest strategy and is appropriate when you are not using
     * any user-defined types (this includes enums).
     */
    case object BuiltinsOnly extends Strategy

    /**
     * This strategy supports built-in Postgres types, as well as types that are defined in
     * namespaces on the session search path. This is the default strategy.
     */
    case object SearchPath extends Strategy

    implicit val OrderStrategy: Order[Strategy] =
      Order.by {
        case BuiltinsOnly => 0
        case SearchPath   => 1
      }

  }


  val Static: Typer = new Typer {
    import Type._

    val strategy: Strategy = Strategy.BuiltinsOnly

    val staticByOid: Map[Int, Type] =
      Map(

        // Built-in Base Types
        702  -> abstime,       1033 -> aclitem,          2276 -> any,         2277 -> anyarray,
        2283 -> anyelement,    3500 -> anyenum,          2776 -> anynonarray, 3831 -> anyrange,
        1560 -> bit,           16   -> bool,             603  -> box,         1042 -> bpchar,
        17   -> bytea,         18   -> char,             29   -> cid,         650  -> cidr,
        718  -> circle,        2275 -> cstring,          1082 -> date,        3912 -> daterange,
        3838 -> event_trigger, 3115 -> fdw_handler,      700  -> float4,      701  -> float8,
        3642 -> gtsvector,     325  -> index_am_handler, 869  -> inet,        21   -> int2,
        22   -> int2vector,    23   -> int4,             3904 -> int4range,   20   -> int8,
        3926 -> int8range,     2281 -> internal,         1186 -> interval,    114  -> json,
        3802 -> jsonb,         2280 -> language_handler, 628  -> line,        601  -> lseg,
        829  -> macaddr,       774  -> macaddr8,         790  -> money,       19   -> name,
        1700 -> numeric,       3906 -> numrange,         26   -> oid,         30   -> oidvector,
        2282 -> opaque,        602  -> path,             600  -> point,       604  -> polygon,
        2249 -> record,        1790 -> refcursor,        2205 -> regclass,    3734 -> regconfig,
        3769 -> regdictionary, 4089 -> regnamespace,     2203 -> regoper,     2204 -> regoperator,
        24   -> regproc,       2202 -> regprocedure,     4096 -> regrole,     2206 -> regtype,
        703  -> reltime,       210  -> smgr,             25   -> text,        27   -> tid,
        1083 -> time,          1114 -> timestamp,        1184 -> timestamptz, 1266 -> timetz,
        704  -> tinterval,     2279 -> trigger,          3310 -> tsm_handler, 3615 -> tsquery,
        3908 -> tsrange,       3910 -> tstzrange,        3614 -> tsvector,    2970 -> txid_snapshot,
        705  -> unknown,       2950 -> uuid,             1562 -> varbit,      1043 -> varchar,
        2278 -> void,          28   -> xid,              142  -> xml,

        // Built-in Array Types
        1000 -> _bool,         1001 -> _bytea,         1002 -> _char,         1003 -> _name,
        1016 -> _int8,         1005 -> _int2,          1006 -> _int2vector,   1007 -> _int4,
        1008 -> _regproc,      1009 -> _text,          1028 -> _oid,          1010 -> _tid,
        1011 -> _xid,          1012 -> _cid,           1013 -> _oidvector,    199  -> _json,
        143  -> _xml,          1017 -> _point,         1018 -> _lseg,         1019 -> _path,
        1020 -> _box,          1027 -> _polygon,       629  -> _line,         1021 -> _float4,
        1022 -> _float8,       1023 -> _abstime,       1024 -> _reltime,      1025 -> _tinterval,
        719  -> _circle,       791  -> _money,         1040 -> _macaddr,      1041 -> _inet,
        651  -> _cidr,         1034 -> _aclitem,       1014 -> _bpchar,       1015 -> _varchar,
        1182 -> _date,         1183 -> _time,          1115 -> _timestamp,    1185 -> _timestamptz,
        1187 -> _interval,     1270 -> _timetz,        1561 -> _bit,          1563 -> _varbit,
        1231 -> _numeric,      2201 -> _refcursor,     2207 -> _regprocedure, 2208 -> _regoper,
        2209 -> _regoperator,  2210 -> _regclass,      2211 -> _regtype,      4097 -> _regrole,
        4090 -> _regnamespace, 2951 -> _uuid,          3221 -> _pg_lsn,       3643 -> _tsvector,
        3644 -> _gtsvector,    3645 -> _tsquery,       3735 -> _regconfig,    3770 -> _regdictionary,
        3807 -> _jsonb,        2949 -> _txid_snapshot, 3905 -> _int4range,    3907 -> _numrange,
        3909 -> _tsrange,      3911 -> _tstzrange,     3913 -> _daterange,    3927 -> _int8range,
        2287 -> _record,       1263 -> _cstring,

     )

    val staticByName: Map[String, Int] =
     staticByOid.map { case (k, v) => v.name -> k }

    /** These types are parameterized and need special handling. */
    object Oid {
      val (bit,         _bit)         = (1560, 1561)
      val (bpchar,      _bpchar)      = (1042, 1014)
      val (interval,    _interval)    = (1186, 1187)
      val (numeric,     _numeric)     = (1700, 1231)
      val (time,        _time)        = (1083, 1183)
      val (timestamp,   _timestamp)   = (1114, 1115)
      val (timestamptz, _timestamptz) = (1184, 1185)
      val (timetz,      _timetz)      = (1266, 1270)
      val (varbit,      _varbit)      = (1562, 1563)
      val (varchar,     _varchar)     = (1043, 1015)
    }

    def oidForType(tpe: Type): Option[Int] =
      staticByName.get(tpe.name.takeWhile(_ != '('))

    def typeForOid(typeOid: Int, typeMod: Int): Option[Type] =
      (typeOid match {

        // bit
        // interval

        case Oid.numeric =>
          if (typeMod == -1) Some(Type("numeric"))
          else {
            val p = ((typeMod - 4) >> 16) & 65535
            val s = (typeMod - 4) & 65535
            Some(Type(s"numeric($p,$s)"))
          }

        case Oid._numeric =>
          typeForOid(Oid.numeric, typeMod).map { e =>
            if (typeMod == -1) Type("_numeric", List(e))
            else {
              val p = ((typeMod - 4) >> 16) & 65535
              val s = (typeMod - 4) & 65535
              Type(s"_numeric($p,$s)", List(e))
            }
          }

        case Oid.bpchar      => if (typeMod == -1) Some(Type("bpchar"))      else Some(Type(s"bpchar(${typeMod - 4})"))
        case Oid.time        => if (typeMod == -1) Some(Type("time"))        else Some(Type(s"time($typeMod)"))
        case Oid.timetz      => if (typeMod == -1) Some(Type("timetz"))      else Some(Type(s"timetz($typeMod)"))
        case Oid.timestamp   => if (typeMod == -1) Some(Type("timestamp"))   else Some(Type(s"timestamp($typeMod)"))
        case Oid.timestamptz => if (typeMod == -1) Some(Type("timestamptz")) else Some(Type(s"timestamptz($typeMod)"))
        case Oid.varchar     => if (typeMod == -1) Some(Type("varchar"))     else Some(Type(s"varchar(${typeMod - 4})"))
        case Oid.varbit      => if (typeMod == -1) Some(Type("varbit"))      else Some(Type(s"varbit(${typeMod})"))
        case Oid.bit         => if (typeMod == -1) Some(Type("bit"))         else Some(Type(s"bit(${typeMod})"))

        // Ok we need to handle arrays of those types as well
        case n => staticByOid.get(n)

      }) orElse staticByOid.get(typeOid)

  }


  case class TypeInfo(oid: Int, name: String, arrayTypeOid: Option[Int], relOid: Option[Int])

  implicit class ProtocolOps[F[_]: Functor](p: Protocol[F]) {
    //Note Postgres defines oids as *unsigned* Ints https://www.postgresql.org/docs/current/datatype-oid.html
    //Since Scala currently lacks a built-in unsigned Int type, if the oid exceeds `Int.MaxValue`
    //it will be converted to/from a negative Int by this Codec (only observed in CockroachDB)
    val oid: Codec[Int] = Codec.simple(java.lang.Integer.toUnsignedLong(_).toString, _.toLong.toInt.asRight, Type.oid)

    val typeInfoMap: F[Map[Int, TypeInfo]] = {

      val typeinfo =
        (oid ~ name ~ oid ~ oid).map { case o ~ n ~ a ~ r =>
          TypeInfo(o, n, Some(a).filter(_ > 0), Some(r).filter(_ > 0))
        }

      val query: Query[Void, TypeInfo] =
        Fragment("""
          SELECT oid typid, typname, typarray, typrelid
          FROM   pg_type
          WHERE typnamespace IN (
            SELECT oid
            FROM   pg_namespace
            WHERE nspname = ANY(current_schemas(true))
          )
        """).query(typeinfo)

      p.execute(query, Typer.Static).map { tis =>
        tis.map(ti => ti.oid -> ti).toMap
      }

    }


    val relInfoMap: F[Map[Int, List[Int]]] = {

      val query: Query[Void, Int ~ Int] =
        Fragment("""
          SELECT attrelid relid, atttypid typid
          FROM   pg_class
          JOIN   pg_attribute ON pg_attribute.attrelid = pg_class.oid
          WHERE  relnamespace IN (
            SELECT oid
            FROM   pg_namespace
            WHERE  nspname = ANY(current_schemas(true))
          )
          AND    attnum > 0
          ORDER  BY attrelid DESC, attnum ASC
        """).query(oid ~ oid)

      p.execute(query, Typer.Static).map { ps =>
        ps.foldMap { case (k, v) => Map(k -> List(v)) }
      }

    }

  }

  def fromProtocol[F[_]: Apply](p: Protocol[F]): F[Typer] =
    (p.typeInfoMap, p.relInfoMap).mapN { (tim, rim) =>
      Static orElse new Typer {

        val strategy: Strategy = Strategy.SearchPath

        val nameToOid: Map[String, Int] =
          tim.map { case (k, v) => v.name -> k }

        val arrayLookup: Map[Int, Int] =
          tim.values.collect {
            case TypeInfo(elem, _, Some(parent), _) => parent -> elem
          } .toMap

        def baseLookup(oid: Int): Option[Type] =
          Type.unfoldM(oid)(tim.get(_).map(_.relOid.flatMap(rim.get).getOrElse(Nil)), tim.get(_).map(_.name))

        def oidForType(tpe: Type): Option[Int] =
          nameToOid.get(tpe.name.takeWhile(_ != '('))

        def typeForOid(oid: Int, typeMod: Int): Option[Type] =
          arrayLookup.get(oid) match {
            case Some(e) => (tim.get(oid), baseLookup(e)).mapN { (a, b) => Type(a.name, List(b)) }
            case None    => baseLookup(oid)
          }

      }
    }

}
