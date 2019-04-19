// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import enumeratum.{ EnumEntry, Enum }
import skunk.exception.PostgresErrorException

/** Enumerated type of Postgres error codes. See the companion object for more information. */
sealed abstract class SqlState(val code: String) extends EnumEntry {

  def unapply(e: Throwable): Option[PostgresErrorException] =
    Some(e).collect { case e: PostgresErrorException if e.code == code => e }

}

/**
 * Enumerated type of Postgres error codes. These can be used as extractors for error handling,
 * for example:
 * {{{
 * doSomething.recoverWith { case SqlState.ForeignKeyViolation(ex) => ... }
 * }}}
 * @see [[https://www.postgresql.org/docs/10/errcodes-appendix.html PostgreSQL Error Codes]]
 */
object SqlState extends Enum[SqlState] {

  /**
   * SqlState `25001`
   * @group Instances
   */
  case object ActiveSqlTransaction extends SqlState("25001")

  /**
   * SqlState `57P01`
   * @group Instances
   */
  case object AdminShutdown extends SqlState("57P01")

  /**
   * SqlState `42P09`
   * @group Instances
   */
  case object AmbiguousAlias extends SqlState("42P09")

  /**
   * SqlState `42702`
   * @group Instances
   */
  case object AmbiguousColumn extends SqlState("42702")

  /**
   * SqlState `42725`
   * @group Instances
   */
  case object AmbiguousFunction extends SqlState("42725")

  /**
   * SqlState `42P08`
   * @group Instances
   */
  case object AmbiguousParameter extends SqlState("42P08")

  /**
   * SqlState `2202E`
   * @group Instances
   */
  case object ArraySubscriptError extends SqlState("2202E")

  /**
   * SqlState `22P04`
   * @group Instances
   */
  case object BadCopyFileFormat extends SqlState("22P04")

  /**
   * SqlState `25002`
   * @group Instances
   */
  case object BranchTransactionAlreadyActive extends SqlState("25002")

  /**
   * SqlState `42846`
   * @group Instances
   */
  case object CannotCoerce extends SqlState("42846")

  /**
   * SqlState `57P03`
   * @group Instances
   */
  case object CannotConnectNow extends SqlState("57P03")

  /**
   * SqlState `55P02`
   * @group Instances
   */
  case object CantChangeRuntimeParam extends SqlState("55P02")

  /**
   * SqlState `21000`
   * @group Instances
   */
  case object CardinalityViolation extends SqlState("21000")

  /**
   * SqlState `20000`
   * @group Instances
   */
  case object CaseNotFound extends SqlState("20000")

  /**
   * SqlState `22021`
   * @group Instances
   */
  case object CharacterNotInRepertoire extends SqlState("22021")

  /**
   * SqlState `23514`
   * @group Instances
   */
  case object CheckViolation extends SqlState("23514")

  /**
   * SqlState `F0000`
   * @group Instances
   */
  case object ConfigFileError extends SqlState("F0000")

  /**
   * SqlState `08003`
   * @group Instances
   */
  case object ConnectionDoesNotExist extends SqlState("08003")

  /**
   * SqlState `08000`
   * @group Instances
   */
  case object ConnectionException extends SqlState("08000")

  /**
   * SqlState `08006`
   * @group Instances
   */
  case object ConnectionFailure extends SqlState("08006")

  /**
   * SqlState `38001`
   * @group Instances
   */
  case object ContainingSqlNotPermitted extends SqlState("38001")

  /**
   * SqlState `57P02`
   * @group Instances
   */
  case object CrashShutdown extends SqlState("57P02")

  /**
   * SqlState `XX001`
   * @group Instances
   */
  case object DataCorrupted extends SqlState("XX001")

  /**
   * SqlState `22000`
   * @group Instances
   */
  case object DataException extends SqlState("22000")

  /**
   * SqlState `57P04`
   * @group Instances
   */
  case object DatabaseDropped extends SqlState("57P04")

  /**
   * SqlState `42804`
   * @group Instances
   */
  case object DatatypeMismatch extends SqlState("42804")

  /**
   * SqlState `22008`
   * @group Instances
   */
  case object DatetimeFieldOverflow extends SqlState("22008")

  /**
   * SqlState `40P01`
   * @group Instances
   */
  case object DeadlockDetected extends SqlState("40P01")

  /**
   * SqlState `2BP01`
   * @group Instances
   */
  case object DependentObjectsStillExist extends SqlState("2BP01")

  /**
   * SqlState `2B000`
   * @group Instances
   */
  case object DependentPrivilegeDescriptorsStillExist extends SqlState("2B000")

  /**
   * SqlState `01P01`
   * @group Instances
   */
  case object DeprecatedFeature extends SqlState("01P01")

  /**
   * SqlState `53100`
   * @group Instances
   */
  case object DiskFull extends SqlState("53100")

  /**
   * SqlState `22012`
   * @group Instances
   */
  case object DivisionByZero extends SqlState("22012")

  /**
   * SqlState `42712`
   * @group Instances
   */
  case object DuplicateAlias extends SqlState("42712")

  /**
   * SqlState `42701`
   * @group Instances
   */
  case object DuplicateColumn extends SqlState("42701")

  /**
   * SqlState `42P03`
   * @group Instances
   */
  case object DuplicateCursor extends SqlState("42P03")

  /**
   * SqlState `42P04`
   * @group Instances
   */
  case object DuplicateDatabase extends SqlState("42P04")

  /**
   * SqlState `58P02`
   * @group Instances
   */
  case object DuplicateFile extends SqlState("58P02")

  /**
   * SqlState `42723`
   * @group Instances
   */
  case object DuplicateFunction extends SqlState("42723")

  /**
   * SqlState `42710`
   * @group Instances
   */
  case object DuplicateObject extends SqlState("42710")

  /**
   * SqlState `42P05`
   * @group Instances
   */
  case object DuplicatePreparedStatement extends SqlState("42P05")

  /**
   * SqlState `42P06`
   * @group Instances
   */
  case object DuplicateSchema extends SqlState("42P06")

  /**
   * SqlState `42P07`
   * @group Instances
   */
  case object DuplicateTable extends SqlState("42P07")

  /**
   * SqlState `0100C`
   * @group Instances
   */
  case object DynamicResultSetsReturned extends SqlState("0100C")

  /**
   * SqlState `22005`
   * @group Instances
   */
  case object ErrorInAssignment extends SqlState("22005")

  /**
   * SqlState `2200B`
   * @group Instances
   */
  case object EscapeCharacterConflict extends SqlState("2200B")

  /**
   * SqlState `23P01`
   * @group Instances
   */
  case object ExclusionViolation extends SqlState("23P01")

  /**
   * SqlState `38000`
   * @group Instances
   */
  case object ExternalRoutineException extends SqlState("38000")

  /**
   * SqlState `39000`
   * @group Instances
   */
  case object ExternalRoutineInvocationException extends SqlState("39000")

  /**
   * SqlState `0A000`
   * @group Instances
   */
  case object FeatureNotSupported extends SqlState("0A000")

  /**
   * SqlState `22P01`
   * @group Instances
   */
  case object FloatingPointException extends SqlState("22P01")

  /**
   * SqlState `23503`
   * @group Instances
   */
  case object ForeignKeyViolation extends SqlState("23503")

  /**
   * SqlState `2F005`
   * @group Instances
   */
  case object FunctionExecutedNoReturnStatement extends SqlState("2F005")

  /**
   * SqlState `42803`
   * @group Instances
   */
  case object GroupingError extends SqlState("42803")

  /**
   * SqlState `25008`
   * @group Instances
   */
  case object HeldCursorRequiresSameIsolationLevel extends SqlState("25008")

  /**
   * SqlState `01008`
   * @group Instances
   */
  case object ImplicitZeroBitPadding extends SqlState("01008")

  /**
   * SqlState `25P02`
   * @group Instances
   */
  case object InFailedSqlTransaction extends SqlState("25P02")

  /**
   * SqlState `25003`
   * @group Instances
   */
  case object InappropriateAccessModeForBranchTransaction extends SqlState("25003")

  /**
   * SqlState `25004`
   * @group Instances
   */
  case object InappropriateIsolationLevelForBranchTransaction extends SqlState("25004")

  /**
   * SqlState `42P18`
   * @group Instances
   */
  case object IndeterminateDatatype extends SqlState("42P18")

  /**
   * SqlState `XX002`
   * @group Instances
   */
  case object IndexCorrupted extends SqlState("XX002")

  /**
   * SqlState `22022`
   * @group Instances
   */
  case object IndicatorOverflow extends SqlState("22022")

  /**
   * SqlState `42501`
   * @group Instances
   */
  case object InsufficientPrivilege extends SqlState("42501")

  /**
   * SqlState `53000`
   * @group Instances
   */
  case object InsufficientResources extends SqlState("53000")

  /**
   * SqlState `23000`
   * @group Instances
   */
  case object IntegrityConstraintViolation extends SqlState("23000")

  /**
   * SqlState `XX000`
   * @group Instances
   */
  case object InternalError extends SqlState("XX000")

  /**
   * SqlState `22015`
   * @group Instances
   */
  case object IntervalFieldOverflow extends SqlState("22015")

  /**
   * SqlState `2201E`
   * @group Instances
   */
  case object InvalidArgumentForLogarithm extends SqlState("2201E")

  /**
   * SqlState `22016`
   * @group Instances
   */
  case object InvalidArgumentForNthValueFunction extends SqlState("22016")

  /**
   * SqlState `22014`
   * @group Instances
   */
  case object InvalidArgumentForNtileFunction extends SqlState("22014")

  /**
   * SqlState `2201F`
   * @group Instances
   */
  case object InvalidArgumentForPowerFunction extends SqlState("2201F")

  /**
   * SqlState `2201G`
   * @group Instances
   */
  case object InvalidArgumentForWidthBucketFunction extends SqlState("2201G")

  /**
   * SqlState `28000`
   * @group Instances
   */
  case object InvalidAuthorizationSpecification extends SqlState("28000")

  /**
   * SqlState `22P03`
   * @group Instances
   */
  case object InvalidBinaryRepresentation extends SqlState("22P03")

  /**
   * SqlState `3D000`
   * @group Instances
   */
  case object InvalidCatalogName extends SqlState("3D000")

  /**
   * SqlState `22018`
   * @group Instances
   */
  case object InvalidCharacterValueForCast extends SqlState("22018")

  /**
   * SqlState `42611`
   * @group Instances
   */
  case object InvalidColumnDefinition extends SqlState("42611")

  /**
   * SqlState `42P10`
   * @group Instances
   */
  case object InvalidColumnReference extends SqlState("42P10")

  /**
   * SqlState `42P11`
   * @group Instances
   */
  case object InvalidCursorDefinition extends SqlState("42P11")

  /**
   * SqlState `34000`
   * @group Instances
   */
  case object InvalidCursorName extends SqlState("34000")

  /**
   * SqlState `24000`
   * @group Instances
   */
  case object InvalidCursorState extends SqlState("24000")

  /**
   * SqlState `42P12`
   * @group Instances
   */
  case object InvalidDatabaseDefinition extends SqlState("42P12")

  /**
   * SqlState `22007`
   * @group Instances
   */
  case object InvalidDatetimeFormat extends SqlState("22007")

  /**
   * SqlState `22019`
   * @group Instances
   */
  case object InvalidEscapeCharacter extends SqlState("22019")

  /**
   * SqlState `2200D`
   * @group Instances
   */
  case object InvalidEscapeOctet extends SqlState("2200D")

  /**
   * SqlState `22025`
   * @group Instances
   */
  case object InvalidEscapeSequence extends SqlState("22025")

  /**
   * SqlState `42830`
   * @group Instances
   */
  case object InvalidForeignKey extends SqlState("42830")

  /**
   * SqlState `42P13`
   * @group Instances
   */
  case object InvalidFunctionDefinition extends SqlState("42P13")

  /**
   * SqlState `0LP01`
   * @group Instances
   */
  case object InvalidGrantOperation extends SqlState("0LP01")

  /**
   * SqlState `0L000`
   * @group Instances
   */
  case object InvalidGrantor extends SqlState("0L000")

  /**
   * SqlState `22010`
   * @group Instances
   */
  case object InvalidIndicatorParameterValue extends SqlState("22010")

  /**
   * SqlState `0F001`
   * @group Instances
   */
  case object InvalidLocatorSpecification extends SqlState("0F001")

  /**
   * SqlState `42602`
   * @group Instances
   */
  case object InvalidName extends SqlState("42602")

  /**
   * SqlState `42P17`
   * @group Instances
   */
  case object InvalidObjectDefinition extends SqlState("42P17")

  /**
   * SqlState `22023`
   * @group Instances
   */
  case object InvalidParameterValue extends SqlState("22023")

  /**
   * SqlState `28P01`
   * @group Instances
   */
  case object InvalidPassword extends SqlState("28P01")

  /**
   * SqlState `42P14`
   * @group Instances
   */
  case object InvalidPreparedStatementDefinition extends SqlState("42P14")

  /**
   * SqlState `42P19`
   * @group Instances
   */
  case object InvalidRecursion extends SqlState("42P19")

  /**
   * SqlState `2201B`
   * @group Instances
   */
  case object InvalidRegularExpression extends SqlState("2201B")

  /**
   * SqlState `0P000`
   * @group Instances
   */
  case object InvalidRoleSpecification extends SqlState("0P000")

  /**
   * SqlState `2201W`
   * @group Instances
   */
  case object InvalidRowCountInLimitClause extends SqlState("2201W")

  /**
   * SqlState `2201X`
   * @group Instances
   */
  case object InvalidRowCountInResultOffsetClause extends SqlState("2201X")

  /**
   * SqlState `3B001`
   * @group Instances
   */
  case object InvalidSavepointSpecification extends SqlState("3B001")

  /**
   * SqlState `42P15`
   * @group Instances
   */
  case object InvalidSchemaDefinition extends SqlState("42P15")

  /**
   * SqlState `3F000`
   * @group Instances
   */
  case object InvalidSchemaName extends SqlState("3F000")

  /**
   * SqlState `26000`
   * @group Instances
   */
  case object InvalidSqlStatementName extends SqlState("26000")

  /**
   * SqlState `39001`
   * @group Instances
   */
  case object InvalidSqlstateReturned extends SqlState("39001")

  /**
   * SqlState `42P16`
   * @group Instances
   */
  case object InvalidTableDefinition extends SqlState("42P16")

  /**
   * SqlState `22P02`
   * @group Instances
   */
  case object InvalidTextRepresentation extends SqlState("22P02")

  /**
   * SqlState `22009`
   * @group Instances
   */
  case object InvalidTimeZoneDisplacementValue extends SqlState("22009")

  /**
   * SqlState `0B000`
   * @group Instances
   */
  case object InvalidTransactionInitiation extends SqlState("0B000")

  /**
   * SqlState `25000`
   * @group Instances
   */
  case object InvalidTransactionState extends SqlState("25000")

  /**
   * SqlState `2D000`
   * @group Instances
   */
  case object InvalidTransactionTermination extends SqlState("2D000")

  /**
   * SqlState `2200C`
   * @group Instances
   */
  case object InvalidUseOfEscapeCharacter extends SqlState("2200C")

  /**
   * SqlState `2200S`
   * @group Instances
   */
  case object InvalidXmlComment extends SqlState("2200S")

  /**
   * SqlState `2200N`
   * @group Instances
   */
  case object InvalidXmlContent extends SqlState("2200N")

  /**
   * SqlState `2200M`
   * @group Instances
   */
  case object InvalidXmlDocument extends SqlState("2200M")

  /**
   * SqlState `2200T`
   * @group Instances
   */
  case object InvalidXmlProcessingInstruction extends SqlState("2200T")

  /**
   * SqlState `58030`
   * @group Instances
   */
  case object IoError extends SqlState("58030")

  /**
   * SqlState `0F000`
   * @group Instances
   */
  case object LocatorException extends SqlState("0F000")

  /**
   * SqlState `F0001`
   * @group Instances
   */
  case object LockFileExists extends SqlState("F0001")

  /**
   * SqlState `55P03`
   * @group Instances
   */
  case object LockNotAvailable extends SqlState("55P03")

  /**
   * SqlState `2F002`
   * @group Instances
   */
  case object ModifyingSqlDataNotPermitted2F extends SqlState("2F002")

  /**
   * SqlState `38002`
   * @group Instances
   */
  case object ModifyingSqlDataNotPermitted38 extends SqlState("38002")

  /**
   * SqlState `2200G`
   * @group Instances
   */
  case object MostSpecificTypeMismatch extends SqlState("2200G")

  /**
   * SqlState `42622`
   * @group Instances
   */
  case object NameTooLong extends SqlState("42622")

  /**
   * SqlState `25P01`
   * @group Instances
   */
  case object NoActiveSqlTransaction extends SqlState("25P01")

  /**
   * SqlState `25005`
   * @group Instances
   */
  case object NoActiveSqlTransactionForBranchTransaction extends SqlState("25005")

  /**
   * SqlState `02001`
   * @group Instances
   */
  case object NoAdditionalDynamicResultSetsReturned extends SqlState("02001")

  /**
   * SqlState `02000`
   * @group Instances
   */
  case object NoData extends SqlState("02000")

  /**
   * SqlState `P0002`
   * @group Instances
   */
  case object NoDataFound extends SqlState("P0002")

  /**
   * SqlState `22P06`
   * @group Instances
   */
  case object NonstandardUseOfEscapeCharacter extends SqlState("22P06")

  /**
   * SqlState `2200L`
   * @group Instances
   */
  case object NotAnXmlDocument extends SqlState("2200L")

  /**
   * SqlState `23502`
   * @group Instances
   */
  case object NotNullViolation extends SqlState("23502")

  /**
   * SqlState `01003`
   * @group Instances
   */
  case object NullValueEliminatedInSetFunction extends SqlState("01003")

  /**
   * SqlState `22002`
   * @group Instances
   */
  case object NullValueNoIndicatorParameter extends SqlState("22002")

  /**
   * SqlState `22004`
   * @group Instances
   */
  case object NullValueNotAllowed extends SqlState("22004")

  /**
   * SqlState `39004`
   * @group Instances
   */
  case object NullValueNotAllowed39 extends SqlState("39004")

  /**
   * SqlState `22003`
   * @group Instances
   */
  case object NumericValueOutOfRange extends SqlState("22003")

  /**
   * SqlState `55006`
   * @group Instances
   */
  case object ObjectInUse extends SqlState("55006")

  /**
   * SqlState `55000`
   * @group Instances
   */
  case object ObjectNotInPrerequisiteState extends SqlState("55000")

  /**
   * SqlState `57000`
   * @group Instances
   */
  case object OperatorIntervention extends SqlState("57000")

  /**
   * SqlState `53200`
   * @group Instances
   */
  case object OutOfMemory extends SqlState("53200")

  /**
   * SqlState `P0000`
   * @group Instances
   */
  case object PlpgsqlError extends SqlState("P0000")

  /**
   * SqlState `01007`
   * @group Instances
   */
  case object PrivilegeNotGranted extends SqlState("01007")

  /**
   * SqlState `01006`
   * @group Instances
   */
  case object PrivilegeNotRevoked extends SqlState("01006")

  /**
   * SqlState `54000`
   * @group Instances
   */
  case object ProgramLimitExceeded extends SqlState("54000")

  /**
   * SqlState `2F003`
   * @group Instances
   */
  case object ProhibitedSqlStatementAttempted2F extends SqlState("2F003")

  /**
   * SqlState `38003`
   * @group Instances
   */
  case object ProhibitedSqlStatementAttempted38 extends SqlState("38003")

  /**
   * SqlState `08P01`
   * @group Instances
   */
  case object ProtocolViolation extends SqlState("08P01")

  /**
   * SqlState `57014`
   * @group Instances
   */
  case object QueryCanceled extends SqlState("57014")

  /**
   * SqlState `P0001`
   * @group Instances
   */
  case object RaiseException extends SqlState("P0001")

  /**
   * SqlState `25006`
   * @group Instances
   */
  case object ReadOnlySqlTransaction extends SqlState("25006")

  /**
   * SqlState `2F004`
   * @group Instances
   */
  case object ReadingSqlDataNotPermitted2F extends SqlState("2F004")

  /**
   * SqlState `38004`
   * @group Instances
   */
  case object ReadingSqlDataNotPermitted38 extends SqlState("38004")

  /**
   * SqlState `42939`
   * @group Instances
   */
  case object ReservedName extends SqlState("42939")

  /**
   * SqlState `23001`
   * @group Instances
   */
  case object RestrictViolation extends SqlState("23001")

  /**
   * SqlState `3B000`
   * @group Instances
   */
  case object SavepointException extends SqlState("3B000")

  /**
   * SqlState `25007`
   * @group Instances
   */
  case object SchemaAndDataStatementMixingNotSupported extends SqlState("25007")

  /**
   * SqlState `40001`
   * @group Instances
   */
  case object SerializationFailure extends SqlState("40001")

  /**
   * SqlState `08001`
   * @group Instances
   */
  case object SqlClientUnableToEstablishSqlConnection extends SqlState("08001")

  /**
   * SqlState `2F000`
   * @group Instances
   */
  case object SqlRoutineException extends SqlState("2F000")

  /**
   * SqlState `08004`
   * @group Instances
   */
  case object SqlServerRejectedEstablishmentOfSqlConnection extends SqlState("08004")

  /**
   * SqlState `03000`
   * @group Instances
   */
  case object SqlStatementNotYetComplete extends SqlState("03000")

  /**
   * SqlState `39P02`
   * @group Instances
   */
  case object SrfProtocolViolated extends SqlState("39P02")

  /**
   * SqlState `40003`
   * @group Instances
   */
  case object StatementCompletionUnknown extends SqlState("40003")

  /**
   * SqlState `54001`
   * @group Instances
   */
  case object StatementTooComplex extends SqlState("54001")

  /**
   * SqlState `22026`
   * @group Instances
   */
  case object StringDataLengthMismatch extends SqlState("22026")

  /**
   * SqlState `22001`
   * @group Instances
   */
  case object StringDataRightTruncation extends SqlState("22001")

  /**
   * SqlState `01004`
   * @group Instances
   */
  case object StringDataRightTruncation01 extends SqlState("01004")

  /**
   * SqlState `22011`
   * @group Instances
   */
  case object SubstringError extends SqlState("22011")

  /**
   * SqlState `00000`
   * @group Instances
   */
  case object SuccessfulCompletion extends SqlState("00000")

  /**
   * SqlState `42601`
   * @group Instances
   */
  case object SyntaxError extends SqlState("42601")

  /**
   * SqlState `42000`
   * @group Instances
   */
  case object SyntaxErrorOrAccessRuleViolation extends SqlState("42000")

  /**
   * SqlState `54023`
   * @group Instances
   */
  case object TooManyArguments extends SqlState("54023")

  /**
   * SqlState `54011`
   * @group Instances
   */
  case object TooManyColumns extends SqlState("54011")

  /**
   * SqlState `53300`
   * @group Instances
   */
  case object TooManyConnections extends SqlState("53300")

  /**
   * SqlState `P0003`
   * @group Instances
   */
  case object TooManyRows extends SqlState("P0003")

  /**
   * SqlState `40002`
   * @group Instances
   */
  case object TransactionIntegrityConstraintViolation extends SqlState("40002")

  /**
   * SqlState `08007`
   * @group Instances
   */
  case object TransactionResolutionUnknown extends SqlState("08007")

  /**
   * SqlState `40000`
   * @group Instances
   */
  case object TransactionRollback extends SqlState("40000")

  /**
   * SqlState `39P01`
   * @group Instances
   */
  case object TriggerProtocolViolated extends SqlState("39P01")

  /**
   * SqlState `09000`
   * @group Instances
   */
  case object TriggeredActionException extends SqlState("09000")

  /**
   * SqlState `27000`
   * @group Instances
   */
  case object TriggeredDataChangeViolation extends SqlState("27000")

  /**
   * SqlState `22027`
   * @group Instances
   */
  case object TrimError extends SqlState("22027")

  /**
   * SqlState `42703`
   * @group Instances
   */
  case object UndefinedColumn extends SqlState("42703")

  /**
   * SqlState `58P01`
   * @group Instances
   */
  case object UndefinedFile extends SqlState("58P01")

  /**
   * SqlState `42883`
   * @group Instances
   */
  case object UndefinedFunction extends SqlState("42883")

  /**
   * SqlState `42704`
   * @group Instances
   */
  case object UndefinedObject extends SqlState("42704")

  /**
   * SqlState `42P02`
   * @group Instances
   */
  case object UndefinedParameter extends SqlState("42P02")

  /**
   * SqlState `42P01`
   * @group Instances
   */
  case object UndefinedTable extends SqlState("42P01")

  /**
   * SqlState `23505`
   * @group Instances
   */
  case object UniqueViolation extends SqlState("23505")

  /**
   * SqlState `22024`
   * @group Instances
   */
  case object UnterminatedCString extends SqlState("22024")

  /**
   * SqlState `22P05`
   * @group Instances
   */
  case object UntranslatableCharacter extends SqlState("22P05")

  /**
   * SqlState `01000`
   * @group Instances
   */
  case object Warning extends SqlState("01000")

  /**
   * SqlState `42P20`
   * @group Instances
   */
  case object WindowingError extends SqlState("42P20")

  /**
   * SqlState `44000`
   * @group Instances
   */
  case object WithCheckOptionViolation extends SqlState("44000")

  /**
   * SqlState `42809`
   * @group Instances
   */
  case object WrongObjectType extends SqlState("42809")

  /**
   * SqlState `2200F`
   * @group Instances
   */
  case object ZeroLengthCharacterString extends SqlState("2200F")

  val values = findValues

}