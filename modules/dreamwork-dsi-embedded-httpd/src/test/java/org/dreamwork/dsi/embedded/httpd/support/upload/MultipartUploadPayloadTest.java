package org.dreamwork.dsi.embedded.httpd.support.upload;

import jakarta.servlet.http.Part;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * {@link MultipartUploadPayload} 的单元测试。
 */
class MultipartUploadPayloadTest {

    @Test
    void setAndGetParameter () {
        MultipartUploadPayload payload = new MultipartUploadPayload ();
        payload.setParameter ("name", "tom");
        assertEquals ("tom", payload.getParameter ("name"));
    }

    @Test
    void getParameterReturnsNullForMissing () {
        MultipartUploadPayload payload = new MultipartUploadPayload ();
        assertNull (payload.getParameter ("missing"));
        assertFalse (payload.isParameterPresent ("missing"));
    }

    @Test
    void setParameterOverwritesPreviousValue () {
        MultipartUploadPayload payload = new MultipartUploadPayload ();
        payload.setParameter ("name", "tom");
        payload.setParameter ("name", "jerry");
        assertEquals ("jerry", payload.getParameter ("name"));
    }

    @Test
    void isParameterPresentForNullValue () {
        MultipartUploadPayload payload = new MultipartUploadPayload ();
        payload.setParameter ("name", null);
        assertTrue (payload.isParameterPresent ("name"));
        assertNull (payload.getParameter ("name"));
    }

    @Test
    void collectsUploadedFilesInOrder () {
        MultipartUploadPayload payload = new MultipartUploadPayload ();
        Part a = mock (Part.class);
        Part b = mock (Part.class);
        payload.addFile (a);
        payload.addFile (b);

        assertEquals (2, payload.getFiles ().size ());
        assertSame (a, payload.getFiles ().get (0));
        assertSame (b, payload.getFiles ().get (1));
    }

    @Test
    void newPayloadIsEmpty () {
        MultipartUploadPayload payload = new MultipartUploadPayload ();
        assertTrue (payload.getFiles ().isEmpty ());
        assertFalse (payload.isParameterPresent ("any"));
    }
}
