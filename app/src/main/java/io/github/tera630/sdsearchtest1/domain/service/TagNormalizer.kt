package io.github.tera630.sdsearchtest1.domain.service
import java.text.Normalizer

object TagNormalizer {
    fun nfkc(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFKC).trim()
}