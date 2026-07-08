package com.blissless.tensei.torrent

import com.google.common.truth.Truth.assertThat
import eu.kanade.tachiyomi.animesource.model.Track
import org.junit.Test

/**
 * Unit tests for [parseStreamUrlResult].
 *
 * These tests pin down the JSON shape that magnet extensions (currently the
 * MegaPlay / Tensei extension) return from their stream endpoint, including
 * the SUB/DUB `streams` array we recently added support for.
 *
 * Why these tests exist:
 *  - The parser is the boundary between untrusted extension output and the
 *    player. A regression here means the player silently gets a null stream
 *    and falls back to error UI.
 *  - The previous implementation was inlined inside `MagnetExtensionClient`
 *    which required an Android Context to test; extracting it as a pure
 *    function made the boundary testable.
 */
class ParseStreamUrlResultTest {

    // ─── Happy path ───────────────────────────────────────────────────────

    @Test
    fun `minimal response with only url returns result with empty defaults`() {
        val json = """{"url":"https://example.com/stream.m3u8"}"""

        val result = parseStreamUrlResult(json)

        assertThat(result).isNotNull()
        assertThat(result!!.url).isEqualTo("https://example.com/stream.m3u8")
        assertThat(result.headers).isEmpty()
        assertThat(result.subtitles).isEmpty()
        assertThat(result.streams).isEmpty()
    }

    @Test
    fun `full top-level response parses url headers and subtitles`() {
        val json = """
            {
              "url": "https://example.com/stream.m3u8",
              "headers": {
                "Referer": "https://megacloud.tv",
                "User-Agent": "Mozilla/5.0"
              },
              "subtitles": [
                {"url": "https://example.com/en.vtt", "label": "English"},
                {"url": "https://example.com/es.vtt", "label": "Spanish"}
              ]
            }
        """.trimIndent()

        val result = parseStreamUrlResult(json)

        assertThat(result).isNotNull()
        assertThat(result!!.url).isEqualTo("https://example.com/stream.m3u8")
        assertThat(result.headers).containsExactly(
            "Referer" to "https://megacloud.tv",
            "User-Agent" to "Mozilla/5.0"
        )
        assertThat(result.subtitles).containsExactly(
            Track("https://example.com/en.vtt", "English"),
            Track("https://example.com/es.vtt", "Spanish")
        ).inOrder()
        assertThat(result.streams).isEmpty()
    }

    // ─── SUB/DUB streams array ────────────────────────────────────────────

    @Test
    fun `streams array with sub and dub parses all entries preserving order`() {
        val json = """
            {
              "url": "https://example.com/sub.m3u8",
              "streams": [
                {"url": "https://example.com/sub.m3u8", "lang": "ja-JP", "default": true},
                {"url": "https://example.com/dub.m3u8", "lang": "en-US", "default": false}
              ]
            }
        """.trimIndent()

        val result = parseStreamUrlResult(json)

        assertThat(result).isNotNull()
        assertThat(result!!.streams).hasSize(2)

        val sub = result.streams[0]
        assertThat(sub.url).isEqualTo("https://example.com/sub.m3u8")
        assertThat(sub.lang).isEqualTo("ja-JP")
        assertThat(sub.isDefault).isTrue()

        val dub = result.streams[1]
        assertThat(dub.url).isEqualTo("https://example.com/dub.m3u8")
        assertThat(dub.lang).isEqualTo("en-US")
        assertThat(dub.isDefault).isFalse()
    }

    @Test
    fun `stream entries carry their own headers and subtitles`() {
        val json = """
            {
              "url": "https://example.com/main.m3u8",
              "streams": [
                {
                  "url": "https://example.com/dub.m3u8",
                  "lang": "en-US",
                  "default": false,
                  "headers": {"Referer": "https://dub.example.com"},
                  "subtitles": [
                    {"url": "https://example.com/dub-sdh.vtt", "label": "English SDH"}
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = parseStreamUrlResult(json)

        assertThat(result).isNotNull()
        val stream = result!!.streams.single()
        assertThat(stream.headers).containsExactly("Referer" to "https://dub.example.com")
        assertThat(stream.subtitles).containsExactly(
            Track("https://example.com/dub-sdh.vtt", "English SDH")
        )
    }

    @Test
    fun `stream entry missing url is silently skipped`() {
        val json = """
            {
              "url": "https://example.com/main.m3u8",
              "streams": [
                {"url": "https://example.com/valid.m3u8", "lang": "ja-JP"},
                {"lang": "en-US"},
                {"url": "https://example.com/also-valid.m3u8", "lang": "es-MX"}
              ]
            }
        """.trimIndent()

        val result = parseStreamUrlResult(json)

        assertThat(result).isNotNull()
        assertThat(result!!.streams).hasSize(2)
        assertThat(result.streams.map { it.lang }).containsExactly("ja-JP", "es-MX").inOrder()
    }

    @Test
    fun `stream entry with blank url is skipped`() {
        val json = """
            {
              "url": "https://example.com/main.m3u8",
              "streams": [
                {"url": "   ", "lang": "blank-skipped"},
                {"url": "https://example.com/valid.m3u8", "lang": "valid"}
              ]
            }
        """.trimIndent()

        val result = parseStreamUrlResult(json)

        assertThat(result).isNotNull()
        assertThat(result!!.streams).hasSize(1)
        assertThat(result.streams.single().lang).isEqualTo("valid")
    }

    @Test
    fun `stream entry that is not a JSON object is skipped`() {
        val json = """
            {
              "url": "https://example.com/main.m3u8",
              "streams": [
                "not-an-object",
                42,
                null,
                {"url": "https://example.com/valid.m3u8", "lang": "valid"}
              ]
            }
        """.trimIndent()

        val result = parseStreamUrlResult(json)

        assertThat(result).isNotNull()
        assertThat(result!!.streams).hasSize(1)
        assertThat(result.streams.single().lang).isEqualTo("valid")
    }

    @Test
    fun `empty streams array yields empty streams list`() {
        val json = """{"url":"https://example.com/main.m3u8","streams":[]}"""

        val result = parseStreamUrlResult(json)

        assertThat(result).isNotNull()
        assertThat(result!!.streams).isEmpty()
    }

    // ─── Error / null paths ───────────────────────────────────────────────

    @Test
    fun `response with error field returns null`() {
        val json = """{"error":"extension not configured"}"""

        assertThat(parseStreamUrlResult(json)).isNull()
    }

    @Test
    fun `response with error field ignores any url present alongside it`() {
        // Some extensions include a partial url in error responses — we must
        // prefer the error and refuse to construct a StreamUrlResult.
        val json = """{"url":"https://example.com/maybe.m3u8","error":"rate limited"}"""

        assertThat(parseStreamUrlResult(json)).isNull()
    }

    @Test
    fun `response missing url returns null`() {
        val json = """{"headers":{"Referer":"https://x.com"}}"""

        assertThat(parseStreamUrlResult(json)).isNull()
    }

    @Test
    fun `response with blank url returns null`() {
        val json = """{"url":"   "}"""

        assertThat(parseStreamUrlResult(json)).isNull()
    }

    @Test
    fun `malformed JSON returns null instead of throwing`() {
        val json = """{not valid json"""

        assertThat(parseStreamUrlResult(json)).isNull()
    }

    @Test
    fun `empty string returns null`() {
        assertThat(parseStreamUrlResult("")).isNull()
    }

    @Test
    fun `JSON array instead of object returns null`() {
        // parseStreamUrlResult expects an object at the top level; an array
        // must not accidentally be coerced into a StreamUrlResult.
        assertThat(parseStreamUrlResult("""["not","an","object"]""")).isNull()
    }

    // ─── Subtitle edge cases ──────────────────────────────────────────────

    @Test
    fun `subtitle with language field instead of label is parsed`() {
        // The parser accepts both "label" (preferred) and "language" (legacy)
        // — this test pins the fallback behavior so we don't silently drop
        // subtitles from older extensions.
        val json = """
            {
              "url": "https://example.com/main.m3u8",
              "subtitles": [
                {"url": "https://example.com/de.vtt", "language": "German"}
              ]
            }
        """.trimIndent()

        val result = parseStreamUrlResult(json)

        assertThat(result).isNotNull()
        assertThat(result!!.subtitles).containsExactly(
            Track("https://example.com/de.vtt", "German")
        )
    }

    @Test
    fun `subtitle with blank label and language is skipped`() {
        val json = """
            {
              "url": "https://example.com/main.m3u8",
              "subtitles": [
                {"url": "https://example.com/no-lang.vtt"},
                {"url": "https://example.com/valid.vtt", "label": "English"}
              ]
            }
        """.trimIndent()

        val result = parseStreamUrlResult(json)

        assertThat(result).isNotNull()
        assertThat(result!!.subtitles).hasSize(1)
        assertThat(result.subtitles.single().lang).isEqualTo("English")
    }

    @Test
    fun `subtitle with blank url is skipped`() {
        val json = """
            {
              "url": "https://example.com/main.m3u8",
              "subtitles": [
                {"url": "   ", "label": "Skipped"},
                {"url": "https://example.com/valid.vtt", "label": "English"}
              ]
            }
        """.trimIndent()

        val result = parseStreamUrlResult(json)

        assertThat(result).isNotNull()
        assertThat(result!!.subtitles).hasSize(1)
        assertThat(result.subtitles.single().lang).isEqualTo("English")
    }

    @Test
    fun `subtitle entry that is not a JSON object is skipped`() {
        val json = """
            {
              "url": "https://example.com/main.m3u8",
              "subtitles": [
                "string-instead-of-object",
                42,
                null,
                {"url": "https://example.com/valid.vtt", "label": "English"}
              ]
            }
        """.trimIndent()

        val result = parseStreamUrlResult(json)

        assertThat(result).isNotNull()
        assertThat(result!!.subtitles).hasSize(1)
    }

    // ─── Headers edge cases ───────────────────────────────────────────────

    @Test
    fun `header with blank value is dropped`() {
        val json = """
            {
              "url": "https://example.com/main.m3u8",
              "headers": {
                "Referer": "https://megacloud.tv",
                "X-Blank": "   ",
                "User-Agent": "Mozilla/5.0"
              }
            }
        """.trimIndent()

        val result = parseStreamUrlResult(json)

        assertThat(result).isNotNull()
        assertThat(result!!.headers).containsExactly(
            "Referer" to "https://megacloud.tv",
            "User-Agent" to "Mozilla/5.0"
        )
    }

    @Test
    fun `headers absent yields empty header map`() {
        val json = """{"url":"https://example.com/main.m3u8"}"""

        val result = parseStreamUrlResult(json)

        assertThat(result).isNotNull()
        assertThat(result!!.headers).isEmpty()
    }

    @Test
    fun `headers value null returns empty header map without throwing`() {
        // org.json optString on a null JSON value can yield "null" or "" depending
        // on the variant — verify we never produce a literal "null" header value.
        val json = """{"url":"https://example.com/main.m3u8","headers":null}"""

        val result = parseStreamUrlResult(json)

        assertThat(result).isNotNull()
        assertThat(result!!.headers).isEmpty()
    }

    // ─── Default value sanity ─────────────────────────────────────────────

    @Test
    fun `stream isDefault defaults to false when field absent`() {
        val json = """
            {
              "url": "https://example.com/main.m3u8",
              "streams": [
                {"url": "https://example.com/a.m3u8", "lang": "ja-JP"}
              ]
            }
        """.trimIndent()

        val result = parseStreamUrlResult(json)

        assertThat(result).isNotNull()
        assertThat(result!!.streams.single().isDefault).isFalse()
    }

    @Test
    fun `stream lang defaults to empty string when field absent`() {
        // Some extensions omit "lang" for the primary stream — we keep it as
        // empty string rather than dropping the entry, so the player can still
        // pick it as the "no language preference" default.
        val json = """
            {
              "url": "https://example.com/main.m3u8",
              "streams": [
                {"url": "https://example.com/unknown-lang.m3u8"}
              ]
            }
        """.trimIndent()

        val result = parseStreamUrlResult(json)

        assertThat(result).isNotNull()
        assertThat(result!!.streams.single().lang).isEqualTo("")
    }
}
