package org.dreamwork.dsi.embedded.httpd.support.upload;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FileUploader} 的单元测试。
 */
class FileUploaderTest {
    @TempDir
    Path tempDir;

    private FileUploader uploader;

    @BeforeEach
    void setUp () {
        uploader = new FileUploader ();
        // basedir 是 public 字段，测试中直接指向临时目录
        uploader.basedir = tempDir.toString ();
    }

    // ------------------------------------------------------
    // checkTarget(Path)
    // ------------------------------------------------------

    @Test
    void checkTargetKeepsPathWhenNotExists () throws Exception {
        Path target = tempDir.resolve ("a/b/c.txt");
        Path result = FileUploader.checkTarget (target);
        assertEquals (target, result);
        assertTrue (Files.isDirectory (tempDir.resolve ("a/b")));
    }

    @Test
    void checkTargetRenamesOnCollision () throws Exception {
        Path first = tempDir.resolve ("a.txt");
        Files.writeString (first, "1");
        Path second = tempDir.resolve ("a-0.txt");
        Files.writeString (second, "2");

        Path result = FileUploader.checkTarget (tempDir.resolve ("a.txt"));
        assertEquals (tempDir.resolve ("a-1.txt"), result);
        assertTrue (Files.notExists (tempDir.resolve ("a-2.txt")));
    }

    @Test
    void checkTargetHandlesTarGzExtension () throws Exception {
        Path exists = tempDir.resolve ("foo.tar.gz");
        Files.writeString (exists, "1");

        Path result = FileUploader.checkTarget (tempDir.resolve ("foo.tar.gz"));
        assertEquals (tempDir.resolve ("foo-0.tar.gz"), result);
    }

    // ------------------------------------------------------
    // checkTarget(String) / remove
    // ------------------------------------------------------

    @Test
    void checkTargetStringResolvesAgainstBasedir () throws Exception {
        Path result = uploader.checkTarget ("sub/x.txt");
        assertEquals (tempDir.resolve ("sub/x.txt"), result);
        assertTrue (Files.isDirectory (tempDir.resolve ("sub")));
    }

    @Test
    void removeDeletesExistingFile () throws Exception {
        Path file = tempDir.resolve ("obsolete.txt");
        Files.writeString (file, "1");

        uploader.remove ("obsolete.txt");
        assertTrue (Files.notExists (file));
    }

    @Test
    void removeIsIdempotentForMissingFile () {
        assertDoesNotThrow (() -> uploader.remove ("not-exist.txt"));
    }

    // ------------------------------------------------------
    // saveTemporaryFile
    // ------------------------------------------------------

    @Test
    void saveTemporaryFileWritesPartAndReturnsRelativePath () throws Exception {
        String filename = "upload-" + UUID.randomUUID () + ".txt";
        Part part = mock (Part.class);
        when (part.getSubmittedFileName ()).thenReturn (filename);

        String result = uploader.saveTemporaryFile (part);

        // 写出目标应位于系统临时目录的 uploaded 子目录下（不同平台上 tmpdir 的
        // canonical 路径可能有差异，这里只断言文件名与相对前缀）
        assertTrue (result.contains ("uploaded"));
        assertTrue (result.endsWith (filename));
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass (String.class);
        verify (part).write (captor.capture ());
        String written = captor.getValue ().replace ('\\', '/');
        assertTrue (written.endsWith ("/uploaded/" + filename));
        verify (part).delete ();
    }

    @Test
    void saveTemporaryFileWithoutNameGeneratesUuid () throws Exception {
        Part part = mock (Part.class);
        when (part.getSubmittedFileName ()).thenReturn ("");
        when (part.getContentType ()).thenReturn ("text/plain");
        when (part.getName ()).thenReturn ("fileField");

        String result = uploader.saveTemporaryFile (part);

        assertNotNull (result);
        assertTrue (result.startsWith ("uploaded/"));
        verify (part).delete ();
    }

    // ------------------------------------------------------
    // move
    // ------------------------------------------------------

    @Test
    void moveRelocatesTemporaryFileToBasedir () throws Exception {
        String sourceName = "src-" + UUID.randomUUID () + ".txt";
        Path uploadedDir = Paths.get (System.getProperty ("java.io.tmpdir"), "uploaded");
        Files.createDirectories (uploadedDir);
        Path source = uploadedDir.resolve (sourceName);
        Files.writeString (source, "hello");

        String result = uploader.move ("uploaded/" + sourceName, "dst/moved.txt");

        assertEquals ("dst/moved.txt", result);
        assertTrue (Files.notExists (source));
        assertEquals ("hello", Files.readString (tempDir.resolve ("dst/moved.txt")));
    }

    @Test
    void moveFailsWhenTargetAlreadyExists () throws Exception {
        // 说明：当前实现里 checkTarget(target) 的改名结果并未被使用，
        // Files.move 默认不覆盖已存在的目标，因此这里会抛出 FileAlreadyExistsException
        String sourceName = "src-" + UUID.randomUUID () + ".txt";
        Path uploadedDir = Paths.get (System.getProperty ("java.io.tmpdir"), "uploaded");
        Files.createDirectories (uploadedDir);
        Path source = uploadedDir.resolve (sourceName);
        Files.writeString (source, "new");
        Files.createDirectories (tempDir.resolve ("dst"));
        Files.writeString (tempDir.resolve ("dst/moved.txt"), "old");

        assertThrows (java.nio.file.FileAlreadyExistsException.class,
                () -> uploader.move ("uploaded/" + sourceName, "dst/moved.txt"));
        // 原文件与既有目标都应保持不变
        assertEquals ("new", Files.readString (source));
        assertEquals ("old", Files.readString (tempDir.resolve ("dst/moved.txt")));
    }

    // ------------------------------------------------------
    // parseMultipart
    // ------------------------------------------------------

    @Test
    void parseMultipartCollectsFilesAndParameters () throws Exception {
        HttpServletRequest request = mock (HttpServletRequest.class);
        Part fileA = mock (Part.class);
        Part fileB = mock (Part.class);
        when (request.getParts ()).thenReturn (List.of (fileA, fileB));
        when (request.getParameterNames ()).thenReturn (Collections.enumeration (List.of ("name", "age")));
        when (request.getParameter ("name")).thenReturn ("tom");
        when (request.getParameter ("age")).thenReturn ("18");

        MultipartUploadPayload payload = uploader.parseMultipart (request);

        assertEquals (2, payload.getFiles ().size ());
        assertTrue (payload.getFiles ().contains (fileA));
        assertTrue (payload.getFiles ().contains (fileB));
        assertEquals ("tom", payload.getParameter ("name"));
        assertEquals ("18", payload.getParameter ("age"));
        assertTrue (payload.isParameterPresent ("name"));
        assertFalse (payload.isParameterPresent ("missing"));
    }

    @Test
    void parseMultipartWithEmptyRequest () throws Exception {
        HttpServletRequest request = mock (HttpServletRequest.class);
        when (request.getParts ()).thenReturn (List.of ());
        when (request.getParameterNames ()).thenReturn (Collections.emptyEnumeration ());

        MultipartUploadPayload payload = uploader.parseMultipart (request);

        assertTrue (payload.getFiles ().isEmpty ());
        assertFalse (payload.isParameterPresent ("any"));
    }

    @Test
    void parseMultipartPropagatesRequestFailure () throws Exception {
        HttpServletRequest request = mock (HttpServletRequest.class);
        when (request.getParts ()).thenThrow (new IOException ("broken pipe"));

        assertThrows (IOException.class, () -> uploader.parseMultipart (request));
    }
}
