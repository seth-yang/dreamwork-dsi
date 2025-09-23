package org.dreamwork.dsi.embedded.httpd.support.upload;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.dreamwork.dsi.embedded.httpd.support.WebJsonResult;
import org.dreamwork.injection.IObjectContext;
import org.dreamwork.misc.MimeType;
import org.dreamwork.misc.MimeTypeManager;
import org.dreamwork.util.FileInfo;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.gson.ToNumberPolicy.LONG_OR_DOUBLE;
import static org.dreamwork.util.CollectionHelper.isNotEmpty;

public abstract class ResourceServlet extends HttpServlet {
    private final Logger logger = LoggerFactory.getLogger (ResourceServlet.class);
    protected FileUploader uploader;

    @Override
    public void init (ServletConfig config) throws ServletException {
        super.init (config);

        ServletContext webapp = config.getServletContext ();
        IObjectContext context = (IObjectContext) webapp.getAttribute (IObjectContext.class.getCanonicalName ());
        uploader = context.getBean (FileUploader.class);

        if (uploader == null) {
            logger.warn ("Cannot find the upload service. Is the package org.dreamwork.dsi.embedded.httpd.support.upload included in the scan packages and annotated in your main class?");
            throw new RuntimeException ("Cannot find the upload service");
        }
    }

    @Override
    protected void doPost (HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 上传
        MultipartUploadPayload payload = uploader.parse (request);
        List<FileItem> files = payload.getFiles ();
        WebJsonResult result;
        if (isNotEmpty (files)) {
            Map<String, String> map = new HashMap<> ();
            for (FileItem file : files) {
                String name = uploader.saveTemporaryFile (file);
                map.put (file.getFieldName (), name);
            }

            result = new WebJsonResult (0, "success", map);
        } else {
            result = new WebJsonResult (400, "无效的参数", null);
        }

        if (result.code != 0) {
            response.setStatus (HttpServletResponse.SC_BAD_REQUEST);
        }
        Gson gson = new GsonBuilder ().setObjectToNumberStrategy (LONG_OR_DOUBLE).create ();
        String content = gson.toJson (result);
        response.setContentType ("application/json;charset=utf-8");
        response.setContentLength (content.getBytes (StandardCharsets.UTF_8).length);
        response.getWriter ().println (content);
    }

    @Override
    protected void doGet (HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 下载
        String pathInfo = request.getPathInfo ();
        Path path = (Path) request.getAttribute ("resource");
        if (path != null && Files.exists (path)) {
            String ext = FileInfo.getExtension (pathInfo);
            MimeType type = MimeTypeManager.getMimeType (ext);
            if (type != null) {
                response.setContentType (type.getName ());
            }
            response.setContentLengthLong (path.toFile ().length ());
            response.resetBuffer ();
            Files.copy (path, response.getOutputStream ());
        }
        response.setStatus (HttpServletResponse.SC_NOT_FOUND);
    }

    @Override
    protected long getLastModified (HttpServletRequest request) {
        String pathinfo = request.getPathInfo ();
        if (StringUtil.isNotEmpty (pathinfo)) {
            Path path = Paths.get (uploader.basedir, pathinfo);
            if (Files.exists (path)) {
                request.setAttribute ("resource", path);
                return path.toFile ().lastModified ();
            }
        }

        return super.getLastModified (request);
    }
}