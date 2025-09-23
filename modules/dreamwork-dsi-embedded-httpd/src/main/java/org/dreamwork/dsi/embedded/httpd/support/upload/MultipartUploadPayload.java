package org.dreamwork.dsi.embedded.httpd.support.upload;

import org.apache.tomcat.util.http.fileupload.FileItem;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultipartUploadPayload implements Serializable {
    private final Map<String, String> map = new HashMap<> ();
    private final List<FileItem> files = new ArrayList<> ();

    public void setParameter (String name, String value) {
        map.put (name, value);
    }

    public String getParameter (String name) {
        return map.get (name);
    }

    public boolean isParameterPresent (String name) {
        return map.containsKey (name);
    }

    public void addFile (FileItem file) {
        files.add (file);
    }

    public List<FileItem> getFiles () {
        return files;
    }
}