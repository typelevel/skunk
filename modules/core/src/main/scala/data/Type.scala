package skunk.data

/**
 * Enumerated type of *built-in* schema types. These are defined as constants in the Postgres source
 * and we can safely assume they will never change, heh-heh.
 */
sealed abstract class Type(val oid: Int, val name: String) extends Product with Serializable
object Type {

  case object _abstime            extends Type(1023, "_abstime")
  case object _aclitem            extends Type(1034, "_aclitem")
  case object _bit                extends Type(1561, "_bit")
  case object _bool               extends Type(1000, "_bool")
  case object _box                extends Type(1020, "_box")
  case object _bpchar             extends Type(1014, "_bpchar")
  case object _bytea              extends Type(1001, "_bytea")
  case object _char               extends Type(1002, "_char")
  case object _cid                extends Type(1012, "_cid")
  case object _cidr               extends Type(651,  "_cidr")
  case object _circle             extends Type(719,  "_circle")
  case object _cstring            extends Type(1263, "_cstring")
  case object _date               extends Type(1182, "_date")
  case object _daterange          extends Type(3913, "_daterange")
  case object _float4             extends Type(1021, "_float4")
  case object _float8             extends Type(1022, "_float8")
  case object _gtsvector          extends Type(3644, "_gtsvector")
  case object _inet               extends Type(1041, "_inet")
  case object _int2               extends Type(1005, "_int2")
  case object _int2vector         extends Type(1006, "_int2vector")
  case object _int4               extends Type(1007, "_int4")
  case object _int4range          extends Type(3905, "_int4range")
  case object _int8               extends Type(1016, "_int8")
  case object _int8range          extends Type(3927, "_int8range")
  case object _interval           extends Type(1187, "_interval")
  case object _json               extends Type(199,  "_json")
  case object _jsonb              extends Type(3807, "_jsonb")
  case object _line               extends Type(629,  "_line")
  case object _lseg               extends Type(1018, "_lseg")
  case object _macaddr            extends Type(1040, "_macaddr")
  case object _macaddr8           extends Type(775,  "_macaddr8")
  case object _money              extends Type(791,  "_money")
  case object _name               extends Type(1003, "_name")
  case object _numeric            extends Type(1231, "_numeric")
  case object _numrange           extends Type(3907, "_numrange")
  case object _oid                extends Type(1028, "_oid")
  case object _oidvector          extends Type(1013, "_oidvector")
  case object _path               extends Type(1019, "_path")
  case object _pg_lsn             extends Type(3221, "_pg_lsn")
  case object _point              extends Type(1017, "_point")
  case object _polygon            extends Type(1027, "_polygon")
  case object _record             extends Type(2287, "_record")
  case object _refcursor          extends Type(2201, "_refcursor")
  case object _regclass           extends Type(2210, "_regclass")
  case object _regconfig          extends Type(3735, "_regconfig")
  case object _regdictionary      extends Type(3770, "_regdictionary")
  case object _regnamespace       extends Type(4090, "_regnamespace")
  case object _regoper            extends Type(2208, "_regoper")
  case object _regoperator        extends Type(2209, "_regoperator")
  case object _regproc            extends Type(1008, "_regproc")
  case object _regprocedure       extends Type(2207, "_regprocedure")
  case object _regrole            extends Type(4097, "_regrole")
  case object _regtype            extends Type(2211, "_regtype")
  case object _reltime            extends Type(1024, "_reltime")
  case object _text               extends Type(1009, "_text")
  case object _tid                extends Type(1010, "_tid")
  case object _time               extends Type(1183, "_time")
  case object _timestamp          extends Type(1115, "_timestamp")
  case object _timestamptz        extends Type(1185, "_timestamptz")
  case object _timetz             extends Type(1270, "_timetz")
  case object _tinterval          extends Type(1025, "_tinterval")
  case object _tsquery            extends Type(3645, "_tsquery")
  case object _tsrange            extends Type(3909, "_tsrange")
  case object _tstzrange          extends Type(3911, "_tstzrange")
  case object _tsvector           extends Type(3643, "_tsvector")
  case object _txid_snapshot      extends Type(2949, "_txid_snapshot")
  case object _uuid               extends Type(2951, "_uuid")
  case object _varbit             extends Type(1563, "_varbit")
  case object _varchar            extends Type(1015, "_varchar")
  case object _xid                extends Type(1011, "_xid")
  case object _xml                extends Type(143,  "_xml")
  case object abstime             extends Type(702,  "abstime")
  case object aclitem             extends Type(1033, "aclitem")
  case object any                 extends Type(2276, "any")
  case object anyarray            extends Type(2277, "anyarray")
  case object anyelement          extends Type(2283, "anyelement")
  case object anyenum             extends Type(3500, "anyenum")
  case object anynonarray         extends Type(2776, "anynonarray")
  case object anyrange            extends Type(3831, "anyrange")
  case object bit                 extends Type(1560, "bit")
  case object bool                extends Type(16,   "bool")
  case object box                 extends Type(603,  "box")
  case object bpchar              extends Type(1042, "bpchar")
  case object bytea               extends Type(17,   "bytea")
  case object char                extends Type(18,   "char")
  case object cid                 extends Type(29,   "cid")
  case object cidr                extends Type(650,  "cidr")
  case object circle              extends Type(718,  "circle")
  case object cstring             extends Type(2275, "cstring")
  case object date                extends Type(1082, "date")
  case object daterange           extends Type(3912, "daterange")
  case object event_trigger       extends Type(3838, "event_trigger")
  case object fdw_handler         extends Type(3115, "fdw_handler")
  case object float4              extends Type(700,  "float4")
  case object float8              extends Type(701,  "float8")
  case object gtsvector           extends Type(3642, "gtsvector")
  case object index_am_handler    extends Type(325,  "index_am_handler")
  case object inet                extends Type(869,  "inet")
  case object int2                extends Type(21,   "int2")
  case object int2vector          extends Type(22,   "int2vector")
  case object int4                extends Type(23,   "int4")
  case object int4range           extends Type(3904, "int4range")
  case object int8                extends Type(20,   "int8")
  case object int8range           extends Type(3926, "int8range")
  case object internal            extends Type(2281, "internal")
  case object interval            extends Type(1186, "interval")
  case object json                extends Type(114,  "json")
  case object jsonb               extends Type(3802, "jsonb")
  case object language_handler    extends Type(2280, "language_handler")
  case object line                extends Type(628,  "line")
  case object lseg                extends Type(601,  "lseg")
  case object macaddr             extends Type(829,  "macaddr")
  case object macaddr8            extends Type(774,  "macaddr8")
  case object money               extends Type(790,  "money")
  case object name                extends Type(19,   "name")
  case object numeric             extends Type(1700, "numeric")
  case object numrange            extends Type(3906, "numrange")
  case object oid                 extends Type(26,   "oid")
  case object oidvector           extends Type(30,   "oidvector")
  case object opaque              extends Type(2282, "opaque")
  case object path                extends Type(602,  "path")
  case object pg_attribute        extends Type(75,   "pg_attribute")
  case object pg_class            extends Type(83,   "pg_class")
  case object pg_ddl_command      extends Type(32,   "pg_ddl_command")
  case object pg_dependencies     extends Type(3402, "pg_dependencies")
  case object pg_lsn              extends Type(3220, "pg_lsn")
  case object pg_ndistinct        extends Type(3361, "pg_ndistinct")
  case object pg_node_tree        extends Type(194,  "pg_node_tree")
  case object pg_proc             extends Type(81,   "pg_proc")
  case object pg_type             extends Type(71,   "pg_type")
  case object point               extends Type(600,  "point")
  case object polygon             extends Type(604,  "polygon")
  case object record              extends Type(2249, "record")
  case object refcursor           extends Type(1790, "refcursor")
  case object regclass            extends Type(2205, "regclass")
  case object regconfig           extends Type(3734, "regconfig")
  case object regdictionary       extends Type(3769, "regdictionary")
  case object regnamespace        extends Type(4089, "regnamespace")
  case object regoper             extends Type(2203, "regoper")
  case object regoperator         extends Type(2204, "regoperator")
  case object regproc             extends Type(24,   "regproc")
  case object regprocedure        extends Type(2202, "regprocedure")
  case object regrole             extends Type(4096, "regrole")
  case object regtype             extends Type(2206, "regtype")
  case object reltime             extends Type(703,  "reltime")
  case object smgr                extends Type(210,  "smgr")
  case object text                extends Type(25,   "text")
  case object tid                 extends Type(27,   "tid")
  case object time                extends Type(1083, "time")
  case object timestamp           extends Type(1114, "timestamp")
  case object timestamptz         extends Type(1184, "timestamptz")
  case object timetz              extends Type(1266, "timetz")
  case object tinterval           extends Type(704,  "tinterval")
  case object trigger             extends Type(2279, "trigger")
  case object tsm_handler         extends Type(3310, "tsm_handler")
  case object tsquery             extends Type(3615, "tsquery")
  case object tsrange             extends Type(3908, "tsrange")
  case object tstzrange           extends Type(3910, "tstzrange")
  case object tsvector            extends Type(3614, "tsvector")
  case object txid_snapshot       extends Type(2970, "txid_snapshot")
  case object unknown             extends Type(705,  "unknown")
  case object uuid                extends Type(2950, "uuid")
  case object varbit              extends Type(1562, "varbit")
  case object varchar             extends Type(1043, "varchar")
  case object void                extends Type(2278, "void")
  case object xid                 extends Type(28,   "xid")
  case object xml                 extends Type(142,  "xml")

  val all: List[Type] =
    List(
      _abstime,       _aclitem,         _bit,         _bool,            _box,
      _bpchar,        _bytea,           _char,        _cid,             _cidr,
      _circle,        _cstring,         _date,        _daterange,       _float4,
      _float8,        _gtsvector,       _inet,        _int2,            _int2vector,
      _int4,          _int4range,       _int8,        _int8range,       _interval,
      _json,          _jsonb,           _line,        _lseg,            _macaddr,
      _macaddr8,      _money,           _name,        _numeric,         _numrange,
      _oid,           _oidvector,       _path,        _pg_lsn,          _point,
      _polygon,       _record,          _refcursor,   _regclass,        _regconfig,
      _regdictionary, _regnamespace,    _regoper,     _regoperator,     _regproc,
      _regprocedure,  _regrole,         _regtype,     _reltime,         _text,
      _tid,           _time,            _timestamp,   _timestamptz,     _timetz,
      _tinterval,     _tsquery,         _tsrange,     _tstzrange,       _tsvector,
      _txid_snapshot, _uuid,            _varbit,      _varchar,         _xid,
      _xml,           abstime,          aclitem,      any,              anyarray,
      anyelement,     anyenum,          anynonarray,  anyrange,         bit,
      bool,           box,              bpchar,       bytea,            char,
       cid,           cidr,             circle,       cstring,          date,
       daterange,     event_trigger,    fdw_handler,  float4,           float8,
       gtsvector,     index_am_handler, inet,         int2,             int2vector,
      int4,           int4range,        int8,         int8range,        internal,
      interval,       json,             jsonb,        language_handler, line,
      lseg,           macaddr,          macaddr8,     money,            name,
      numeric,        numrange,         oid,          oidvector,        opaque,
      path,           pg_attribute,     pg_class,     pg_ddl_command,   pg_dependencies,
      pg_lsn,         pg_ndistinct,     pg_node_tree, pg_proc,          pg_type,
      point,          polygon,          record,       refcursor,        regclass,
      regconfig,      regdictionary,    regnamespace, regoper,          regoperator,
      regproc,        regprocedure,     regrole,      regtype,          reltime,
      smgr,           text,             tid,          time,             timestamp,
      timestamptz,    timetz,           tinterval,    trigger,          tsm_handler,
      tsquery,        tsrange,          tstzrange,    tsvector,         txid_snapshot,
      unknown,        uuid,             varbit,       varchar,          void,
      xid,            xml,
    )

  val forOid: Int => Option[Type] =
    all.map(t => (t.oid -> t)).toMap.lift

}