package org.dreamwork.dsi.embedded.httpd.support.upload;

import jakarta.annotation.Resource;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.dreamwork.dsi.embedded.httpd.support.InjectableServlet;
import org.dreamwork.dsi.embedded.httpd.support.WebJsonResult;
import org.dreamwork.misc.MimeType;
import org.dreamwork.misc.MimeTypeManager;
import org.dreamwork.util.FileInfo;
import org.dreamwork.util.JsonHelper;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.dreamwork.util.CollectionHelper.isNotEmpty;

public abstract class ResourceServlet extends InjectableServlet {
    private final Logger logger = LoggerFactory.getLogger (ResourceServlet.class);

    @Resource
    protected FileUploader uploader;



    @Override
    protected void doPost (HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        // 上传
        MultipartUploadPayload payload = uploader.parseMultipart (request);
        WebJsonResult result;
        if (isNotEmpty (payload.getFiles ())) {
            Map<String, String> map = new HashMap<> ();
            for (Part part : payload.getFiles ()) {
                String name = uploader.saveTemporaryFile (part);
                map.put (part.getName (), name);
            }

            try {
                Object ret = onFilesSaved (map);
                result = new WebJsonResult (0, "success", ret);
            } catch (Exception ex) {
                result = new WebJsonResult (500, "Internal Error", null);
                logger.warn (ex.getMessage (), ex);
            }
        } else {
            result = new WebJsonResult (400, "无效的参数", null);
        }

        if (result.code != 0) {
            response.setStatus (HttpServletResponse.SC_BAD_REQUEST);
        }
        String content = JsonHelper.toJson (result);
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
        String pathInfo = request.getPathInfo ();
        if (StringUtil.isNotEmpty (pathInfo)) {
            Path path = Paths.get (uploader.basedir, pathInfo);
            if (Files.exists (path)) {
                request.setAttribute ("resource", path);
                return path.toFile ().lastModified ();
            }
        }

        return super.getLastModified (request);
    }

    /**
     * 当临时文件全部被保存后触发的事件
     * @param savedFiles 所有成功保存的临时文件的信息，以客户端字段名为 key
     * @return 即将返回给客户端，处理后的结果
     */
    protected Object onFilesSaved (Map<String, String> savedFiles) {
        // return the savedFiles itself by default
        return savedFiles;
    }
}