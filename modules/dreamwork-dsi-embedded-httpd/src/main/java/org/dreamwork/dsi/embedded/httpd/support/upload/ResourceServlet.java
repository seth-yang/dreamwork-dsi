package org.dreamwork.dsi.embedded.httpd.support.upload;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.dreamwork.dsi.embedded.httpd.support.InjectableServlet;
import org.dreamwork.dsi.embedded.httpd.support.WebJsonResult;
import org.dreamwork.misc.MimeType;
import org.dreamwork.misc.MimeTypeManager;
import org.dreamwork.util.FileInfo;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
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

public abstract class ResourceServlet extends InjectableServlet {
    private final Logger logger = LoggerFactory.getLogger (ResourceServlet.class);

    @Resource
    protected FileUploader uploader;

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