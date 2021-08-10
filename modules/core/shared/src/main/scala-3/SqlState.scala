// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import scala.collection.immutable.IndexedSeq
import skunk.exception.PostgresErrorException

/**
 * Enumerated type of Postgres error codes. These can be used as extractors for error handling,
 * for example:
 * {{{
 * doSomething.recoverWith { case SqlState.ForeignKeyViolation(ex) => ... }
 * }}}
 * @see [[https://www.postgresql.org/docs/10/errcodes-appendix.html PostgreSQL Error Codes]]
 */
enum SqlState(val code: String) {

  def unapply(e: Throwable): Option[PostgresErrorException] =
    Some(e).collect { case e: PostgresErrorException if e.code == code => e }

  /**
   * SqlState `25001`
   * @group Instances
   */
  case ActiveSqlTransaction extends SqlState("25001")

  /**
   * SqlState `57P01`
   * @group Instances
   */
  case AdminShutdown extends SqlState("57P01")

  /**
   * SqlState `42P09`
   * @group Instances
   */
  case AmbiguousAlias extends SqlState("42P09")

  /**
   * SqlState `42702`
   * @group Instances
   */
  case AmbiguousColumn extends SqlState("42702")

  /**
   * SqlState `42725`
   * @group Instances
   */
  case AmbiguousFunction extends SqlState("42725")

  /**
   * SqlState `42P08`
   * @group Instances
   */
  case AmbiguousParameter extends SqlState("42P08")

  /**
   * SqlState `2202E`
   * @group Instances
   */
  case ArraySubscriptError extends SqlState("2202E")

  /**
   * SqlState `22P04`
   * @group Instances
   */
  case BadCopyFileFormat extends SqlState("22P04")

  /**
   * SqlState `25002`
   * @group Instances
   */
  case BranchTransactionAlreadyActive extends SqlState("25002")

  /**
   * SqlState `42846`
   * @group Instances
   */
  case CannotCoerce extends SqlState("42846")

  /**
   * SqlState `57P03`
   * @group Instances
   */
  case CannotConnectNow extends SqlState("57P03")

  /**
   * SqlState `55P02`
   * @group Instances
   */
  case CantChangeRuntimeParam extends SqlState("55P02")

  /**
   * SqlState `21000`
   * @group Instances
   */
  case CardinalityViolation extends SqlState("21000")

  /**
   * SqlState `20000`
   * @group Instances
   */
  case CaseNotFound extends SqlState("20000")

  /**
   * SqlState `22021`
   * @group Instances
   */
  case CharacterNotInRepertoire extends SqlState("22021")

  /**
   * SqlState `23514`
   * @group Instances
   */
  case CheckViolation extends SqlState("23514")

  /**
   * SqlState `F0000`
   * @group Instances
   */
  case ConfigFileError extends SqlState("F0000")

  /**
   * SqlState `08003`
   * @group Instances
   */
  case ConnectionDoesNotExist extends SqlState("08003")

  /**
   * SqlState `08000`
   * @group Instances
   */
  case ConnectionException extends SqlState("08000")

  /**
   * SqlState `08006`
   * @group Instances
   */
  case ConnectionFailure extends SqlState("08006")

  /**
   * SqlState `38001`
   * @group Instances
   */
  case ContainingSqlNotPermitted extends SqlState("38001")

  /**
   * SqlState `57P02`
   * @group Instances
   */
  case CrashShutdown extends SqlState("57P02")

  /**
   * SqlState `XX001`
   * @group Instances
   */
  case DataCorrupted extends SqlState("XX001")

  /**
   * SqlState `22000`
   * @group Instances
   */
  case DataException extends SqlState("22000")

  /**
   * SqlState `57P04`
   * @group Instances
   */
  case DatabaseDropped extends SqlState("57P04")

  /**
   * SqlState `42804`
   * @group Instances
   */
  case DatatypeMismatch extends SqlState("42804")

  /**
   * SqlState `22008`
   * @group Instances
   */
  case DatetimeFieldOverflow extends SqlState("22008")

  /**
   * SqlState `40P01`
   * @group Instances
   */
  case DeadlockDetected extends SqlState("40P01")

  /**
   * SqlState `2BP01`
   * @group Instances
   */
  case DependentObjectsStillExist extends SqlState("2BP01")

  /**
   * SqlState `2B000`
   * @group Instances
   */
  case DependentPrivilegeDescriptorsStillExist extends SqlState("2B000")

  /**
   * SqlState `01P01`
   * @group Instances
   */
  case DeprecatedFeature extends SqlState("01P01")

  /**
   * SqlState `53100`
   * @group Instances
   */
  case DiskFull extends SqlState("53100")

  /**
   * SqlState `22012`
   * @group Instances
   */
  case DivisionByZero extends SqlState("22012")

  /**
   * SqlState `42712`
   * @group Instances
   */
  case DuplicateAlias extends SqlState("42712")

  /**
   * SqlState `42701`
   * @group Instances
   */
  case DuplicateColumn extends SqlState("42701")

  /**
   * SqlState `42P03`
   * @group Instances
   */
  case DuplicateCursor extends SqlState("42P03")

  /**
   * SqlState `42P04`
   * @group Instances
   */
  case DuplicateDatabase extends SqlState("42P04")

  /**
   * SqlState `58P02`
   * @group Instances
   */
  case DuplicateFile extends SqlState("58P02")

  /**
   * SqlState `42723`
   * @group Instances
   */
  case DuplicateFunction extends SqlState("42723")

  /**
   * SqlState `42710`
   * @group Instances
   */
  case DuplicateObject extends SqlState("42710")

  /**
   * SqlState `42P05`
   * @group Instances
   */
  case DuplicatePreparedStatement extends SqlState("42P05")

  /**
   * SqlState `42P06`
   * @group Instances
   */
  case DuplicateSchema extends SqlState("42P06")

  /**
   * SqlState `42P07`
   * @group Instances
   */
  case DuplicateTable extends SqlState("42P07")

  /**
   * SqlState `0100C`
   * @group Instances
   */
  case DynamicResultSetsReturned extends SqlState("0100C")

  /**
   * SqlState `22005`
   * @group Instances
   */
  case ErrorInAssignment extends SqlState("22005")

  /**
   * SqlState `2200B`
   * @group Instances
   */
  case EscapeCharacterConflict extends SqlState("2200B")

  /**
   * SqlState `23P01`
   * @group Instances
   */
  case ExclusionViolation extends SqlState("23P01")

  /**
   * SqlState `38000`
   * @group Instances
   */
  case ExternalRoutineException extends SqlState("38000")

  /**
   * SqlState `39000`
   * @group Instances
   */
  case ExternalRoutineInvocationException extends SqlState("39000")

  /**
   * SqlState `0A000`
   * @group Instances
   */
  case FeatureNotSupported extends SqlState("0A000")

  /**
   * SqlState `22P01`
   * @group Instances
   */
  case FloatingPointException extends SqlState("22P01")

  /**
   * SqlState `23503`
   * @group Instances
   */
  case ForeignKeyViolation extends SqlState("23503")

  /**
   * SqlState `2F005`
   * @group Instances
   */
  case FunctionExecutedNoReturnStatement extends SqlState("2F005")

  /**
   * SqlState `42803`
   * @group Instances
   */
  case GroupingError extends SqlState("42803")

  /**
   * SqlState `25008`
   * @group Instances
   */
  case HeldCursorRequiresSameIsolationLevel extends SqlState("25008")

  /**
   * SqlState `01008`
   * @group Instances
   */
  case ImplicitZeroBitPadding extends SqlState("01008")

  /**
   * SqlState `25P02`
   * @group Instances
   */
  case InFailedSqlTransaction extends SqlState("25P02")

  /**
   * SqlState `25003`
   * @group Instances
   */
  case InappropriateAccessModeForBranchTransaction extends SqlState("25003")

  /**
   * SqlState `25004`
   * @group Instances
   */
  case InappropriateIsolationLevelForBranchTransaction extends SqlState("25004")

  /**
   * SqlState `42P18`
   * @group Instances
   */
  case IndeterminateDatatype extends SqlState("42P18")

  /**
   * SqlState `XX002`
   * @group Instances
   */
  case IndexCorrupted extends SqlState("XX002")

  /**
   * SqlState `22022`
   * @group Instances
   */
  case IndicatorOverflow extends SqlState("22022")

  /**
   * SqlState `42501`
   * @group Instances
   */
  case InsufficientPrivilege extends SqlState("42501")

  /**
   * SqlState `53000`
   * @group Instances
   */
  case InsufficientResources extends SqlState("53000")

  /**
   * SqlState `23000`
   * @group Instances
   */
  case IntegrityConstraintViolation extends SqlState("23000")

  /**
   * SqlState `XX000`
   * @group Instances
   */
  case InternalError extends SqlState("XX000")

  /**
   * SqlState `22015`
   * @group Instances
   */
  case IntervalFieldOverflow extends SqlState("22015")

  /**
   * SqlState `2201E`
   * @group Instances
   */
  case InvalidArgumentForLogarithm extends SqlState("2201E")

  /**
   * SqlState `22016`
   * @group Instances
   */
  case InvalidArgumentForNthValueFunction extends SqlState("22016")

  /**
   * SqlState `22014`
   * @group Instances
   */
  case InvalidArgumentForNtileFunction extends SqlState("22014")

  /**
   * SqlState `2201F`
   * @group Instances
   */
  case InvalidArgumentForPowerFunction extends SqlState("2201F")

  /**
   * SqlState `2201G`
   * @group Instances
   */
  case InvalidArgumentForWidthBucketFunction extends SqlState("2201G")

  /**
   * SqlState `28000`
   * @group Instances
   */
  case InvalidAuthorizationSpecification extends SqlState("28000")

  /**
   * SqlState `22P03`
   * @group Instances
   */
  case InvalidBinaryRepresentation extends SqlState("22P03")

  /**
   * SqlState `3D000`
   * @group Instances
   */
  case InvalidCatalogName extends SqlState("3D000")

  /**
   * SqlState `22018`
   * @group Instances
   */
  case InvalidCharacterValueForCast extends SqlState("22018")

  /**
   * SqlState `42611`
   * @group Instances
   */
  case InvalidColumnDefinition extends SqlState("42611")

  /**
   * SqlState `42P10`
   * @group Instances
   */
  case InvalidColumnReference extends SqlState("42P10")

  /**
   * SqlState `42P11`
   * @group Instances
   */
  case InvalidCursorDefinition extends SqlState("42P11")

  /**
   * SqlState `34000`
   * @group Instances
   */
  case InvalidCursorName extends SqlState("34000")

  /**
   * SqlState `24000`
   * @group Instances
   */
  case InvalidCursorState extends SqlState("24000")

  /**
   * SqlState `42P12`
   * @group Instances
   */
  case InvalidDatabaseDefinition extends SqlState("42P12")

  /**
   * SqlState `22007`
   * @group Instances
   */
  case InvalidDatetimeFormat extends SqlState("22007")

  /**
   * SqlState `22019`
   * @group Instances
   */
  case InvalidEscapeCharacter extends SqlState("22019")

  /**
   * SqlState `2200D`
   * @group Instances
   */
  case InvalidEscapeOctet extends SqlState("2200D")

  /**
   * SqlState `22025`
   * @group Instances
   */
  case InvalidEscapeSequence extends SqlState("22025")

  /**
   * SqlState `42830`
   * @group Instances
   */
  case InvalidForeignKey extends SqlState("42830")

  /**
   * SqlState `42P13`
   * @group Instances
   */
  case InvalidFunctionDefinition extends SqlState("42P13")

  /**
   * SqlState `0LP01`
   * @group Instances
   */
  case InvalidGrantOperation extends SqlState("0LP01")

  /**
   * SqlState `0L000`
   * @group Instances
   */
  case InvalidGrantor extends SqlState("0L000")

  /**
   * SqlState `22010`
   * @group Instances
   */
  case InvalidIndicatorParameterValue extends SqlState("22010")

  /**
   * SqlState `0F001`
   * @group Instances
   */
  case InvalidLocatorSpecification extends SqlState("0F001")

  /**
   * SqlState `42602`
   * @group Instances
   */
  case InvalidName extends SqlState("42602")

  /**
   * SqlState `42P17`
   * @group Instances
   */
  case InvalidObjectDefinition extends SqlState("42P17")

  /**
   * SqlState `22023`
   * @group Instances
   */
  case InvalidParameterValue extends SqlState("22023")

  /**
   * SqlState `28P01`
   * @group Instances
   */
  case InvalidPassword extends SqlState("28P01")

  /**
   * SqlState `42P14`
   * @group Instances
   */
  case InvalidPreparedStatementDefinition extends SqlState("42P14")

  /**
   * SqlState `42P19`
   * @group Instances
   */
  case InvalidRecursion extends SqlState("42P19")

  /**
   * SqlState `2201B`
   * @group Instances
   */
  case InvalidRegularExpression extends SqlState("2201B")

  /**
   * SqlState `0P000`
   * @group Instances
   */
  case InvalidRoleSpecification extends SqlState("0P000")

  /**
   * SqlState `2201W`
   * @group Instances
   */
  case InvalidRowCountInLimitClause extends SqlState("2201W")

  /**
   * SqlState `2201X`
   * @group Instances
   */
  case InvalidRowCountInResultOffsetClause extends SqlState("2201X")

  /**
   * SqlState `3B001`
   * @group Instances
   */
  case InvalidSavepointSpecification extends SqlState("3B001")

  /**
   * SqlState `42P15`
   * @group Instances
   */
  case InvalidSchemaDefinition extends SqlState("42P15")

  /**
   * SqlState `3F000`
   * @group Instances
   */
  case InvalidSchemaName extends SqlState("3F000")

  /**
   * SqlState `26000`
   * @group Instances
   */
  case InvalidSqlStatementName extends SqlState("26000")

  /**
   * SqlState `39001`
   * @group Instances
   */
  case InvalidSqlstateReturned extends SqlState("39001")

  /**
   * SqlState `42P16`
   * @group Instances
   */
  case InvalidTableDefinition extends SqlState("42P16")

  /**
   * SqlState `22P02`
   * @group Instances
   */
  case InvalidTextRepresentation extends SqlState("22P02")

  /**
   * SqlState `22009`
   * @group Instances
   */
  case InvalidTimeZoneDisplacementValue extends SqlState("22009")

  /**
   * SqlState `0B000`
   * @group Instances
   */
  case InvalidTransactionInitiation extends SqlState("0B000")

  /**
   * SqlState `25000`
   * @group Instances
   */
  case InvalidTransactionState extends SqlState("25000")

  /**
   * SqlState `2D000`
   * @group Instances
   */
  case InvalidTransactionTermination extends SqlState("2D000")

  /**
   * SqlState `2200C`
   * @group Instances
   */
  case InvalidUseOfEscapeCharacter extends SqlState("2200C")

  /**
   * SqlState `2200S`
   * @group Instances
   */
  case InvalidXmlComment extends SqlState("2200S")

  /**
   * SqlState `2200N`
   * @group Instances
   */
  case InvalidXmlContent extends SqlState("2200N")

  /**
   * SqlState `2200M`
   * @group Instances
   */
  case InvalidXmlDocument extends SqlState("2200M")

  /**
   * SqlState `2200T`
   * @group Instances
   */
  case InvalidXmlProcessingInstruction extends SqlState("2200T")

  /**
   * SqlState `58030`
   * @group Instances
   */
  case IoError extends SqlState("58030")

  /**
   * SqlState `0F000`
   * @group Instances
   */
  case LocatorException extends SqlState("0F000")

  /**
   * SqlState `F0001`
   * @group Instances
   */
  case LockFileExists extends SqlState("F0001")

  /**
   * SqlState `55P03`
   * @group Instances
   */
  case LockNotAvailable extends SqlState("55P03")

  /**
   * SqlState `2F002`
   * @group Instances
   */
  case ModifyingSqlDataNotPermitted2F extends SqlState("2F002")

  /**
   * SqlState `38002`
   * @group Instances
   */
  case ModifyingSqlDataNotPermitted38 extends SqlState("38002")

  /**
   * SqlState `2200G`
   * @group Instances
   */
  case MostSpecificTypeMismatch extends SqlState("2200G")

  /**
   * SqlState `42622`
   * @group Instances
   */
  case NameTooLong extends SqlState("42622")

  /**
   * SqlState `25P01`
   * @group Instances
   */
  case NoActiveSqlTransaction extends SqlState("25P01")

  /**
   * SqlState `25005`
   * @group Instances
   */
  case NoActiveSqlTransactionForBranchTransaction extends SqlState("25005")

  /**
   * SqlState `02001`
   * @group Instances
   */
  case NoAdditionalDynamicResultSetsReturned extends SqlState("02001")

  /**
   * SqlState `02000`
   * @group Instances
   */
  case NoData extends SqlState("02000")

  /**
   * SqlState `P0002`
   * @group Instances
   */
  case NoDataFound extends SqlState("P0002")

  /**
   * SqlState `22P06`
   * @group Instances
   */
  case NonstandardUseOfEscapeCharacter extends SqlState("22P06")

  /**
   * SqlState `2200L`
   * @group Instances
   */
  case NotAnXmlDocument extends SqlState("2200L")

  /**
   * SqlState `23502`
   * @group Instances
   */
  case NotNullViolation extends SqlState("23502")

  /**
   * SqlState `01003`
   * @group Instances
   */
  case NullValueEliminatedInSetFunction extends SqlState("01003")

  /**
   * SqlState `22002`
   * @group Instances
   */
  case NullValueNoIndicatorParameter extends SqlState("22002")

  /**
   * SqlState `22004`
   * @group Instances
   */
  case NullValueNotAllowed extends SqlState("22004")

  /**
   * SqlState `39004`
   * @group Instances
   */
  case NullValueNotAllowed39 extends SqlState("39004")

  /**
   * SqlState `22003`
   * @group Instances
   */
  case NumericValueOutOfRange extends SqlState("22003")

  /**
   * SqlState `55006`
   * @group Instances
   */
  case ObjectInUse extends SqlState("55006")

  /**
   * SqlState `55000`
   * @group Instances
   */
  case ObjectNotInPrerequisiteState extends SqlState("55000")

  /**
   * SqlState `57000`
   * @group Instances
   */
  case OperatorIntervention extends SqlState("57000")

  /**
   * SqlState `53200`
   * @group Instances
   */
  case OutOfMemory extends SqlState("53200")

  /**
   * SqlState `P0000`
   * @group Instances
   */
  case PlpgsqlError extends SqlState("P0000")

  /**
   * SqlState `01007`
   * @group Instances
   */
  case PrivilegeNotGranted extends SqlState("01007")

  /**
   * SqlState `01006`
   * @group Instances
   */
  case PrivilegeNotRevoked extends SqlState("01006")

  /**
   * SqlState `54000`
   * @group Instances
   */
  case ProgramLimitExceeded extends SqlState("54000")

  /**
   * SqlState `2F003`
   * @group Instances
   */
  case ProhibitedSqlStatementAttempted2F extends SqlState("2F003")

  /**
   * SqlState `38003`
   * @group Instances
   */
  case ProhibitedSqlStatementAttempted38 extends SqlState("38003")

  /**
   * SqlState `08P01`
   * @group Instances
   */
  case ProtocolViolation extends SqlState("08P01")

  /**
   * SqlState `57014`
   * @group Instances
   */
  case QueryCanceled extends SqlState("57014")

  /**
   * SqlState `P0001`
   * @group Instances
   */
  case RaiseException extends SqlState("P0001")

  /**
   * SqlState `25006`
   * @group Instances
   */
  case ReadOnlySqlTransaction extends SqlState("25006")

  /**
   * SqlState `2F004`
   * @group Instances
   */
  case ReadingSqlDataNotPermitted2F extends SqlState("2F004")

  /**
   * SqlState `38004`
   * @group Instances
   */
  case ReadingSqlDataNotPermitted38 extends SqlState("38004")

  /**
   * SqlState `42939`
   * @group Instances
   */
  case ReservedName extends SqlState("42939")

  /**
   * SqlState `23001`
   * @group Instances
   */
  case RestrictViolation extends SqlState("23001")

  /**
   * SqlState `3B000`
   * @group Instances
   */
  case SavepointException extends SqlState("3B000")

  /**
   * SqlState `25007`
   * @group Instances
   */
  case SchemaAndDataStatementMixingNotSupported extends SqlState("25007")

  /**
   * SqlState `40001`
   * @group Instances
   */
  case SerializationFailure extends SqlState("40001")

  /**
   * SqlState `08001`
   * @group Instances
   */
  case SqlClientUnableToEstablishSqlConnection extends SqlState("08001")

  /**
   * SqlState `2F000`
   * @group Instances
   */
  case SqlRoutineException extends SqlState("2F000")

  /**
   * SqlState `08004`
   * @group Instances
   */
  case SqlServerRejectedEstablishmentOfSqlConnection extends SqlState("08004")

  /**
   * SqlState `03000`
   * @group Instances
   */
  case SqlStatementNotYetComplete extends SqlState("03000")

  /**
   * SqlState `39P02`
   * @group Instances
   */
  case SrfProtocolViolated extends SqlState("39P02")

  /**
   * SqlState `40003`
   * @group Instances
   */
  case StatementCompletionUnknown extends SqlState("40003")

  /**
   * SqlState `54001`
   * @group Instances
   */
  case StatementTooComplex extends SqlState("54001")

  /**
   * SqlState `22026`
   * @group Instances
   */
  case StringDataLengthMismatch extends SqlState("22026")

  /**
   * SqlState `22001`
   * @group Instances
   */
  case StringDataRightTruncation extends SqlState("22001")

  /**
   * SqlState `01004`
   * @group Instances
   */
  case StringDataRightTruncation01 extends SqlState("01004")

  /**
   * SqlState `22011`
   * @group Instances
   */
  case SubstringError extends SqlState("22011")

  /**
   * SqlState `00000`
   * @group Instances
   */
  case SuccessfulCompletion extends SqlState("00000")

  /**
   * SqlState `42601`
   * @group Instances
   */
  case SyntaxError extends SqlState("42601")

  /**
   * SqlState `42000`
   * @group Instances
   */
  case SyntaxErrorOrAccessRuleViolation extends SqlState("42000")

  /**
   * SqlState `54023`
   * @group Instances
   */
  case TooManyArguments extends SqlState("54023")

  /**
   * SqlState `54011`
   * @group Instances
   */
  case TooManyColumns extends SqlState("54011")

  /**
   * SqlState `53300`
   * @group Instances
   */
  case TooManyConnections extends SqlState("53300")

  /**
   * SqlState `P0003`
   * @group Instances
   */
  case TooManyRows extends SqlState("P0003")

  /**
   * SqlState `40002`
   * @group Instances
   */
  case TransactionIntegrityConstraintViolation extends SqlState("40002")

  /**
   * SqlState `08007`
   * @group Instances
   */
  case TransactionResolutionUnknown extends SqlState("08007")

  /**
   * SqlState `40000`
   * @group Instances
   */
  case TransactionRollback extends SqlState("40000")

  /**
   * SqlState `39P01`
   * @group Instances
   */
  case TriggerProtocolViolated extends SqlState("39P01")

  /**
   * SqlState `09000`
   * @group Instances
   */
  case TriggeredActionException extends SqlState("09000")

  /**
   * SqlState `27000`
   * @group Instances
   */
  case TriggeredDataChangeViolation extends SqlState("27000")

  /**
   * SqlState `22027`
   * @group Instances
   */
  case TrimError extends SqlState("22027")

  /**
   * SqlState `42703`
   * @group Instances
   */
  case UndefinedColumn extends SqlState("42703")

  /**
   * SqlState `58P01`
   * @group Instances
   */
  case UndefinedFile extends SqlState("58P01")

  /**
   * SqlState `42883`
   * @group Instances
   */
  case UndefinedFunction extends SqlState("42883")

  /**
   * SqlState `42704`
   * @group Instances
   */
  case UndefinedObject extends SqlState("42704")

  /**
   * SqlState `42P02`
   * @group Instances
   */
  case UndefinedParameter extends SqlState("42P02")

  /**
   * SqlState `42P01`
   * @group Instances
   */
  case UndefinedTable extends SqlState("42P01")

  /**
   * SqlState `23505`
   * @group Instances
   */
  case UniqueViolation extends SqlState("23505")

  /**
   * SqlState `22024`
   * @group Instances
   */
  case UnterminatedCString extends SqlState("22024")

  /**
   * SqlState `22P05`
   * @group Instances
   */
  case UntranslatableCharacter extends SqlState("22P05")

  /**
   * SqlState `01000`
   * @group Instances
   */
  case Warning extends SqlState("01000")

  /**
   * SqlState `42P20`
   * @group Instances
   */
  case WindowingError extends SqlState("42P20")

  /**
   * SqlState `44000`
   * @group Instances
   */
  case WithCheckOptionViolation extends SqlState("44000")

  /**
   * SqlState `42809`
   * @group Instances
   */
  case WrongObjectType extends SqlState("42809")

  /**
   * SqlState `2200F`
   * @group Instances
   */
  case ZeroLengthCharacterString extends SqlState("2200F")

}