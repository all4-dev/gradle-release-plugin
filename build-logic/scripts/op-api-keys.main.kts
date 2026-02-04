#!/usr/bin/env kotlin

import java.io.File
import kotlin.system.exitProcess

private enum class OutputFormat(val cliValue: String) {
  ENV("env"),
  PROPERTIES("properties"),
  JSON("json");
}

private fun parseOutputFormat(raw: String): OutputFormat {
  return OutputFormat.entries.firstOrNull { it.cliValue == raw }
    ?: fail("Formato invalido '$raw'. Usa: env | properties | json")
}

private data class SecretMapping(
  val keyName: String,
  val secretRef: String,
)

private data class Config(
  val mappings: List<SecretMapping>,
  val format: OutputFormat,
  val outputFile: String?,
)

private fun fail(message: String): Nothing {
  System.err.println("error: $message")
  exitProcess(1)
}

private fun usage(): String = """
1Password API Keys - Obtiene secretos desde 1Password y los exporta en diferentes formatos

REQUISITOS:
  - 1Password CLI instalado: https://developer.1password.com/docs/cli/get-started#install
  - Autenticación activa: op signin

USO:
  ./op-api-keys.main.kts [opciones] KEY=op://vault/item/field ...

OPCIONES:
  --format <formato>     Formato de salida: env, properties, json (default: env)
  --output <archivo>     Guardar en archivo en vez de stdout
  --from-file <archivo>  Leer mappings desde archivo
  -h, --help            Mostrar esta ayuda

EJEMPLOS:
  # Exportar como variables de entorno
  ./op-api-keys.main.kts OPENAI_API_KEY=op://Engineering/OpenAI/api_key

  # Guardar en archivo properties
  ./op-api-keys.main.kts --format properties --output .secrets/api-keys.properties \\
    GITHUB_TOKEN=op://Engineering/GitHub/token \\
    NPM_TOKEN=op://Engineering/NPM/token

  # Usar archivo de configuración
  ./op-api-keys.main.kts --from-file .secrets/op-refs.txt --format json

FORMATO DE --from-file:
  # Comentarios con '#'
  OPENAI_API_KEY=op://Engineering/OpenAI/api_key
  GITHUB_TOKEN=op://Engineering/GitHub/token

  # Las líneas vacías se ignoran
  NPM_TOKEN=op://Engineering/NPM/token

USO CON GRADLE:
  # Exportar y cargar en el shell actual
  eval "$(./op-api-keys.main.kts GRADLE_KEY=op://vault/item/field)"
  ./gradlew publish

NOTAS:
  - Los nombres de variables deben seguir el formato: [A-Za-z_][A-Za-z0-9_]*
  - Las referencias deben iniciar con 'op://'
  - No se permiten variables duplicadas
""".trimIndent()

private fun parseMapping(raw: String): SecretMapping {
  val separatorIndex = raw.indexOf('=')
  if (separatorIndex <= 0 || separatorIndex == raw.lastIndex) {
    fail("Mapping invalido '$raw'. Usa KEY=op://vault/item/field")
  }

  val keyName = raw.substring(0, separatorIndex).trim()
  val secretRef = raw.substring(separatorIndex + 1).trim()

  if (!keyName.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) {
    fail("Nombre de variable invalido '$keyName'. Usa [A-Za-z_][A-Za-z0-9_]*")
  }
  if (!secretRef.startsWith("op://")) {
    fail("Referencia invalida '$secretRef'. Debe iniciar con op://")
  }

  return SecretMapping(keyName = keyName, secretRef = secretRef)
}

private fun loadMappingsFromFile(path: String): List<SecretMapping> {
  val file = File(path)
  if (!file.exists()) {
    fail("No existe --from-file: $path")
  }

  return file.readLines()
    .map(String::trim)
    .filter { it.isNotEmpty() && !it.startsWith('#') }
    .map(::parseMapping)
}

private fun parseArgs(args: Array<String>): Config {
  if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
    println(usage())
    exitProcess(0)
  }

  val mappings = mutableListOf<SecretMapping>()
  var format = OutputFormat.ENV
  var outputFile: String? = null

  var index = 0
  while (index < args.size) {
    val arg = args[index]
    when {
      arg == "--format" -> {
        val value = args.getOrNull(index + 1) ?: fail("Falta valor para --format")
        format = parseOutputFormat(value)
        index += 2
      }
      arg.startsWith("--format=") -> {
        format = parseOutputFormat(arg.substringAfter("="))
        index += 1
      }
      arg == "--output" -> {
        outputFile = args.getOrNull(index + 1) ?: fail("Falta valor para --output")
        index += 2
      }
      arg.startsWith("--output=") -> {
        outputFile = arg.substringAfter("=")
        index += 1
      }
      arg == "--from-file" -> {
        val filePath = args.getOrNull(index + 1) ?: fail("Falta valor para --from-file")
        mappings += loadMappingsFromFile(filePath)
        index += 2
      }
      arg.startsWith("--from-file=") -> {
        val filePath = arg.substringAfter("=")
        mappings += loadMappingsFromFile(filePath)
        index += 1
      }
      arg.startsWith("--") -> fail("Opcion no soportada: $arg")
      else -> {
        mappings += parseMapping(arg)
        index += 1
      }
    }
  }

  if (mappings.isEmpty()) {
    fail("No hay mappings. Usa argumentos KEY=op://... o --from-file")
  }

  val duplicatedKeys = mappings.groupBy { it.keyName }.filterValues { it.size > 1 }.keys
  if (duplicatedKeys.isNotEmpty()) {
    fail("Hay variables repetidas: ${duplicatedKeys.joinToString(", ")}")
  }

  return Config(
    mappings = mappings,
    format = format,
    outputFile = outputFile,
  )
}

private fun findOpCli(): String {
  // Intenta encontrar 'op' en PATH
  val pathLocations = listOf("op", "/usr/local/bin/op", "/opt/homebrew/bin/op")

  for (location in pathLocations) {
    try {
      val process = ProcessBuilder(location, "--version")
        .redirectErrorStream(true)
        .start()

      val exitCode = process.waitFor()
      if (exitCode == 0) {
        return location
      }
    } catch (e: Exception) {
      // Continuar con la siguiente ubicación
    }
  }

  fail("""
    No se encontró 1Password CLI ('op').

    Instalación:
      macOS:   brew install 1password-cli
      Linux:   https://developer.1password.com/docs/cli/get-started#install
      Windows: https://developer.1password.com/docs/cli/get-started#install

    Después de instalar, autentícate con: op signin
  """.trimIndent())
}

private fun readSecretFromOnePassword(opCli: String, secretRef: String): String {
  val process = ProcessBuilder(opCli, "read", secretRef)
    .redirectErrorStream(true)
    .start()

  val output = process.inputStream.bufferedReader().use { it.readText() }
  val exitCode = process.waitFor()
  if (exitCode != 0) {
    val opOutput = output.trim()
    fail("No se pudo leer '$secretRef' con 1Password CLI. Detalle: $opOutput")
  }

  return output.trimEnd('\n', '\r')
}

private fun escapeForShell(value: String): String = buildString {
  append('\'')
  value.forEach { char ->
    if (char == '\'') {
      append("'\"'\"'")
    } else {
      append(char)
    }
  }
  append('\'')
}

private fun escapeForProperties(value: String): String = buildString {
  value.forEach { char ->
    when (char) {
      '\\' -> append("\\\\")
      '\n' -> append("\\n")
      '\r' -> append("\\r")
      '\t' -> append("\\t")
      else -> append(char)
    }
  }
}

private fun escapeForJson(value: String): String = buildString {
  value.forEach { char ->
    when (char) {
      '\\' -> append("\\\\")
      '"' -> append("\\\"")
      '\b' -> append("\\b")
      '\u000C' -> append("\\f")
      '\n' -> append("\\n")
      '\r' -> append("\\r")
      '\t' -> append("\\t")
      else -> {
        if (char.code in 0..31) {
          append("\\u%04x".format(char.code))
        } else {
          append(char)
        }
      }
    }
  }
}

private fun renderOutput(format: OutputFormat, values: Map<String, String>): String {
  return when (format) {
    OutputFormat.ENV -> values.entries.joinToString("\n") { (key, value) ->
      "export $key=${escapeForShell(value)}"
    }
    OutputFormat.PROPERTIES -> values.entries.joinToString("\n") { (key, value) ->
      "$key=${escapeForProperties(value)}"
    }
    OutputFormat.JSON -> {
      val items = values.entries.joinToString(",\n") { (key, value) ->
        "  \"${escapeForJson(key)}\": \"${escapeForJson(value)}\""
      }
      "{\n$items\n}"
    }
  }
}

run {
  val config = parseArgs(args)
  val opCli = findOpCli()

  val resolvedValues = config.mappings.associate { mapping ->
    mapping.keyName to readSecretFromOnePassword(opCli, mapping.secretRef)
  }

  val rendered = renderOutput(config.format, resolvedValues)
  val outputFile = config.outputFile
  if (outputFile == null) {
    println(rendered)
  } else {
    File(outputFile).apply {
      parentFile?.mkdirs()
      writeText("$rendered\n")
    }
    println("Escrito: $outputFile")
  }
}
