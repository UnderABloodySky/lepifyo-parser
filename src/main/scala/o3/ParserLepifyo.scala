package o3

import o3.ParserLepifyo.{MissingFunctionError, ParseError}

import scala.util.parsing.combinator._

case class ParserLepifyo[TPrograma, TExpresion](
   programa: List[TExpresion] => TPrograma = { throw MissingFunctionError("programa") },
   numero: Int => TExpresion = { throw MissingFunctionError("numero") },
   booleano: Boolean => TExpresion = { throw MissingFunctionError("booleano") },
   string: String => TExpresion = { throw MissingFunctionError("string") },
   suma: (TExpresion, TExpresion) => TExpresion = { throw MissingFunctionError("suma") },
   resta: (TExpresion, TExpresion) => TExpresion = { throw MissingFunctionError("resta") },
   multiplicacion: (TExpresion, TExpresion) => TExpresion = { throw MissingFunctionError("multiplicacion") },
   division: (TExpresion, TExpresion) => TExpresion = { throw MissingFunctionError("division") },
   igual: (TExpresion, TExpresion) => TExpresion = { throw MissingFunctionError("igual") },
   distinto: (TExpresion, TExpresion) => TExpresion = { throw MissingFunctionError("distinto") },
   mayor: (TExpresion, TExpresion) => TExpresion = { throw MissingFunctionError("mayor") },
   mayorIgual: (TExpresion, TExpresion) => TExpresion = { throw MissingFunctionError("mayorIgual") },
   menor: (TExpresion, TExpresion) => TExpresion = { throw MissingFunctionError("menor") },
   menorIgual: (TExpresion, TExpresion) => TExpresion = { throw MissingFunctionError("menorIgual") },
   declaracionVariable: (String, TExpresion) => TExpresion = { throw MissingFunctionError("declaracionVariable") },
   variable: String => TExpresion = { throw MissingFunctionError("variable") },
   asignacion: (String, TExpresion) => TExpresion = { throw MissingFunctionError("asignacion") },
   concatenacion: (TExpresion, TExpresion) => TExpresion = { throw MissingFunctionError("concatenacion") },
   printLn: TExpresion => TExpresion = { throw MissingFunctionError("printLn") },
   promptString: TExpresion => TExpresion = { throw MissingFunctionError("promptString") },
   promptInt: TExpresion => TExpresion = { throw MissingFunctionError("promptInt") },
   promptBool: TExpresion => TExpresion = { throw MissingFunctionError("promptBool") },
   si: (TExpresion, List[TExpresion], List[TExpresion]) => TExpresion = { throw MissingFunctionError("si") }
 ) extends RegexParsers {
  private val funciones = Map(
    "PrintLn" -> printLn,
    "PromptInt" -> promptInt,
    "PromptBool" -> promptBool,
    "PromptString" -> promptString,
  )

  def parsear(textoPrograma: String): TPrograma = {
    def parserNumero: Parser[TExpresion] = """[0-9]+""".r ^^ { n => numero(n.toInt) }
    def parserBooleano: Parser[TExpresion] = "true" ^^^ booleano(true) | "false" ^^^ booleano(false)
    def parserString: Parser[TExpresion] = """"\s*""".r ~ """(\\\\|\\"|[^"])*""".r <~ "\"" ^^ {
      // Consumir los espacios del inicio (con la primera regex) es necesario porque si usáramos ~> descartaría
      // los espacios al inicio del string
      case inicioConEspacios ~ restoDelString =>
        string(
          (inicioConEspacios.drop(1) + restoDelString)
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
        )
    }

    def parserIdentificador: Parser[String] = """[_a-z][_a-zA-Z0-9]*""".r
    def parserVariable: Parser[TExpresion] = parserIdentificador ^^ variable

    def parserFuncion(nombre: String, funcion: TExpresion => TExpresion) = nombre ~> "(" ~> parserExpresion <~ ")" ^^ funcion
    def parserFunciones: Parser[TExpresion] = funciones.map((parserFuncion _).tupled).reduce(_ | _)

    def parserFactor: Parser[TExpresion] =  parserFunciones | parserString | parserNumero | parserBooleano | parserVariable | "(" ~> parserExpresion <~ ")"

    def parserTermino = chainl1(parserFactor, "*" ^^^ multiplicacion | "/" ^^^ division)

    def parserMiembros = chainl1(parserTermino, "+" ^^^ suma | "-" ^^^ resta)

    def parserConcatenacion = chainl1(parserMiembros, "," ^^^ concatenacion)

    def parserMiembroDesigualdad = chainl1(parserConcatenacion,
      ">=" ^^^ mayorIgual |
      "<=" ^^^ menorIgual |
      ">" ^^^ mayor |
      "<" ^^^ menor
    )
    def parserExpresion = parserIf | chainl1(parserMiembroDesigualdad, "==" ^^^ igual | "!=" ^^^ distinto)
    def parserDeclaracionVariables = ("let " ~> parserIdentificador <~ "=") ~ parserExpresion ^^ {
      case identificador ~ expresion => declaracionVariable(identificador, expresion)
    }
    def parserAsignacion = (parserIdentificador <~ "=") ~ parserExpresion ^^ {
      case identificador ~ expresion => asignacion(identificador, expresion)
    }

    def parserInstruccion = parserDeclaracionVariables | parserAsignacion | parserExpresion
    def parserBloque = "{" ~> parserInstruccion.* <~ "}" | (parserInstruccion ^^ { List(_) })

    def parserIf: Parser[TExpresion] = ("if" ~> "(" ~> parserExpresion <~ ")" <~ "then") ~ parserBloque ~ ("else" ~> parserBloque).? ^^ {
      case cond ~ pos ~ neg => si(cond, pos, neg.getOrElse(List()))
    }

    def parserPrograma = parserInstruccion.* ^^ programa

    parseAll(parserPrograma, textoPrograma) match {
      case Success(matched, _) => matched
      case Failure(message, rest) => throw ParseError(s"$message: ${rest.source}")
      case Error(message, rest) => throw ParseError(s"$message: ${rest.source}")
    }
  }
}

object ParserLepifyo {
  case class ParseError(message: String) extends RuntimeException(message)
  case class MissingFunctionError(fn: String) extends RuntimeException("Falta especificar una función para: "+fn)
}