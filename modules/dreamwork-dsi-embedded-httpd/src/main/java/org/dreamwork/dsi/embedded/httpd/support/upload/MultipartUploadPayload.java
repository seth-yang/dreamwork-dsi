package org.dreamwork.dsi.embedded.httpd.support.upload;

import jakarta.servlet.http.Part;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultipartUploadPayload implements Serializable {
    private final Map<String, String> map = new HashMap<> ();
    private final List<Part> files = new ArrayList<> ();

    public void setParameter (String name, String value) {
        map.put (name, value);
    }

    public String getParameter (String name) {
        return map.get (name);
    }

    public boolean isParameterPresent (String name) {
        return map.containsKey (name);
    }

    public void addFile (Part file) {
        files.add (file);
    }

    public List<Part> getFiles () {
        return files;
    }
}