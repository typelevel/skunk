// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package mdoc

import java.io.{ ByteArrayOutputStream, PrintStream }
import java.nio.file.Path
import metaconfig.Configured
import scala.meta.internal.io.PathIO
import scala.meta.io.AbsolutePath
import mdoc.internal.cli.MainOps
import mdoc.internal.cli.Settings
import mdoc.internal.io.ConsoleReporter
import mdoc.internal.markdown.Markdown
import mdoc.internal.markdown.Renderer
import pprint.{ PPrinter, Tree }
import skunk._

// copy-pasted from mdoc source, adding custom variablePrinter
object Main {

  lazy val custom: PPrinter =
    PPrinter.BlackWhite.copy(
      additionalHandlers = {

        case f: Fragment[_] =>
          Tree.Apply("Fragment", Iterator(
            Tree.KeyValue("sql", custom.treeify(f.sql)),
            Tree.KeyValue("encoder", custom.treeify(f.encoder)),
          ))

        case q: Query[_, _] =>
          Tree.Apply("Query", Iterator(
            Tree.KeyValue("sql", custom.treeify(q.sql)),
            Tree.KeyValue("encoder", custom.treeify(q.encoder)),
            Tree.KeyValue("decoder", custom.treeify(q.decoder)),
          ))

        case c: Command[_] =>
          Tree.Apply("Query", Iterator(
            Tree.KeyValue("sql", custom.treeify(c.sql)),
            Tree.KeyValue("encoder", custom.treeify(c.encoder)),
          ))
      }
    )

  def main(args: Array[String]): Unit = {
    val code = process(args, System.out, PathIO.workingDirectory.toNIO)
    if (code != 0) sys.exit(code)
  }

  def process(args: Array[String], out: PrintStream, cwd: Path): Int = {
    process(args, new ConsoleReporter(out), cwd)
  }

  def process(args: Array[String], reporter: Reporter, cwd: Path): Int = {
    val base = Settings.default(AbsolutePath(cwd)).copy(variablePrinter = ReplVariablePrinter(pprinter = custom))
    val ctx  = Settings.fromCliArgs(args.toList, base)
    MainOps.process(ctx, reporter)
  }

}

// copy-pasted from mdoc source, adding `pprinter` as an arg
case class ReplVariablePrinter(
    leadingNewline: Boolean   = true,
    width:    Int             = 80,
    height:   Int             = 80,
    indent:   Int             = 2,
    pprinter: PPrinter        = PPrinter.BlackWhite,
) extends (_root_.mdoc.Variable => String) {

  override def apply(binder: Variable): String = {
    if (binder.isUnit) ""
    else {
      val baos = new ByteArrayOutputStream()
      val sb = new PrintStream(baos)
      if (leadingNewline) {
        sb.append('\n')
      }
      sb.append("// ")
        .append(binder.name)
        .append(": ")
        .append(binder.staticType)
        .append(" = ")
      if (binder.isToString) {
        Renderer.appendMultiline(sb, binder.runtimeValue.toString)
      } else {
        val heightOverride = binder.mods.heightOverride
        val widthOverride = binder.mods.widthOverride

        val lines = pprinter.tokenize(
          binder.runtimeValue,
          width = widthOverride.getOrElse(width),
          height = heightOverride.getOrElse(height),
          indent = 2,
          initialOffset = baos.size()
        )
        lines.foreach { lineStr =>
          val line = lineStr.plainText
          Renderer.appendMultiline(sb, line)
        }
      }
      baos.toString()
    }
  }

}
