package com.dipandhali.hermesnative.files

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The workspace file panel's wire contract: relative paths everywhere,
 * entries typed `dir|file|symlink` (symlinks carry their own `is_dir`),
 * text reads capped by the server with its own wording, raw files served
 * as bytes, git optional, and the four JSON mutations each posting their
 * minimal body.
 */
class FilesApiTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun api() = HermesApi(OkHttpClient())

    @Test
    fun `list decodes entries with signature and workspace`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """{"entries":[""" +
                        """{"name":"sub","path":"sub","type":"dir","size":null,"mtime_ns":1730000000000000000,"workspace_sort_rank":1},""" +
                        """{"name":"notes.md","path":"sub/notes.md","type":"file","size":42,"mtime_ns":1730000001000000000,"workspace_sort_rank":2},""" +
                        """{"name":"link","path":"link","type":"symlink","is_dir":true,"workspace_sort_rank":0,"target_outside_workspace":false,"mtime_ns":1730000002000000000}""" +
                        """],"signature":"sig-1","path":"sub","workspace":"/home/dipandhali/work","workspace_recovered":false}""",
                )
                .build(),
        )
        val result = api().listFiles(server.url("/").toString(), sessionId = "s1", path = "sub").getOrThrow()

        assertEquals("sig-1", result.signature)
        assertEquals("/home/dipandhali/work", result.workspace)
        assertEquals(3, result.entries.size)
        assertTrue(result.entries[0].entryIsDir)
        assertFalse(result.entries[1].entryIsDir)
        assertEquals(42L, result.entries[1].size)
        assertTrue(result.entries[2].entryIsDir) // a symlink to a directory lists like one
        assertEquals("symlink", result.entries[2].type)

        assertEquals("/api/list?session_id=s1&path=sub", server.takeRequest().target)
    }

    @Test
    fun `file read decodes content size and lines`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"path":"sub/notes.md","content":"hello\nworld\n","size":13,"lines":2}""")
                .build(),
        )
        val result = api().readFile(server.url("/").toString(), sessionId = "s1", path = "sub/notes.md").getOrThrow()

        assertEquals("hello\nworld\n", result.content)
        assertEquals(13L, result.size)
        assertEquals(2L, result.lines)
        assertEquals("/api/file?session_id=s1&path=sub%2Fnotes.md", server.takeRequest().target)
    }

    @Test
    fun `an oversized read surfaces the server wording`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(404)
                .body("""{"error":"File too large (500112 bytes, max 400000)"}""")
                .build(),
        )
        val result = api().readFile(server.url("/").toString(), sessionId = "s1", path = "big.bin")

        val error = (result.exceptionOrNull() as? ApiException)?.error
        assertTrue(error is DataError.Server)
        assertEquals(404, (error as DataError.Server).status)
        assertEquals("File too large (500112 bytes, max 400000)", error.message)
    }

    @Test
    fun `raw download returns the bytes`() = runTest {
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0A, 0x1A)
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(okio.Buffer().write(pngBytes))
                .build(),
        )
        val bytes = api().downloadFile(server.url("/").toString(), sessionId = "s1", path = "img.png").getOrThrow()

        assertTrue(pngBytes.contentEquals(bytes))
        assertEquals("/api/file/raw?session_id=s1&path=img.png", server.takeRequest().target)
    }

    @Test
    fun `git decodes the status object and tolerates null`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"git":{"branch":"main","dirty":3,"modified":2,"untracked":1,"ahead":0,"behind":1,"is_git":true}}""")
                .build(),
        )
        val withGit = api().gitInfo(server.url("/").toString(), sessionId = "s1").getOrThrow()
        assertEquals("main", withGit.git?.branch)
        assertEquals(3, withGit.git?.dirty)
        assertEquals("/api/git-info?session_id=s1", server.takeRequest().target)

        server.enqueue(MockResponse.Builder().code(200).body("""{"git":null}""").build())
        val noGit = api().gitInfo(server.url("/").toString(), sessionId = "s1").getOrThrow()
        assertNull(noGit.git)
    }

    @Test
    fun `create posts session path and content`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body("""{"ok":true,"path":"notes.md"}""").build(),
        )
        api().createFile(server.url("/").toString(), sessionId = "s1", path = "notes.md", content = "hi")
        assertEquals(
            """{"session_id":"s1","path":"notes.md","content":"hi"}""",
            server.takeRequest().body?.utf8(),
        )
    }

    @Test
    fun `create-dir posts the minimal body`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body("""{"ok":true,"path":"sub"}""").build(),
        )
        api().createDir(server.url("/").toString(), sessionId = "s1", path = "sub")
        assertEquals(
            """{"session_id":"s1","path":"sub"}""",
            server.takeRequest().body?.utf8(),
        )
    }

    @Test
    fun `rename posts the new name`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body("""{"ok":true,"old_path":"sub/notes.md","new_path":"sub/renamed.md"}""").build(),
        )
        api().renameFile(server.url("/").toString(), sessionId = "s1", path = "sub/notes.md", newName = "renamed.md")
        assertEquals(
            """{"session_id":"s1","path":"sub/notes.md","new_name":"renamed.md"}""",
            server.takeRequest().body?.utf8(),
        )
    }

    @Test
    fun `delete posts path and only carries recursive for directories`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body("""{"ok":true,"path":"notes.md"}""").build(),
        )
        api().deleteEntry(server.url("/").toString(), sessionId = "s1", path = "notes.md", recursive = false)
        assertEquals(
            """{"session_id":"s1","path":"notes.md"}""",
            server.takeRequest().body?.utf8(),
        )

        server.enqueue(
            MockResponse.Builder().code(200).body("""{"ok":true,"path":"sub"}""").build(),
        )
        api().deleteEntry(server.url("/").toString(), sessionId = "s1", path = "sub", recursive = true)
        assertEquals(
            """{"session_id":"s1","path":"sub","recursive":true}""",
            server.takeRequest().body?.utf8(),
        )
    }

    @Test
    fun `workspace upload posts session path and file parts`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"filename":"a.png","path":"/home/dipandhali/work/sub/a.png","size":6,"mime":"image/png","is_image":true}""")
                .build(),
        )
        val bytes = "PNGGY!".toByteArray()
        val result = api().uploadWorkspaceFile(
            server.url("/").toString(),
            sessionId = "s1",
            subPath = "sub",
            fileName = "a.png",
            mimeType = "image/png",
            bytes = bytes,
        ).getOrThrow()

        assertEquals("a.png", result.filename)
        assertEquals(6L, result.size)
        assertTrue(result.isImage == true)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.headers["Content-Type"].orEmpty().startsWith("multipart/form-data"))
        val body = recorded.body!!.utf8()
        assertTrue(body.contains("""name="session_id""""))
        assertTrue(body.contains("s1"))
        assertTrue(body.contains("""name="path""""))
        assertTrue(body.contains("""filename="a.png""""))
        assertTrue(body.contains("PNGGY!"))
    }
}
