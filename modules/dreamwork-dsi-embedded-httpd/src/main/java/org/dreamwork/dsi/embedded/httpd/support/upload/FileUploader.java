package org.dreamwork.dsi.embedded.httpd.support.upload;

import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.dreamwork.injection.AConfigured;
import org.dreamwork.misc.MimeType;
import org.dreamwork.misc.MimeTypeManager;
import org.dreamwork.util.FileInfo;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.dreamwork.util.StringUtil.isNotEmpty;

@Resource
public class FileUploader {
    private final Logger logger = LoggerFactory.getLogger (FileUploader.class);
    private final DiskFileItemFactory factory;
    private final File tmpdir = new File (System.getProperty ("java.io.tmpdir"));

    @AConfigured ("${embedded.httpd.uploader.base.dir}")
    public String basedir;

    @AConfigured ("${embedded.httpd.uploader.max.file.size}")
    public int maxFileSize = 1 << 24;   // 默认 16M

    public FileUploader () {
        factory = new DiskFileItemFactory (1 << 24, tmpdir);
        factory.setDefaultCharset ("utf-8");
    }

    @PostConstruct
    public void init () throws IOException {
        Path path = Paths.get (basedir);
        if (Files.notExists (path)) {
            Files.createDirectories (path);
        }
    }

    public ServletFileUpload create () {
        ServletFileUpload uploader = new ServletFileUpload (factory);
        uploader.setHeaderEncoding ("utf-8");
        uploader.setFileSizeMax (maxFileSize);  // 16M
        uploader.setSizeMax (maxFileSize);      // 16M
        return uploader;
    }

    public MultipartUploadPayload parse (HttpServletRequest request) throws IOException {
        ServletFileUpload uploader = create ();
        MultipartUploadPayload payload = new MultipartUploadPayload ();
        Map<String, List<FileItem>> map = uploader.parseParameterMap (request);
        for (List<FileItem> values : map.values ()) {
            if (values.isEmpty ()) continue;
            FileItem item = values.get (0);
            if (item.isFormField ()) {
                // 表单字段
                String name = item.getFieldName (), value = item.getString ("utf-8");
                if (!StringUtil.isEmpty (value)) {
                    payload.setParameter (name, value.trim ());
                }
            } else {
                payload.addFile (item);
            }
        }

        return payload;
    }

    public Path checkTarget (Path path) throws IOException {
        Path parent = path.getParent ();
        if (Files.notExists (parent)) {
            Files.createDirectories (parent);
        }

        return parent;
    }

    public Path checkTarget (String file) throws IOException {
        Path target = Paths.get (basedir, file);
        return checkTarget (target);
    }

    public String saveTemporaryFile (FileItem file) throws IOException {
        String contentType = file.getContentType (), ext = null;
        if (isNotEmpty (contentType)) {
            contentType = contentType.trim ();
            int pos = contentType.indexOf (";");
            if (pos > 0) {
                contentType = contentType.substring (0, pos);
            }

            if (isNotEmpty (contentType)) {
                MimeType type = MimeTypeManager.getMimeTypeByName (contentType);
                if (type != null) {
                    ext = type.getExt ();
                }
            }
        }
        if (StringUtil.isEmpty (ext)) {
            String filename = FileInfo.getFileNameWithoutPath (file.getName ());
            if (filename.contains (".")) {
                int index = filename.lastIndexOf ('.');
                ext = filename.substring (index + 1);
            }
        }

        String filename = StringUtil.uuid ();
        if (isNotEmpty (ext)) {
            filename += "." + ext;
        }
        Path target = Paths.get (tmpdir.getCanonicalPath (), "uploaded", filename);
        try {
            checkTarget (target);
            file.write (target.toFile ());
        } catch (Exception ex) {
            logger.warn (ex.getMessage (), ex);
            throw new IOException (ex);
        }

        return tmpdir.toPath ().relativize (target).toString ().replace ('\\', '/');
    }

    public String move (String source, String target) throws IOException {
        checkTarget (target);
        Path from = Paths.get (tmpdir.getCanonicalPath (), source);
        Path to   = Paths.get (basedir, target);
        Files.move (from, to);
        return Paths.get (basedir).relativize (to).toString ().replace ('\\', '/');
    }

    public void remove (String resource) throws IOException {
        Path path = Paths.get (basedir, resource);
        Files.deleteIfExists (path);
    }
}