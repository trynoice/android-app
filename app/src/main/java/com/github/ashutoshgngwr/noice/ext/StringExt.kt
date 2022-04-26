package com.github.ashutoshgngwr.noice.ext

private val duplicateWhiteSpaceRegex = """\s+""".toRegex()

/**
 * Removes duplicate white space characters from a [String].
 */
fun String.normalizeSpace(): String {
  return this.replace(duplicateWhiteSpaceRegex, " ")
}
