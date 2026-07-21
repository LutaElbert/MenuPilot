/*
 * Compatibility shim for the Android ADK 0.5.0 runtime.
 *
 * ADK instantiates this historical class when it serializes a generated tool schema. The upstream
 * kxml2 artifact also packages org.xmlpull.v1, which Android already provides and R8 rejects as a
 * program/library duplicate. Keeping this tiny adapter lets the app use the platform XmlSerializer
 * without packaging a second copy of the framework API.
 */
package org.kxml2.io;

import android.util.Xml;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import org.xmlpull.v1.XmlSerializer;

@SuppressWarnings("deprecation")
public final class KXmlSerializer implements XmlSerializer {
    private final XmlSerializer delegate = Xml.newSerializer();

    @Override
    public void setFeature(String name, boolean state) {
        delegate.setFeature(name, state);
    }

    @Override
    public boolean getFeature(String name) {
        return delegate.getFeature(name);
    }

    @Override
    public void setProperty(String name, Object value) {
        delegate.setProperty(name, value);
    }

    @Override
    public Object getProperty(String name) {
        return delegate.getProperty(name);
    }

    @Override
    public void setOutput(OutputStream outputStream, String encoding) throws IOException {
        delegate.setOutput(outputStream, encoding);
    }

    @Override
    public void setOutput(Writer writer) throws IOException {
        delegate.setOutput(writer);
    }

    @Override
    public void startDocument(String encoding, Boolean standalone) throws IOException {
        delegate.startDocument(encoding, standalone);
    }

    @Override
    public void endDocument() throws IOException {
        delegate.endDocument();
    }

    @Override
    public void setPrefix(String prefix, String namespace) throws IOException {
        delegate.setPrefix(prefix, namespace);
    }

    @Override
    public String getPrefix(String namespace, boolean generatePrefix) {
        return delegate.getPrefix(namespace, generatePrefix);
    }

    @Override
    public int getDepth() {
        return delegate.getDepth();
    }

    @Override
    public String getNamespace() {
        return delegate.getNamespace();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public XmlSerializer startTag(String namespace, String name) throws IOException {
        delegate.startTag(namespace, name);
        return this;
    }

    @Override
    public XmlSerializer attribute(String namespace, String name, String value) throws IOException {
        delegate.attribute(namespace, name, value);
        return this;
    }

    @Override
    public XmlSerializer endTag(String namespace, String name) throws IOException {
        delegate.endTag(namespace, name);
        return this;
    }

    @Override
    public XmlSerializer text(String text) throws IOException {
        delegate.text(text);
        return this;
    }

    @Override
    public XmlSerializer text(char[] buffer, int start, int length) throws IOException {
        delegate.text(buffer, start, length);
        return this;
    }

    @Override
    public void cdsect(String text) throws IOException {
        delegate.cdsect(text);
    }

    @Override
    public void entityRef(String text) throws IOException {
        delegate.entityRef(text);
    }

    @Override
    public void processingInstruction(String text) throws IOException {
        delegate.processingInstruction(text);
    }

    @Override
    public void comment(String text) throws IOException {
        delegate.comment(text);
    }

    @Override
    public void docdecl(String text) throws IOException {
        delegate.docdecl(text);
    }

    @Override
    public void ignorableWhitespace(String text) throws IOException {
        delegate.ignorableWhitespace(text);
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }
}
